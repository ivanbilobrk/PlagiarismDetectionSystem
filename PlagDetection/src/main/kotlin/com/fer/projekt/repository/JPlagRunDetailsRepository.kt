package com.fer.projekt.repository

import com.fer.projekt.models.JPlagRunDetails
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface JPlagRunDetailsRepository: MongoRepository<JPlagRunDetails, String> {

    fun findByResultHash(resultHash: String): JPlagRunDetails?
}
