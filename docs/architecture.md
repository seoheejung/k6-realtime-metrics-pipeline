# Architecture

## 전체 구조

```
[Load Path]
k6 ──────────────────────────────────────► Kotlin API

[Metrics Path]
k6 ──► Kafka (k6-metrics) ──► Kotlin Collector ──► InfluxDB v3 ──► Grafana

[Infra Path]
Terraform ──► Azure 리소스 생성
Ansible   ──► VM 환경 구성 및 서비스 배포
```

### Load Path
- 실제 부하 발생
- 응답 시간 / 상태 코드 생성

### Metrics Path
- k6가 메트릭을 Kafka로 전송
- Collector가 batch 처리 후 InfluxDB 적재
- Grafana가 시각화

---

## 컴포넌트별 역할

| 컴포넌트 | 역할 | 비고 |
|---|---|---|
| k6 | 부하 생성 + 메트릭 발행 | 대상 API 호출 + Kafka로 메트릭 전송 |
| Kafka | 메트릭 스트림 버퍼 | topic: `k6-metrics` |
| Kotlin Collector | Kafka consume → 가공 → InfluxDB write | batch write, 재시도, 내부 처리 메트릭 |
| InfluxDB v3 | 시계열 데이터 저장 | line protocol write (`/api/v3/write_lp`) |
| Grafana | 결과 및 상태 시각화 | datasource: InfluxDB v3 |
| Kotlin API | 부하 테스트 대상 서비스 | 실제 트래픽 처리 대상 |
| Terraform | Azure 리소스 프로비저닝 | VM, VNet, NSG, Public IP |
| Ansible | 런타임 구성 및 서비스 기동 | Docker, Java, 설정 파일 배치 

---

## 경로 분리 설계 의도

```
서비스 트래픽  : k6 ──► Kotlin API
메트릭 트래픽 : k6 ──► Kafka ──► Kotlin Collector ──► InfluxDB v3
```

* 서비스 요청 처리 경로와 메트릭 수집 경로를 분리한다.
  - 서비스 장애가 발생해도 메트릭 수집 경로는 유지
  - 테스트 대상 시스템과 관측 시스템 간 간섭 제거
  - 병목 위치를 경로 단위로 분리하여 분석 가능

---

## 로컬 실행 구조

```
Docker Network (compose)

k6 (host 실행)
├──► Kotlin API (container, :8080)
└──► Kafka (container, :9092)

Kafka
└──► Kotlin Collector

Kotlin Collector
└──► InfluxDB v3 (:8181)

Grafana
└──► InfluxDB v3 조회
```

### 핵심 포인트
- Kotlin API는 반드시 실행되어야 함 (부하 대상)
- Collector는 외부 포트를 열지 않음 (내부 처리 전용)
- Kafka / Influx / Collector는 동일 네트워크에서 동작

---

## Azure 배포 구조

### 1차 구조 (단일 VM, 권장)

```
Azure VM 1
├── Kafka
├── Kotlin Collector
├── InfluxDB v3
├── Grafana
└── Kotlin API

외부
└── k6 실행
```

#### 단일 VM 기준
- 네트워크 구성 단순
- 장애 발생 시 추적 용이
- Terraform / Ansible 적용 단순화


### 2차 구조 (VM 분리, 확장 시)

```
Azure VM 1 ── Kafka
Azure VM 2 ── Kotlin Collector + InfluxDB v3 + Grafana
Azure VM 3 ── Kotlin API
```

#### 분리 기준
- Kafka I/O 분리
- API와 관측 스택 분리
- 부하 테스트 시 리소스 간섭 최소화

---

## 네트워크 구성 (예정)

```
VNet: 10.0.0.0/16
  Subnet: 10.0.1.0/24
    VM-1 (pipeline): 10.0.1.10
    VM-2 (api):      10.0.1.20  ← 확장 시
```

### NSG 인바운드 포트

| 포트 | 용도 |
|---|---|
| 22 | SSH |
| 9092 | Kafka |
| 8181 | InfluxDB v3 |
| 3000 | Grafana |
| 8080 | Kotlin API |


---

## 구현 단계

```
1단계: Kotlin API 실행 (부하 대상 확보)
2단계: k6 → Kafka → Kotlin Collector → InfluxDB v3 → Grafana (로컬)
3단계: 병목 분석 → 수정 → 재측정 (개선 사례 확보)
4단계: Azure Terraform (리소스 생성)
5단계: Azure Ansible (환경 구성 및 배포)
```
