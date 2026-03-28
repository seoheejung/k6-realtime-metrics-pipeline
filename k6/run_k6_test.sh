#!/usr/bin/env bash

# 에러 발생 시 즉시 종료
set -euo pipefail

# --------------------------------------------------
# 사용법
# ./run_k6_test.sh smoke
# ./run_k6_test.sh load
# ./run_k6_test.sh stress
# --------------------------------------------------

# 에러 발생 시 줄 번호와 종료 코드 출력
trap 'echo "[ERROR] ${BASH_SOURCE[0]}:${LINENO} line failed (exit code: $?)"' ERR

# --------------------------------------------------
# 현재 스크립트 위치 기준으로 경로 계산
#
# Git Bash(Windows)에서는 pwd -W 사용
# Linux/macOS에서는 일반 pwd 사용
# --------------------------------------------------
if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -W)"
  export MSYS_NO_PATHCONV=1
else
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fi

ENV_FILE="${SCRIPT_DIR}/.env"

# 첫 번째 인자를 시나리오 이름으로 받음
SCENARIO="${1:-smoke}"

# --------------------------------------------------
# .env 로드
# k6 디렉토리 안의 .env를 사용
# --------------------------------------------------
if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다: ${ENV_FILE}"
  exit 1
fi

# .env 내용을 현재 셸 환경변수로 로드
set -a
source "${ENV_FILE}"
set +a

# --------------------------------------------------
# 필수 환경변수 검증 함수
# --------------------------------------------------
require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "[ERROR] ${name} 환경변수가 필요합니다."
    exit 1
  fi
}

# --------------------------------------------------
# 공통 환경변수 검증
# --------------------------------------------------
require_env BASE_URL
require_env TARGET_PATH
require_env KAFKA_BROKERS
require_env KAFKA_TOPIC
require_env K6_IMAGE

# REQUEST_TIMEOUT은 선택값
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-5s}"

# --------------------------------------------------
# 실행할 시나리오 파일 경로 결정
# 시나리오별로 필요한 env만 검증
# --------------------------------------------------
case "${SCENARIO}" in
  smoke)
    TEST_FILE="/work/scenarios/smoke.js"
    ;;
  load)
    require_env LOAD_VUS
    require_env LOAD_DURATION
    TEST_FILE="/work/scenarios/load.js"
    ;;
  stress)
    require_env STRESS_START_VUS
    require_env STRESS_STAGE_1_TARGET
    require_env STRESS_STAGE_2_TARGET
    require_env STRESS_STAGE_3_TARGET
    TEST_FILE="/work/scenarios/stress.js"
    ;;
  *)
    echo "[ERROR] 지원하지 않는 시나리오입니다: ${SCENARIO}"
    echo "사용 가능 값: smoke | load | stress"
    exit 1
    ;;
esac

echo "[INFO] 시나리오: ${SCENARIO}"
echo "[INFO] BASE_URL: ${BASE_URL}"
echo "[INFO] TARGET_PATH: ${TARGET_PATH}"
echo "[INFO] Kafka: ${KAFKA_BROKERS}"
echo "[INFO] Topic: ${KAFKA_TOPIC}"
echo "[INFO] K6_IMAGE: ${K6_IMAGE}"

# --------------------------------------------------
# Docker 이미지가 없으면 자동 빌드
# --------------------------------------------------
if ! docker image inspect "${K6_IMAGE}" >/dev/null 2>&1; then
  echo "[INFO] k6 Docker 이미지가 없어서 빌드를 시작합니다."
  docker build -t "${K6_IMAGE}" "${SCRIPT_DIR}/docker"
fi

# --------------------------------------------------
# Linux에서 host.docker.internal 대응
# Docker Desktop이 아닌 Linux 환경에서는
# host-gateway를 직접 추가해주는 편이 안전하다.
# --------------------------------------------------
DOCKER_EXTRA_ARGS=()

if [[ "$(uname -s)" == "Linux" ]]; then
  DOCKER_EXTRA_ARGS+=(--add-host=host.docker.internal:host-gateway)
fi

# --------------------------------------------------
# k6 실행
#
# - Git Bash에서는 MSYS_NO_PATHCONV=1로 경로 변환 차단
# - Kafka brokers는 단일 문자열 그대로 전달
#   예: localhost:9092
#   예: broker1:9092,broker2:9092
# --------------------------------------------------
docker run --rm \
  "${DOCKER_EXTRA_ARGS[@]}" \
  -v "${SCRIPT_DIR}:/work" \
  -e BASE_URL="${BASE_URL}" \
  -e TARGET_PATH="${TARGET_PATH}" \
  -e REQUEST_TIMEOUT="${REQUEST_TIMEOUT}" \
  -e LOAD_VUS="${LOAD_VUS:-}" \
  -e LOAD_DURATION="${LOAD_DURATION:-}" \
  -e STRESS_START_VUS="${STRESS_START_VUS:-}" \
  -e STRESS_STAGE_1_TARGET="${STRESS_STAGE_1_TARGET:-}" \
  -e STRESS_STAGE_2_TARGET="${STRESS_STAGE_2_TARGET:-}" \
  -e STRESS_STAGE_3_TARGET="${STRESS_STAGE_3_TARGET:-}" \
  "${K6_IMAGE}" run \
  --out "xk6-kafka=brokers=${KAFKA_BROKERS},topic=${KAFKA_TOPIC}" \
  "${TEST_FILE}"

echo "[INFO] 테스트 완료"