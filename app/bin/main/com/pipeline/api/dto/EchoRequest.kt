package com.pipeline.api.dto

// /api/echo 요청 바디를 받기 위한 DTO
data class EchoRequest(
    val message: String,
    val repeat: Int = 1
)