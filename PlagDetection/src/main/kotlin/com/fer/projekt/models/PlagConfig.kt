package com.fer.projekt.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document(collection = "plag_job_configs")
data class PlagConfig(
    @Id val id: String? = null,
    val name: String,
    val subjectName: String,
    val resourcesTTL: Long,
    val clientBackendURL: String,
    val projects: List<StudentProject>,
    val scheduleType: ScheduleType,
    val userName: String,
    val suffixes: Set<String>,
    val languages: Set<String>,
    val lastUpdateTime: Date? = null,
    val subjectId: String,
    val disallowedFiles: Set<String>,
)

data class StudentProject(
    val name: String,
    val resources: List<Resource>,
    val suffixes: Set<String>,
    val languages: Set<String>,
    val firstRun: Boolean = true,
    val taskText: String? = null,
    val resultHashes: Set<String>?,
)

open class Resource(
    open val exceptionMessage: String? = null,
    open val resourceType: ResourceType,
    open val lastUpdate: Date? = null,
    open val path: String,
    open val academicYear: String,
    open val hash: String? = null,
    open val hasBeenChanged: Boolean? = null,
    val repoURL: String? = null,
    val branch: String? = null,
    val taskText: String? = null,
    val edgarHash: String?
)

enum class ScheduleType(val days: Int) {
    DAILY(1),
    WEEKLY(7),
    EVERY_TWO_WEEKS(14),
    MONTHLY(30),
}

enum class ResourceType {
    EDGAR,
    GIT,
    ZIP,
    WHITELIST,
    AI
}
