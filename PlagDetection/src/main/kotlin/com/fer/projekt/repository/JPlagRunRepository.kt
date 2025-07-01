package com.fer.projekt.repository

import com.fer.projekt.models.JPlagRun
import org.springframework.data.mongodb.repository.MongoRepository

interface JPlagRunRepository: MongoRepository<JPlagRun, String> {
    fun findByResultNameAndUserName(resultName: String, userName: String): JPlagRun?

    fun findByUserName(userName: String): List<JPlagRun>

    fun deleteByResultNameAndUserName(resultName: String, userName: String)

    fun findByProjectNameAndUserNameAndConfigName(projectName: String, userName: String, configName: String): List<JPlagRun>

    fun findByResultHash(resultHash: String): JPlagRun?

    fun deleteByResultHash(resultHash: String)
}
