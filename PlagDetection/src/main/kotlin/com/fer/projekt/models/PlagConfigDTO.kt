package com.fer.projekt.models

data class PlagConfigDTO(
    val plagConfig: PlagConfig,
    val numberOfSubmissions: Map<String, Int>
)
