# --------------------------------------------------
# xk6 / k6 / extension 버전 고정
# --------------------------------------------------
ARG XK6_IMAGE=grafana/xk6:1.3.7
FROM ${XK6_IMAGE} AS builder

ARG K6_VERSION=v0.50.0
ARG XK6_OUTPUT_KAFKA_VERSION=v0.7.0

# Kafka output extension 포함 k6 빌드
RUN xk6 build \
    --k6-version ${K6_VERSION} \
    --with github.com/grafana/xk6-output-kafka@${XK6_OUTPUT_KAFKA_VERSION} \
    --output /tmp/k6

FROM alpine:3.19

RUN apk add --no-cache ca-certificates bash

COPY --from=builder /tmp/k6 /usr/local/bin/k6

WORKDIR /work
ENTRYPOINT ["k6"]
