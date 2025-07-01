package com.fer.projekt.repository

import com.fer.projekt.models.PlagConfig
import org.springframework.data.mongodb.repository.MongoRepository

interface PlagConfigRepository: MongoRepository<PlagConfig, String> {
    fun findByUserNameAndName(userName: String, name: String): PlagConfig?
    fun findByUserName(userName: String): List<PlagConfig>
    fun findByUserNameAndId(userName: String, id: String): PlagConfig?
}
