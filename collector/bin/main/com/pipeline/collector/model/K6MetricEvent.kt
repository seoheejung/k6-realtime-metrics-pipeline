package com.pipeline.collector.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Kafka에서 받은 k6 메트릭 이벤트의 최상위 구조
// 실제 payload에 불필요한 필드가 있어도 무시하도록 설정
@JsonIgnoreProperties(ignoreUnknown = true)
data class K6MetricEvent(
    val metric: String,
    val type: String? = null,
    val data: K6MetricData
)