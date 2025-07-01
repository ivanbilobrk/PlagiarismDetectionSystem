package com.fer.projekt.repository

import com.fer.projekt.models.CustomJPlagComparison
import org.springframework.data.mongodb.repository.MongoRepository

interface CustomJPlagComparisonRepository: MongoRepository<CustomJPlagComparison, String> {
}
