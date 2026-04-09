package com.pipeline.api.dto

// 공통 응답 포맷을 맞추기 위한 DTO
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    val timestamp: Long = System.currentTimeMillis()
)