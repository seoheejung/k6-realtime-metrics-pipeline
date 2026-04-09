# Kafka Strategy

## 도입 목적

- k6 메트릭을 실시간 스트리밍으로 전달
- producer(k6)와 consumer(Collector) 분리
- burst traffic 시 버퍼 역할 수행
- Collector 장애 시 재처리 가능

### 메트릭 처리 흐름
```
k6
→ Kafka (topic: k6-metrics)
→ Kotlin Collector
→ 변환
→ batch 처리
→ InfluxDB v3 write
```

- Kafka 이후 경로는 메트릭 처리 전용 경로
- 성능 측정 대상은 Kotlin API이며 Collector 이후는 측정 대상이 아님

---

## Topic 설정

| 항목           | 값            |
| ------------ | ------------ |
| topic        | `k6-metrics` |
| partitions   | 1            |
| replication  | 1            |
| retention.ms | 3600000      |


### Topic 생성 (필수 1회)

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic k6-metrics \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=3600000
```

### Topic 확인
> Kafka 볼륨을 삭제한 경우(`docker compose down -v`) 재생성 필요
```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

---

## Bootstrap Server 규칙

> host와 container 간 네트워크 경로가 다르므로 주소를 구분한다.

| 위치 | 주소 |
|---|---|
| k6 (host) | localhost:29092 |
| Collector (container) | kafka:9092 |

---

## k6 출력 설정 (k6 실행 시 옵션으로 지정)

```bash
--out kafka=brokers=localhost:29092,topic=k6-metrics
```

---

## Collector 설정

```
bootstrap.servers=kafka:9092
group.id=kotlin-collector
enable.auto.commit=false
auto.offset.reset=earliest
max.poll.records=500
```

---

## Offset 정책
- write 성공 후 commit
- 실패 시 재처리
- 재시도 초과 시 drop 후 commit

---

## 장애 시나리오별 동작

| 시나리오 | 동작 |
|---|---|
| Collector 일시 중단 | Kafka에 메시지 적체, 재기동 후 earliest부터 재처리 |
| InfluxDB 일시 중단 | Collector 재시도 3회 → dead-letter 로그 기록, Kafka offset은 commit |
| Kafka 재시작 | retention.ms 내 메시지는 보존, Collector 재연결 후 재처리 |
| k6 burst | Kafka가 버퍼 역할 수행, Collector는 batch 단위로 처리 |

---

## Kafka Lag 정의

> lag: Collector 처리 지연 상태 의미 (Grafana에서 Collector 상태 지표로 사용)

```
lag = endOffset - committedOffset
```

- endOffset: topic의 최신 offset
- committedOffset: consumer가 처리 완료한 offset
- 현재 값: 0 (미구현)
- 추후 AdminClient 기반으로 확장
