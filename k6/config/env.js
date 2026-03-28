// 필수 환경변수 검증 함수
export function requireEnv(name) {
    if (!__ENV[name]) {
        throw new Error(`${name} 환경변수가 필요합니다.`);
    }
    return __ENV[name];
}

// 숫자형 환경변수 검증 함수
export function requireNumberEnv(name) {
    const value = Number(requireEnv(name));

    if (Number.isNaN(value)) {
        throw new Error(`${name} 환경변수는 숫자여야 합니다.`);
    }

    return value;
}

// 선택 환경변수 조회 함수
export function getOptionalEnv(name, defaultValue) {
    return __ENV[name] || defaultValue;
}