import { buildScenarioOptions } from "../config/k6-options.js";
import { executeSingleApiLoad } from "../scripts/single_api_load.js";

/**
 * load 테스트
 * - 정상 운영 범위와 비슷한 부하를 일정 시간 유지
 * - p95, error rate, RPS 추세 확인
 */
export const options = buildScenarioOptions("load");

export default function () {
    // load는 요청 간 아주 짧은 기본 간격을 둔다.
    executeSingleApiLoad({ sleepSeconds: 1 });
}