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

    // Java 11+ 기본 HTTP 클라이언트
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()

    // batch write용 메모리 버퍼
    private val buffer = mutableListOf<String>()

    // 마지막 flush 시각
    private var lastFlushAt = System.currentTimeMillis()

    // Line Protocol 1건을 버퍼에 추가
    fun add(line: String): Boolean {
        buffer += line

        // Collector 내부 메트릭에 현재 버퍼 크기 반영
        metrics.bufferSize = buffer.size

        // batch-size에 도달하면 즉시 flush
        return if (buffer.size >= config.batchSize) {
            log.debug(
                "Batch threshold reached. bufferSize={}, batchSize={}",
                buffer.size,
                config.batchSize
            )
            flush()
        } else {
            true
        }
    }

    // flush interval이 지난 경우 현재 버퍼를 flush
    fun flushIfDue(): Boolean {
        val now = System.currentTimeMillis()

        // 마지막 flush 이후 경과 시간 계산
        val elapsed = now - lastFlushAt

        // 데이터가 있고, flush interval이 지났으면 flush 수행
        return if (buffer.isNotEmpty() && elapsed >= config.flushIntervalMs) {
            log.debug(
                "Flush interval reached. elapsedMs={}, flushIntervalMs={}, bufferSize={}",
                elapsed,
                config.flushIntervalMs,
                buffer.size
            )
            flush()
        } else {
            true
        }
    }

    // 현재 버퍼를 하나의 payload로 묶어 InfluxDB에 적재
    fun flush(): Boolean {
        // 버퍼가 비어 있으면 flush할 것이 없음
        if (buffer.isEmpty()) {
            log.debug("Flush skipped because buffer is empty")
            return true
        }

        // 현재 버퍼를 개행 기준 Line Protocol payload로 변환
        val body = buffer.joinToString("\n")
        val start = System.currentTimeMillis()

        log.debug(
            "Influx flush start. lines={}, bytes={}",
            buffer.size,
            body.length
        )

        // retry 정책 포함 write 수행
        val success = writeWithRetry(body)

        // flush 소요 시간 기록
        metrics.lastFlushMs = System.currentTimeMillis() - start

        return if (success) {
            // 성공 시 flush 성공 로그
            log.info(
                "Influx flush success. lines={}, bytes={}, flushMs={}",
                buffer.size,
                body.length,
                metrics.lastFlushMs
            )

            // 성공한 batch는 버퍼 비움
            buffer.clear()

            // Collector 내부 메트릭 버퍼 크기 초기화
            metrics.bufferSize = 0

            // 마지막 flush 시각 갱신
            lastFlushAt = System.currentTimeMillis()

            true
        } else {
            // 최종 실패 로그
            log.error(
                "Influx flush failed permanently. lines={}, bytes={}, flushMs={}",
                buffer.size,
                body.length,
                metrics.lastFlushMs
            )

            // 최종 실패한 batch는 dead-letter 파일로 기록
            writeDeadLetter(body)
            buffer.clear()
            metrics.bufferSize = 0
            lastFlushAt = System.currentTimeMillis()

            false
        }
    }

    // Collector 자체 상태를 collector_stats measurement로 기록
    fun writeCollectorStats() {
        val nowNs = System.currentTimeMillis() * 1_000_000L

        // Collector 상태를 하나의 Line Protocol로 생성
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

        // collector_stats write 시작 로그
        log.debug(
            "Writing collector_stats. processedTotal={}, failedTotal={}, bufferSize={}, kafkaLag={}, lastFlushMs={}",
            metrics.processedTotal,
            metrics.failedTotal,
            metrics.bufferSize,
            metrics.kafkaLag,
            metrics.lastFlushMs
        )

        // Collector 상태 write 수행
        val success = writeWithRetry(line)

        // 실패 시 dead-letter 파일로 기록
        if (!success) {
            log.error("Failed to write collector_stats. Writing to dead-letter")
            writeDeadLetter(line)
        }
    }

    // InfluxDB write를 retry 정책과 함께 수행
    private fun writeWithRetry(body: String): Boolean {
        // 0부터 maxRetries-1까지 반복
        repeat(config.maxRetries) { attempt ->
            val currentAttempt = attempt + 1

            // 요청 시작 로그
            log.debug(
                "Influx write attempt start. attempt={}, maxRetries={}, bytes={}",
                currentAttempt,
                config.maxRetries,
                body.length
            )

            // 실제 HTTP POST 수행
            val response = post(body)

            // 응답 자체를 받지 못한 경우
            if (response == null) {
                log.warn(
                    "Influx request failed without response. attempt={}, bytes={}",
                    currentAttempt,
                    body.length
                )
            } else {
                // HTTP 상태 코드 추출
                val status = response.statusCode()

                // 2xx면 성공 처리
                if (status in 200..299) {
                    log.info(
                        "Influx write success. status={}, attempt={}, bytes={}",
                        status,
                        currentAttempt,
                        body.length
                    )
                    return true
                }

                // 4xx 중 일부는 재시도해도 의미 없음
                if (status in listOf(
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN
                    )
                ) {
                    log.error(
                        "Influx write rejected. status={}, body={}, attempt={}, bytes={}",
                        status,
                        response.body(),
                        currentAttempt,
                        body.length
                    )
                    return false
                }

                // 그 외 상태 코드는 일단 재시도 대상
                log.warn(
                    "Influx write failed. status={}, attempt={}, bytes={}, body={}",
                    status,
                    currentAttempt,
                    body.length,
                    response.body()
                )
            }

            // 마지막 시도가 아니면 exponential backoff 후 재시도
            if (currentAttempt < config.maxRetries) {
                // exponential backoff: 1s -> 2s -> 4s ...
                val waitMs = 1000.0 * 2.0.pow(attempt.toDouble())

                // 재시도 대기 로그
                log.warn(
                    "Influx write retry scheduled. nextAttempt={}, waitMs={}",
                    currentAttempt + 1,
                    waitMs.toLong()
                )

                Thread.sleep(waitMs.toLong())
            }
        }

        // 모든 재시도를 소진한 경우
        log.error(
            "Influx write failed after all retries. maxRetries={}, bytes={}",
            config.maxRetries,
            body.length
        )
        return false
    }

    // InfluxDB v3 write API로 HTTP POST 요청 전송
    private fun post(body: String): HttpResponse<String>? {
        return try {
            // 최종 요청 URL 구성
            val url = "${config.url}/api/v3/write_lp?db=${config.database}"

            // 요청 전 디버그 로그
            log.debug(
                "Posting to InfluxDB. url={}, db={}, tokenPresent={}, bytes={}",
                config.url,
                config.database,
                config.token.isNotBlank(),
                body.length
            )

            // HTTP 요청 객체 생성
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.timeoutMs))
                .header("Authorization", "Token ${config.token}")
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            // 요청 전송 및 문자열 응답 수신
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.error("Influx HTTP request error", e)
            null
        }
    }

    // 최종 실패한 payload를 dead-letter 파일에 기록
    private fun writeDeadLetter(content: String) {
        try {
            val file = File(config.deadLetterPath)
            file.parentFile?.mkdirs()
            file.appendText(content + "\n")

            // dead-letter 기록 성공 로그
            log.warn(
                "Dead-letter written. path={}, bytes={}",
                config.deadLetterPath,
                content.length
            )
        } catch (e: Exception) {
            log.error("Failed to write dead-letter log", e)
        }
    }
}