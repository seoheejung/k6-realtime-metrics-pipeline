# app

> Kotlin API는 실제 서비스 로직 구현체가 아니라,   
> k6 기반 부하 테스트와 메트릭 파이프라인 검증을 위한 테스트 대상 API

---

## 역할

- 요청 처리 성능 측정
- 에러율 / 지연 / CPU 부하 시뮬레이션
- Kafka → Collector → InfluxDB 파이프라인 검증용 트래픽 생성 대상

---

## 디렉토리 구조
```
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   └── com/pipeline/api/
    │   │       ├── Application.kt
    │   │       ├── controller/
    │   │       │   └── LoadTestController.kt
    │   │       ├── dto/
    │   │       │   ├── EchoRequest.kt
    │   │       │   └── ApiResponse.kt
    │   │       └── service/
    │   │           └── LoadTestService.kt
    │   └── resources/
    │       └── application.yml
    └── test/
        └── kotlin/
            └── com/pipeline/api/
                └── ApplicationTests.kt
```

---

## 제공 endpoint

| endpoint      | method | 목적              | 검증 포인트      |
| ------------- | ------ | --------------- | ----------------- |
| `/api/health` | GET    | 서비스 상태 확인     |  기본 헬스체크           |
| `/api/hello`  | GET    | 기본 요청 응답 확인   | 네트워크/라우팅/응답 확인    |
| `/api/echo`   | POST   | JSON 처리 성능 검증  | 직렬화/역직렬화, POST 성능 |
| `/api/delay`  | GET    | 지연(latency) 테스트 | latency 분포 측정     |
| `/api/cpu`    | GET    | CPU 부하 테스트      | 애플리케이션 계산 부하      |
| `/api/error`  | GET    | 에러율 측정          | 실패율, 에러 핸들링       |


---

## 실행 방법

### 1. 환경 설정 (.env)

- 현재 API는 필수 환경 변수 없음
- 확장 시 아래 형태로 추가
```
SERVER_PORT=8080
```

---

### 2. 설정 로딩 방식

- 기본 설정은 application.yml 사용
- Spring Boot 표준 설정 방식 사용
- .env는 향후 Docker / Compose 환경에서만 사용

```
# 우선순위
환경변수 > application.yml
```

### 3. 실행

```
cd app
./gradlew bootRun
```
#### 확인
```
curl http://localhost:8080/api/health
```

### 4. docker compose로 확인 (root 디렉토리)

#### 루트에서 실행
```
docker compose up -d
```

#### 확인
```
docker compose ps
curl http://localhost:8080/api/health
```

---

## 동작 흐름
```
k6 → HTTP 요청 → Kotlin API

Kotlin API 내부:
Controller → Service → 응답 생성
```

### 응답
- JSON 형태 반환
- timestamp 포함
- 요청 단위 독립 처리 (stateless)

---

## 실패 처리 기준

| 항목                    | 처리 방식             |
| --------------------- | ----------------- |
| 잘못된 요청                | 400 반환            |
| 의도적 에러 (`/api/error`) | 지정된 status 반환     |
| 내부 예외                 | 500 반환            |
| timeout               | 클라이언트(k6) 기준으로 측정 |

### 설계 기준
- retry 없음
- circuit breaker 없음
- 단순 처리 구조 유지 (부하 테스트 대상이므로)

---

## 확장 포인트
- DB 연동 추가 (쓰기 부하 테스트)
- Redis 연동 (캐시 성능 테스트)
- ThreadPool 조정 (Tomcat tuning)
- 비동기 처리 (WebFlux or Coroutine)
- Rate Limit / Circuit Breaker 실험용 추가

---

## 목적

> 이 API의 목적은 기능 제공이 아니라 부하 테스트 기준점 제공이다.

### 측정 대상
1. 응답 시간 (p95, p99)
2. 처리량 (RPS)
3. 에러율
4. 지연 분포
5. CPU 부하 시 성능 변화

### 사용 위치
```
k6 → Kafka → Collector → InfluxDB → Grafana
```
- k6의 실제 요청 대상이 되는 컴포넌트
