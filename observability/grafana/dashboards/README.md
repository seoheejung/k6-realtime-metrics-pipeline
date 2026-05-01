# Grafana Dashboard

k6 → Kafka → Collector → InfluxDB v3 메트릭 시각화

---

## Data Source

- Type: InfluxDB
- Query Language: InfluxQL
- URL: http://influxdb:8181
- Database: k6_metrics
- Header: Authorization: Bearer <INFLUX_TOKEN>

> Grafana 컨테이너 내부에서는 `localhost`가 아닌 `influxdb`로 접근한다.

---

## Dashboard

- File: `k6-realtime-metrics.json`

---

## 상세 문서

docs/grafana-dashboard.md