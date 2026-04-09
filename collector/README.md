# Collector

> Kafka로 전달된 k6 메트릭을 소비하여 가공 후 InfluxDB v3에 적재하는 Kotlin 기반 스트리밍 처리 컴포넌트

---

## 목적

k6의 실시간 메트릭 출력 제약을 Kafka로 우회하고,
Collector에서 가공 후 InfluxDB에 적재하여
실시간 모니터링 및 분석 파이프라인을 구성한다.

---

## 역할

- Kafka Topic으로 전달된 k6 메트릭 이벤트 소비
- JSON 기반 메트릭 데이터를 내부 모델로 변환
- InfluxDB Line Protocol 형태로 정규화
- InfluxDB v3에 batch write 수행
- 처리 상태 및 내부 메트릭을 InfluxDB에 기록

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

- 입력: Kafka 메시지 (JSON)
- 출력: InfluxDB Line Protocol

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

- Kafka broker 연결 및 topic 구독
- poll loop 수행
- 처리 성공 시 offset commit
- write 실패 시 commit하지 않음 (재처리 보장)

---

### MetricsProcessor

- Kafka raw JSON → 내부 모델 변환
- tag / field 분리
- timestamp → nanosecond 변환
- URL query 제거 (cardinality 제한)

---

### InfluxWriter

- Line Protocol 버퍼링
- HTTP write 수행 (`/api/v3/write_lp`)
- retry (exponential backoff)
- write 성공 / 실패 / 재시도 / latency 로그 기록
- 최종 실패 시 dead-letter 파일 기록

#### batch 조건
- 건수 (`batch-size`)
- 시간 (`flush-interval-ms`)

---

### CollectorMetrics

- processed_total
- failed_total
- buffer_size
- tps
- kafka_lag (현재 미구현, placeholder)
- last_flush_ms

→ InfluxDB에 `collector_stats` measurement로 기록   
→ 처리 실패 여부, 버퍼 상태, flush 지연, 처리량을 추적하기 위한 내부 메트릭

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
# 실제 실행 전 InfluxDB v3에서 생성한 토큰으로 교체
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

### 2. InfluxDB v3 토큰 생성

`INFLUX_TOKEN=your-token`은 예시 값이다.  
실행 전 반드시 실제 토큰을 생성해서 `.env`에 반영해야 한다.

- 토큰 생성
```bash
docker exec -it influxdb influxdb3 create token --admin
```

- 토큰 생성 명령이 다르게 동작하는 경우 아래도 확인 필요
```bash
docker exec -it influxdb influxdb3 --help
```

- 정상 생성 시 출력되는 토큰 값을 복사해서 .env에 반영
```bash
INFLUX_TOKEN=<실제 생성한 토큰>
```

- 토큰 반영 후 collector를 다시 기동
```bash
docker compose down
docker compose up -d
```

- 반영 확인
```bash
docker exec collector env
```

---

### 3. 설정 로딩 방식

1. `application.yml` 로드
2. `.env` 파일 로드
3. 시스템 환경변수 로드
4. `${ENV_KEY}` 치환
5. AppConfig로 변환

#### 우선순위

```
시스템 환경변수 > .env > application.yml 기본값
```

---

### 4. 실행

```bash
./gradlew clean shadowJar
```

또는

```bash
java -jar build/libs/collector.jar
```

### 5. docker compose로 확인 (root 디렉토리)
```bash
docker compose build collector
docker compose up -d collector
docker logs collector --tail 100
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

## 로그 확인 포인트

- batch flush 시작 로그
- Influx write 성공 로그
- retry 발생 로그
- 최종 실패 시 dead-letter 기록 로그

→ InfluxDB 로그에서는 WAL flush 발생 여부를 확인한다.

---

## 운영 기준

- write 성공 로그 + WAL flush + SQL 조회 결과가 모두 확인되어야 정상으로 판단한다.

---

## 실패 처리 기준

- JSON 파싱 실패 → skip + 실패 카운트 증가
- Influx write 실패 → retry 수행
- retry 실패 → dead-letter 로그 기록
- write 실패 시 status code / response body 로그 기록
- Kafka commit → batch 전체 성공 시만 수행

---

## 확장 포인트

- Kafka lag 실측 (AdminClient)
- dead-letter → Kafka DLQ topic 전환
- metric aggregation (window 기반 집계)
- URL path 템플릿화 (`/user/:id`)
- multi-field metric 지원
- Prometheus exporter 추가

---

## 제한 사항

- Kafka lag 미구현 (0 고정)
- DLQ topic 미구현 (파일 기반 로그)
- 단일 field(value)만 지원
- Influx schema 고정

---

## 관련 문서

- [influx-write-validation.md](../docs/influx-write-validation.md)

→ InfluxDB v3 적재 검증 절차 및 실제 결과 확인

