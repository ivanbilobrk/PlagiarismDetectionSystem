package com.fer.projekt.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "clusters")
data class ClusterReport(
    @Id val id: String? = null,
    @JsonProperty("average_similarity") val averageSimilarity: Double,
    @JsonProperty("strength") val strength: Double,
    @JsonProperty("members") val members: List<String>,
)
