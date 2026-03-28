# Collector Design

## 1. 목적

Kafka로 전달된 k6 메트릭을 실시간으로 수집하고,
정규화 후 InfluxDB v3에 적재하는 스트리밍 처리 컴포넌트이다.

이 모듈은 다음 역할에 집중한다:

* 실시간 메트릭 ingestion
* 최소 가공(normalization)
* 안정적인 write (retry + batch)
* 자체 상태 관측 가능성 확보

---

## 2. 아키텍처 개요

```
k6 → Kafka → Collector → InfluxDB → Grafana
```

Collector는 **stateless stream processor**로 동작하며,
데이터 저장 책임은 InfluxDB에 위임한다.

---

## 3. 모듈 구조

```
com/pipeline/collector/
├── Application.kt
├── config/
├── model/
├── kafka/
├── processor/
├── influx/
└── metrics/
```

### 역할 분리

| 모듈        | 역할                  |
| --------- | ------------------- |
| kafka     | 메시지 수신 (Ingress)    |
| processor | 데이터 변환 (Pure logic) |
| influx    | 데이터 적재 (Egress)     |
| metrics   | 내부 상태 관측            |
| config    | 설정 로딩 및 변환          |

---

## 4. 데이터 흐름

```
KafkaConsumer
  → MetricsProcessor
    → InfluxWriter (buffer)
      → batch flush
        → InfluxDB
```

### 특징

* pull 기반 처리 (poll loop)
* synchronous 처리 모델
* batch write 적용
* offset commit은 write 이후 수행

---

## 5. 처리 보장 모델

```
Delivery Semantics: At-Least-Once
```

### 동작 방식

* Kafka offset commit은 Influx write 성공 이후 수행
* write 실패 시 commit하지 않음 → 재처리 발생
* 동일 데이터 중복 저장 가능

### 의도

* 데이터 유실 방지 우선
* 중복 허용 (Influx 특성상 큰 문제 아님)

---

## 6. 데이터 정규화 전략

### 입력

* k6 Kafka output (JSON)

### 출력

* InfluxDB Line Protocol

### 규칙

| 항목        | 처리                 |
| --------- | ------------------ |
| metric    | 공백 제거              |
| tag       | 공백 → `_`, null 제거  |
| timestamp | ISO → nanosecond   |
| value     | Double, NaN/Inf 제외 |
| url       | query 제거           |

### 의도

* cardinality 폭발 방지
* 저장 구조 단순화
* query 기반 분석 유도

---

## 7. Batch 처리 전략

### Flush 조건

| 조건             | 값      |
| -------------- | ------ |
| batch-size     | 500    |
| flush-interval | 1000ms |

둘 중 먼저 만족 시 flush

### 의도

* write 성능 확보
* latency와 throughput 균형

---

## 8. 재시도 정책

```
max-retries: 3
backoff: 1s → 2s → 4s
```

### 대상

* HTTP 429
* HTTP 5xx

### 제외

* 400 / 401 / 403

### 실패 시

* dead-letter 로그 기록
* batch 폐기

---

## 9. 장애 처리 전략

### 1. JSON 파싱 실패

* 해당 메시지 skip
* 실패 카운트 증가

### 2. Influx write 실패

* retry 수행
* 실패 시 dead-letter 기록

### 3. batch flush 실패

* batch 폐기
* offset commit 수행

### 결과

* 일부 데이터 유실 가능
* 무한 재처리 방지

---

## 10. 내부 메트릭

### measurement: `collector_stats`

| 필드              | 설명         |
| --------------- | ---------- |
| processed_total | 처리 건수      |
| failed_total    | 실패 건수      |
| tps             | 처리량        |
| buffer_size     | 버퍼 상태      |
| kafka_lag       | 현재 0 (미구현) |
| last_flush_ms   | flush 시간   |

### 특징

* InfluxDB에 동일 경로로 저장
* Grafana에서 직접 조회 가능

---

## 11. 설계 의도

### 1. 단순성 유지

* Spring / DI 미사용
* 최소한의 구조

### 2. 상태 최소화

* Collector는 상태를 거의 가지지 않음
* Kafka + Influx에 책임 분산

### 3. 실시간 처리 우선

* batch는 있지만 지연 최소화

### 4. 확장 가능성 확보

* Kafka lag 추가 가능
* DLQ Kafka 전환 가능

---

## 12. 트레이드오프

### 1. Exactly-Once 미지원

* 구현 복잡도 증가 방지
* 대신 At-Least-Once 채택

### 2. DLQ → 파일 기반

* 단순 구현
* 운영 환경에서는 Kafka DLQ 필요

### 3. Aggregation 미적용

* Collector 단순화
* 대신 Influx query 비용 증가

### 4. Kafka lag 미구현

* 초기 구현 단순화
* 추후 AdminClient 확장 필요

---

## 13. 확장 방향

* Kafka AdminClient 기반 lag 측정
* DLQ → Kafka topic 전환
* metric aggregation (window 기반)
* multi-field metric 지원
* Prometheus exporter 추가

---

## 14. 고려사항

* k6 Kafka 메시지 포맷은 버전별 차이가 있음 → 최초 연동 시 raw 로그 확인 필요
* URL tag는 cardinality 증가 요인 → query 제거 적용, 필요 시 path 정규화 고려
* Kafka lag은 현재 미구현 → backlog 모니터링 필요 시 AdminClient 확장
* Influx schema는 단일 field 구조 → 복잡한 분석은 query 레이어에서 처리
* DLQ는 파일 기반 → 운영 환경에서는 Kafka DLQ 전환 필요

---

## 15. 처리 모델 (Buffer & Backpressure)

KafkaConsumer는 poll batch 단위로 메시지를 수신하고,
내부 버퍼를 통해 처리와 write 단계를 분리한다.

* buffer를 통해 ingestion과 write를 decoupling
* write 지연 시 backlog 발생 가능

Backpressure 발생 시:

* 일정 임계치 초과 시 drop 또는 throttling 적용 가능
* 현재 구현은 drop 없이 처리, 향후 정책 확장 가능

---
