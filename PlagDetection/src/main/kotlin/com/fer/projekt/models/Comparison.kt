package com.fer.projekt.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "comparisons")
data class Comparison(
    @Id val id: String? = null,
    @JsonProperty("first_submission") val firstSubmission: String,
    @JsonProperty("second_submission") val secondSubmission: String,
    @JsonProperty("similarities") val similarities: Map<String, Double>,
)
