import http from "k6/http";
import { check, sleep } from "k6";
import { requireEnv, getOptionalEnv } from "../config/env.js";

/**
 * 실제 요청을 보내는 공통 함수
 * smoke / load / stress 시나리오에서 공통으로 재사용
 */
export function executeSingleApiLoad({ sleepSeconds = 0 } = {}) {
    // 필수 설정값
    const BASE_URL = requireEnv("BASE_URL");
    const TARGET_PATH = requireEnv("TARGET_PATH");

    // 선택 설정값
    const REQUEST_TIMEOUT = getOptionalEnv("REQUEST_TIMEOUT", "5s");

    // URL 조합
    const url = `${BASE_URL}${TARGET_PATH}`;

    // 요청 설정
    const params = {
        headers: {
            Accept: "application/json",
        },
        timeout: REQUEST_TIMEOUT,
    };

    // HTTP 요청 실행
    const res = http.get(url, params);

    /**
     * 체크 결과는 k6 내부 메트릭으로 자동 집계됨
     * smoke에서는 최소 연결 확인 용도로 사용
     * load / stress에서는 요청 성공 조건 확인 용도로 사용
     */
    check(res, {
        "status is 200": (r) => r.status === 200,
    });

    /**
     * 요청 간 간격
     * smoke에서는 기본 0초
     * load / stress에서는 시나리오에서 명시적으로 전달
     */
    if (sleepSeconds > 0) {
        sleep(sleepSeconds);
    }
}