package com.fer.projekt.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "jplag_runs")
data class JPlagRun(
    @Id val id: String? = null,
    @Indexed(unique = true) val resultHash: String,
    val resultName: String,
    val suffixes: Set<String>? = null,
    val languages: Set<String>? = null,
    val solutionPaths: Set<String?>? = null,
    val oldSubmissionPaths: Set<String?>? = null,
    val whitelistPath: String? = null,
    val userName: String,
    val projectName: String? = null,
    val configName: String? = null,
    val error: String? = null,
    val finished: Boolean? = null,
    val submissionTotal: Int? = null,
    val comparisonTotal: Int? = null,
    val failedToParseSubmissions: List<FailedToParseSubmission>? = null,
    val failedToCompareSubmissions: List<FailedToCompareSubmissions>? = null,
    val isPaused: Boolean? = null
)
