package com.fer.projekt.models

import de.jplag.reporting.reportobject.model.SubmissionFileIndex
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "results")
data class Result(
    @Id val id: String? = null,
    @Indexed(unique = true) val resultHash: String,
    val topComparisonsIds: Set<String?>,
    val clustersIds: Set<String?>,
    val submissionFileIndex: SubmissionFileIndex,
    val submissionComparisonIds: Set<String?>,
    val resultName: String,
    val userName: String,
)
