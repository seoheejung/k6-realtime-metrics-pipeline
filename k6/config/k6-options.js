// 필수 환경변수 검증 함수
function requireEnv(name) {
    if (!__ENV[name]) {
        throw new Error(`${name} 환경변수가 필요합니다.`);
    }
    return __ENV[name];
}

// 숫자형 환경변수 검증 함수
function requireNumberEnv(name) {
    const value = Number(requireEnv(name));

    if (Number.isNaN(value)) {
        throw new Error(`${name} 환경변수는 숫자여야 합니다.`);
    }

    return value;
}

/**
 * 공통 thresholds
 * 모든 시나리오에서 기본적으로 보는 기준
 *
 * - http_req_failed: 요청 실패율
 * - http_req_duration: 응답 시간
 * - checks: check 성공률
 */
const commonThresholds = {
    http_req_failed: ["rate<0.01"], // 실패율 1% 미만
    http_req_duration: ["p(95)<1000"], // p95 1000ms 미만
    checks: ["rate>0.99"], // check 성공률 99% 초과
};

// smoke 시나리오 옵션 (아주 가볍게 연결 상태만 확인)
function buildSmokeOptions() {
    // smoke는 빠른 검증용이므로 고정값 사용
    return {
        vus: 1,
        iterations: 3,
        thresholds: commonThresholds,
        tags: {
        scenario: "smoke",
        },
    };
}

// load 시나리오 옵션 (정상 부하를 일정 시간 유지)
function buildLoadOptions() {
    const vus = requireNumberEnv("LOAD_VUS");
    const duration = requireEnv("LOAD_DURATION");

    return {
        scenarios: {
            load_test: {
                executor: "constant-vus",
                vus,
                duration,
                exec: "default",
            },
        },
        thresholds: commonThresholds,
        tags: {
            scenario: "load",
        },
    };
}

// stress 시나리오 옵션 (단계적으로 부하를 높이면서 병목 구간 확인)
function buildStressOptions() {
    const startVUs = requireNumberEnv("STRESS_START_VUS");
    const stage1Target = requireNumberEnv("STRESS_STAGE_1_TARGET");
    const stage2Target = requireNumberEnv("STRESS_STAGE_2_TARGET");
    const stage3Target = requireNumberEnv("STRESS_STAGE_3_TARGET");

    return {
        scenarios: {
            stress_test: {
                executor: "ramping-vus",
                startVUs,
                stages: [
                    // 1단계: 낮은 부하
                    { duration: "30s", target: stage1Target },

                    // 2단계: 중간 부하
                    { duration: "30s", target: stage2Target },

                    // 3단계: 높은 부하
                    { duration: "30s", target: stage3Target },

                    // 종료 전 정리
                    { duration: "10s", target: 0 },
                ],
                gracefulRampDown: "5s",
                exec: "default",
            },
        },
        thresholds: commonThresholds,
        tags: {
            scenario: "stress",
        },
    };
}

export function buildScenarioOptions(type) {
    if (type === "smoke") {
        return buildSmokeOptions();
    }

    if (type === "load") {
        return buildLoadOptions();
    }

    if (type === "stress") {
        return buildStressOptions();
    }

    throw new Error(`지원하지 않는 시나리오입니다: ${type}`);
}
