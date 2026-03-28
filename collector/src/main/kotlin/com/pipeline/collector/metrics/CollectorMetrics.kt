package com.pipeline.collector.metrics

class CollectorMetrics {

    // Influx write까지 성공한 총 건수
    var processedTotal: Long = 0

    // 파싱 실패 / write 실패 포함 총 실패 건수
    var failedTotal: Long = 0

    // 현재 메모리 버퍼에 쌓여 있는 line 개수
    var bufferSize: Int = 0

    // Kafka lag
    // 현재 버전에서는 실제 조회 없이 0으로 유지
    // 이후 AdminClient 연결 시 실제 값으로 교체 가능
    var kafkaLag: Long = 0

    // 마지막 flush 수행 시간(ms)
    var lastFlushMs: Long = 0

    // 프로그램 시작 시각
    private val startedAt = System.currentTimeMillis()

    // 내부 상태를 마지막으로 기록한 시각
    private var lastStatsWrittenAt: Long = 0

    fun calculateTps(): Double {
        val elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(1.0)
        return processedTotal / elapsedSec
    }

    fun shouldWriteStats(intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return now - lastStatsWrittenAt >= intervalMs
    }

    fun markStatsWritten() {
        lastStatsWrittenAt = System.currentTimeMillis()
    }
}