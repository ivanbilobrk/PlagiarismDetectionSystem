package com.fer.projekt.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "jplag_runs_details")
data class JPlagRunDetails(
    @Id val id: String? = null,
    @Indexed(unique = true) val resultHash: String,
    val error: String? = null,
    val finished: Boolean? = null,
    val submissionTotal: Int? = null,
    val comparisonTotal: Int? = null,
    val failedToParseSubmissions: Set<FailedToParseSubmission>? = null,
    val failedToCompareSubmissions: Set<FailedToCompareSubmissions>? = null,
    val isPaused: Boolean? = null,
    val processedNormalizations: Int = 0,
    val processedParsings: Int = 0,
    val processedComparisons: Int = 0,
    val currentlyProcessingSubmissions: Set<String>? = null
)
