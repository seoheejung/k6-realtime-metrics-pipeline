package com.pipeline.collector.config

// KafkaConsumer가 실제 실행 시 필요한 값만 따로 묶은 설정 클래스
data class KafkaConsumerConfig(
    val bootstrapServers: String,
    val topic: String,
    val groupId: String,
    val pollTimeoutMs: Long,
    val autoOffsetReset: String
)