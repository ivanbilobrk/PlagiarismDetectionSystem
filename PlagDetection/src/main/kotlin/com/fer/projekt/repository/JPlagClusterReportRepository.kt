package com.fer.projekt.repository

import com.fer.projekt.models.ClusterReport
import org.springframework.data.mongodb.repository.MongoRepository

interface JPlagClusterReportRepository: MongoRepository<ClusterReport, String> {
}
