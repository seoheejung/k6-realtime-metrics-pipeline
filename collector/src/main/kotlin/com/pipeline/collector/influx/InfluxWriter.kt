package com.pipeline.collector.influx

import com.pipeline.collector.config.InfluxWriterConfig
import com.pipeline.collector.metrics.CollectorMetrics
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.pow

private val log = LoggerFactory.getLogger("com.pipeline.collector.influx.InfluxWriter")

class InfluxWriter(
    private val config: InfluxWriterConfig,
    private val metrics: CollectorMetrics
) {

    // Java 11+ 기본 HTTP 클라이언트 사용
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()

    // batch write를 위한 메모리 버퍼
    private val buffer = mutableListOf<String>()

    // 마지막 flush 시각
    private var lastFlushAt = System.currentTimeMillis()

    fun add(line: String): Boolean {
        buffer += line
        metrics.bufferSize = buffer.size

        // batch-size에 도달하면 바로 flush
        return if (buffer.size >= config.batchSize) {
            flush()
        } else {
            true
        }
    }

    fun flushIfDue(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastFlushAt

        // 데이터가 있고, flush interval이 지났으면 flush
        return if (buffer.isNotEmpty() && elapsed >= config.flushIntervalMs) {
            flush()
        } else {
            true
        }
    }

    fun flush(): Boolean {
        if (buffer.isEmpty()) {
            return true
        }

        val body = buffer.joinToString("\n")
        val start = System.currentTimeMillis()

        val success = writeWithRetry(body)

        metrics.lastFlushMs = System.currentTimeMillis() - start

        return if (success) {
            buffer.clear()
            metrics.bufferSize = 0
            lastFlushAt = System.currentTimeMillis()
            true
        } else {
            // 최종 실패한 batch는 dead-letter 로그로 남김
            writeDeadLetter(body)
            buffer.clear()
            metrics.bufferSize = 0
            lastFlushAt = System.currentTimeMillis()
            false
        }
    }

    fun writeCollectorStats() {
        val nowNs = System.currentTimeMillis() * 1_000_000L

        // Collector 자신의 상태도 Influx에 measurement로 적재
        val line = buildString {
            append("collector_stats ")
            append("processed_total=${metrics.processedTotal}i,")
            append("failed_total=${metrics.failedTotal}i,")
            append("tps=${metrics.calculateTps()},")
            append("buffer_size=${metrics.bufferSize}i,")
            append("kafka_lag=${metrics.kafkaLag}i,")
            append("last_flush_ms=${metrics.lastFlushMs}i ")
            append(nowNs)
        }

        val success = writeWithRetry(line)
        if (!success) {
            writeDeadLetter(line)
        }
    }

    private fun writeWithRetry(body: String): Boolean {
        repeat(config.maxRetries) { attempt ->
            val response = post(body)

            if (response == null) {
                log.warn("Influx request failed without response. attempt={}", attempt + 1)
            } else {
                val status = response.statusCode()

                // 2xx면 성공 처리
                if (status in 200..299) {
                    return true
                }

                // 4xx 중 일부는 재시도해도 의미 없음
                if (status in listOf(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN
                    )
                ) {
                    log.error("Influx write rejected. status={}, body={}", status, response.body())
                    return false
                }

                log.warn("Influx write failed. status={}, attempt={}", status, attempt + 1)
            }

            // exponential backoff: 1s -> 2s -> 4s
            val waitMs = 1000.0 * 2.0.pow(attempt.toDouble())
            Thread.sleep(waitMs.toLong())
        }

        return false
    }

    private fun post(body: String): HttpResponse<String>? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/api/v3/write_lp?db=${config.database}"))
                .timeout(Duration.ofMillis(config.timeoutMs))
                .header("Authorization", "Token ${config.token}")
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.error("Influx HTTP request error: {}", e.message)
            null
        }
    }

    private fun writeDeadLetter(content: String) {
        try {
            val file = File(config.deadLetterPath)
            file.parentFile?.mkdirs()
            file.appendText(content + "\n")
        } catch (e: Exception) {
            log.error("Failed to write dead-letter log: {}", e.message)
        }
    }
}