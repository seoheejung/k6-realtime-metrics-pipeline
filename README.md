# k6 Realtime Metrics Pipeline

k6 기반 부하 테스트 메트릭을 Kafka로 스트리밍하고, Kotlin Collector를 통해 InfluxDB v3에 적재하여 Grafana로 시각화하는 파이프라인이다. Azure 환경에서 Terraform과 Ansible을 통해 인프라 프로비저닝 및 배포를 자동화한다.

---

## 아키텍처

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

## 기술 스택

| 영역 | 기술 |
|---|---|
| 부하 생성 | k6 + xk6-output-kafka |
| 메시지 버퍼 | Apache Kafka |
| 메트릭 수집기 | Kotlin (JVM) |
| 시계열 DB | InfluxDB v3 |
| 시각화 | Grafana |
| 테스트 대상 | Kotlin (Spring Boot or Ktor) |
| IaC | Terraform |
| 구성 자동화 | Ansible |
| 클라우드 | Azure |

---

## 디렉토리 구조

```
k6-realtime-metrics-pipeline/
│
├── k6/                         # 부하 테스트 스크립트
│   ├── scripts/
│   ├── scenarios/
│   ├── config/
│   ├── docker/
│   └── run_k6_test.sh
│
├── kafka/
│   ├── docker-compose.yml      # 로컬 Kafka 단독 실행용
│   └── config/
│       └── server.properties
│
├── collector/                  # Kotlin Collector
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/collector/
│       ├── Application.kt
│       ├── kafka/KafkaConsumer.kt
│       ├── processor/MetricsProcessor.kt
│       ├── influx/InfluxWriter.kt
│       └── metrics/CollectorMetrics.kt
│
├── observability/
│   ├── grafana/
│   │   ├── dashboards/k6-dashboard.json
│   │   └── provisioning/
│   │       ├── datasources/influxdb.yml
│   │       └── dashboards/dashboard.yml
│   └── influxdb/
│       └── config/influx-config.yml
│
├── infra/
│   ├── terraform/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── network.tf
│   │   ├── vm.tf
│   │   └── terraform.tfvars
│   └── ansible/
│       ├── inventory/hosts.ini
│       ├── playbooks/
│       │   ├── setup.yml
│       │   └── deploy.yml
│       └── roles/
│           ├── common/
│           ├── kafka/
│           ├── collector/
│           ├── influxdb/
│           └── grafana/
│
├── app/                        # 테스트 대상 Kotlin API
│   ├── build.gradle.kts
│   └── src/main/kotlin/api/Application.kt
│
├── docs/
│   ├── architecture.md
│   ├── collector-design.md
│   ├── influx-schema.md
│   ├── kafka-strategy.md
│   └── load-test-results.md
│
├── scripts/
│   ├── start-local.sh          # 전체 로컬 스택 기동
│   └── clean.sh
│
├── .env
├── docker-compose.yml          # 로컬 전체 실행 (kafka 포함 통합)
├── README.md
└── .gitignore
```

> `kafka/docker-compose.yml`은 Kafka 단독 개발 및 검증용이다.
> 전체 스택 실행은 루트의 `docker-compose.yml`을 사용한다.

---

## 문서

| 문서 | 내용 |
|---|---|
| [architecture.md](docs/architecture.md) | 전체 구조, 컴포넌트 역할, Azure 배포 구조 |
| [collector-design.md](docs/collector-design.md) | Kotlin Collector 모듈 설계, 데이터 흐름, 재시도 정책 |
| [influx-schema.md](docs/influx-schema.md) | InfluxDB v3 measurement 스키마, Grafana 쿼리 예시 |
| [kafka-strategy.md](docs/kafka-strategy.md) | Kafka 도입 이유, xk6 빌드, topic 설정, 장애 시나리오 |
| [load-test-results.md](docs/load-test-results.md) | 부하 테스트 결과, 병목 분석, 개선 전후 비교 |

---

## 로컬 실행

### 사전 요구사항

- Docker + Docker Compose
- Go 1.21 이상 (xk6 빌드용)
- JDK 17 이상 (Kotlin Collector 빌드용)

### 환경 변수 설정

```bash
cp .env.example .env
# .env에서 INFLUX_TOKEN 등 설정
```

### k6 커스텀 바이너리 빌드

```bash
cd k6/docker
docker build -t k6-kafka .
```

> xk6-output-kafka 포함 바이너리 빌드 확인은 프로젝트 시작 전 스파이크 검증을 통해 수행한다.
> 실제 Kafka 메시지 포맷 확인 방법은 `docs/kafka-strategy.md` 참고.

### 전체 스택 기동

```bash
docker-compose up -d
```

기동 순서: Kafka → InfluxDB v3 → Grafana → Kotlin Collector → Kotlin API

### 부하 테스트 실행

```bash
# smoke 테스트
./k6/run_k6_test.sh smoke

# load 테스트
./k6/run_k6_test.sh load

# stress 테스트
./k6/run_k6_test.sh stress
```

### Grafana 접속

```
http://localhost:3000
기본 계정: admin / admin
```

---

## 주요 포트

| 서비스 | 포트 |
|---|---|
| Kafka | 9092 |
| InfluxDB v3 | 8086 |
| Grafana | 3000 |
| Kotlin API | 8080 |

---

## 시각화 항목

| 지표 | 설명 |
|---|---|
| RPS | 초당 요청 수 |
| p95 / p99 latency | 응답 시간 분포 |
| Error rate | 요청 실패 비율 |
| Status code 분포 | 2xx / 4xx / 5xx 비율 |
| Kafka lag | Collector consumer lag |
| Collector TPS | Collector 처리량 |
| Buffer size | Collector 내부 버퍼 상태 |

---

## Azure 배포

### 1. 리소스 생성 (Terraform)

```bash
cd infra/terraform
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

### 2. 환경 구성 및 배포 (Ansible)

```bash
cd infra/ansible
ansible-playbook -i inventory/hosts.ini playbooks/setup.yml
ansible-playbook -i inventory/hosts.ini playbooks/deploy.yml
```

Terraform은 인프라(VM, VNet, NSG, Public IP) 생성을 담당하고, Ansible은 Docker 설치, 서비스 기동, 설정 파일 배치를 담당한다.

자세한 내용은 `docs/architecture.md` 참고.

---

## 구현 단계

```
[완료 목표]
1. k6 작성 (부하 테스트)
2. Kotlin Collector 작성 (consume → 가공 → write)
3. k6 → Kafka 연결 (xk6-output-kafka 빌드 및 검증)
3. InfluxDB v3 적재 확인
4. Grafana 대시보드 구성
5. 개선 사례 1건 확보 (병목 발견 → 수정 → 재측정)

[확장]
6. Azure Terraform (VM, VNet, NSG)
7. Azure Ansible (Docker 설치, 서비스 기동)
```
