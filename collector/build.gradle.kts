buildscript {
    repositories {
        mavenCentral() // 플러그인 포털 대신 메이븐 중앙 저장소 활용
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.github.johnrengelman:shadow:8.1.1")
    }
}
plugins {
    // Kotlin JVM 프로젝트
    kotlin("jvm") version "2.0.21"

    // 실행 entrypoint 지정용
    application

}

// buildscript에서 가져온 플러그인을 프로젝트에 강제 적용
apply(plugin = "com.github.johnrengelman.shadow")


group = "com.pipeline"
version = "0.1.0"

repositories {
    mavenCentral()

}

dependencies {
    // Kotlin 표준 라이브러리
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
    // Kotlin top-level main 함수 진입점
    mainClass.set("com.pipeline.collector.ApplicationKt")
}

// Shadow 8.1.1 + Gradle 8.x 충돌 방지를 위해 배포 태스크 비활성화
tasks {
    named("distZip") { enabled = false }
    named("distTar") { enabled = false }
}

// shadowJar를 문자열로 찾아 타입 캐스팅하여 설정 (컴파일 에러 해결)
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("collector")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.pipeline.collector.ApplicationKt"
    }
}