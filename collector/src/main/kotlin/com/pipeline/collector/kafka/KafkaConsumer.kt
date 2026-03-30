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

// Kafka Consumer 전용 로그 객체
private val log = LoggerFactory.getLogger("com.pipeline.collector.kafka.KafkaConsumer")

class KafkaConsumer(

    // Kafka 접속 및 poll 관련 설정
    private val config: KafkaConsumerConfig,

    // Kafka raw JSON을 Influx line protocol로 변환하는 처리기
    private val processor: MetricsProcessor,

    // 변환된 line protocol을 InfluxDB로 적재하는 writer
    private val writer: InfluxWriter,

    // 처리 건수 / 실패 건수 / 통계 기록 시각 등을 관리하는 객체
    private val metrics: CollectorMetrics,

    // collector 내부 통계를 몇 ms 간격으로 쓸지 결정하는 값
    private val statsWriteIntervalMs: Long
) {

    fun run() {

        // Kafka Consumer 생성용 설정 객체
        val props = Properties().apply {

            // Kafka broker 주소
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)

            // consumer group 이름
            put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)

            // key deserialize 방식
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)

            // value deserialize 방식
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)

            // write 성공 후에만 commit하기 위해 auto commit 비활성화
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

            // 기존 committed offset이 없을 때 시작 위치
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset)
        }

        // use 블록 종료 시 consumer.close() 자동 호출
        ApacheKafkaConsumer<String, String>(props).use { consumer ->

            // 지정한 topic 구독 시작
            consumer.subscribe(listOf(config.topic))
            log.info("Subscribed to topic={}", config.topic)

            // 무한 consume loop
            while (true) {

                // Kafka에서 일정 시간 동안 레코드 poll
                val records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs))

                // 읽은 데이터가 없어도 flush 주기와 통계 기록 주기는 계속 확인해야 함
                if (records.isEmpty) {
                    writer.flushIfDue()
                    maybeWriteStats()
                    continue
                }

                // 이번 poll batch 전체가 성공했는지 추적
                var allSucceeded = true

                // poll로 읽은 각 Kafka 레코드를 순회
                for (record in records) {

                    // Kafka 메시지 본문(JSON 문자열)
                    val rawMessage = record.value()

                    // JSON -> Influx line protocol 변환
                    val line = try {
                        processor.toLineProtocol(rawMessage)
                    } catch (e: Exception) {

                        // 파싱 실패, timestamp 형식 오류, 필드 누락 같은 예외 처리
                        log.error(
                            "Failed to process record. topic={}, partition={}, offset={}, error={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            e.message
                        )

                        // 실패 건수 증가
                        metrics.failedTotal++

                        // 이 메시지는 변환 실패 처리
                        null
                    }

                    // processor가 null을 반환한 경우
                    // NaN / Infinity / 저장 불필요한 데이터라고 판단한 상황
                    if (line == null) {
                        continue
                    }

                    // writer.add():
                    // 1) 내부 버퍼에 추가
                    // 2) batch size 도달 시 flush 시도
                    val success = writer.add(line)

                    if (success) {

                        // 적재 경로까지 성공한 경우 처리 건수 증가
                        metrics.processedTotal++

                    } else {

                        // writer 최종 실패 시
                        // 이번 poll batch는 commit하지 않고 종료
                        // Kafka에서 같은 메시지를 다시 읽게 만들기 위한 처리
                        allSucceeded = false
                        metrics.failedTotal++
                        break
                    }
                }

                // poll batch 전체가 성공했을 때만 offset commit
                if (allSucceeded) {
                    consumer.commitSync()
                }

                // collector 내부 통계 기록 주기 확인
                maybeWriteStats()
            }
        }
    }

    private fun maybeWriteStats() {

        // 정해둔 간격이 지났을 때만 collector 상태값 적재
        if (metrics.shouldWriteStats(statsWriteIntervalMs)) {
            writer.writeCollectorStats()
            metrics.markStatsWritten()
        }
    }
}