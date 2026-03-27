# InfluxDB v3 Schema

## 기본 정보

| 항목 | 값 |
|---|---|
| 버전 | InfluxDB v3 (IOx 기반) |
| Write endpoint | `/api/v3/write_lp` |
| 인증 | Token (Authorization: Token <token>) |
| Database | `k6_metrics` |
| 데이터 형식 | Line Protocol |

---

## Write 방식 결정

InfluxDB v3는 v2 호환 API(`/api/v2/write`)를 제공하지만, v3 네이티브 endpoint인 `/api/v3/write_lp`를 사용한다.

이유:
- v3 OSS에서 v2 호환 API의 지원 범위가 제한적
- 공식 influxdb-client-kotlin은 v2 기반이므로 직접 HTTP 호출 방식이 더 안정적
- Line Protocol 포맷은 v2/v3 공통이므로 write 로직 자체는 동일

---

## Measurement 목록

### 1. `http_req_duration`

HTTP 요청 응답 시간

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| tag | `method` | string | HTTP 메서드 (GET, POST, ...) |
| tag | `status` | string | HTTP 상태 코드 (200, 404, ...) |
| tag | `scenario` | string | k6 시나리오 이름 |
| tag | `url_path` | string | 정규화된 요청 경로 (/user/:id 형태) |
| field | `value` | float | 응답 시간 (ms) |
| timestamp | - | int64 | Unix nanosecond |

- raw URL은 저장하지 않는다.
- 동적 path는 반드시 정규화한다.

### 2. `http_reqs`

HTTP 요청 수

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| tag | `method` | string | HTTP 메서드 |
| tag | `status` | string | HTTP 상태 코드 |
| tag | `scenario` | string | k6 시나리오 이름 |
| field | `value` | float | 요청 수 (counter) |
| timestamp | - | int64 | Unix nanosecond |

### 3. `http_req_failed`

HTTP 요청 실패 여부

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| tag | `scenario` | string | k6 시나리오 이름 |
| field | `value` | float | 0 또는 1 (실패 시 1) |
| timestamp | - | int64 | Unix nanosecond |

### 4. `vus`

가상 유저 수

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| tag | `scenario` | string | k6 시나리오 이름 |
| field | `value` | float | 현재 VU 수 |
| timestamp | - | int64 | Unix nanosecond |

### 5. `iteration_duration`

시나리오 1회 반복 소요 시간

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| tag | `scenario` | string | k6 시나리오 이름 |
| field | `value` | float | 소요 시간 (ms) |
| timestamp | - | int64 | Unix nanosecond |

### 6. `collector_stats`

Kotlin Collector 내부 상태 (Collector 자체가 write)

| 구분 | 이름 | 타입 | 설명 |
|---|---|---|---|
| field | `processed_total` | int | 총 처리 건수 |
| field | `failed_total` | int | 총 실패 건수 |
| field | `tps` | float | 초당 처리 건수 |
| field | `buffer_size` | int | 현재 버퍼 크기 |
| field | `kafka_lag` | int | Kafka consumer lag |
| field | `last_flush_ms` | float | 마지막 flush 소요 시간 (ms) |
| timestamp | - | int64 | Unix nanosecond |

---

## Line Protocol 예시

```
# http_req_duration
http_req_duration,method=GET,status=200,scenario=load,url=/api/items value=87.32 1704067200000000000

# http_reqs
http_reqs,method=GET,status=200,scenario=load value=1.0 1704067200000000000

# http_req_failed
http_req_failed,scenario=load value=0.0 1704067200000000000

# vus
vus,scenario=load value=50.0 1704067200000000000

# collector_stats
collector_stats processed_total=12400i,failed_total=3i,tps=82.4,buffer_size=120i,kafka_lag=0i,last_flush_ms=14.2 1704067200000000000
```

---

## Grafana 쿼리 예시 (SQL, InfluxDB v3)

### p95 latency

```sql
SELECT
  percentile_cont(0.95) WITHIN GROUP (ORDER BY value) AS p95
FROM http_req_duration
WHERE time >= now() - interval '5 minutes'
  AND scenario = 'load'
```

### RPS (초당 요청 수)

```sql
SELECT
  date_trunc('second', time) AS ts,
  count(*) AS rps
FROM http_reqs
WHERE time >= now() - interval '5 minutes'
GROUP BY ts
ORDER BY ts
```

### Error rate

```sql
SELECT
  date_trunc('second', time) AS ts,
  avg(value) AS error_rate
FROM http_req_failed
WHERE time >= now() - interval '5 minutes'
GROUP BY ts
ORDER BY ts
```

---

## Cardinality 관리

고유 tag 값의 조합이 많아질수록 InfluxDB 성능이 저하된다.

| 위험 요소 | 대응 방법 |
|---|---|
| url에 동적 경로 포함 (`/user/123`) | Collector에서 정규화 (`/user/:id`) 또는 url 태그 제외 |
| url에 쿼리스트링 포함 | Collector에서 쿼리스트링 제거 |
| status code 세분화 | 200, 4xx, 5xx 수준으로 그룹핑 고려 |

---

## Retention Policy

InfluxDB v3는 database 단위로 retention을 설정한다.

```
database: k6_metrics
retention: 30d (기본값, 운영 환경에 따라 조정)
```

로컬 개발 환경에서는 별도 설정 없이 진행하고, Azure 배포 시 디스크 용량에 맞게 조정한다.
