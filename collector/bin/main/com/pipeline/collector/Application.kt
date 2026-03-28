package com.pipeline.collector

import com.pipeline.collector.config.ConfigLoader
import com.pipeline.collector.config.InfluxWriterConfig
import com.pipeline.collector.config.KafkaConsumerConfig
import com.pipeline.collector.influx.InfluxWriter
import com.pipeline.collector.kafka.KafkaConsumer
import com.pipeline.collector.metrics.CollectorMetrics
import com.pipeline.collector.processor.MetricsProcessor
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.pipeline.collector.Application")

fun main() {
    // application.yml + .env + 시스템 환경변수를 합쳐 최종 설정 생성
    val appConfig = ConfigLoader.load()

    // 실행 중 누적되는 내부 상태 객체
    val metrics = CollectorMetrics()

    // Kafka raw JSON -> Influx line protocol 변환기
    val processor = MetricsProcessor()

    // KafkaConsumer가 직접 참조할 설정 객체
    val kafkaConfig = KafkaConsumerConfig(
        bootstrapServers = appConfig.kafka.`bootstrap-servers`,
        topic = appConfig.kafka.topic,
        groupId = appConfig.kafka.`group-id`,
        pollTimeoutMs = appConfig.kafka.`poll-timeout-ms`,
        autoOffsetReset = appConfig.kafka.`auto-offset-reset`
    )

    // InfluxWriter가 직접 참조할 설정 객체
    val influxConfig = InfluxWriterConfig(
        url = appConfig.influx.url,
        token = appConfig.influx.token,
        database = appConfig.influx.database,
        batchSize = appConfig.influx.`batch-size`,
        flushIntervalMs = appConfig.influx.`flush-interval-ms`,
        maxRetries = appConfig.influx.`max-retries`,
        timeoutMs = appConfig.influx.`timeout-ms`,
        deadLetterPath = appConfig.collector.`dead-letter-path`
    )

    // Influx write 담당
    val writer = InfluxWriter(
        config = influxConfig,
        metrics = metrics
    )

    // Kafka consume loop 담당
    val consumer = KafkaConsumer(
        config = kafkaConfig,
        processor = processor,
        writer = writer,
        metrics = metrics,
        statsWriteIntervalMs = appConfig.collector.`stats-write-interval-ms`
    )

    // 종료 시 남은 버퍼를 최대한 정리
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutdown detected. Flushing remaining data before exit...")
            writer.flush()
            writer.writeCollectorStats()
        }
    )

    log.info("Collector started.")
    consumer.run()
}