package com.fer.projekt.models

import aws.smithy.kotlin.runtime.time.Instant

data class EdgarProjectDTO(
    val downloadAttachmentsUrl: String,
    val examUserCreated: String,
    val idAcademicYear: Int,
    val id: Int,
    val title: String,
    val tsCreated: String,
    val tsModified: String,
    val userModified: String,
    val userCreated: String,
    val titleAbbrev: String,
    val typeName: String,
    val testOrdinal: Int,
    val isGlobal: Boolean,
    val isPublic: Boolean,
    val useInStats: Boolean,
    val testScoreIgnored: Boolean,
    val showSolutions: Boolean,
    val maxScore: String,
    val maxRuns: Int,
    val questionsNo: Int,
    val durationSeconds: Int,
    val allowAnonymousStalk: Boolean,
    val tsAvailableFrom: String,
    val tsAvailableTo: String
)
