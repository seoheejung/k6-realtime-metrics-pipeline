# xk6 공식 이미지 사용
FROM grafana/xk6:latest AS builder

# Kafka output extension 포함 k6 빌드
RUN xk6 build v0.50.0 \
    --with github.com/grafana/xk6-output-kafka@v0.7.1 \
    --output /tmp/k6

# 2단계: 실행용 이미지
FROM alpine:3.19

# ca-certificates는 HTTPS 호출 시 필요
RUN apk add --no-cache ca-certificates bash

# 빌드된 k6 바이너리 복사
COPY --from=builder /tmp/k6 /usr/local/bin/k6

WORKDIR /work
ENTRYPOINT ["k6"]
