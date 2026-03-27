import http from "k6/http";
import { check, sleep } from "k6";

// 필수 환경변수 검증 함수
function requireEnv(name) {
    if (!__ENV[name]) {
        throw new Error(`${name} 환경변수가 필요합니다.`);
    }
    return __ENV[name];
}

// 필수 설정값
const BASE_URL = requireEnv("BASE_URL");
const TARGET_PATH = requireEnv("TARGET_PATH");

// 선택 설정값
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "5s";

// 실제 요청을 보내는 공통 함수 (smoke / load / stress 시나리오에서 공통으로 재사용)
export function executeSingleApiLoad() {
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

    // 체크 결과는 k6 내부 메트릭으로 자동 집계됨 (checks, http_req_failed, http_req_duration)
    check(res, {
        "status is 200": (r) => r.status === 200,
        "response time < 1000ms": (r) => r.timings.duration < 1000,
    });

    // 요청 간 간격 (너무 aggressive하게 때리지 않도록 최소 sleep 유지)
    sleep(1);
}

// 단독 실행 시 기본 옵션 (scenarios 파일 사용 시 override됨)
export const options = {
    vus: 1,
    iterations: 1,
};

// k6 기본 진입점
export default function () {
    executeSingleApiLoad();
}
