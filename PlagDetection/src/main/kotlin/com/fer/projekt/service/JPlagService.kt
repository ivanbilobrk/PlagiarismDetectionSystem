package com.fer.projekt.service

import com.fer.projekt.config.JPlagExecutorServiceConfig
import com.fer.projekt.config.PlagSuspendedTaskManager
import com.fer.projekt.controller.JPlagResource
import com.fer.projekt.controller.SolutionProviderName
import com.fer.projekt.controller.TypeOfResource
import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.fileproviders.RESOURCES_PATH
import com.fer.projekt.fileproviders.TEMP_RESOURCES_PATH
import com.fer.projekt.languages.CustomNaturalLanguage
import com.fer.projekt.models.JPlagRunDetails
import com.fer.projekt.models.StudentProject
import com.fer.projekt.models.SubmissionComparison
import com.fer.projekt.repository.JPlagRunDetailsRepository
import com.fer.projekt.solutionproviders.RepositorySolutionProvider
import de.jplag.*
import de.jplag.c.CLanguage
import de.jplag.cpp.CPPLanguage
import de.jplag.java.JavaLanguage
import de.jplag.javascript.JavaScriptLanguage
import de.jplag.python3.PythonLanguage
import de.jplag.reporting.FilePathUtil
import de.jplag.reporting.reportobject.mapper.ClusteringResultMapper
import de.jplag.reporting.reportobject.mapper.MetricMapper
import de.jplag.reporting.reportobject.mapper.SubmissionNameToIdMapper
import de.jplag.reporting.reportobject.model.*
import de.jplag.reporting.reportobject.model.Match
import de.jplag.typescript.TypeScriptLanguage
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.collections.HashMap
import de.jplag.multilang.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.atomic.AtomicInteger

@Service
class JPlagService(
    @Qualifier("solutionProvidersMap") private val solutionProvidersMap: MutableMap<SolutionProviderName, out RepositorySolutionProvider>,
    private val jPlagExecutorService: JPlagExecutorServiceConfig,
    private val jPlagMongoService: JPlagMongoService,
    private val plagSuspendedTaskManager: PlagSuspendedTaskManager,
    private val jPlagServiceCommunicator: JPlagServiceCommunicator,
    private val jPlagRunDetailsRepository: JPlagRunDetailsRepository,
    private val stringRedisTemplate: StringRedisTemplate,
    private val mongoTemplate: MongoTemplate
) {
    fun deleteByResultHash(resultHash: String) {
        val query = Query(Criteria.where("resultHash").`is`(resultHash))
        mongoTemplate.remove(query, JPlagRunDetails::class.java)
    }

    private val log = KotlinLogging.logger {  }

    fun getCurrentlyProcessingSubmissions(resultHash: String): Set<String> {
        try {
            return stringRedisTemplate.opsForSet().members("processing:${resultHash}") ?: emptySet()
        } catch (e: Exception) {
            return emptySet()
        }
    }

    fun findByResultHash(resultHash: String): JPlagRunDetails? {
        val query = Query(Criteria.where("resultHash").`is`(resultHash))
        return mongoTemplate.findOne(query, JPlagRunDetails::class.java)
    }

    fun isProjectRunning(resultHash: String): Boolean {
        val jPlagRunDetails = jPlagRunDetailsRepository.findByResultHash(resultHash)
            ?: return false
        return !(jPlagRunDetails.isPaused == false && jPlagRunDetails.finished == true)
    }

    fun runPlagNew(
        configName: String,
        projectName: String,
        userName: String,
        firstRun: Boolean,
    ) {
        val studentProject = jPlagMongoService.getProjectForUserNameAndConfigNameAndProjectName(
            configName = configName,
            projectName = projectName,
            userName = userName
        )!!

        var resultHash = if (studentProject.resultHashes != null && studentProject.resultHashes.isNotEmpty()) {
            studentProject.resultHashes.firstOrNull { resultHash ->
                jPlagServiceCommunicator.checkJplagRunning(resultHash)?.get("running") == true
            }
        } else {
            null
        }


        if (resultHash == null) {
            resultHash = UUID.randomUUID().toString()
        }

        jPlagMongoService.addResultHashToProject(userName, configName, projectName, resultHash)

        val oldJPlagResources = jPlagMongoService.getOldResourcesByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName,
        ).map { resource -> JPlagResource(
            typeOfResource = TypeOfResource.OLD_SUBMISSION,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }
        val studentResources = jPlagMongoService.getStudentResourcesByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName
        ).map { resource -> JPlagResource(
            typeOfResource = TypeOfResource.STUDENTS,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }
        val whiteListResource = jPlagMongoService.getWhitelistResourceByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName
        )?.let { resource -> JPlagResource(
            typeOfResource = TypeOfResource.WHITELIST,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }

        var jPlagRun = jPlagMongoService.getPlagRunByResultHash(resultHash)
        val jplagResources = studentResources + oldJPlagResources + if (whiteListResource != null) listOf(whiteListResource) else emptyList()
        if (jPlagRun == null) {
            jPlagMongoService.saveJPlagRun(
                (projectName + "_${Date()}"),
                studentProject.suffixes.toSet(),
                studentProject.languages.toSet(),
                userName,
                jplagResources,
                projectName,
                configName,
                resultHash
            )
        }
        if (firstRun) {
            jPlagMongoService.undoFirstRunForStudentProjectForConfigAndUserName(
                configName = configName,
                projectName = projectName,
                userName = userName
            )
        }

        jPlagServiceCommunicator.runJPlag(
            jplagResources,
            studentProject.languages.toList(),
            resultHash,
            userName,
            studentProject.suffixes.toList()
        )

    }

//    fun runScheduledJplagForConfig(
//        configName: String,
//        projectName: String,
//        userName: String,
//        firstRun: Boolean,
//    ) {
//        val plagSuspendedTaskCollection = plagSuspendedTaskManager.findByConfigNameAndProjectNameAndUserName(
//            configName = configName,
//            projectName = projectName,
//            userName = userName
//        )
//        var isPausedRun = false
//        if (plagSuspendedTaskCollection != null) {
//            var jPlagRun = jPlagMongoService.getPlagRunById(plagSuspendedTaskCollection.plagRunId!!)
//            isPausedRun = jPlagRun?.isPaused == true
//        }
//        if (isProjectRunning(configName, projectName, userName) && !isPausedRun) {
//            log.warn { "JPlag is already running for project: $projectName with user: $userName. Skipping" }
//            throw IllegalArgumentException("JPlag is already running for project: $projectName with user: $userName. Skipping")
//            return
//        }
//        setProjectRunning(configName, projectName, userName, true)
//        val oldJPlagResources = jPlagMongoService.getOldResourcesByConfigNameAndProject(
//            configName = configName,
//            projectName = projectName,
//            userName = userName,
//        ).map { resource -> JPlagResource(
//            typeOfResource = TypeOfResource.OLD_SUBMISSION,
//            solutionPath = resource.path,
//            singleSolution = false,
//            resourceName = null,
//            solutionProvider = null,
//            exceptionMessage = resource.exceptionMessage
//        ) }
//        val studentResources = jPlagMongoService.getStudentResourcesByConfigNameAndProject(
//            configName = configName,
//            projectName = projectName,
//            userName = userName
//        ).map { resource -> JPlagResource(
//            typeOfResource = TypeOfResource.STUDENTS,
//            solutionPath = resource.path,
//            singleSolution = false,
//            resourceName = null,
//            solutionProvider = null,
//            exceptionMessage = resource.exceptionMessage
//        ) }
//        val whiteListResource = jPlagMongoService.getWhitelistResourceByConfigNameAndProject(
//            configName = configName,
//            projectName = projectName,
//            userName = userName
//        )?.let { resource -> JPlagResource(
//            typeOfResource = TypeOfResource.WHITELIST,
//            solutionPath = resource.path,
//            singleSolution = false,
//            resourceName = null,
//            solutionProvider = null,
//            exceptionMessage = resource.exceptionMessage
//        ) }
//
//        val studentProject = jPlagMongoService.getProjectForUserNameAndConfigNameAndProjectName(
//            configName = configName,
//            projectName = projectName,
//            userName = userName
//        )
//
//        log.info { "Running JPlag for project: $projectName with user: $userName.\n Found ${studentResources.size} this academic year solutions" +
//                " and ${oldJPlagResources.size} old student solutions." }
//
//        if (studentResources.isEmpty()) {
//            setProjectRunning(configName, projectName, userName, false)
//            throw IllegalArgumentException("No current year student solutions found for project: $projectName with user: $userName")
//        }
//        val jplagResources = studentResources + oldJPlagResources + if (whiteListResource != null) listOf(whiteListResource) else emptyList()
//        if (plagSuspendedTaskCollection == null) {
//            val resultName = (projectName + "_${Date()}")
//            var jPlagRun = jPlagMongoService.saveJPlagRun(
//                resultName,
//                studentProject?.suffixes?.toSet(),
//                studentProject!!.languages.toSet(),
//                userName,
//                jplagResources,
//                projectName,
//                configName
//            )
//            if (firstRun) {
//                jPlagMongoService.undoFirstRunForStudentProjectForConfigAndUserName(
//                    configName = configName,
//                    projectName = projectName,
//                    userName = userName
//                )
//            }
//            val jPlagLanguages = getJplagLanguagesForProject(studentProject)
//            val multiLanguageOptions = MultiLanguageOptions()
//            multiLanguageOptions.languages = jPlagLanguages
//            val languages = listOf(MultiLanguage(multiLanguageOptions))
//            val plagSuspendedTaskCollection = plagSuspendedTaskManager.savePlagSuspendedTask(PlagSuspendedTask(
//                id = null,
//                configName = configName,
//                projectName = projectName,
//                userName = userName,
//                plagRunId = jPlagRun.id!!,
//                languageToVariables = mutableMapOf()
//            ))
//            val canceledDetected = AtomicBoolean(false)
//            val futures = languages.map {
//                language ->
//                CompletableFuture.supplyAsync(
//                    JPlagConfigTaskSupplier(
//                        configName = configName,
//                        projectName = projectName,
//                        userName = userName,
//                        resources = jplagResources,
//                        language = language,
//                        suffixes = studentProject.suffixes.toList(),
//                        plagSuspendedTask = plagSuspendedTaskManager.toDomain(plagSuspendedTaskCollection),
//                        plagSuspendedTaskManager = plagSuspendedTaskManager,
//                        jPlagMongoService = jPlagMongoService,
//                        jPlagRun = jPlagRun,
//                        jPlagExecutorService = jPlagExecutorService,
//                    ), jPlagExecutorService.jPlagExecutorService()
//                ).handle { result, throwable ->
//                    if (throwable != null) {
//                        when (throwable.cause) {
//                            is CancelPlagException -> {
//                                log.warn("JPlag task canceled: ${throwable.message}")
//                                jPlagRun = jPlagRun.copy(isPaused = true)
//                                canceledDetected.compareAndSet(false, true)
//                            }
//                            else -> {
//                                log.error("Error processing JPlag task: ${throwable.message}")
//                                setProjectRunning(configName, projectName, userName, false)
//                                resetStepStatusesForPlagRun(jPlagRun.id!!)
//                                jPlagRun = jPlagRun.copy(error = "Pogreška prilikom provjere plagijata: ${throwable.message}")
//                                jPlagMongoService.updateJplagRun(jPlagRun, jPlagRun.error)
//                                plagSuspendedTaskManager.deletePlagSuspendedTask(plagSuspendedTaskCollection.id!!)
//                            }
//                        }
//                        null
//                    } else {
//                        result
//                    }
//                }
//            }
//            CompletableFuture.allOf(*futures.toTypedArray()).thenRunAsync {
//                if (canceledDetected.get()) {
//                } else {
//                    val results = futures.mapNotNull { it.get() }
//                    saveJPlagResult(results.map { it.first }, resultName, userName)
//                    setProjectRunning(configName, projectName, userName, false)
//                    jPlagMongoService.updateJplagRun(results[0].second, jPlagRun.error)
//                    resetStepStatusesForPlagRun(jPlagRun.id!!)
//                    plagSuspendedTaskManager.deletePlagSuspendedTask(plagSuspendedTaskCollection.id!!)
//                }
//            }
//        } else {
//            val jPlagLanguages = getJplagLanguagesForProject(studentProject!!)
//            val multiLanguageOptions = MultiLanguageOptions()
//            var jPlagRun = jPlagMongoService.getPlagRunById(plagSuspendedTaskCollection.plagRunId!!)!!
//            multiLanguageOptions.languages = jPlagLanguages
//            val languages = listOf(MultiLanguage(multiLanguageOptions))
//            val canceledDetected = AtomicBoolean(false)
//            val futures = languages.map {
//                    language ->
//                CompletableFuture.supplyAsync(
//                    JPlagConfigTaskSupplier(
//                        configName = configName,
//                        projectName = projectName,
//                        userName = userName,
//                        resources = jplagResources,
//                        language = language,
//                        suffixes = studentProject.suffixes.toList(),
//                        plagSuspendedTask = plagSuspendedTaskManager.toDomain(plagSuspendedTaskCollection),
//                        plagSuspendedTaskManager = plagSuspendedTaskManager,
//                        jPlagMongoService = jPlagMongoService,
//                        jPlagRun = jPlagRun,
//                        jPlagExecutorService = jPlagExecutorService,
//                    ), jPlagExecutorService.jPlagExecutorService()
//                ).handle { result, throwable ->
//                    if (throwable != null) {
//                        when (throwable) {
//                            is CancelPlagException -> {
//                                log.warn("JPlag task canceled: ${throwable.message}")
//                                jPlagRun = jPlagRun.copy(isPaused = true)
//                                canceledDetected.compareAndSet(false, true)
//                            }
//                            else -> {
//                                log.error("Error processing JPlag task: ${throwable.message}")
//                                setProjectRunning(configName, projectName, userName, false)
//                                resetStepStatusesForPlagRun(jPlagRun.id!!)
//                                jPlagRun = jPlagRun.copy(error = "Pogreška prilikom provjere plagijata: ${throwable.message}")
//                                jPlagMongoService.updateJplagRun(jPlagRun, jPlagRun.error)
//                                plagSuspendedTaskManager.deletePlagSuspendedTask(plagSuspendedTaskCollection.id!!)
//                            }
//                        }
//                        null
//                    } else {
//                        result
//                    }
//                }
//            }
//            CompletableFuture.allOf(*futures.toTypedArray()).thenRunAsync {
//                if (canceledDetected.get()) {
//                } else {
//                    val results = futures.mapNotNull { it.get() }
//                    saveJPlagResult(results.map { it.first }, jPlagRun.resultName, userName)
//                    jPlagMongoService.updateJplagRun(results[0].second, jPlagRun.error)
//                    resetStepStatusesForPlagRun(jPlagRun.id!!)
//                    plagSuspendedTaskManager.deletePlagSuspendedTask(plagSuspendedTaskCollection.id!!)
//                    setProjectRunning(configName, projectName, userName, false)
//                }
//            }
//        }
//    }

    private fun getJplagLanguagesForProject(studentProject: StudentProject): List<Language> {
        var jPlagLanguages = studentProject.languages.map { languageToLanguageObject(it.lowercase(), studentProject.suffixes.toList()) }
        val suffixes = studentProject.languages.flatMap { getLanguageSuffixes(it) }
        val remainingSuffixes = studentProject.suffixes.filter { !suffixes.contains(it) }
        jPlagLanguages = jPlagLanguages + CustomNaturalLanguage(remainingSuffixes)
        return jPlagLanguages
    }

    fun runJplagForConfig(
        configName: String,
        projectName: String,
        userName: String,
        firstRun: Boolean,
        manualRun: Boolean = false
    ) {
        if (isProjectRunning(configName, projectName, userName)) {
            log.warn { "JPlag is already running for project: $projectName with user: $userName. Skipping" }
            throw IllegalArgumentException("JPlag is already running for project: $projectName with user: $userName. Skipping")
            return
        }
        setProjectRunning(configName, projectName, userName, true)
        val oldJPlagResources = jPlagMongoService.getOldResourcesByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName,
        ).map { resource -> JPlagResource(
            typeOfResource = TypeOfResource.OLD_SUBMISSION,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }
        val studentResources = jPlagMongoService.getStudentResourcesByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName
        ).map { resource -> JPlagResource(
            typeOfResource = TypeOfResource.STUDENTS,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }
        val whiteListResource = jPlagMongoService.getWhitelistResourceByConfigNameAndProject(
            configName = configName,
            projectName = projectName,
            userName = userName
        )?.let { resource -> JPlagResource(
            typeOfResource = TypeOfResource.WHITELIST,
            solutionPath = resource.path,
            singleSolution = false,
            resourceName = null,
            solutionProvider = null,
            exceptionMessage = resource.exceptionMessage,
            edgarHash = resource.edgarHash
        ) }

        val studentProject = jPlagMongoService.getProjectForUserNameAndConfigNameAndProjectName(
            configName = configName,
            projectName = projectName,
            userName = userName
        )

        log.info { "Running JPlag for project: $projectName with user: $userName.\n Found ${studentResources.size} this academic year solutions" +
                " and ${oldJPlagResources.size} old student solutions." }

        if (studentResources.isEmpty()) {
            setProjectRunning(configName, projectName, userName, false)
            throw IllegalArgumentException("No current year student solutions found for project: $projectName with user: $userName")
        }
        runJplag(
            resources = studentResources + oldJPlagResources + if (whiteListResource != null) listOf(whiteListResource) else emptyList(),
            languages = studentProject!!.languages.toList(),
            resultName = if (!manualRun) (projectName + "_${Date()}") else (projectName + "_${Date()} manual"),
            userName = userName,
            suffixes = studentProject.suffixes.toList(),
            projectName = projectName,
            firstRun = firstRun,
            configName = configName
        )

    }

    fun runJplag(
        resources: List<JPlagResource>,
        languages: List<String>,
        resultName: String,
        userName: String,
        suffixes: List<String>?,
        projectName: String? = null,
        configName: String? = null,
        firstRun: Boolean? = null
    ) {
        verifyRunRequest(resources, languages)
        var jPlagRun = jPlagMongoService.saveJPlagRun(resultName, suffixes?.toSet(), languages.toSet(), userName, resources, projectName, configName, "")
        if (configName == null && projectName == null) {
            currentlyRunningPlagProjects[jPlagRun.id!!] = true
        }
        if (firstRun == true) {
            jPlagMongoService.undoFirstRunForStudentProjectForConfigAndUserName(
                configName = configName!!,
                projectName = projectName!!,
                userName = userName
            )
        }
        val jPlagLanguages = languages.map { languageToLanguageObject(it.lowercase(), suffixes) }
        val multiLanguageOptions = MultiLanguageOptions()
        multiLanguageOptions.languages = jPlagLanguages
        val languages = listOf(MultiLanguage(multiLanguageOptions))
        val whitelistFile = getWhitelistFile(resources, userName)
        val solutionPaths = getSolutionPaths(resources, userName).minus(whitelistFile)
        val oldSubmissionsPaths = getOldSubmissionsPaths(resources, userName).minus(whitelistFile).minus(solutionPaths)

        val futures = languages.mapIndexed { index, jPlagLanguage ->
            CompletableFuture.supplyAsync(JPlagTaskSupplier(
                jPlagLanguage,
                solutionPaths,
                whitelistFile,
                oldSubmissionsPaths,
                suffixes ?: emptyList(),
                jPlagRun,
                jPlagMongoService,
                jPlagExecutorService
            ), jPlagExecutorService.jPlagExecutorService()).handle { result, throwable ->
                if (throwable != null) {
                    log.error("Error processing JPlag task: ${throwable.message}")
                    jPlagRun = jPlagRun.copy(error = "Pogreška prilikom provjere plagijata: ${throwable.message}")
                    jPlagMongoService.updateJplagRun(jPlagRun, jPlagRun.error)
                    if (configName != null && projectName != null) {
                        setProjectRunning(configName, projectName, userName, false)
                    }
                    null
                } else {
                    result
                }
            }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).thenRunAsync {
            val results = futures.mapNotNull { it.get() }
            saveJPlagResult(results, resultName, userName)
            jPlagMongoService.updateJplagRun(jPlagRun, jPlagRun.error)
            if (projectName != null && configName != null) {
                setProjectRunning(configName, projectName, userName, false)
            } else {
                currentlyRunningPlagProjects.remove(jPlagRun.id!!)
            }
        }
    }

    private fun saveJPlagResult(results: List<JPlagResult>, resultName: String, userName: String) {
        val topComparisons = results.flatMap {
            getTopComparisons(it)
        }

        val mergedComparisons = mergeTopComparisons(topComparisons)

        val submissionToIdFunction = getSubmissionToIdFunction()
        val clustersMerged = results.filter { it.clusteringResult != null }.flatMap {
            ClusteringResultMapper(submissionToIdFunction).map(it)
        }.toSet()
        val submissionComparisons = results.flatMap { result ->
            val singleResultJPlagComparisons = result.allComparisons
            singleResultJPlagComparisons.map { generateComparisonReportForComparison(it, submissionToIdFunction, userName, resultName) }
        }
        val mergedSubmissionComparisons = mergeSubmissionComparisons(submissionComparisons, userName)

        val submissionFileIndexes = results.map { getSubmissionIndexFile(it) }
        val mergedSubmissionFileIndexes = mergeSubmissionIndexFile(submissionFileIndexes)
        val submissionFileIndex = SubmissionFileIndex(mergedSubmissionFileIndexes)

        jPlagMongoService.saveResult(mergedComparisons.toSet(), clustersMerged, mergedSubmissionComparisons, submissionFileIndex, resultName, userName)
    }

    private fun getSubmissionIndexFile(result: JPlagResult): Map<String, Map<String, SubmissionFile>> {
        val comparisons = result.allComparisons
        val submissions = getSubmissionsForComparisons(comparisons)
        val fileIndexes: MutableMap<String, Map<String, SubmissionFile>> = HashMap()

        val submissionTokenCountList = submissions.parallelStream().map { submission ->
            val tokenCounts = submission.tokenCountPerFile.map { (key1, value) ->
                val key = FilePathUtil.getRelativeSubmissionPath(key1, submission, getSubmissionToIdFunction())
                    .toString()
                    .replace(".", "[dot]")
                key to SubmissionFile(value!!)
            }.toMap()

            mapOf(submission.name.substringAfter("/") to tokenCounts)
        }.toList()

        submissionTokenCountList.forEach {
            fileIndexes.putAll(it)
        }

        return fileIndexes
    }

    private fun mergeSubmissionIndexFile(submissionIndexFiles: List<Map<String, Map<String, SubmissionFile>>>): Map<String, Map<String, SubmissionFile>> {
        val mergedSubmissionIndexFiles: MutableMap<String, MutableMap<String, SubmissionFile>> = HashMap()
        submissionIndexFiles.forEach { submissionIndexFile ->
            submissionIndexFile.forEach { (submissionName, submissionFiles) ->
                val mergedSubmissionFiles = mergedSubmissionIndexFiles.getOrPut(submissionName) { HashMap() }
                submissionFiles.forEach { (fileName, submissionFile) ->
                    mergedSubmissionFiles[fileName] = submissionFile
                }
            }
        }
        return mergedSubmissionIndexFiles
    }

    private fun getSubmissionsForComparisons(comparisons: List<JPlagComparison>) =
        comparisons.map { it.firstSubmission }.toSet() + comparisons.map { it.secondSubmission }.toSet()

    private fun getSubmissionNameToIdMap(result: JPlagResult) =
        SubmissionNameToIdMapper.buildSubmissionNameToIdMap(result)

    private fun getSubmissionToIdFunction() =
        Function { submission: Submission ->
            if (submission.name.contains("/")) {
                submission.name.substringAfterLast("/")
            } else {
                submission.name
            }
        }

    private fun generateComparisonReportForComparison(comparison: JPlagComparison, submissionToIdFunction: Function<Submission, String>, userName: String, resultName: String): SubmissionComparison {
        val firstSubmissionId = submissionToIdFunction.apply(comparison.firstSubmission())
        val secondSubmissionId = submissionToIdFunction.apply(comparison.secondSubmission())
        val similarities = mapOf(
            "MAX" to comparison.maximalSimilarity(),
            "AVG" to comparison.similarity()
        )

        return SubmissionComparison(
            firstSubmissionId = firstSubmissionId,
            secondSubmissionId = secondSubmissionId,
            similarities = similarities,
            matches = convertMatchesToReportMatches(comparison, submissionToIdFunction),
            firstSimilarity = comparison.similarityOfFirst(),
            secondSimilarity = comparison.similarityOfSecond(),
            firstSubmissionPath = comparison.firstSubmission().root.absolutePath,
            secondSubmissionPath = comparison.secondSubmission().root.absolutePath,
            userName = userName,
            resultName = resultName,
            searchKey = "$firstSubmissionId-$secondSubmissionId"
        )
    }

    private fun convertMatchesToReportMatches(comparison: JPlagComparison, submissionToIdFunction: Function<Submission, String>) =
        comparison.matches.map { convertMatchToReportMatch(comparison, it, submissionToIdFunction) }

    private fun convertMatchToReportMatch(comparison: JPlagComparison, match: de.jplag.Match, submissionToIdFunction: Function<Submission, String>): Match {
        val tokensFirst: List<Token> = comparison.firstSubmission().tokenList.subList(match.startOfFirst(), match.endOfFirst() + 1)
        val tokensSecond: List<Token> = comparison.secondSubmission().tokenList.subList(match.startOfSecond(), match.endOfSecond() + 1)
        val lineStartComparator: Comparator<in Token> = Comparator.comparingInt { obj: Token -> obj.line }
            .thenComparingInt { obj: Token -> obj.column }
        val lineEndComparator: Comparator<in Token> = Comparator.comparingInt { obj: Token -> obj.line }
            .thenComparingInt { t: Token -> t.column + t.length }

        val startOfFirst = tokensFirst.stream().min(lineStartComparator).orElseThrow()
        val endOfFirst = tokensFirst.stream().max(lineEndComparator).orElseThrow()
        val startOfSecond = tokensSecond.stream().min(lineStartComparator).orElseThrow()
        val endOfSecond = tokensSecond.stream().max(lineEndComparator).orElseThrow()

        val firstFileName = FilePathUtil.getRelativeSubmissionPath(
            startOfFirst.file,
            comparison.firstSubmission(),
            submissionToIdFunction
        ).toString()
        val secondFileName = FilePathUtil.getRelativeSubmissionPath(
            startOfSecond.file,
            comparison.secondSubmission(),
            submissionToIdFunction
        ).toString()

        val startInFirst = CodePosition(startOfFirst.line, startOfFirst.column - 1, match.startOfFirst())
        val endInFirst = CodePosition(endOfFirst.line, endOfFirst.column + endOfFirst.length - 1, match.endOfFirst())

        val startInSecond = CodePosition(startOfSecond.line, startOfSecond.column - 1, match.startOfSecond())
        val endInSecond = CodePosition(
            endOfSecond.line, endOfSecond.column + endOfSecond.length - 1,
            match.endOfSecond()
        )
        return Match(
            firstFileName,
            secondFileName,
            startInFirst,
            endInFirst,
            startInSecond,
            endInSecond,
            match.length()
        )
    }

    private fun mergeSubmissionComparisons(submissionComparisons: List<SubmissionComparison>, userName: String): List<SubmissionComparison> {
        val groupedByIds = submissionComparisons.groupBy { submissionComparison ->
            setOf(submissionComparison.firstSubmissionId, submissionComparison.secondSubmissionId)
        }
        return groupedByIds.map { (_, comparisons) ->
            val max = comparisons.map { it.similarities["MAX"] ?: 0.0 }.average()
            val avg = comparisons.map { it.similarities["AVG"] ?: 0.0 }.average()
            val matches = comparisons.flatMap { it.matches }
            val firstSimilarity = comparisons.map { it.firstSimilarity }.average()
            val secondSimilarity = comparisons.map { it.secondSimilarity }.average()
            SubmissionComparison(
                firstSubmissionId = comparisons.first().firstSubmissionId,
                secondSubmissionId = comparisons.first().secondSubmissionId,
                similarities = mapOf("MAX" to max, "AVG" to avg),
                matches = matches,
                firstSimilarity = firstSimilarity,
                secondSimilarity = secondSimilarity,
                firstSubmissionPath = findRealResourceLocation(comparisons.first().firstSubmissionPath, userName),
                secondSubmissionPath = findRealResourceLocation(comparisons.first().secondSubmissionPath, userName),
                userName = comparisons.first().userName,
                resultName = comparisons.first().resultName,
                searchKey = "${comparisons.first().firstSubmissionId}-${comparisons.first().secondSubmissionId}"
            )
        }
    }

    private fun findRealResourceLocation(path: String, userName: String): String {
        if (!path.contains(TEMP_RESOURCES_PATH)) {
            return path
        }
        val tempSegment = "/temp/$userName/"
        val localSolutionsPath = path.replace(tempSegment, "/$userName/solutions/local/")
        val gitSolutionsPath = path.replace(tempSegment, "/$userName/solutions/git/")
        return if (File(localSolutionsPath).exists()) {
            localSolutionsPath
        } else {
            gitSolutionsPath
        }
    }

    private fun mergeTopComparisons(comparisons: List<TopComparison>): List<TopComparison> {
        val groupedByNames = comparisons.groupBy { comparison ->
            setOf(comparison.firstSubmission, comparison.secondSubmission)
        }
        return groupedByNames.map { (_, comparisons) ->
            val max = comparisons.map { it.similarities["MAX"] ?: 0.0 }.average()
            val avg = comparisons.map { it.similarities["AVG"] ?: 0.0 }.average()
            TopComparison(
                comparisons.first().firstSubmission,
                comparisons.first().secondSubmission,
                mapOf("MAX" to max, "AVG" to avg)
            )
        }
    }

    private fun getTopComparisons(result: JPlagResult): List<TopComparison> {
        return MetricMapper(getSubmissionToIdFunction()).getTopComparisons(result)
    }

    private fun getDate(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yy")
        val date = Date()
        return dateFormat.format(date)
    }

    private fun verifyRunRequest(solutions: List<JPlagResource>, languages: List<String>) {
        solutions.forEach {
            if ((it.solutionProvider == null || it.resourceName == null) && it.solutionPath == null) {
                throw IllegalArgumentException("You have to either specify solution provider and resource name or solution path!")
            }
            if (it.solutionProvider != null && !setOf(SolutionProviderName.GIT, SolutionProviderName.LOCAL).contains(it.solutionProvider)) {
                throw IllegalArgumentException("Solution provider not supported; can only be git or local!")
            }
        }

        languages.forEach {
            if (!ALLOWED_LANGUAGES.contains(it.lowercase())) {
                throw IllegalArgumentException("Language not supported!")
            }
        }
    }

    private fun deleteTempSolutions(userName: String, uuid: String) {
        val tempDir = File("$TEMP_RESOURCES_PATH/$userName/$uuid/")
        tempDir.deleteRecursively()
    }

    companion object {
        fun languageToLanguageObject(language: String, suffixes: List<String>?): Language {
            return when (language) {
                "java" -> JavaLanguage()
                "c" -> CLanguage()
                "cpp" -> CPPLanguage()
                "python" -> PythonLanguage()
                "javascript" -> JavaScriptLanguage()
                "typescript" -> TypeScriptLanguage()
                "sql", "csymple", "text" -> CustomNaturalLanguage(suffixes ?: emptyList())
                else -> throw IllegalArgumentException("Language not supported")
            }
        }

        fun getLanguageSuffixes(language: String): List<String> {
            val language = languageToLanguageObject(language.lowercase(), null)
            return language.suffixes().toList()
        }

        private fun mapSolutionToFile(solution: JPlagResource, userName: String): File {
            return if (solution.solutionPath == null) {
                val file = if (solution.solutionProvider == SolutionProviderName.GIT) {
                    FileUtils.getGitRepoAsFile(solution.resourceName!!, userName)
                } else {
                    FileUtils.getZipRepoAsFile(solution.resourceName!!, userName)
                }
                if (solution.singleSolution) {
                    if (solution.resourceName.endsWith("$")) {
                        copySingleSolutionToTempDir(file, userName, solution.resourceName)
                    } else {
                        copySingleSolutionToTempDir(file, userName, solution.resourceName + "$")
                    }
                } else {
                    file
                }
            } else {
                if (solution.singleSolution) {
                    val resourceName = solution.solutionPath.substringBeforeLast("/").substringAfterLast("/")
                    if (resourceName.endsWith("$")) {
                        copySingleSolutionToTempDir(File("${RESOURCES_PATH}/${solution.solutionPath}"), userName, resourceName)
                    } else {
                        copySingleSolutionToTempDir(File("${RESOURCES_PATH}/${solution.solutionPath}"), userName,
                            "$resourceName$"
                        )
                    }
                } else {
                    val fileFullPath = File("${RESOURCES_PATH}/${solution.solutionPath}")
                    if (fileFullPath.exists()) {
                        fileFullPath
                    } else {
                        File("${solution.solutionPath}")
                    }
                }
            }
        }

        private fun copySingleSolutionToTempDir(solution: File, userName: String, uuid: String): File {
            val tempSolutionDir = File("$TEMP_RESOURCES_PATH/$userName/$uuid/${solution.name}/")
            tempSolutionDir.mkdirs()

            if (solution.isDirectory) {
                solution.listFiles()?.forEach { file ->
                    val targetFile = File(tempSolutionDir, file.name)
                    file.copyRecursively(targetFile, overwrite = true)
                }
            } else {
                solution.copyTo(tempSolutionDir, overwrite = true)
            }

            return File("$TEMP_RESOURCES_PATH/$userName/$uuid/")
        }

        fun getWhitelistFile(solutions: List<JPlagResource>, userName: String): File? {
            return solutions.filter { it.typeOfResource == TypeOfResource.WHITELIST }.firstNotNullOfOrNull { solution ->
                if (solution.exceptionMessage == null)
                    mapSolutionToFile(solution, userName)
                else null
            }.takeIf { it?.length() != 0L }
        }

        fun getOldSubmissionsPaths(solutions: List<JPlagResource>, userName: String): Set<File> {
            return solutions.filter { it.typeOfResource == TypeOfResource.OLD_SUBMISSION  }.map { solution ->
                mapSolutionToFile(solution, userName)
            }.toSet()
        }

        fun getSolutionPaths(solutions: List<JPlagResource>, userName: String): Set<File> {
            return solutions.filter { it.typeOfResource == TypeOfResource.STUDENTS }.map { solution ->
                mapSolutionToFile(solution, userName)
            }.toSet()
        }

        private val currentlyRunningPlagProjects = ConcurrentHashMap<String, Boolean>()
        private val currentlyRunningNormalizations = ConcurrentHashMap<String, AtomicInteger>()
        private val currentlyRunningParsings = ConcurrentHashMap<String, AtomicInteger>()
        private val currentlyRunningComparisons = ConcurrentHashMap<String, AtomicInteger>()
        private val currentlyProcessingSubmissions = ConcurrentHashMap<String, Set<String>>()

        fun getCurrentlyProcessingSubmissionsForRun(
            plagRunId: String
        ) = currentlyProcessingSubmissions[plagRunId] ?: emptyList()

        fun addCurrentlyProcessingSubmissionsForRun(
            plagRunId: String,
            submissionName: String
        ) {
            currentlyProcessingSubmissions.compute(plagRunId) { _, oldValue ->
                if (oldValue == null) setOf(submissionName) else {
                    oldValue + submissionName
                }
            }
        }

        fun removeCurrentlyProcessingSubmissionsForRun(
            plagRunId: String,
            submissionName: String
        ) {
            currentlyProcessingSubmissions.compute(plagRunId) { _, oldValue ->
                if (oldValue == null) emptySet() else {
                    oldValue - submissionName
                }
            }
        }

        fun resetStepStatusesForPlagRun(
            plagRunId: String,
        ) {
            currentlyRunningNormalizations.remove(plagRunId)
            currentlyRunningParsings.remove(plagRunId)
            currentlyRunningComparisons.remove(plagRunId)
        }

        fun getStepStatus(
            step: String,
            plagRunId: String,
        ): Int {
            return when (step) {
                "parsing" -> currentlyRunningParsings[plagRunId]?.get() ?: 0
                "normalization"-> currentlyRunningNormalizations[plagRunId]?.get() ?: 0
                "comparison" -> currentlyRunningComparisons[plagRunId]?.get() ?: 0
                else -> 0
            }
        }

        fun incrementStepStatus(
            step: String,
            plagRunId: String,
            opis: String
        ) {
            val map = when (step) {
                "parsing" -> currentlyRunningParsings
                "normalization" -> currentlyRunningNormalizations
                "comparison" -> currentlyRunningComparisons
                else -> throw IllegalArgumentException("Invalid step")
            }
            map.compute(plagRunId) { _, oldValue ->
                if (oldValue == null) AtomicInteger(1) else {
                    oldValue.incrementAndGet()
                    oldValue
                }
            }
        }


        fun getRunningProjectKey(
            configName: String,
            projectName: String,
            userName: String,
        ) = "$projectName-$userName-$configName"

        fun isProjectRunning(
            configName: String,
            projectName: String,
            userName: String,
        ): Boolean {
            return currentlyRunningPlagProjects[getRunningProjectKey(configName, projectName, userName)] ?: false
        }

        fun isBasicPlagRunning(
            jPlagRunId: String
        ) = currentlyRunningPlagProjects[jPlagRunId] ?: false

        fun setProjectRunning(
            configName: String,
            projectName: String,
            userName: String,
            isRunning: Boolean
        ) {
            if (isRunning) {
                currentlyRunningPlagProjects[getRunningProjectKey(configName, projectName, userName)] = isRunning
            } else {
                currentlyRunningPlagProjects.remove(getRunningProjectKey(configName, projectName, userName))
            }
        }
    }
}

const val ALLOWED_LANGUAGES = "java, c, cpp, python, javascript, typescript, sql, csymple, text, multi-language"
