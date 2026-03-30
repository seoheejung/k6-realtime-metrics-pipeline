package com.pipeline.collector.config

// Kafka Consumer 실행에 필요한 최소 설정만 모아둔 데이터 클래스
data class KafkaConsumerConfig(

    // Kafka broker 주소
    val bootstrapServers: String,

    // 읽을 topic 이름
    val topic: String,

    // consumer group 이름
    val groupId: String,

    // poll 호출 대기 시간(ms)
    val pollTimeoutMs: Long,

    // group offset이 없을 때 시작 위치
    val autoOffsetReset: String
)