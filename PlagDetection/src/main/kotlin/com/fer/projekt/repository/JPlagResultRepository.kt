package com.fer.projekt.repository

import com.fer.projekt.models.Result
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface JPlagResultRepository: MongoRepository<Result, String> {
    fun findByResultNameAndUserName(resultName: String, userName: String): Result?

    fun deleteByResultNameAndUserName(resultName: String, userName: String)

    fun findByResultHash(resultHash: String): Result?
    fun deleteByResultHash(resultHash: String)
}
