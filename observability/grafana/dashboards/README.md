# Grafana Dashboard

> k6 → Kafka → Collector → InfluxDB v3로 수집된 메트릭을 시각화한다.

## Data Source 설정

- Type: InfluxDB
- Query Language: SQL
- URL: http://localhost:8181
- Database: k6_metrics
- Token: INFLUX_TOKEN

## 주요 패널

- 요청 수 (http_reqs)
- 응답 시간 (http_req_duration)
- 실패율 (http_req_failed)
- VUs (vus)
- Collector 상태 (collector_stats)

## 검증 방법

1. k6 테스트 실행
2. Grafana에서 그래프 확인

정상 기준:
- 그래프가 실시간으로 변화
- 응답 시간 값 존재
- VUs 변화 반영
