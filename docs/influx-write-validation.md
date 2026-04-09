# InfluxDB v3 적재 검증

## 1. 목적

> Kafka를 통해 수집된 k6 메트릭이 Collector를 거쳐 InfluxDB v3에 정상적으로 적재되는지 검증한다.

### 검증 범위
- Kafka → Collector consume
- Collector → InfluxDB write
- InfluxDB 내부 저장 반영
- 시계열 데이터 정상 누적 여부

### 검증 대상 구성

```
k6 → Kafka → Collector → InfluxDB v3
```

---

## 2. 사전 조건

### InfluxDB 인증 토큰

> `.env`에 설정된 토큰은 예시 값이며 실제 토큰으로 교체해야 한다.

```
INFLUX_TOKEN=your-token
```

#### 토큰 생성
```
docker exec -it influxdb influxdb3 create token --admin
```

#### 토큰 확인
```
docker exec -it influxdb influxdb3 --help
```

#### 생성된 토큰을 `.env`에 반영
```
INFLUX_TOKEN=<실제 생성한 토큰>
```

#### 환경변수 반영을 위해 컨테이너 재기동
```
docker compose down
docker compose up -d
```

#### 반영 확인
```
docker exec collector env
```

---

## 3. 설정 검증

### Influx write 설정 확인

- InfluxDB URL (`INFLUX_URL`)
- write endpoint (`/api/v3/write_lp`)
- database (`INFLUX_DATABASE`)
- 인증 토큰 (`INFLUX_TOKEN`)
- Line Protocol 생성 로직
- write 실패 시 로그 출력 여부

### 서비스 연결 확인

- `collector` → `influxdb` 서비스명 접근 여부
- 포트 매핑 (`8181`)
- 환경변수 주입 여부
- 컨테이너 기동 상태

---

## 4. 검증 절차

### 전체 스택 기동

```
docker compose down
docker compose up -d
docker compose ps
```

### 부하 테스트 실행

```
./k6/run_k6_test.sh smoke

# 필요 시
./k6/run_k6_test.sh load
```

### Collector 로그 확인

```
docker logs collector --tail 200
```

#### 확인 항목
- Kafka 메시지 수신
- 메시지 파싱 성공
- Influx write 요청 수행
- write 성공 또는 실패 로그

```
DEBUG org.apache.kafka.clients.consumer.internals.AbstractFetch
Sending FETCH request ... topic=k6-metrics

DEBUG org.apache.kafka.clients.NetworkClient
Sending FETCH request ... broker=host.docker.internal:29092
```
- Kafka broker로 FETCH 요청 발생
- Consumer가 정상적으로 topic(k6-metrics)에서 메시지를 가져오는 상태

### InfluxDB 로그 확인

```
docker logs influxdb --tail 200
```

#### 확인 항목
- 인증 오류 여부 (`InvalidToken`)
- write 처리 오류 여부
- WAL flush 로그 발생 여부

```
INFO influxdb3_wal::object_store: flushing WAL buffer to object store ...
n_ops=1 ... wal_file_number=160

INFO influxdb3_wal::object_store: flushing WAL buffer to object store ...
n_ops=1 ... wal_file_number=161
```
- WAL flush 발생 → InfluxDB 내부 저장 성공
- `n_ops=1` → write operation 실제 수행됨
= wal_file_number 증가 → 지속적인 데이터 적재 흐름 확인

### 데이터 적재 확인

#### measurement 생성 확인
```
docker exec -it influxdb influxdb3 query \
  --database k6_metrics \
  --token <QUERY_ADMIN_TOKEN> \
  "SHOW TABLES"
```

- 확인 결과
```
http_req_duration
http_reqs
http_req_failed
iteration_duration
vus
collector_stats
```

→ k6 메트릭 및 Collector 내부 메트릭 정상 생성

#### 실제 데이터 확인
```
docker exec -it influxdb influxdb3 query \
  --database k6_metrics \
  --token <QUERY_ADMIN_TOKEN> \
  "SELECT * FROM http_req_duration ORDER BY time DESC LIMIT 10"
```

- 확인 결과
```
method: GET
status: 200
scenario: load_test
url: /api/health
value: 5.6 ~ 6.2 ms
time: 2026-04-09T16:03:26.xxx
```

→ 시계열 데이터 정상 적재 및 누적 확인

#### Collector 내부 상태
```
docker exec -it influxdb influxdb3 query \
  --database k6_metrics \
  --token <QUERY_ADMIN_TOKEN> \
  "SELECT * FROM collector_stats ORDER BY time DESC LIMIT 10"
```

- 확인 결과

```
buffer_size = 0
failed_total = 0
kafka_lag = 0
last_flush_ms = 485 ms
processed_total = 4302
tps = 2.13 ~ 2.19 (조회 시점 기준)
```

- 처리 실패 없음
- consumer lag 없음
- flush 정상 동작
- 처리량 안정 유지

---

## 5. 로그 요구사항

- Kafka 메시지 수신 로그
- 변환된 measurement / tag / field 요약 로그
- Influx write 요청 로그
- write 성공 / 실패 로그
- 실패 시 status code 및 response body

---

## 6. 성공 판정 기준

- Collector 로그에서 Kafka consume 및 Influx write 성공 확인
- InfluxDB 로그에 인증 오류 및 write 오류 없음
- WAL flush 로그 발생 확인
- 부하 테스트 실행 시 데이터 누적 확인
- 데이터 구조가 schema 정의와 일치

---

## 7. 실패 대응 기준

### 인증 오류

- 증상: `401`, `InvalidToken`
- 원인: 잘못된 토큰 또는 미반영
- 조치: `.env` 토큰 값 확인 및 재기동

### InfluxDB 로그 없음

- 증상: write 요청 로그 없음
- 원인: Collector write 미실행
- 조치: Collector 로그에서 write 호출 여부 확인

### Kafka consume 실패

- 증상: 메시지 수신 로그 없음
- 원인: Kafka 연결 또는 토픽 문제
- 조치: k6 → Kafka 경로 재확인

### write 성공 로그 없음

- 증상: write 요청은 있으나 성공 로그 없음
- 원인: retry 반복 또는 내부 예외
- 조치: InfluxWriter 로그 및 retry 로그 확인

---

## 8. 결과 해석 기준

| 상태 | 의미 |
| --- | --- |
| WAL flush 발생 | InfluxDB 내부 적재 성공 |
| write success 로그 | Collector → Influx write 성공 |
| 데이터 조회 가능 | end-to-end 검증 완료 |

---

## 9. 최종 판정

- 데이터 적재: 정상
- 데이터 조회: 정상
- 처리 안정성: 정상

→ end-to-end 파이프라인 검증 완료

---

## 최종 정리

이 문서는 단순 실행 가이드가 아니라 다음을 검증하기 위한 기준이다.

- 데이터 파이프라인의 end-to-end 정상 동작
- 인증/네트워크/변환/적재 단계의 오류 여부
- 시계열 데이터의 구조적 일관성
