package com.fer.projekt.repository

import com.fer.projekt.models.PlagSuspendedTask
import com.fer.projekt.models.PlagSuspendedTaskCollection
import org.springframework.data.mongodb.repository.MongoRepository

interface PlagSuspendedTaskRepository: MongoRepository<PlagSuspendedTaskCollection, String> {
    fun findByPlagRunId(plagRunId: String): PlagSuspendedTaskCollection?
    fun findByConfigNameAndProjectNameAndUserName(
        configName: String,
        projectName: String,
        userName: String
    ): PlagSuspendedTaskCollection?
}
