# k6

k6 기반 부하 테스트 실행 영역

이 디렉토리는 테스트 대상 API에 부하를 발생시키고,  
생성된 메트릭을 Kafka로 전송하기 위한 스크립트와 실행 구성을 포함한다.

---

## 역할

- 부하 테스트 시나리오 실행
- smoke / load / stress 테스트 분리
- 공통 요청 로직 재사용
- Kafka output extension이 포함된 k6 커스텀 바이너리 실행

---

## 디렉토리 구조

``` id="k6tree"
k6/
├── scripts/
│   └── single_api_load.js     # 공통 요청 로직
├── scenarios/
│   ├── smoke.js               # 최소 연결 확인
│   ├── load.js                # 정상 부하 테스트
│   └── stress.js              # 점진적 부하 증가 테스트
├── config/
│   ├── env.js                 # requireEnv 중복 제거용
│   └── k6-options.js          # 시나리오별 공통 옵션
├── docker/
│   └── Dockerfile             # xk6-output-kafka 포함 커스텀 k6 빌드
├── .env
└── run_k6_test.sh             # 시나리오 실행 스크립트
```

---

## 실행 전 준비
### 1. .env 파일 준비
- `.env.example` 파일을 복사하여 `k6/.env` 파일 생성

### 2. 실행 권한 부여

```
chmod +x k6/run_k6_test.sh
```

### 3. 테스트 대상 API (임시)

현재 Kotlin API가 없는 상태에서는  
k6에서 제공하는 테스트용 HTTP 서버(`httpbin`)를 사용한다.

#### 사용 URL
```
https://test.k6.io

https://httpbin.test.k6.io
```

#### .env 예시
```
BASE_URL=https://test.k6.io
TARGET_PATH=/get
```
- `test.k6.io`는 k6 공식 테스트 서버
- 다양한 HTTP 응답을 테스트할 수 있음
- 부하 테스트 로직 검증 및 Kafka 파이프라인 확인 용도로 사용

※ 200 응답을 보장하는 endpoint(`/get`) 사용 권장

#### 이후 변경
```
BASE_URL=http://<your-api-host>
TARGET_PATH=/api/endpoint
```

---

## 실행 방법

### smoke 테스트

```
./k6/run_k6_test.sh smoke
```

목적:

- 최소 연결 확인
- 요청 성공 여부 확인

---

### load 테스트

```
./k6/run_k6_test.sh load
```

목적:

- 정상 부하 구간 확인
- p95 / error rate / throughput 확인

---

### stress 테스트

```
./k6/run_k6_test.sh stress
```

목적:

- 점진적 부하 증가
- 병목 발생 시점 확인

---

## 실행 흐름

`run_k6_test.sh` 실행 시 아래 순서로 진행된다.

1. `k6/.env` 로드
2. 시나리오 선택
3. k6 Docker 이미지 존재 여부 확인
4. 없으면 `docker/Dockerfile` 기준으로 이미지 빌드
5. Kafka output 옵션을 포함하여 k6 실행

---

## 사용 환경변수

| 변수명 | 설명 |
| --- | --- |
| `BASE_URL` | 테스트 대상 API 주소 |
| `TARGET_PATH` | 요청할 API 경로 |
| `REQUEST_TIMEOUT` | 요청 타임아웃 |
| `LOAD_VUS` | load 테스트 VU 수 |
| `LOAD_DURATION` | load 테스트 유지 시간 |
| `STRESS_START_VUS` | stress 시작 VU |
| `STRESS_STAGE_1_TARGET` | stress 1단계 목표 VU |
| `STRESS_STAGE_2_TARGET` | stress 2단계 목표 VU |
| `STRESS_STAGE_3_TARGET` | stress 3단계 목표 VU |
| `KAFKA_BROKERS` | Kafka broker 주소 |
| `KAFKA_TOPIC` | Kafka topic 이름 |
| `K6_IMAGE` | k6 Docker 이미지 이름 |

---

## 참고

- Kafka output은 기본 k6가 아니라 커스텀 빌드된 바이너리를 사용한다.
- 시나리오 로직은 `scenarios/`에, 공통 요청은 `scripts/`에 둔다.
- 부하 수치는 코드 수정이 아니라 `.env` 변경으로 조절한다.
