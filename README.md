# k6-realtime-metrics-pipeline

> k6 + Kafka + Kotlin + InfluxDB v3 기반 실시간 부하 테스트 메트릭 파이프라인

---

## 프로젝트 개요
> k6의 실시간 출력 제약을 Kafka로 우회하고, Kotlin Collector를 통해 메트릭을 가공하여 InfluxDB v3에 적재하는 스트리밍 기반 부하 테스트 파이프라인

- 실시간 부하 테스트 메트릭 수집
- Kafka 기반 스트림 처리
- Kotlin Collector를 통한 데이터 가공 및 적재
- InfluxDB v3 시계열 저장
- Grafana 시각화
- Terraform + Ansible 기반 Azure 자동화

---

## 아키텍처
```
[Load Path]
k6 -> Kotlin API

[Metrics Path]
k6 -> Kafka -> Kotlin Collector -> InfluxDB v3 -> Grafana

[Infra Path]
Terraform -> Azure 자원 생성
Ansible -> VM / 실행환경 / 배포 자동화
```

> 부하 트래픽 경로와 메트릭 수집 경로를 분리하여 테스트 정확도를 확보한다.

---

## 메트릭 경로 분리
```
서비스 트래픽: k6 -> Kotlin API
메트릭 경로: k6 -> Kafka -> Kotlin -> InfluxDB
```
- 테스트 대상과 수집 경로 분리
- 부하 영향 최소화
- 분석 정확도 확보

---

## 디렉토리 구조
```
k6-realtime-metrics-pipeline/
├── k6/              # 부하 생성
├── kafka/
├── collector/       # 데이터 처리
├── observability/   # 저장/시각화
├── infra/
├── app/             # 테스트 대상
├── docs/
├── scripts/
```

---

## 특징

- k6 실시간 출력 한계를 Kafka로 우회
- 메트릭 수집과 서비스 트래픽 경로 분리
- Kotlin Collector를 통한 데이터 가공 레이어 구성
- InfluxDB v3 기반 시계열 저장

---

## 구성 요소
### k6
- HTTP 부하 생성
- latency / RPS / error rate 수집
- Kafka로 메트릭 전송

### Kafka
- 메트릭 스트림 버퍼
- producer / consumer 분리
- burst traffic 흡수
- 장애 발생 시 메트릭 유실 방지

#### k6 → Kafka 전송 방식

k6는 기본적으로 Kafka 출력 기능을 제공하지 않기 때문에   
xk6-output-kafka extension을 사용하여 메트릭을 Kafka로 전송한다.

### Kotlin Collector
- Kafka Consumer
- 메트릭 스키마 표준화
- 메트릭 가공 / 정규화
- batch write
- InfluxDB v3 적재

### InfluxDB v3
- 시계열 데이터 저장
- write API 기반 적재

### Grafana
- 부하 테스트 결과 시각화
- 시스템 상태 모니터링

### Terraform / Ansible
- Azure 인프라 생성
- 실행 환경 구성 및 배포 자동화

---

## 실행 환경

- Docker
- Docker Compose
- Node.js (k6 실행 시 필요)
- Java 17+ (Kotlin Collector 실행 시)
- k6 (또는 Docker 기반 실행)

---

## 실행 방법 (로컬)
### 1. 전체 스택 실행
```
docker-compose up -d
```
### 2. k6 테스트 실행
```
cd k6
./run_k6_test.sh
```
### 3. Grafana 접속
```
http://localhost:3000
```

---


## 데이터 흐름
```
k6
 -> Kafka
 -> Kotlin Collector
 -> InfluxDB v3
 -> Grafana
 ```

--- 

## 관측 항목
- RPS
- p95 / p99 latency
- error rate
- status code 분포
- Kafka lag
- collector 처리량
- 큐 적체 상태

---

## 구현 순서
1. k6 -> Kafka 연결
2. Kotlin Collector 구현
3. InfluxDB v3 적재 확인
4. Grafana 대시보드 구성
5. 성능 개선 사례 확보
6. Terraform (Azure)
7. Ansible 배포 자동화

---

## Azure 구성 (예정)
### 1차 구조
```
Azure VM 1
- Kafka
- Collector
- InfluxDB v3
- Grafana

외부
- k6 실행

테스트 대상
- Kotlin API
```

---

## 성능 검증 기준

- p95 latency 기준 병목 판단
- error rate 증가 구간 분석
- RPS 대비 응답시간 변화 확인
- Kafka lag 기반 처리 지연 확인

### 목표
- p95 latency 안정 구간 확보
- error rate 1% 이하 유지

### 결과 분석 (작성 예정)
- p95 latency
- throughput
- error rate
- 병목 구간
- 개선 전/후 비교

