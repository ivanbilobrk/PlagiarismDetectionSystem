package com.fer.projekt.repository

import com.fer.projekt.models.SubmissionComparison
import org.springframework.data.mongodb.repository.MongoRepository

interface JPlagSubmissionComparisonRepository: MongoRepository<SubmissionComparison, String> {

    fun findByResultNameAndUserNameAndFirstSubmissionIdAndSecondSubmissionId(resultName: String, userName: String, firstSubmissionId: String, secondSubmissionId: String): SubmissionComparison?
}
