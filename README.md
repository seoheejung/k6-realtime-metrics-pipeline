# k6 Realtime Metrics Pipeline

k6 기반 부하 테스트 메트릭을 Kafka로 스트리밍하고, Kotlin Collector를 통해 InfluxDB v3에 적재한 뒤 Grafana로 시각화하는 파이프라인이다.    
부하 경로와 메트릭 경로를 분리해 테스트 대상 API와 관측 파이프라인을 독립적으로 다룬다.    
Azure 환경에서는 Terraform과 Ansible로 인프라 생성과 배포 자동화를 수행한다.

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
| 테스트 대상 | Kotlin API |
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
│
├── collector/                  # Kotlin Collector
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/
│       │   └── com/pipeline/collector/
│       │       ├── Application.kt         # 실행 진입점
│       │       ├── config/
│       │       ├── model/
│       │       ├── kafka/
│       │       ├── processor/
│       │       ├── influx/
│       │       └── metrics/
│       └── resources/
│
├── observability/                       # Grafana / InfluxDB 설정
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
├── app/                        # 부하 테스트 대상 Kotlin API
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── kotlin/
│       │   │   └── com/pipeline/api/
│       │   │       ├── Application.kt
│       │   │       ├── controller/
│       │   │       ├── dto/
│       │   │       └── service/
│       │   └── resources/
│       └── test/
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

## 변경 관리 프로세스 (Git Workflow)

### PR 사용 목적
- 인프라 변경으로 인한 장애 리스크 통제 목적
- 협업 도구가 아닌 변경 검증 게이트로 사용
- 코드 리뷰가 아닌 self-review 강제 수단
- 설정 변경이 Kafka, InfluxDB, API 전체에 영향을 주므로 변경 단위 분리 및 사전 검증 필요

### 작업 흐름

```bash
git checkout -b feature/xxx
git add .
git commit -m "feat: xxx"
git push origin feature/xxx
```
1. 기능 단위 브랜치 생성 후 작업 수행
2. 원격 저장소로 push
3. PR 생성 (`Compare & pull request`)
4. 체크리스트 기반 검증
5. 검증 완료 후 main 브랜치로 merge
6. CI/CD를 통한 배포 진행

### 검증 방식
- 체크리스트 기반 self-review
- 모든 PR 동일 기준 적용
- `.github/pull_request_template.md`를 통해 자동 적용

### 브랜치 전략
```
feature/xxx
```
- 기능 단위로 브랜치 분리
- main 브랜치는 항상 배포 가능한 상태 유지
- main 브랜치 직접 커밋 금지

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

### 0. 사전 요구사항
- Docker + Docker Compose
- Go 1.21 이상 (xk6 빌드용)
- JDK 17 이상 (Collector / Kotlin API 빌드용)
- Gradle Wrapper 사용 가능 환경

### 1. 환경 변수 설정

- 실행 위치 기준으로 관리

#### 루트 디렉토리
- 루트 `.env`는 전체 `docker compose` 실행 시 사용
```
cp .env.example .env
```

#### collector 디렉토리
- collector 단독 실행 시 Kafka, InfluxDB 연결 값은 collector 기준 설정으로 관리
```
cd collector
./gradlew bootRun
```

#### app 디렉토리
- app 단독 실행 시 application.yml 또는 환경 변수 기준으로 실행
```
cd app
./gradlew bootRun
```


### 2. k6 커스텀 바이너리 빌드

```bash
cd k6/docker
docker build -t k6-kafka .
cd ../..
```

> xk6-output-kafka 포함 바이너리 빌드 확인은 프로젝트 시작 전 스파이크 검증을 통해 수행한다.
> 실제 Kafka 메시지 포맷 확인 방법은 `docs/kafka-strategy.md` 참고.

### 3. Collector / Kotlin API 빌드

- 각 컴포넌트는 독립 Gradle 프로젝트로 관리

#### Collector 빌드
```
cd collector
./gradlew clean build
cd ..
```

#### Kotlin API 빌드
```
cd app
./gradlew clean build
cd ..
```

> 단독 실행 모드는 개발/디버깅 용도이며,   
> 실제 파이프라인 검증은 docker compose 기반 통합 실행을 기준으로 한다.

### 4. 메트릭 파이프라인 기동 (root 디렉토리)

```bash
# 1. 기존 컨테이너와 볼륨 정리
docker compose down -v

# 2. 전체 스택 기동
docker compose up -d

# 3. 상태 확인
docker compose ps
```

- docker-compose.yml 기준으로 전체 로컬 스택 한 번에 기동
- 기본 기동 순서: Kafka → InfluxDB v3 → Grafana → Kotlin Collector → Kotlin API

> 서비스는 docker compose로 동시에 기동되며,   
> Collector는 Kafka, InfluxDB 준비 상태를 전제로 동작한다.   
> 초기 기동 시 일부 재시도 로그가 발생할 수 있다.   

### 5. 단독 실행 확인

#### Kotlin API 확인
```
curl http://localhost:8080/api/health
```
#### Grafana 확인
```
curl http://localhost:3000
```
#### Collector 로그 확인
```
docker compose logs -f collector
```

### 6. 부하 테스트 실행

```bash
# smoke 테스트
./k6/run_k6_test.sh smoke

# load 테스트
./k6/run_k6_test.sh load

# stress 테스트
./k6/run_k6_test.sh stress
```

- run_k6_test.sh는 환경 변수 BASE_URL을 기준으로 Kotlin API에 요청을 보낸다.
    - 기본값: http://localhost:8080
    - docker compose 환경: http://app:8080 (컨테이너 내부 DNS)

### 7. Grafana 접속

```
http://localhost:3000
기본 계정: admin / admin
```

---

## 정상 동작 기준

1. k6 실행 시 오류 없이 요청 전송
2. Kafka topic(k6-metrics)에 메시지 유입
3. Collector 로그에서 consume 확인
4. InfluxDB write 성공 로그 확인
5. Grafana에서 데이터 시각화 확인

### 장애 확인 포인트

- docker compose ps에서 모든 핵심 컨테이너가 Up 상태인지 확인
- app 헬스체크가 200 OK인지 확인
- collector 로그에 Kafka consume / Influx write 오류가 없는지 확인
- Grafana에서 InfluxDB datasource가 정상 연결됐는지 확인
- Collector가 Kafka 연결 실패 후 재시도 로그를 출력하는 것은 정상 동작일 수 있음
- InfluxDB 연결 실패 로그도 초기 기동 시 일시적으로 발생 가능

---

## 주요 포트

| 서비스         | 포트           | 설명 |
|---------------|----------------|------|
| Kafka         | 9092           | 내부 통신 |
| Kafka         | 29092          | 외부 접근 (k6 등) |
| InfluxDB v3   | 8181           | write/query API |
| Grafana       | 3000           | 대시보드 |
| Kotlin API    | 8080           | 부하 테스트 대상 |

---

## 시각화 항목

| 지표                | 설명                     |
| ----------------- | ---------------------- |
| RPS               | 초당 요청 수                |
| p95 / p99 latency | 응답 시간 분포               |
| Error rate        | 요청 실패 비율               |
| Status code 분포    | 2xx / 4xx / 5xx 비율     |
| Kafka lag         | Collector consumer lag |
| Collector TPS     | Collector 처리량          |
| Buffer size       | Collector 내부 버퍼 상태     |

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
1. 부하 대상 Kotlin API 작성 및 실행 경로 확보 ✅
2. k6 작성 (부하 테스트) ✅
3. Kotlin Collector 작성 (consume → 가공 → write) ✅
4. k6 → Kafka 연결 (xk6-output-kafka 빌드 및 검증) ✅
5. InfluxDB v3 적재 확인
6. Grafana 대시보드 구성
7. 개선 사례 1건 확보 (병목 발견 → 수정 → 재측정)

[확장]
8. Azure Terraform (VM, VNet, NSG)
9. Azure Ansible (Docker 설치, 서비스 기동)
```
