package com.fer.projekt.service

import com.fer.projekt.controller.JPlagResource
import com.fer.projekt.controller.JPlagResultResponse
import com.fer.projekt.controller.TypeOfResource
import com.fer.projekt.models.*
import com.fer.projekt.repository.*
import de.jplag.reporting.reportobject.model.Cluster
import de.jplag.reporting.reportobject.model.SubmissionFile
import de.jplag.reporting.reportobject.model.SubmissionFileIndex
import de.jplag.reporting.reportobject.model.TopComparison
import org.springframework.stereotype.Service
import java.util.*

@Service
class JPlagMongoService(
    private val jPlagRunRepository: JPlagRunRepository,
    private val jPlagResultRepository: JPlagResultRepository,
    private val jPlagComparisonRepository: JPlagComparisonRepository,
    private val jPlagClusterReportRepository: JPlagClusterReportRepository,
    private val jPlagSubmissionComparisonRepository: JPlagSubmissionComparisonRepository,
    private val plagConfigRepository: PlagConfigRepository,
    private val jPlagServiceCommunicator: JPlagServiceCommunicator,
    ) {

    fun saveJPlagRun(
        resultName: String,
        suffixes: Set<String>?,
        languages: Set<String>,
        username: String,
        resources: List<JPlagResource>,
        projectName: String?,
        configName: String?,
        resultHash: String
    ): JPlagRun {
        val whiteListPath = resources
            .filter { it.typeOfResource == TypeOfResource.WHITELIST }
            .firstNotNullOfOrNull { it.solutionPath }

        val oldSubmissionsPaths = resources
            .filter { it.typeOfResource == TypeOfResource.OLD_SUBMISSION }
            .mapNotNull { it.solutionPath }
            .toSet()
            .minus(whiteListPath)

        val solutionPaths = resources.asSequence()
            .filter { it.typeOfResource == TypeOfResource.STUDENTS }
            .mapNotNull { it.solutionPath }
            .toSet()
            .minus(whiteListPath)
            .minus(oldSubmissionsPaths)
            .toSet()

        val jPlagRun = JPlagRun(
            resultName = resultName,
            suffixes = suffixes?.toSet() ?: emptySet(),
            languages = languages.toSet(),
            solutionPaths = solutionPaths,
            oldSubmissionPaths = oldSubmissionsPaths,
            whitelistPath = whiteListPath,
            userName = username,
            projectName = projectName,
            configName = configName,
            resultHash = resultHash,
        )

        return jPlagRunRepository.save(jPlagRun)
    }

    fun getJplagRunByNameAndUsername(resultName: String, username: String): JPlagRun? {
        return jPlagRunRepository.findByResultNameAndUserName(resultName, username)
    }

    fun updateJplagRun(jPlagRunOld: JPlagRun, error: String?): JPlagRun {
        val jPlagRun = jPlagRunRepository.findById(jPlagRunOld.id!!).orElseThrow { IllegalStateException("JPlag run not found") }
        val updatedJPlagRun = jPlagRun.copy(
            error = error,
            finished = true,
            isPaused = jPlagRun.isPaused,
        )
        return jPlagRunRepository.save(updatedJPlagRun)
    }

    fun saveResult(topComparisons: Set<TopComparison>, clusters: Set<Cluster>, submissionComparisons: List<SubmissionComparison>, submissionFileIndex: SubmissionFileIndex, resultName: String, username: String) {
        val clusterIds = clusters.map { cluster ->
            jPlagClusterReportRepository.save(ClusterReport(
                averageSimilarity = cluster.averageSimilarity,
                strength = cluster.strength,
                members = cluster.members
            )).id
        }.toSet()
        val topComparisonIds = topComparisons.map { comparison ->
            jPlagComparisonRepository.save(Comparison(
                firstSubmission = comparison.firstSubmission,
                secondSubmission = comparison.secondSubmission,
                similarities = comparison.similarities
            )).id
        }.toSet()
        try {
            val submissionComparisonIds = jPlagSubmissionComparisonRepository.saveAll(submissionComparisons).map { it.id }
            val result = Result(
                resultName = resultName,
                userName = username,
                topComparisonsIds = topComparisonIds,
                clustersIds = clusterIds,
                submissionComparisonIds = submissionComparisonIds.toSet(),
                submissionFileIndex = submissionFileIndex,
                resultHash = resultName,
            )
            jPlagResultRepository.save(result)
        } catch (e: Exception) {
        }

    }

    fun restoreDotsInKeys(map: Map<String, Map<String, SubmissionFile>>): Map<String, Map<String, SubmissionFile>> {
        return map.mapKeys { (key, _) -> key.replace("[dot]", ".") }
            .mapValues { (_, innerMap) ->
                innerMap.mapKeys { (innerKey, _) -> innerKey.replace("[dot]", ".") }
            }
    }

    fun getResult(resultName: String, userName: String): JPlagResultResponse {
        val result = jPlagResultRepository.findByResultNameAndUserName(resultName, userName)
            ?: throw IllegalArgumentException("Result with name $resultName not found for user $userName")
        val topComparisons = jPlagComparisonRepository.findAllById(result.topComparisonsIds).toSet()
        val clusters = jPlagClusterReportRepository.findAllById(result.clustersIds).toSet()
        val submissionFileIndex = restoreDotsInKeys(result.submissionFileIndex.fileIndexes)
        return JPlagResultResponse(
            topComparisons = topComparisons,
            clusters = clusters,
            resultName = resultName,
            userName = userName,
            submissionFileIndex = SubmissionFileIndex(submissionFileIndex)
        )
    }

    fun getResultByResultHash(resultHash: String): JPlagResultResponse {
        val result = jPlagResultRepository.findByResultHash(resultHash)
            ?: throw IllegalArgumentException("Result with hash $resultHash not found")
        val topComparisons = jPlagComparisonRepository.findAllById(result.topComparisonsIds).toSet()
        val clusters = jPlagClusterReportRepository.findAllById(result.clustersIds).toSet()
        val submissionFileIndex = restoreDotsInKeys(result.submissionFileIndex.fileIndexes)
        return JPlagResultResponse(
            topComparisons = topComparisons,
            clusters = clusters,
            resultName = result.resultName,
            userName = result.userName,
            submissionFileIndex = SubmissionFileIndex(submissionFileIndex)
        )
    }

    fun getSubmissionComparison(resultName: String, userName: String, firstSubmissionId: String, secondSubmissionId: String): SubmissionComparison? {
        return jPlagSubmissionComparisonRepository.findByResultNameAndUserNameAndFirstSubmissionIdAndSecondSubmissionId(
            resultName = resultName,
            userName = userName,
            firstSubmissionId = firstSubmissionId,
            secondSubmissionId = secondSubmissionId
        ) ?: jPlagSubmissionComparisonRepository.findByResultNameAndUserNameAndFirstSubmissionIdAndSecondSubmissionId(
            resultName = resultName,
            userName = userName,
            firstSubmissionId = secondSubmissionId,
            secondSubmissionId = firstSubmissionId
        )
    }

    fun getAllRunsForUser(username: String): List<JPlagRun>? {
        return jPlagRunRepository.findByUserName(username)
    }

    fun deleteJPlagResult(resultHash: String) {
        jPlagRunRepository.deleteByResultHash(resultHash)
        val result = jPlagResultRepository.findByResultHash(resultHash)

        try {
            jPlagServiceCommunicator.deletePlagRunDetails(result?.resultHash ?: "")
        } catch(e: Exception) {
        }

        try {
            jPlagResultRepository.deleteByResultHash(resultHash)
        } catch(e: Exception) {
        }

        try {
            jPlagClusterReportRepository.deleteAllById(result!!.clustersIds)
        } catch(e: Exception) {
        }

        try {
            jPlagComparisonRepository.deleteAllById(result!!.topComparisonsIds)
        } catch(e: Exception) {
        }
    }

    fun getOldResourcesByConfigNameAndProject(
        configName: String,
        projectName: String,
        userName: String,
    ): List<Resource> {
        val config = plagConfigRepository.findByUserNameAndName(userName = userName, name = configName)
        if (config != null) {
            return config.projects.first { it.name == projectName }
                .resources
                .filter { it.academicYear != PlagConfigService.getCurrentAcademicYear(Date()) }
        } else {
            return emptyList()
        }
    }

    fun getStudentResourcesByConfigNameAndProject(
        configName: String,
        projectName: String,
        userName: String,
    ): List<Resource> {
        val config = plagConfigRepository.findByUserNameAndName(userName = userName, name = configName)
        if (config != null) {
            return config.projects.first { it.name == projectName }
                .resources
                .filter { it.academicYear == PlagConfigService.getCurrentAcademicYear(Date()) }
        } else {
            return emptyList()
        }
    }

    fun getWhitelistResourceByConfigNameAndProject(
        configName: String,
        projectName: String,
        userName: String
    ): Resource? {
        val config = plagConfigRepository.findByUserNameAndName(userName = userName, name = configName)
        if (config != null) {
            return config.projects.first { it.name == projectName }
                .resources.firstOrNull { it.resourceType == ResourceType.WHITELIST }
        } else {
            return null
        }
    }

    fun getProjectForUserNameAndConfigNameAndProjectName(
        configName: String,
        projectName: String,
        userName: String
    ): StudentProject? {
        val config = plagConfigRepository.findByUserNameAndName(userName = userName, name = configName)
        return config?.projects?.firstOrNull { it.name == projectName }
    }

    fun getAllRunsForUserNameConfigNameAndProjectName(
        configName: String,
        projectName: String,
        userName: String
    ): List<JPlagRun>  {
        return jPlagRunRepository.findByProjectNameAndUserNameAndConfigName(userName = userName, projectName = projectName, configName = configName)
    }

    fun getPlagRunById(
        id: String
    ): JPlagRun? {
        return jPlagRunRepository.findById(id).orElse(null)
    }

    fun getPlagRunByResultHash(
        resultHash: String
    ): JPlagRun? {
        return jPlagRunRepository.findByResultHash(resultHash)
    }

    fun undoFirstRunForStudentProjectForConfigAndUserName(
        configName: String,
        projectName: String,
        userName: String,
    ) {
        val config = plagConfigRepository.findByUserNameAndName(userName = userName, name = configName)
        val project = config?.projects?.firstOrNull { it.name == projectName }?.copy(
            firstRun = false
        )
        if (project != null) {
            val updatedConfig = config.copy(
                projects = config.projects.map { if (it.name == projectName) project else it }
            )
            plagConfigRepository.save(updatedConfig)
        } else {
            throw IllegalArgumentException("Project with name $projectName not found for user $userName")
        }
    }

    fun updateTotalNumbersForRun(
        jPlagRunOld: JPlagRun,
        submissionTotal: Int?,
        comparisonTotal: Int?
    ): JPlagRun {
        val jPlagRun = jPlagRunRepository.findById(jPlagRunOld.id!!).orElseThrow {
            IllegalArgumentException("JPlagRun with id ${jPlagRunOld.id} not found")
        }
        val updatedJPlagRun = jPlagRun.copy(
            submissionTotal = submissionTotal,
            comparisonTotal = comparisonTotal
        )
        return jPlagRunRepository.save(updatedJPlagRun)
    }

    fun updateFailedToParseSubmissions(
        jPlagRunId: String,
        failedToParseSubmissions: Set<FailedToParseSubmission>
    ): JPlagRun {
        val jPlagRun = jPlagRunRepository.findById(jPlagRunId).orElseThrow {
            IllegalArgumentException("JPlagRun with id $jPlagRunId not found")
        }
        val updatedJPlagRun = jPlagRun.copy(
            failedToParseSubmissions = (failedToParseSubmissions.toList() + jPlagRun.failedToParseSubmissions.orEmpty()).toSet().toList()
        )
        return jPlagRunRepository.save(updatedJPlagRun)
    }

    fun updateFailedToCompareSubmissions(
        jPlagRunId: String,
        failedToCompareSubmissions: Set<FailedToCompareSubmissions>
    ): JPlagRun {
        val jPlagRun = jPlagRunRepository.findById(jPlagRunId).orElseThrow {
            IllegalArgumentException("JPlagRun with id $jPlagRunId not found")
        }
        val updatedJPlagRun = jPlagRun.copy(
            failedToCompareSubmissions = failedToCompareSubmissions.toList() + jPlagRun.failedToCompareSubmissions.orEmpty()
        )
        return jPlagRunRepository.save(updatedJPlagRun)
    }

    fun addResultHashToProject(userName: String, configName: String, projectName: String, resultHash: String) {
        val plagConfig = plagConfigRepository.findByUserNameAndName(userName, configName)!!

        val updatedProjects = plagConfig.projects.map { project ->
            if (project.name == projectName) {
                val updatedHashes = (project.resultHashes ?: emptySet()) + resultHash
                project.copy(resultHashes = updatedHashes)
            } else {
                project
            }
        }

        val updatedConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedConfig)
    }
}
