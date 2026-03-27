import { buildScenarioOptions } from "../config/k6-options.js";
import { executeSingleApiLoad } from "../scripts/single_api_load.js";

/**
 * stress 테스트
 * - 점진적으로 부하를 올려 병목 구간 확인
 * - 응답 지연, 에러율 증가, Kafka lag 증가 시점 확인
 */
export const options = buildScenarioOptions("stress");

export default function () {
    executeSingleApiLoad();
}
