import { requireEnv, requireNumberEnv } from "./env.js";

// 공통 thresholds (모든 시나리오에서 기본적으로 보는 기준)
const commonThresholds = {
    http_req_failed: ["rate<0.01"], // 요청 실패율 / 실패율 1% 미만
    http_req_duration: ["p(95)<1000"], // 응답 시간 / p95 1000ms 미만
    checks: ["rate>0.99"], // check 성공률 99% 초과
};

// smoke 전용 thresholds
const smokeThresholds = {
    checks: ["rate>0.99"],
};

// smoke 시나리오 옵션 (아주 가볍게 연결 상태만 확인)
function buildSmokeOptions() {
    return {
        vus: 1,
        iterations: 3,
        thresholds: smokeThresholds,
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

// 외부에서 시나리오 이름만 넘기면 맞는 options 객체를 반환
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