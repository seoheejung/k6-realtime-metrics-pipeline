plugins {
    // Kotlin JVM 프로젝트
    kotlin("jvm") version "2.0.21"

    application
}

group = "com.pipeline"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {

    // Kotlin 표준 라이브러리 명시 (에러 방지 강화)
    implementation(kotlin("stdlib"))

    // Kafka Consumer client
    implementation("org.apache.kafka:kafka-clients:3.9.0")

    // YAML 파일(application.yml) 읽기
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")

    // Kotlin data class와 Jackson 매핑 지원
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // 로그 인터페이스
    implementation("org.slf4j:slf4j-api:2.0.13")

    // 콘솔 로그 구현체
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    // Kotlin의 top-level main 함수는 파일명 + Kt 로 잡힘
    mainClass.set("com.pipeline.collector.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}