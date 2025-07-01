package com.fer.projekt.models

data class FailedToParseSubmission(
    val submissionName: String,
    val reason: String,
)

data class FailedToCompareSubmissions(
    val firstSubmissionName: String,
    val secondSubmissionName: String,
    val reason: String,
)
