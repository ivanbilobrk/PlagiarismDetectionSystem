package com.fer.projekt.repository

import com.fer.projekt.models.Comparison
import org.springframework.data.mongodb.repository.MongoRepository

interface JPlagComparisonRepository: MongoRepository<Comparison, String> {
}
