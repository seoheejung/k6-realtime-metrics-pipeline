# Kafka Strategy

## 도입 이유

k6는 기본적으로 InfluxDB v3 직접 연동을 지원하지 않는다.
임의의 커스텀 목적지로 실시간 전송하려면 output extension 빌드가 필요하다.

Kafka를 중간에 두면:
- k6(producer)와 Kotlin Collector(consumer)가 독립적으로 동작한다
- burst traffic 발생 시 Kafka가 버퍼 역할을 수행한다
- Collector 장애 시 메시지가 적체되며 재기동 후 재처리 가능하다
- 향후 consumer를 추가해도 k6 쪽 변경이 없다

---

## k6 Kafka Output Extension

### 사용 extension

`xk6-output-kafka` (grafana/xk6-output-kafka)

k6는 기본 바이너리에 Kafka output이 포함되어 있지 않다.
xk6를 통해 커스텀 바이너리를 빌드해야 한다.

### 빌드 방법

```bash
# xk6 설치 (Go 1.21 이상 필요)
go install go.k6.io/xk6/cmd/xk6@latest

# Kafka output 포함 k6 빌드
xk6 build --with github.com/grafana/xk6-output-kafka

# 결과: ./k6 바이너리 생성
```

### Dockerfile (k6/docker/Dockerfile)

```dockerfile
FROM golang:1.21-alpine AS builder
RUN go install go.k6.io/xk6/cmd/xk6@latest
RUN xk6 build --with github.com/grafana/xk6-output-kafka --output /k6

FROM alpine:3.18
COPY --from=builder /k6 /usr/local/bin/k6
ENTRYPOINT ["k6"]
```

### k6 실행 옵션

```bash
k6 run \
  --out kafka=brokers=localhost:9092,topic=k6-metrics \
  scripts/load.js
```

---

## 사전 검증 (스파이크 테스트)

**프로젝트 시작 전 반드시 수행한다.**

xk6-output-kafka의 실제 메시지 포맷이 Collector 구현에 직접 영향을 미치기 때문이다.

### 검증 절차

```bash
# 1. Kafka 로컬 실행
docker-compose -f kafka/docker-compose.yml up -d

# 2. k6 커스텀 바이너리 빌드
docker build -t k6-kafka k6/docker/

# 3. 테스트 스크립트로 메시지 발행
docker run --rm --network host k6-kafka run \
  --out kafka=brokers=localhost:9092,topic=k6-metrics \
  k6/scripts/single_api_load.js

# 4. 메시지 내용 확인
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic k6-metrics \
  --from-beginning \
  --max-messages 10
```

확인 후 실제 메시지 포맷을 `docs/kafka-message-sample.json`에 기록하고 MetricsProcessor를 작성한다.

---

## Topic 설정

| 항목 | 값 | 비고 |
|---|---|---|
| topic name | `k6-metrics` | - |
| partitions | 1 | 단일 Collector 기준, 확장 시 증가 |
| replication factor | 1 | 로컬/단일 VM 기준 |
| retention.ms | 3,600,000 | 1시간 (메트릭 특성상 장기 보관 불필요) |
| retention.bytes | -1 | 무제한 (retention.ms로 제어) |

- 운영 환경에서는 Collector 장애 복구 시간을 고려하여 retention.ms를 조정한다.

### topic 생성 명령

```bash
kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic k6-metrics \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=3600000
```

---

## Kotlin Collector Consumer 설정

```properties
bootstrap.servers=localhost:9092
group.id=kotlin-collector
auto.offset.reset=earliest
enable.auto.commit=false
max.poll.records=500
fetch.min.bytes=1
fetch.max.wait.ms=500
```

### offset 관리 전략

- `enable.auto.commit=false` 로 설정하고 수동 commit 사용
- InfluxDB write 성공 확인 후 offset commit
- write 실패 시 commit 없이 재시도 → Kafka에서 동일 메시지 재처리
- 재시도 소진 시 dead-letter 로그 기록 후 offset commit (무한 재처리 방지)

---

## Lag 모니터링

Collector 내부에서 Kafka AdminClient를 통해 consumer lag을 조회하고 InfluxDB `collector_stats` measurement에 write한다.

```kotlin
// lag 조회 방식
val adminClient = AdminClient.create(props)
val listConsumerGroupOffsetsResult = adminClient.listConsumerGroupOffsets(groupId)
// endOffsets - committedOffsets = lag
```

Grafana에서 `collector_stats.kafka_lag` 필드를 조회하여 시각화한다.

---

## 로컬 실행 (docker-compose)

```yaml
# kafka/docker-compose.yml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
```

KRaft 모드(Zookeeper 없는 구성)는 Kafka 3.x에서 지원되나, 로컬 환경에서는 안정성이 검증된 Zookeeper 구성을 우선 사용한다.

---

## 장애 시나리오별 동작

| 시나리오 | 동작 |
|---|---|
| Collector 일시 중단 | Kafka에 메시지 적체, 재기동 후 earliest부터 재처리 |
| InfluxDB 일시 중단 | Collector 재시도 3회 → dead-letter 로그 기록, Kafka offset은 commit |
| Kafka 재시작 | retention.ms 내 메시지는 보존, Collector 재연결 후 재처리 |
| k6 burst | Kafka가 버퍼 역할 수행, Collector는 batch 단위로 처리 |
