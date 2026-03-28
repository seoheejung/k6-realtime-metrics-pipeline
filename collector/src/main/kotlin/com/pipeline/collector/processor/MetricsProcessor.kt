package com.pipeline.collector.processor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pipeline.collector.model.K6MetricEvent
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant

private val log = LoggerFactory.getLogger("com.pipeline.collector.processor.MetricsProcessor")

class MetricsProcessor {

    private val mapper = jacksonObjectMapper()

    fun toLineProtocol(rawJson: String): String? {
        // 1) Kafka에서 읽은 raw JSON을 k6 이벤트 객체로 변환
        val event = mapper.readValue<K6MetricEvent>(rawJson)

        // 2) measurement 이름 정리
        val metricName = normalizeMetricName(event.metric)

        // 3) timestamp를 Influx line protocol 용 ns 단위로 변환
        val timestampNs = parseTimestampToNano(event.data.time)

        // 4) value는 field로 저장
        val value = event.data.value

        // NaN / Infinity는 Influx에 넣지 않음
        if (!value.isFinite()) {
            log.warn("Skip invalid numeric value. metric={}", event.metric)
            return null
        }

        // 5) tags를 line protocol 포맷으로 정리
        val tagParts = mutableListOf<String>()

        event.data.tags?.forEach { (key, rawValue) ->
            // 비어 있는 태그는 저장하지 않음
            if (rawValue.isNullOrBlank()) return@forEach

            val normalizedValue = when (key) {
                // URL은 쿼리스트링 제거
                "url" -> normalizeUrl(rawValue)

                // 그 외 문자열은 공백 정리 정도만 수행
                else -> normalizeTagValue(rawValue)
            }

            if (normalizedValue.isNotBlank()) {
                tagParts += "${escapeTag(key)}=${escapeTag(normalizedValue)}"
            }
        }

        val tagSection = if (tagParts.isEmpty()) {
            ""
        } else {
            "," + tagParts.joinToString(",")
        }

        // 최종 line protocol 생성
        // 형식: measurement,tag1=v1,tag2=v2 field=value timestamp
        return "$metricName$tagSection value=$value $timestampNs"
    }

    private fun normalizeMetricName(metric: String): String {
        // 공백 제거 수준만 적용
        return metric.trim().replace(" ", "_")
    }

    private fun normalizeTagValue(value: String): String {
        return value.trim().replace(" ", "_")
    }

    private fun normalizeUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)

            // URL path만 남기고 query 제거
            val path = uri.path ?: rawUrl

            if (path.isBlank()) rawUrl else path
        } catch (_: Exception) {
            // URL 형식이 아니면 query만 잘라냄
            rawUrl.substringBefore("?")
        }
    }

    private fun parseTimestampToNano(isoTime: String): Long {
        val instant = Instant.parse(isoTime)
        return instant.epochSecond * 1_000_000_000L + instant.nano
    }

    private fun escapeTag(value: String): String {
        // Influx line protocol에서 특수문자 이스케이프 필요
        return value
            .replace("\\", "\\\\")
            .replace(" ", "\\ ")
            .replace(",", "\\,")
            .replace("=", "\\=")
    }
}