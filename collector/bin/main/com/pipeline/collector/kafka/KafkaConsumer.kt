package com.pipeline.collector.kafka

import com.pipeline.collector.config.KafkaConsumerConfig
import com.pipeline.collector.influx.InfluxWriter
import com.pipeline.collector.metrics.CollectorMetrics
import com.pipeline.collector.processor.MetricsProcessor
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer as ApacheKafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

private val log = LoggerFactory.getLogger("com.pipeline.collector.kafka.KafkaConsumer")

class KafkaConsumer(
    private val config: KafkaConsumerConfig,
    private val processor: MetricsProcessor,
    private val writer: InfluxWriter,
    private val metrics: CollectorMetrics,
    private val statsWriteIntervalMs: Long
) {

    fun run() {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)

            // write 성공 후 commit하기 위해 auto commit 비활성화
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

            // group offset이 없을 때 시작 위치
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset)
        }

        ApacheKafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(config.topic))
            log.info("Subscribed to topic={}", config.topic)

            while (true) {
                val records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs))

                // 읽을 데이터가 없더라도 flush interval 체크는 계속 필요
                if (records.isEmpty) {
                    writer.flushIfDue()
                    maybeWriteStats()
                    continue
                }

                var allSucceeded = true

                for (record in records) {
                    val rawMessage = record.value()

                    val line = try {
                        // Kafka raw JSON -> Influx line protocol
                        processor.toLineProtocol(rawMessage)
                    } catch (e: Exception) {
                        log.error(
                            "Failed to process record. topic={}, partition={}, offset={}, error={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            e.message
                        )
                        metrics.failedTotal++
                        null
                    }

                    // 파싱 결과가 없으면 skip
                    if (line == null) {
                        continue
                    }

                    // writer.add 내부에서 batch-size 도달 시 flush 가능
                    val success = writer.add(line)

                    if (success) {
                        metrics.processedTotal++
                    } else {
                        // flush 최종 실패면 현재 poll batch는 commit하지 않음
                        allSucceeded = false
                        metrics.failedTotal++
                        break
                    }
                }

                // 여기까지 성공했을 때만 offset commit
                if (allSucceeded) {
                    consumer.commitSync()
                }

                maybeWriteStats()
            }
        }
    }

    private fun maybeWriteStats() {
        if (metrics.shouldWriteStats(statsWriteIntervalMs)) {
            writer.writeCollectorStats()
            metrics.markStatsWritten()
        }
    }
}