# Collector Design

## 역할 요약

Kotlin Collector는 Kafka에서 k6 메트릭을 consume하여 정규화한 뒤 InfluxDB v3에 batch write한다.
부가적으로 자신의 내부 상태(처리량, 실패율, 큐 적체)를 InfluxDB에 함께 적재하여 Grafana에서 조회 가능하게 한다.

---

## 모듈 구조

```
collector/
└── src/main/kotlin/collector/
    ├── Application.kt          ← 진입점, 의존성 조립
    ├── kafka/
    │   └── KafkaConsumer.kt    ← Kafka consume 루프
    ├── processor/
    │   └── MetricsProcessor.kt ← 메트릭 변환 및 정규화
    ├── influx/
    │   └── InfluxWriter.kt     ← batch write, 재시도
    └── metrics/
        └── CollectorMetrics.kt ← 내부 상태 수집 및 HTTP 노출
```

---

## 데이터 흐름

```
Kafka (k6-metrics)
  └─► KafkaConsumer       ← poll loop, offset 관리
        └─► MetricsProcessor  ← 필드 정규화, 태그 구조화
              └─► InfluxWriter   ← 버퍼 누적 → batch write
                    └─► InfluxDB v3 /api/v3/write_lp
```

---

## KafkaConsumer

**역할:** topic `k6-metrics`를 구독하고 레코드를 MetricsProcessor로 전달한다.

```kotlin
// 핵심 설정값 (application.yml에서 주입)
bootstrap.servers: <kafka-host>:9092
group.id: kotlin-collector
auto.offset.reset: earliest
enable.auto.commit: false   // 수동 commit, 처리 보장
```

- 처리 완료 후 수동 offset commit
- poll timeout: 500ms
- write 성공 시 offset commit
- write 실패 시 commit하지 않고 재시도
- 재시도 소진 시:
  - 해당 레코드를 dead-letter 로그로 기록
  - offset commit 수행 (무한 재처리 방지)

---

## MetricsProcessor

**역할:** raw Kafka 레코드를 InfluxDB line protocol 형태로 변환한다.

### 입력 예시 (k6 Kafka output 포맷)

```json
{
  "metric": "http_req_duration",
  "type": "Point",
  "data": {
    "time": "2024-01-01T00:00:00.000Z",
    "value": 123.45,
    "tags": {
      "method": "GET",
      "status": "200",
      "url": "http://api/endpoint",
      "scenario": "load"
    }
  }
}
```

### 출력 예시 (line protocol)

```
http_req_duration,method=GET,status=200,scenario=load value=123.45 1704067200000000000
```

### 정규화 규칙

| 항목 | 처리 내용 |
|---|---|
| metric name | snake_case 유지, 공백 제거 |
| tag 값 | 공백 → `_`, 빈 값 → 제외 |
| timestamp | ISO8601 → Unix nanosecond |
| value | Double, NaN/Infinity → 해당 포인트 제외 |
| url 태그 | 쿼리스트링 제거 (cardinality 폭발 방지) |

---

## InfluxWriter

**역할:** 변환된 포인트를 버퍼에 누적하고 조건 충족 시 batch write한다.

### Flush 조건 (둘 중 먼저 충족되는 쪽)

| 조건 | 기본값 |
|---|---|
| 버퍼 누적 건수 | 500건 |
| 경과 시간 | 1,000ms |

### Write 설정

```
endpoint: /api/v3/write_lp
method:   POST
headers:  Authorization: Token <token>
          Content-Type: text/plain; charset=utf-8
database: k6_metrics
```

### 재시도 정책

```
최대 재시도: 3회
대기 시간:   1s → 2s → 4s (exponential backoff)
재시도 대상: HTTP 429, 5xx
즉시 포기:   HTTP 4xx (400, 401, 403)
```

재시도 소진 시 해당 배치를 dead-letter 로그로 기록하고 계속 진행한다.

---

## CollectorMetrics

**역할:** Collector 내부 메트릭은 InfluxDB에 write하고 Grafana에서 조회한다.

### 노출 방식

InfluxDB에 별도 measurement로 write한다. Grafana에서 동일한 datasource로 조회 가능하다.
Prometheus endpoint는 추가 컴포넌트(Prometheus 서버)가 필요하므로 배제한다.

### measurement: `collector_stats`

| 필드 | 설명 |
|---|---|
| `processed_total` | 총 처리 건수 |
| `failed_total` | 총 실패 건수 |
| `tps` | 초당 처리 건수 |
| `buffer_size` | 현재 버퍼 누적 건수 |
| `kafka_lag` | consumer lag (topic 전체 합산) |
| `last_flush_ms` | 마지막 flush 소요 시간 (ms) |

write 주기: 5초

---

## application.yml 구조

```yaml
kafka:
  bootstrap-servers: localhost:9092
  topic: k6-metrics
  group-id: kotlin-collector
  poll-timeout-ms: 500

influx:
  url: http://localhost:8086
  token: ${INFLUX_TOKEN}
  database: k6_metrics
  batch-size: 500
  flush-interval-ms: 1000
  max-retries: 3

collector:
  stats-write-interval-ms: 5000
```

---

## 고려사항

- k6 Kafka output의 실제 메시지 포맷은 extension 버전에 따라 다를 수 있다. 첫 연동 시 raw 메시지를 로그로 찍어 포맷을 확인한 후 MetricsProcessor를 작성한다.
- url 태그의 cardinality 관리가 중요하다. 동적 경로(`/user/123`)는 정규화(`/user/:id`)하거나 태그에서 제외한다.
- Kafka consumer lag은 Kafka AdminClient API로 조회한다.
