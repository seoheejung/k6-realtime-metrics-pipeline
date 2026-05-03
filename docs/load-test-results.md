# Load Test Improvement Case

## 1. 개선 사례 목표

> 부하 테스트를 통해 병목을 발견하고, Collector 설정을 개선한 뒤 동일 조건으로 재측정하여 성능 개선 효과를 검증한다.

### 검증 흐름
```text
기준 측정 → 병목 확인 → 개선 적용 → 재측정 → Before / After 비교
```

---

## 2. 기준 측정 및 확인 지표
> 기준 부하 테스트는 기존 load 시나리오를 그대로 사용한다.
```
./k6/run_k6_test.sh load
```

### Grafana 확인 지표
| 지표 | 의미 | 기록값 |
| --- | --- | --- |
| Response Time | latency | avg latency, p95 latency |
| Requests (RPS) | 처리량 | RPS |
| Failed Requests | 오류 | error rate |
| kafka_lag | Collector 처리 지연 | kafka_lag |

### 테스트 조건
| 항목       | 값                          |
| -------- | -------------------------- |
| 실행 명령    | `./k6/run_k6_test.sh load` |
| VUs      | ___                        |
| duration | ___                        |
| 대상 API   | ___                        |
| 측정 일시    | ___                        |

---

## 3. 병목 가설 및 개선 후보
> 기준 부하 테스트에서 아래 패턴이 발생하는지 확인한다.

- Response Time 급증
- RPS 정체
- VUs 증가 대비 성능 유지 안됨
- kafka_lag 증가

### 원인 분리 기준

| 위치 | 의심 |
| --- | --- |
| API | CPU / DB / blocking |
| Collector | batch / retry |
| Kafka | lag |
| Influx | write 지연 |


### 병목 후보 우선순위

현재 구조에서 우선 확인할 병목 후보는 Collector → Influx write 구간이다.

| 순위 | 위치 | 이유 |
| -- | -- | -- |
| 1 | Collector → Influx write | 네트워크 + batch 처리 + sync I/O |
| 2 | Kafka lag | consumer 처리속도 영향 |
| 3 | API | 현재 단순 endpoint라 병목 가능성 낮음 |

### 개선 후보

| 후보 | 변경 내용 | 기대 효과 |
| --- | --- | --- |
| batch-size 증가 | Collector batch size 증가 | Influx write 호출 횟수 감소 |
| flush interval 조정 | Collector flush interval 조정 | buffer 체류 시간 및 write 주기 조정 |
| API sleep 제거 | 테스트용 지연 로직 제거 | API 응답 지연 감소 |
| thread pool 조정 | API 또는 Collector worker/thread pool 조정 | blocking 구간 처리량 개선 |


---

## 4. Before 측정 결과

| 지표          | 값      |
| ----------- | ------ |
| avg latency | ___ ms |
| p95 latency | ___ ms |
| RPS         | ___    |
| error rate  | ___ %  |
| kafka_lag   | ___    |


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

---

## 8. 결론
- 병목: ___
- 개선 방법: ___
- 결과: ___

---
