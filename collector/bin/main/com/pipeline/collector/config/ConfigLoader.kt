package com.pipeline.collector.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

object ConfigLoader {

    // ${ENV_KEY} 형태를 찾기 위한 정규식
    private val placeholderRegex = Regex("""\$\{([A-Z0-9_]+)}""")

    fun load(): AppConfig {
        // 1) resources/application.yml 원문 읽기
        val yamlTemplate = loadYamlTemplate()

        // 2) .env 파일 + 시스템 환경변수 병합
        //    같은 키면 시스템 환경변수가 우선
        val envMap = loadDotEnv() + System.getenv()

        // 3) ${KEY} -> 실제 값 치환
        val resolvedYaml = replacePlaceholders(yamlTemplate, envMap)

        // 4) YAML 문자열을 AppConfig로 변환
        val mapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return mapper.readValue(resolvedYaml)
    }

    private fun loadYamlTemplate(): String {
        val inputStream = object {}.javaClass.classLoader.getResourceAsStream("application.yml")
            ?: throw IllegalStateException("application.yml not found in resources")

        return inputStream.bufferedReader().use { it.readText() }
    }

    private fun loadDotEnv(): Map<String, String> {
        val envFile = File(".env")

        if (!envFile.exists()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, String>()

        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }
            .forEach { line ->
                val idx = line.indexOf("=")
                if (idx <= 0) return@forEach

                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()

                result[key] = value
            }

        return result
    }

    private fun replacePlaceholders(
        yamlTemplate: String,
        envMap: Map<String, String>
    ): String {
        return placeholderRegex.replace(yamlTemplate) { match ->
            val key = match.groupValues[1]
            envMap[key]
                ?: throw IllegalStateException("Missing config value for placeholder: $key")
        }
    }
}