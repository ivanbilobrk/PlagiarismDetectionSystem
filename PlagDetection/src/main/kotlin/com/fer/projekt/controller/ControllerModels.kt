package com.fer.projekt.controller

import com.fer.projekt.models.ClusterReport
import com.fer.projekt.models.Comparison
import com.fer.projekt.models.ResourceType
import com.fer.projekt.models.ScheduleType
import de.jplag.reporting.reportobject.model.SubmissionFileIndex
import org.springframework.web.multipart.MultipartFile

data class SolutionUploadRequest(
    val zipFile: MultipartFile?,
    val repoUrl: String?,
    val branch: String?,
    val solutionProvider: SolutionProviderName,
    val disallowedFiles: List<String>?,
    val allowedExtensions: List<String>?,
    val userName: String,
    val resourceName: String,
)

data class JPlagResource(
    val edgarHash: String?,
    val resourceName: String?,
    val solutionProvider: SolutionProviderName?,
    val typeOfResource: TypeOfResource,
    val solutionPath: String?,
    val singleSolution: Boolean,
    val exceptionMessage: String?,
)

data class JPlagRunRequest(
    val solutions: List<JPlagResource>,
    val languages: List<String>,
    val resultHash: String,
    val userName: String,
    val fileSuffixes: List<String>,
)

enum class SolutionProviderName {
    LOCAL,
    GIT,
}

enum class TypeOfResource {
    STUDENTS,
    OLD_SUBMISSION,
    WHITELIST,
}

data class JPlagResultResponse(
    val topComparisons: Set<Comparison>,
    val clusters: Set<ClusterReport>,
    val resultName: String,
    val userName: String,
    val submissionFileIndex: SubmissionFileIndex
)

data class CreateBasicConfigRequest(
    val name: String,
    val subjectId: String,
    val resourcesTTL: Int?,
    val suffixes: Set<String>?,
    val languages: Set<String>,
    val scheduleType: ScheduleType,
    val userName: String,
    val disallowedFiles: List<String>?
)

data class UpdateResourceRequest(
    val configName: String,
    val resourcePath: String,
    val userName: String,
    val projectName: String,
)

open class ManuallyAddResourceRequest(
    open val resourceType: ResourceType,
    open val userName: String,
    open val configName: String,
    open val projectName: String,
    open val academicYear: String?=null,
    open val disallowedFiles: List<String>? = null,
    open val allowedExtensions: List<String>? = null,
)

data class ManuallyAddZipResourceRequest(
    override val resourceType: ResourceType,
    override val userName: String,
    override val configName: String,
    override val projectName: String,
    override val academicYear: String?=null,
    override val disallowedFiles: List<String>?,
    override val allowedExtensions: List<String>?,
    val zipFile: MultipartFile,
): ManuallyAddResourceRequest(resourceType, userName, configName, projectName, academicYear, disallowedFiles)

data class ManuallyAddGitResourceRequest(
    override val resourceType: ResourceType,
    override val userName: String,
    override val configName: String,
    override val projectName: String,
    override val academicYear: String?=null,
    override val disallowedFiles: List<String>?,
    override val allowedExtensions: List<String>?,
    val repoURL: String,
    val branch: String,
): ManuallyAddResourceRequest(resourceType, userName, configName, projectName, academicYear, disallowedFiles)

data class ManuallyAddAIResourceRequest(
    override val resourceType: ResourceType,
    override val userName: String,
    override val configName: String,
    override val projectName: String,
    override val academicYear: String?=null,
    override val disallowedFiles: List<String>?,
    override val allowedExtensions: List<String>?,
    val language: String,
    val taskText: String,
): ManuallyAddResourceRequest(resourceType, userName, configName, projectName, academicYear, disallowedFiles)

data class PlagRunState(
    val submissionTotal: Int?,
    val normalizationDone: Int?,
    val parsingDone: Int?,
    val comparisonTotal: Int?,
    val comparisonDone: Int?,
)

data class PlagSuspendedStatus(
    val suspended: Boolean,
    val plagRunId: String,
    val projectName: String,
)
