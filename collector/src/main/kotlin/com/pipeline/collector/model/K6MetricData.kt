package com.pipeline.collector.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// metric 내부의 실제 측정 데이터
@JsonIgnoreProperties(ignoreUnknown = true)
data class K6MetricData(
    val time: String,
    val value: Double,
    val tags: Map<String, String?>? = emptyMap()
)