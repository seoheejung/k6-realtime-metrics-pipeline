package com.pipeline.api.controller

import com.pipeline.api.dto.ApiResponse
import com.pipeline.api.dto.EchoRequest
import com.pipeline.api.service.LoadTestService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

// 부하 테스트용 endpoint를 제공하는 컨트롤러
@RestController
@RequestMapping("/api")
class LoadTestController(
    private val loadTestService: LoadTestService
) {

    // 가장 기본적인 서비스 생존 확인 endpoint
    @GetMapping("/health")
    fun health(request: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "ok",
                data = mapOf(
                    "status" to "UP",
                    "path" to request.requestURI
                )
            )
        )
    }

    // 단순 GET 요청 처리 성능을 보기 위한 endpoint
    @GetMapping("/hello")
    fun hello(request: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "hello",
                data = mapOf(
                    "message" to "hello kotlin api",
                    "requestId" to UUID.randomUUID().toString(),
                    "path" to request.requestURI
                )
            )
        )
    }

    // JSON 바디를 받아 그대로 가공 후 반환하는 POST endpoint
    @PostMapping("/echo")
    fun echo(@RequestBody body: EchoRequest): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "echo success",
                data = loadTestService.echo(body)
            )
        )
    }

    // 응답 지연 시간을 인위적으로 만드는 endpoint
    @GetMapping("/delay")
    fun delay(
        @RequestParam(defaultValue = "100") ms: Long
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "delay success",
                data = loadTestService.delay(ms)
            )
        )
    }

    // CPU 반복 연산을 발생시키는 endpoint
    @GetMapping("/cpu")
    fun cpu(
        @RequestParam(defaultValue = "100000") iterations: Int
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "cpu success",
                data = loadTestService.cpu(iterations)
            )
        )
    }

    // 의도적으로 에러 응답을 반환하는 endpoint
    @GetMapping("/error")
    fun error(
        @RequestParam(defaultValue = "500") status: Int
    ): ResponseEntity<ApiResponse<Nothing>> {
        return when (status) {
            // 400 에러 강제 반환
            400 -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse(
                    success = false,
                    message = "intentional bad request",
                    data = null
                )
            )

            // 503 에러 강제 반환
            503 -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiResponse(
                    success = false,
                    message = "intentional service unavailable",
                    data = null
                )
            )

            // 기본값은 500 에러 반환
            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "intentional internal server error",
                    data = null
                )
            )
        }
    }
}