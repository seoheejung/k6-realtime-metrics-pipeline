# Load Test Improvement Case

## 1. 개선 사례 목표

> 부하 테스트를 통해 병목을 발견하고, 병목 위치에 맞는 설정 또는 코드를 개선한 뒤 동일 조건으로 재측정하여 성능 개선 효과를 검증한다.

### 검증 흐름
```text
stress 병목 탐색 → load 기준 측정 → 개선 적용 → load 재측정 → stress 재측정 → Before / After 비교
```

### 진행 순서
| 단계 | 목적            | 실행                           |
| -- | ------------- | ---------------------------- |
| 1  | 병목 탐색         | `./k6/run_k6_test.sh stress` |
| 2  | Before 기준값 기록 | `./k6/run_k6_test.sh load`   |
| 3  | 개선 적용         | Collector batch-size 조정      |
| 4  | After 기준값 재측정 | `./k6/run_k6_test.sh load`   |
| 5  | 개선 효과 검증      | `./k6/run_k6_test.sh stress` |

---

## 2. 측정 기준
> Before / After 비교는 동일 조건 유지를 위해 load 시나리오 기준으로 기록한다.
```
./k6/run_k6_test.sh load
```
- `load` 시나리오는 Before / After의 기본 응답 성능 비교에 사용한다.
- `stress` 시나리오는 Collector → InfluxDB write 경로의 병목 확인과 개선 효과 검증에 사용한다.
- 최종 판단은 `load` 결과와 `stress` 결과를 분리해서 기록한다.

### Grafana 확인 지표
| 지표              | 의미              | 기록값                      |
| --------------- | --------------- | ------------------------ |
| Response Time   | latency         | avg latency, p95 latency |
| Requests (RPS)  | 처리량             | RPS                      |
| Failed Requests | 오류              | error rate               |
| kafka_lag       | Collector 처리 지연 | kafka_lag                |


### Load 테스트 조건

| 항목 | 값 |
| --- | --- |
| 실행 명령 | `./k6/run_k6_test.sh load` |
| VUs | 10 |
| duration | 30s |
| 대상 API | `/api/health` |
| 측정 일시 | 2026-05-07 |

### Stress 테스트 조건

| 항목 | 값 |
| --- | --- |
| 실행 명령 | `./k6/run_k6_test.sh stress` |
| STRESS_STAGE_1_TARGET | 100 |
| STRESS_STAGE_2_TARGET | 200 |
| STRESS_STAGE_3_TARGET | 500 |
| duration | 300s |
| 대상 API | `/api/health` |
| 측정 일시 | 2026-05-07 |

### Grafana 측정 방법

테스트 실행 후 Grafana의 시간 범위를 테스트 실행 구간으로 고정한다.

#### 권장 방식
```text
stress 확인: Last 15 minutes
load Before 기록: 테스트 시작 시간 ~ 테스트 종료 시간
load After 기록: 테스트 시작 시간 ~ 테스트 종료 시간
```
문서에 기록할 Before / After 값은 Last 15 minutes처럼 계속 변하는 범위가 아니라, 테스트 실행 구간을 absolute range로 고정한 뒤 확인한다.

#### 패널 확인 순서
| 순서 | 패널              | 확인 내용                             |
| -- | --------------- | --------------------------------- |
| 1  | VUs             | 부하가 의도한 대로 증가하거나 유지되는지 확인         |
| 2  | Requests (RPS)  | VUs 증가 또는 유지에 따라 처리량이 증가/유지되는지 확인 |
| 3  | Response Time   | 평균 응답 시간과 지연 급증 구간 확인             |
| 4  | Failed Requests | 실패율 증가 여부 확인                      |
| 5  | Collector Stats | kafka_lag 증가 여부 확인                |

#### 값 확인 방법

Grafana 패널에서 수치를 확인할 때는 패널 메뉴를 열고 아래 경로로 실제 데이터를 확인한다.
```
Panel menu → Inspect → Data
```

| 기록값         | 확인 위치                      |
| ----------- | -------------------------- |
| avg latency | Response Time 패널           |
| p95 latency | k6 실행 종료 summary 또는 p95 패널 |
| RPS         | Requests (RPS) 패널          |
| error rate  | Failed Requests 패널         |
| kafka_lag   | Collector Stats 패널         |


Failed Requests 값이 소수로 표시될 경우 문서에는 퍼센트로 변환해서 기록한다.
```
error rate (%) = Failed Requests 값 * 100

# 예시
0.012 * 100 = 1.2%
```

---

## 3. 병목 가설 및 개선 후보

> stress 시나리오에서 Collector → InfluxDB write 경로가 고부하 메트릭 유입량을 따라가는지 확인한다.

### 확인할 병목 패턴

- Kafka consumer lag 증가
- Collector batch flush 지연
- Grafana 최신 구간에서 HTTP metric 반영 지연
- RPS 증가 대비 Collector 처리 지연 발생

### 원인 분리 기준

| 위치 | 확인 지표 | 판단 기준 |
| --- | --- | --- |
| API | Response Time, Failed Requests | latency 급증 또는 error rate 증가 |
| Kafka | consumer lag | `kotlin-collector` group lag 증가 |
| Collector | batch size, flush time, offset commit | consume은 되지만 write 처리 지연 발생 |
| InfluxDB | write status, flush time | `status=204` 여부와 write 소요 시간 확인 |

### 병목 판단

| 관측 결과 | 판단 |
| --- | --- |
| k6 → Kafka 유입 정상 | k6 output 경로는 정상 |
| Collector offset commit 정상 | Kafka consume은 정상 |
| InfluxDB write `status=204` | write 요청 자체는 성공 |
| Kafka consumer lag 대량 증가 | Collector → InfluxDB write 처리량이 유입량을 따라가지 못함 |

### 개선 후보

| 후보 | 변경 내용 | 기대 효과 |
| --- | --- | --- |
| batch-size 증가 | `INFLUX_BATCH_SIZE=500 → 2000` | InfluxDB write 호출 횟수 감소 |
| flush interval 조정 | flush 주기 조정 | buffer 체류 시간 및 write 주기 조정 |
| async write 적용 | consume loop와 write I/O 분리 | InfluxDB write 대기 영향 감소 |

### 이번 개선 선택

| 항목 | 선택 |
| --- | --- |
| 개선 대상 | Collector → InfluxDB write batch size |
| 변경 내용 | `INFLUX_BATCH_SIZE=500 → 2000` |
| 선택 이유 | stress 고부하 실행 후 Kafka consumer lag가 1,212,689건까지 증가했기 때문 |

---

## 4. Before 측정 결과

### Load 기준 결과

| 지표 | 값 |
| --- | --- |
| avg latency | 약 4~5 ms |
| p95 latency | k6 summary 확인 필요 |
| RPS | 약 10 |
| error rate | 0 % |
| kafka_lag | 0 |

### Stress 병목 관측 결과

| 항목 | 값 |
| --- | --- |
| Kafka consumer lag 최대값 | 1,212,689 |
| Collector batch size | 500 |
| batch write size | 70KB~75KB |
| flush time | 약 990ms |
| 컨테이너 재시작 | 없음 |
| InfluxDB write status | 204 |
| Grafana 최신 HTTP 패널 | No data 발생 |

### 관측 내용

- stress 시나리오에 단계별 유지 구간을 추가한 뒤 총 실행 시간이 300초로 증가함
- Kafka topic에는 현재 시각의 `http_reqs` 메트릭이 정상 유입됨
- Collector 로그에서 `lines=500`, `bytes=70KB~75KB`, `flushMs≈990ms` 수준의 InfluxDB batch write가 반복됨
- stress 고부하 실행 후 Kafka consumer lag가 최대 1,212,689건까지 증가함
- 컨테이너 RestartCount는 모두 0으로 확인되어 크래시는 발생하지 않음
- 정확한 load 측정을 위해 collector를 중지하고 consumer group offset을 latest로 reset함
- reset 후 `CURRENT-OFFSET=LOG-END-OFFSET=2655694`, `LAG=0`을 확인함

### 병목 판단

- 병목 위치: Collector → InfluxDB write 경로

- 판단 근거:
  - k6 → Kafka 유입은 정상
  - Collector consume 및 offset commit은 정상
  - InfluxDB write는 `status=204`로 성공함
  - 그러나 stress 고부하 구간에서 Kafka consumer lag가 1,212,689건까지 증가함
  - 따라서 Collector → InfluxDB write 처리량이 유입량을 따라가지 못한 것으로 판단함

---

## 5. 적용 변경

> 이번 개선 사례에서는 변경 범위를 하나로 제한한다.

| 항목 | Before | After |
| --- | --- | --- |
| InfluxDB write batch size | `INFLUX_BATCH_SIZE=500` | `INFLUX_BATCH_SIZE=2000` |

### 변경 이유
- stress 고부하 실행 후 Kafka consumer lag가 최대 1,212,689건까지 증가함
- Collector 로그에서 `lines=500`, `bytes=70KB~75KB`, `flushMs≈990ms` 수준의 batch write가 반복됨
- batch size를 증가시켜 InfluxDB write 호출 횟수를 줄이고, Collector의 Kafka backlog 처리량 개선을 확인한다

---

## 6. After 측정 결과
> 동일 조건으로 다시 부하 테스트를 실행한다.
```
./k6/run_k6_test.sh load
```

### After 값 기록 기준

After 측정은 Before와 동일한 조건으로 수행한다.

#### 동일하게 유지할 조건

```text
- 실행 시나리오: load
- VUs
- duration
- 대상 API
- Kafka / InfluxDB / Collector 실행 조건
```
After에서는 Before에서 관측된 병목 패턴이 줄었는지 확인한다.

#### 확인 항목
- avg latency 감소 여부
- p95 latency 감소 여부
- RPS 증가 또는 안정화 여부
- error rate 감소 또는 유지 여부
- kafka_lag 감소 또는 증가 중단 여부

### After Load 검증 결과

| 지표 | 값 |
| --- | --- |
| avg latency | 약 3.2 ms |
| p95 latency | k6 summary 확인 필요 |
| RPS | 약 10 |
| error rate | 0 % |
| kafka_lag | 0 |

### Load 관측 내용
- load 시나리오에서 VUs는 10으로 유지됨
- Requests는 약 10 ops/s 수준으로 안정적으로 유지됨
- Response Time은 대부분 약 3ms대에서 유지됨
- Failed Requests는 0으로 유지됨
- Kafka consumer lag는 0으로 유지됨

### After stress 검증 결과

| 항목 | 값 |
| --- | --- |
| stress 조건 | `STRESS_STAGE_1_TARGET=100`, `STRESS_STAGE_2_TARGET=200`, `STRESS_STAGE_3_TARGET=500` |
| Collector batch size | `INFLUX_BATCH_SIZE=2000` |
| Kafka consumer lag | 203,648 |
| Response Time | 약 2.0~3.1ms |
| RPS | 약 50~100 ops/s |
| Failed Requests | 0 |
| kafka_lag | Grafana 기준 낮은 수준 유지 |
| 컨테이너 재시작 | 없음 |

> Kafka consumer lag는 `kafka-consumer-groups.sh --describe --group kotlin-collector` 기준으로 기록했다.

### Stress 관측 내용

- `INFLUX_BATCH_SIZE=2000` 적용 후 stress 시나리오를 재실행함
- VUs 증가에 따라 RPS도 약 50 ops/s에서 약 100 ops/s까지 증가함
- Response Time은 약 2.0~3.1ms 수준으로 유지됨
- Failed Requests는 0으로 유지됨
- Grafana에서는 Collector Stats가 낮은 수준으로 표시되었으나, Kafka consumer group 기준으로는 `LAG=203,648`이 확인됨
- Before stress의 `LAG=1,212,689` 대비 After stress의 `LAG=203,648`로 감소함

---

## 7. 결과 비교

### Load 결과 비교

| 지표 | Before | After | 변화 |
| --- | --- | --- | --- |
| avg latency | 약 4~5 ms | 약 3.2 ms | 약 20~36% 감소 |
| p95 latency | k6 summary 확인 필요 | k6 summary 확인 필요 | 계산 불가 |
| RPS | 약 10 | 약 10 | 0% |
| error rate | 0 % | 0 % | 변화 없음 |
| kafka_lag | 0 | 0 | 유지 |

### Stress 결과 비교

| 지표 | Before | After | 변화 |
| --- | --- | --- | --- |
| Kafka consumer lag | 1,212,689 | 203,648 | 약 83.2% 감소 |
| batch size | 500 | 2000 | 4배 증가 |
| Response Time | 최신 반영 지연으로 Grafana No data 발생 | 약 2.0~3.1ms | 정상 표시 |
| RPS | backlog 발생으로 최신 구간 판단 어려움 | 약 50~100 ops/s | 정상 표시 |
| Failed Requests | 0 | 0 | 유지 |

### 변화값 기록 기준

| 지표 | 변화 계산 방향 |
| --- | --- |
| avg latency | 낮아질수록 개선 |
| p95 latency | 낮아질수록 개선 |
| RPS | 높아질수록 개선 |
| error rate | 낮아질수록 개선 |
| Kafka consumer lag | 낮아지거나 증가가 멈추면 개선 |

> `kafka_lag`는 퍼센트 개선율보다 Before / After 패턴 비교를 우선한다.

#### 예시
- Before: 테스트 중 지속 증가
- After: 0 근처 유지 또는 증가 후 감소

### 개선율 계산
```
avg latency 감소율 = (Before - After) / Before * 100
p95 latency 감소율 = (Before - After) / Before * 100
RPS 증가율 = (After - Before) / Before * 100
error rate 감소율 = (Before - After) / Before * 100
```

### 실제 계산
```
avg latency 감소율 = (Before - After) / Before * 100
= (4.5 - 3.2) / 4.5 * 100
= 28.9 %

p95 latency 감소율 = k6 summary 미확보로 계산 불가

RPS 증가율 = (After RPS - Before RPS) / Before RPS * 100
= (10 - 10) / 10 * 100
= 0 %

error rate 감소율 = Before error rate가 0%이므로 감소율 계산 불가

Kafka consumer lag 감소율 = (Before - After) / Before * 100
= (1,212,689 - 203,648) / 1,212,689 * 100
= 83.2 %
```

---

## 8. 결론

- 병목: Collector → InfluxDB write 경로
- 개선 방법: InfluxDB write batch size 조정 (`INFLUX_BATCH_SIZE=500` → `INFLUX_BATCH_SIZE=2000`)
- load 결과:
  - avg latency는 약 4~5ms에서 약 3.2ms로 감소함
  - RPS는 약 10으로 유지됨
  - error rate는 0%로 유지됨
  - kafka_lag는 0으로 유지됨
- stress 결과:
  - Before stress에서는 Kafka consumer lag가 최대 1,212,689건까지 증가함
  - After stress에서는 VUs 증가에 따라 RPS가 증가했고, Response Time은 약 2.0~3.1ms 수준으로 유지됨
  - Failed Requests는 0으로 유지됨
  - After stress에서도 Kafka consumer lag는 발생했지만, Before 1,212,689건에서 After 203,648건으로 감소했다.

### 최종 판단
`INFLUX_BATCH_SIZE=2000` 적용 후 load 기준 안정성은 유지되었고, stress 기준 Kafka consumer lag는 1,212,689건에서 203,648건으로 약 83.2% 감소했다. Collector → InfluxDB write 경로의 backlog 처리 지연이 완화된 것으로 판단한다.

---
