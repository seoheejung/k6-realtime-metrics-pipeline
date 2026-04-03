package com.pipeline.api.service

import com.pipeline.api.dto.EchoRequest
import org.springframework.stereotype.Service
import kotlin.math.min

// 부하 테스트용 단순 로직을 분리한 서비스 계층
@Service
class LoadTestService {

    // 전달받은 문자열을 repeat 횟수만큼 반복해서 반환
    fun echo(request: EchoRequest): Map<String, Any> {
        // repeat 값이 너무 커지지 않도록 최대 10으로 제한
        val repeatCount = min(request.repeat.coerceAtLeast(1), 10)

        return mapOf(
            "message" to request.message,
            "repeat" to repeatCount,
            "result" to request.message.repeat(repeatCount)
        )
    }

    // 지정된 시간만큼 sleep 해서 인위적 지연 생성
    fun delay(delayMs: Long): Map<String, Any> {
        // 과도한 지연 방지를 위해 0~10000ms 범위로 제한
        val safeDelay = delayMs.coerceIn(0, 10_000)

        // 응답 시간을 일부러 늦추는 테스트용 sleep
        Thread.sleep(safeDelay)

        return mapOf(
            "delayMs" to safeDelay,
            "status" to "completed"
        )
    }

    // 단순 반복 계산으로 CPU 부하 생성
    fun cpu(iterations: Int): Map<String, Any> {
        // 과도한 반복 횟수를 막기 위해 상한을 둔다
        val safeIterations = iterations.coerceIn(1, 5_000_000)

        // 계산 결과를 누적할 변수
        var result = 0L

        // 의미 없는 연산이 아니라 실제 반복 계산을 수행
        for (i in 1..safeIterations) {
            result += i % 7
        }

        return mapOf(
            "iterations" to safeIterations,
            "result" to result
        )
    }
}