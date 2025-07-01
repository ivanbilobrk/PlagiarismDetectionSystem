package com.fer.projekt.repository

import com.fer.projekt.models.CustomSubmission
import org.springframework.data.mongodb.repository.MongoRepository

interface CustomSubmissionsRepoistory: MongoRepository<CustomSubmission, String> {
}
