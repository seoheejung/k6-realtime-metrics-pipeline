import { buildScenarioOptions } from "../config/k6-options.js";
import { executeSingleApiLoad } from "../scripts/single_api_load.js";

/**
 * smoke 테스트
 * - 스크립트가 정상 동작하는지 확인
 * - API가 최소한 응답하는지 확인
 * - Kafka output 연결 전에 가장 먼저 확인
 */
export const options = buildScenarioOptions("smoke");

export default function () {
    executeSingleApiLoad();
}
