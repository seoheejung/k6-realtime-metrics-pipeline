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

---

## 컴포넌트별 역할

| 컴포넌트 | 역할 | 비고 |
|---|---|---|
| k6 | 부하 생성 + 메트릭 발행 | xk6-output-kafka 커스텀 빌드 필요 |
| Kafka | 메트릭 스트림 버퍼 | topic: `k6-metrics` |
| Kotlin Collector | Kafka consume → 가공 → InfluxDB write | batch write, 재시도, 내부 메트릭 노출 |
| InfluxDB v3 | 시계열 데이터 저장 | `/api/v3/write_lp` 사용 |
| Grafana | 결과 및 상태 시각화 | datasource: InfluxDB v3 |
| Kotlin API | 부하 테스트 대상 서비스 | - |
| Terraform | Azure 리소스 프로비저닝 | VM, VNet, NSG, Public IP |
| Ansible | 런타임 구성 및 서비스 기동 | Docker, Java, 설정 파일 배치 |

---

## 경로 분리 설계 의도

```
서비스 트래픽  : k6 ──► Kotlin API
메트릭 트래픽 : k6 ──► Kafka ──► Kotlin Collector ──► InfluxDB v3
```

부하를 받는 경로와 메트릭 수집 경로를 물리적으로 분리한다.
서비스 경로 장애가 메트릭 수집에 영향을 주지 않으며, 테스트 결과 해석의 정확도를 높인다.

---

## Azure 배포 구조

### 1차 구조 (단일 VM, 권장)

```
Azure VM 1
├── Kafka
├── Kotlin Collector
├── InfluxDB v3
└── Grafana

외부 / 로컬
└── k6 실행

별도 VM 또는 동일 VM
└── Kotlin API (테스트 대상)
```

단일 VM 기준으로 Terraform/Ansible 적용이 용이하고, 장애 원인 분리가 쉽다.

### 2차 구조 (VM 분리, 확장 시)

```
Azure VM 1 ── Kafka
Azure VM 2 ── Kotlin Collector + InfluxDB v3 + Grafana
Azure VM 3 ── Kotlin API
```

---

## 네트워크 구성 (예정)

```
VNet: 10.0.0.0/16
  Subnet: 10.0.1.0/24
    VM-1 (pipeline): 10.0.1.10
    VM-2 (api):      10.0.1.20  ← 확장 시

NSG 인바운드 허용 포트:
  22    ── SSH
  9092  ── Kafka
  8086  ── InfluxDB v3
  3000  ── Grafana
  8080  ── Kotlin API
```

---

## 구현 단계

```
1단계: k6 → Kafka → Kotlin Collector → InfluxDB v3 → Grafana (로컬)
2단계: 개선 사례 1건 확보 (병목 분석 → 수정 → 재측정)
3단계: Azure Terraform (리소스 생성)
4단계: Azure Ansible (환경 구성 및 배포)
```
