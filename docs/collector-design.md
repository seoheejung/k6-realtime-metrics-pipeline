# Collector Design

## 목적

> Kafka로 전달된 k6 메트릭을 수집하고, 정규화 후 InfluxDB v3에 적재하는 스트리밍 처리 컴포넌트

- 메트릭 ingestion
- normalization
- batch write + retry
- 내부 상태 메트릭 기록

---

## 아키텍처 개요

```
k6 → Kafka → Collector → InfluxDB → Grafana
```

- stateless 처리 모델 (buffer/metrics 제외)
- storage 책임은 InfluxDB

---

## 모듈 구조

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

## 데이터 흐름

```
KafkaConsumer
  → MetricsProcessor
    → InfluxWriter (buffer)
      → batch flush
        → InfluxDB
```

### 특징

- pull 기반 처리 (poll loop)
- synchronous 처리 모델
- batch write 적용
- offset commit은 write 이후 수행

---

## 처리 보장 모델

```
Delivery Semantics: At-Least-Once
```
- 중복 데이터는 InfluxDB에서 허용

### 동작 방식

- Kafka offset commit은 Influx write 성공 이후 수행
- write 실패 시 commit하지 않음 → 재처리 발생

---

## 데이터 정규화 전략

### 입력

- k6 Kafka output (JSON)

### 출력

- InfluxDB Line Protocol

### 규칙

| 항목        | 처리                 |
| --------- | ------------------ |
| metric    | 공백 제거              |
| tag       | 공백 → `_`, null 제거  |
| timestamp | ISO → nanosecond   |
| value     | Double, NaN/Inf 제외 |
| url       | query 제거           |

---

## Batch 처리 전략

### Flush 조건

| 조건             | 값      |
| -------------- | ------ |
| batch-size     | 500    |
| flush-interval | 1000ms |

- 둘 중 먼저 만족 시 flush

---

## 재시도 정책

```
max-retries: 3
backoff: 1s → 2s → 4s
```

### 대상

- HTTP 429
- HTTP 5xx

### 제외

- 400 / 401 / 403

### 실패 시

- dead-letter 파일 기록
- batch 폐기

---

## 장애 처리 전략

### 1. JSON 파싱 실패

- 해당 메시지 skip
- 실패 카운트 증가

### 2. Influx write 실패

- retry 수행
- 실패 시 dead-letter 기록

### 3. batch flush 실패

- batch 폐기
- offset commit 수행

### 결과

- 일부 데이터 유실 가능
- 무한 재처리 방지

---

## 내부 메트릭

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

- InfluxDB에 동일 경로로 저장
- Grafana에서 직접 조회 가능

---

## 트레이드오프

### 1. Exactly-Once 미지원

- 구현 복잡도 증가 방지
- 대신 At-Least-Once 채택

### 2. DLQ → 파일 기반

- 단순 구현
- 운영 환경에서는 Kafka DLQ 필요

### 3. Aggregation 미적용

- Collector 단순화
- 대신 Influx query 비용 증가

---

## 확장 방향

- Kafka AdminClient 기반 lag 측정
- DLQ → Kafka topic 전환
- metric aggregation (window 기반)
- multi-field metric 지원
- Prometheus exporter 추가

---

## 운영 기준

- Kafka 메시지 포맷 변경 시 모델(`K6MetricEvent`) 수정 필요
- URL tag는 query 제거 기준 유지 (cardinality 제어)
- Kafka lag은 현재 0 (미구현)
- Influx schema는 단일 field 구조
- DLQ는 파일 기반 (운영 시 Kafka DLQ 권장)

---

## 처리 모델 (Buffer & Backpressure)

- poll batch 기반 수신
- buffer로 ingestion / write 분리
- write 지연 시 backlog 발생

### Backpressure

- 임계치 초과 시 drop 또는 throttling 가능
- 현재: drop 없음

---
