package com.fer.projekt.models

import com.fasterxml.jackson.annotation.JsonProperty
import de.jplag.reporting.reportobject.model.Match
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "submission_comparisons")
data class SubmissionComparison(
    @Id val id: String? = null,
    @JsonProperty("id1") val firstSubmissionId: String,
    @JsonProperty("id2") val secondSubmissionId: String,
    @JsonProperty("similarities") val similarities: Map<String, Double>,
    @JsonProperty("matches") val matches: List<Match>,
    @JsonProperty("first_similarity") val firstSimilarity: Double,
    @JsonProperty("second_similarity") val secondSimilarity: Double,
    @JsonProperty("first_submission_path") val firstSubmissionPath: String,
    @JsonProperty("second_submission_path") val secondSubmissionPath: String,
    val userName: String,
    val resultName: String,
    val searchKey: String,
)
