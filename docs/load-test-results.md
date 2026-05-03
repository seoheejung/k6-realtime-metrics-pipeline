# Load Test Improvement Case

## 기준 측정 (이미 있는 그대로)
```
./k6/run_k6_test.sh load
```
### Grafana 캡쳐
- avg latency
- p95 latency
- RPS
- error rate
- kafka_lag

---

## 문제 상황
> 기준 부하 테스트에서 아래 패턴이 발생하는지 확인한다.

- kafka_lag 증가
- RPS 증가 안됨
- latency 상승

## 원인 분석
> 현재 구조에서 우선 확인할 병목 후보는 Collector → Influx write 구간이다.

- Collector batch 처리 비효율
- Influx write 호출 횟수 과다
- 병목 후보 우선 순위
| 순위 | 위치                       | 이유                         |
| -- | ------------------------ | -------------------------- |
| 1  | Collector → Influx write | 네트워크 + batch 처리 + sync I/O |
| 2  | Kafka lag                | consumer 처리속도 영향           |
| 3  | API                      | 현재 단순 endpoint라 병목 가능성 낮음  |


---

## 개선 후보

### (1) batch size 증가
- BATCH_SIZE = 500 → 2000
### (2) flush interval 감소
- FLUSH_INTERVAL_MS = 1000 → 200~300
### (3) sync write → async write (가능하면)
- blocking HTTP 호출이면 병목 확정

---
## 테스트 조건
| 항목       | 값                          |
| -------- | -------------------------- |
| 실행 명령    | `./k6/run_k6_test.sh load` |
| VUs      | ___                        |
| duration | ___                        |
| 대상 API   | ___                        |
| 측정 일시    | ___                        |


## Before 측정 결과

| 지표          | 값      |
| ----------- | ------ |
| avg latency | ___ ms |
| p95 latency | ___ ms |
| RPS         | ___    |
| error rate  | ___ %  |
| kafka_lag   | ___    |

### Before 관측 내용
- 
- 
- 

---

## 적용 변경
| 항목  | Before | After |
| --- | ------ | ----- |
| ___ | ___    | ___   |

---

## After 측정 결과

| 지표          | 값      |
| ----------- | ------ |
| avg latency | ___ ms |
| p95 latency | ___ ms |
| RPS         | ___    |
| error rate  | ___ %  |
| kafka_lag   | ___    |

---


## 결과 비교

| 지표          | Before | After  | 변화    |
| ----------- | ------ | ------ | ----- |
| avg latency | ___ ms | ___ ms | ___ % |
| p95 latency | ___ ms | ___ ms | ___ % |
| RPS         | ___    | ___    | ___ % |
| error rate  | ___ %  | ___ %  | ___ % |
| kafka_lag   | ___    | ___    | ___   |


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
RPS 증가율 = (___ - ___) / ___ * 100 = ___ %
error rate 감소율 = (___ - ___) / ___ * 100 = ___ %
```

## 결론
- batch 최적화로 write 호출 감소
- 전체 처리량 증가 및 latency 개선

---
