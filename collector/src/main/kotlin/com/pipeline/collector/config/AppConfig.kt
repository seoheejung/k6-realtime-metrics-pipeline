package com.pipeline.collector.config

// application.yml 전체 구조를 그대로 받는 최상위 설정 클래스
data class AppConfig(
    val kafka: KafkaConfig,
    val influx: InfluxConfig,
    val collector: CollectorConfig
)

// Kafka 관련 원본 설정
data class KafkaConfig(
    val `bootstrap-servers`: String,
    val topic: String,
    val `group-id`: String,
    val `poll-timeout-ms`: Long,
    val `auto-offset-reset`: String
)

// InfluxDB 관련 원본 설정
data class InfluxConfig(
    val url: String,
    val token: String,
    val database: String,
    val `batch-size`: Int,
    val `flush-interval-ms`: Long,
    val `max-retries`: Int,
    val `timeout-ms`: Long
)

// Collector 자체 동작과 관련된 설정
data class CollectorConfig(
    val `stats-write-interval-ms`: Long,
    val `dead-letter-path`: String
)