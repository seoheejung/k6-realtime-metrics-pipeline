# Load Test Improvement Case

## 1. 개선 사례 목표

> 부하 테스트를 통해 병목을 발견하고, 병목 위치에 맞는 설정 또는 코드를 개선한 뒤 동일 조건으로 재측정하여 성능 개선 효과를 검증한다.

### 검증 흐름
```text
stress 병목 탐색 → load 기준 측정 → 개선 적용 → load 재측정 → Before / After 비교
```

### 진행 순서
| 단계 | 목적              | 실행                                       |
| -- | --------------- | ---------------------------------------- |
| 1  | 병목 탐색           | `./k6/run_k6_test.sh stress`             |
| 2  | Before 기준값 기록   | `./k6/run_k6_test.sh load`               |
| 3  | 개선 적용           | Collector batch-size / flush interval 조정 |
| 4  | After 동일 조건 재측정 | `./k6/run_k6_test.sh load`               |

---

## 2. 측정 기준
> Before / After 비교는 동일 조건 유지를 위해 load 시나리오 기준으로 기록한다.
```
./k6/run_k6_test.sh load
```
- `stress` 시나리오는 병목 위치를 찾는 용도로만 사용하고, 최종 개선율 계산에는 포함하지 않는다.

### Grafana 확인 지표
| 지표              | 의미              | 기록값                      |
| --------------- | --------------- | ------------------------ |
| Response Time   | latency         | avg latency, p95 latency |
| Requests (RPS)  | 처리량             | RPS                      |
| Failed Requests | 오류              | error rate               |
| kafka_lag       | Collector 처리 지연 | kafka_lag                |


### 테스트 조건
| 항목       | 값                          |
| -------- | -------------------------- |
| 실행 명령         | `./k6/run_k6_test.sh load` |
| VUs            | ___ |
| duration       | ___ |
| 대상 API        | ___ |
| 테스트 시작 시간 | ___ |
| 테스트 종료 시간 | ___ |
| 측정 일시       | ___ |

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
> stress 시나리오에서 아래 패턴을 확인한다.

- Response Time 급증
- RPS 정체
- VUs 증가 대비 성능 유지 안됨
- kafka_lag 증가

### 원인 분리 기준

| 위치        | 의심                  |
| --------- | ------------------- |
| API       | CPU / DB / blocking |
| Collector | batch / retry       |
| Kafka     | lag                 |
| Influx    | write 지연            |

### 병목 후보 우선순위
> 현재 구조에서 우선 확인할 병목 후보는 Collector → Influx write 구간이다.

| 순위 | 위치                       | 이유                         |
| -- | ------------------------ | -------------------------- |
| 1  | Collector → Influx write | 네트워크 + batch 처리 + sync I/O |
| 2  | Kafka lag                | consumer 처리속도 영향           |
| 3  | API                      | 현재 단순 endpoint라 병목 가능성 낮음  |

### 병목 판단 기준

| 관측 패턴 | 판단 |
| --- | --- |
| kafka_lag 증가 + RPS 정체 + Response Time 상승 | Collector 또는 Influx write 병목 가능성 높음 |
| Response Time 상승 + Failed Requests 증가 + kafka_lag 정상 | API 병목 가능성 높음 |
| kafka_lag 증가 + Collector TPS 정체 | Collector → Influx write 처리 지연 가능성 높음 |
| RPS 증가 없이 VUs만 증가 | 처리 한계 도달 가능성 있음 |
| Failed Requests 증가 | API 오류, timeout, connection 문제 확인 필요 |

### 정상 패턴

```text
VUs 증가 또는 유지
→ RPS 증가 또는 안정 유지
→ Response Time 완만
→ Failed Requests 0에 가까움
→ kafka_lag 0 또는 낮은 수준 유지
```

### 병목 의심 패턴
```
VUs 증가
→ RPS 정체
→ Response Time 급증
→ kafka_lag 증가
```

### 개선 후보

| 후보                | 변경 내용                    | 기대 효과                      |
| ----------------- | -------------------------------------- | -------------------------- |
| batch-size 증가     | Collector batch size 증가   | Influx write 호출 횟수 감소      |
| flush interval 조정 | Collector flush interval 조정  | buffer 체류 시간 및 write 주기 조정 |
| API sleep 제거      | 테스트용 지연 로직 제거    | API 응답 지연 감소   |
| thread pool 조정    | API 또는 Collector worker/thread pool 조정 | blocking 구간 처리량 개선 |

---

## 4. Before 측정 결과

| 지표          | 값      |
| ----------- | ------ |
| avg latency | ___ ms |
| p95 latency | ___ ms |
| RPS         | ___    |
| error rate  | ___ %  |
| kafka_lag   | ___    |

### Before 값 기록 기준

| 지표 | 기록 기준 |
| --- | --- |
| avg latency | load 실행 구간의 Response Time 평균값 |
| p95 latency | k6 종료 summary의 `http_req_duration p(95)` 값 |
| RPS | load 실행 구간의 Requests 처리량 |
| error rate | load 실행 구간의 Failed Requests 비율 |
| kafka_lag | load 실행 구간의 kafka_lag 최대값 또는 지속 증가 여부 |

`kafka_lag`는 단일 평균값보다 증가 패턴이 중요하다.  
값이 일시적으로 튄 뒤 다시 감소하면 회복 가능한 지연으로 보고, 테스트 종료 시점까지 계속 증가하면 Collector 처리 지연으로 판단한다.

### 관측 내용
- 
- 
- 

### 병목 판단
- 병목 위치: ___
- 판단 근거:
  - ___
  - ___

---

## 5. 적용 변경
> 이번 개선 사례에서는 변경 범위를 하나로 제한한다.

| 항목  | Before | After |
| --- | ------ | ----- |
| ___ | ___    | ___   |

### 변경 이유
- 
- 

---

## 6. After 측정 결과
> 동일 조건으로 다시 부하 테스트를 실행한다.
```
./k6/run_k6_test.sh load
```

| 지표          | 값      |
| ----------- | ------ |
| avg latency | ___ ms |
| p95 latency | ___ ms |
| RPS         | ___    |
| error rate  | ___ %  |
| kafka_lag   | ___    |

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

### 관측 내용
- 
- 

---

## 7. 결과 비교

| 지표          | Before | After  | 변화    |
| ----------- | ------ | ------ | ----- |
| avg latency | ___ ms | ___ ms | ___ % |
| p95 latency | ___ ms | ___ ms | ___ % |
| RPS         | ___    | ___    | ___ % |
| error rate  | ___ %  | ___ %  | ___ % |
| kafka_lag   | ___    | ___    | 증가 / 감소 / 유지 |

### 변화값 기록 기준

| 지표 | 변화 계산 방향 |
| --- | --- |
| avg latency | 낮아질수록 개선 |
| p95 latency | 낮아질수록 개선 |
| RPS | 높아질수록 개선 |
| error rate | 낮아질수록 개선 |
| kafka_lag | 낮아지거나 증가가 멈추면 개선 |

`kafka_lag`는 퍼센트 개선율보다 Before / After 패턴 비교를 우선한다.

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
avg latency 감소율 = (___ - ___) / ___ * 100 = ___ %
p95 latency 감소율 = (___ - ___) / ___ * 100 = ___ %
RPS 증가율 = (After RPS - Before RPS) / Before RPS * 100
= (___ - ___) / ___ * 100 = ___ %
error rate 감소율 = (___ - ___) / ___ * 100 = ___ %
```

---

## 8. 결론
- 병목: ___
- 개선 방법: ___
- 결과: ___

---
