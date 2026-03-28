# Collector

> Kafka로 전달된 k6 메트릭을 소비하여 가공 후 InfluxDB v3에 적재하는 Kotlin 기반 스트리밍 처리 컴포넌트

---

## 역할

* Kafka Topic으로 전달된 k6 메트릭 이벤트 소비
* JSON 기반 메트릭 데이터를 내부 모델로 변환
* InfluxDB Line Protocol 형태로 정규화
* InfluxDB v3에 batch write 수행
* 처리 상태 및 내부 메트릭을 InfluxDB에 기록

---

## 데이터 흐름

```
Kafka Topic
   ↓
KafkaConsumer
   ↓
MetricsProcessor
   ↓
InfluxWriter (buffer → batch flush)
   ↓
InfluxDB v3
```

---

## 처리 단위

* 입력: Kafka 메시지 (JSON)
* 출력: InfluxDB Line Protocol

---

## 디렉토리 구조

```
collector/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── .dockerignore
├── .env
└── src/main/
    ├── kotlin/
    │   └── com/pipeline/collector/
    │       ├── Application.kt         # 실행 진입점
    │       ├── config/
    │       │   ├── AppConfig.kt
    │       │   ├── ConfigLoader.kt
    │       │   ├── KafkaConsumerConfig.kt
    │       │   └── InfluxWriterConfig.kt
    │       ├── model/
    │       │   ├── K6MetricEvent.kt
    │       │   └── K6MetricData.kt
    │       ├── kafka/
    │       │   └── KafkaConsumer.kt    # Kafka 메시지 소비
    │       ├── processor/
    │       │   └── MetricsProcessor.kt  # 메트릭 변환 및 정제
    │       ├── influx/
    │       │   └── InfluxWriter.kt      # InfluxDB write 처리
    │       └── metrics/
    │           └── CollectorMetrics.kt  # 내부 처리 메트릭
    └── resources/
        └── application.yml
```

---

## 주요 컴포넌트

### KafkaConsumer

* Kafka broker 연결 및 topic 구독
* poll loop 수행
* 처리 성공 시 offset commit
* write 실패 시 commit하지 않음 (재처리 보장)

---

### MetricsProcessor

* Kafka raw JSON → 내부 모델 변환
* tag / field 분리
* timestamp → nanosecond 변환
* URL query 제거 (cardinality 제한)

---

### InfluxWriter

* Line Protocol 버퍼링
* batch 조건:

  * 건수 (`batch-size`)
  * 시간 (`flush-interval-ms`)
* HTTP write 수행 (`/api/v3/write_lp`)
* retry (exponential backoff)
* 최종 실패 시 dead-letter 파일 기록

---

### CollectorMetrics

* processed_total
* failed_total
* buffer_size
* tps
* kafka_lag (현재 미구현, placeholder)
* last_flush_ms

→ InfluxDB에 `collector_stats` measurement로 기록

---

## 실행 방법

### 1. 환경 설정 (.env)

프로젝트 루트에 `.env` 파일 생성

```
# Kafka
KAFKA_BROKER=localhost:9092
KAFKA_TOPIC=k6-metrics
KAFKA_GROUP_ID=kotlin-collector
KAFKA_POLL_TIMEOUT_MS=500
KAFKA_AUTO_OFFSET_RESET=earliest

# InfluxDB v3
INFLUX_URL=http://localhost:8181
INFLUX_TOKEN=your-token
INFLUX_DATABASE=k6_metrics
INFLUX_BATCH_SIZE=500
INFLUX_FLUSH_INTERVAL_MS=1000
INFLUX_MAX_RETRIES=3
INFLUX_TIMEOUT_MS=3000

# Collector
COLLECTOR_STATS_WRITE_INTERVAL_MS=5000
COLLECTOR_DEAD_LETTER_PATH=./logs/dead-letter.log
```

---

### 2. 설정 로딩 방식

1. `application.yml` 로드
2. `.env` 파일 로드
3. 시스템 환경변수 로드
4. `${ENV_KEY}` 치환
5. AppConfig로 변환

우선순위:

```
시스템 환경변수 > .env > application.yml 기본값
```

---

### 3. 실행

```
./gradlew run
```

또는

```
java -jar build/libs/collector.jar
```

---

## 동작 흐름

1. Application 시작
2. ConfigLoader로 설정 로드
3. KafkaConsumer 생성
4. 메시지 poll loop 시작
5. MetricsProcessor에서 변환
6. InfluxWriter 버퍼 적재
7. batch 조건 충족 시 write
8. 성공 시 offset commit

---

## 실패 처리 기준

* JSON 파싱 실패 → skip + 실패 카운트 증가
* Influx write 실패 → retry 수행
* retry 실패 → dead-letter 로그 기록
* Kafka commit → batch 전체 성공 시만 수행

---

## 확장 포인트

* Kafka lag 실측 (AdminClient)
* dead-letter → Kafka DLQ topic 전환
* metric aggregation (window 기반 집계)
* URL path 템플릿화 (`/user/:id`)
* multi-field metric 지원
* Prometheus exporter 추가

---

## 제한 사항

* Kafka lag 미구현 (0 고정)
* DLQ topic 미구현 (파일 기반 로그)
* 단일 field(value)만 지원
* Influx schema 고정

---

## 목적

k6의 실시간 메트릭 출력 제약을 Kafka로 우회하고,
Collector에서 가공 후 InfluxDB에 적재하여
실시간 모니터링 및 분석 파이프라인을 구성한다.
