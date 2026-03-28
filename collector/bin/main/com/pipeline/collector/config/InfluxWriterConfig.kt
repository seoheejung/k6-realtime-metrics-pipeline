package com.pipeline.collector.config

// InfluxWriter가 실제 write 시 필요한 값만 담는 설정 클래스
data class InfluxWriterConfig(
    val url: String,
    val token: String,
    val database: String,
    val batchSize: Int,
    val flushIntervalMs: Long,
    val maxRetries: Int,
    val timeoutMs: Long,
    val deadLetterPath: String
)