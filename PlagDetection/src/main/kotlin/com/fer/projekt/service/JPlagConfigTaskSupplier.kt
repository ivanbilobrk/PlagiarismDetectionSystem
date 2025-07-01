package com.fer.projekt.service

import com.fer.projekt.CancelPlagException
import com.fer.projekt.config.JPlagExecutorServiceConfig
import com.fer.projekt.config.PlagSuspendedTaskManager
import com.fer.projekt.controller.JPlagResource
import com.fer.projekt.models.FailedToCompareSubmissions
import com.fer.projekt.models.FailedToParseSubmission
import com.fer.projekt.models.JPlagRun
import com.fer.projekt.models.PlagSuspendedTask
import com.fer.projekt.service.JPlagService.Companion.getOldSubmissionsPaths
import com.fer.projekt.service.JPlagService.Companion.getSolutionPaths
import com.fer.projekt.service.JPlagService.Companion.getWhitelistFile
import com.fer.projekt.service.JPlagTaskSupplier.Companion.buildComparisonTuples
import com.fer.projekt.service.JPlagTaskSupplier.Companion.compareTwoSubmissions
import com.fer.projekt.service.JPlagTaskSupplier.Companion.handleBaseCode
import de.jplag.*
import de.jplag.clustering.ClusteringFactory
import de.jplag.comparison.GreedyStringTiling
import de.jplag.comparison.TokenValueMapper
import de.jplag.exceptions.ExitException
import de.jplag.exceptions.SubmissionException
import de.jplag.options.JPlagOptions
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class JPlagConfigTaskSupplier(
    val configName: String,
    val projectName: String,
    val userName: String,
    val resources: List<JPlagResource>,
    val language: Language,
    val suffixes: List<String>,
    val plagSuspendedTask: PlagSuspendedTask,
    val plagSuspendedTaskManager: PlagSuspendedTaskManager,
    val jPlagMongoService: JPlagMongoService,
    var jPlagRun: JPlagRun,
    val jPlagExecutorService: JPlagExecutorServiceConfig
): Supplier<Pair<JPlagResult, JPlagRun>> {

    val TIMEOUT = 5L
    val log = KotlinLogging.logger {}
    private val failedToParseSubmissions: MutableSet<FailedToParseSubmission> = Collections.synchronizedSet(mutableSetOf())
    private val failedToCompareSubmissions: MutableSet<FailedToCompareSubmissions> = Collections.synchronizedSet(mutableSetOf())
    private val comparisonsCompleted: MutableSet<JPlagComparison> = Collections.synchronizedSet(mutableSetOf())
    private val comparedSubmissions: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())


    override fun get(): Pair<JPlagResult, JPlagRun>  {
        val whitelistFile = getWhitelistFile(resources, userName)
        val solutionPaths = getSolutionPaths(resources, userName).minus(whitelistFile)
        val oldSubmissionsPaths = getOldSubmissionsPaths(resources, userName).minus(whitelistFile).minus(solutionPaths)
        var jPlagOptions = JPlagOptions(language, solutionPaths, emptySet())
            //.withFileSuffixes(suffixes)
            .withNormalize(true)
        if (whitelistFile != null) {
            jPlagOptions = jPlagOptions.withBaseCodeSubmissionDirectory(whitelistFile)
        }
        if (oldSubmissionsPaths.isNotEmpty()) {
            jPlagOptions = jPlagOptions.withOldSubmissionDirectories(oldSubmissionsPaths)
        }

        var jPlagRunVariables = plagSuspendedTask.languageToVariables["multi-language"]
        var submissionSet: SubmissionSet
        if (jPlagRunVariables == null) {
            val builder = SubmissionSetBuilder(jPlagOptions)
            submissionSet = builder.buildSubmissionSet()
            jPlagRun = jPlagMongoService.updateTotalNumbersForRun(
                jPlagRunOld = jPlagRun,
                comparisonTotal = null,
                submissionTotal = submissionSet.submissions.size + if (whitelistFile != null) 1 else 0
            )
            if (!(jPlagOptions.normalize() && jPlagOptions.language().supportsNormalization() && jPlagOptions.language()
                    .requiresCoreNormalization()
            )) {
                if (whitelistFile != null) {
                    submissionSet.baseCode.stepState = SubmissionStepState.NORMALIZED
                    JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje5")
                }
                submissionSet.submissions.forEach { submission ->
                    submission.stepState = SubmissionStepState.NORMALIZED
                    JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje6")
                }
            }
        } else {
            submissionSet = SubmissionSet(
                jPlagRunVariables.submissions.map { plagSuspendedTaskManager.customSubmissionToSubmission(it, suffixes, language) },
                jPlagRunVariables.baseCode?.let { plagSuspendedTaskManager.customSubmissionToSubmission(it, suffixes, language) },
                jPlagOptions
            )
            comparisonsCompleted.addAll(jPlagRunVariables.comparisons.map { plagSuspendedTaskManager.customJPlagComparisonToJPlagComparison(it, suffixes, language) })
            comparedSubmissions.addAll(jPlagRunVariables.comparedSubmissions)
            failedToParseSubmissions.addAll(jPlagRun.failedToParseSubmissions ?: emptyList())
            failedToCompareSubmissions.addAll(jPlagRun.failedToCompareSubmissions ?: emptyList())
        }

        val submissionCount = submissionSet.numberOfSubmissions()
        if (submissionCount < 2) {
            throw IllegalArgumentException("You have submitted less than 2 solutions! Please select at least 2 solutions to compare.")
        }

        if (jPlagOptions.normalize() && jPlagOptions.language().supportsNormalization() && jPlagOptions.language()
            .requiresCoreNormalization()) {
            normalizeSubmissions(submissionSet.submissions, if (whitelistFile != null) submissionSet.baseCode else null)
        }

        if (whitelistFile != null && submissionSet.baseCode != null && submissionSet.baseCode.stepState == SubmissionStepState.NORMALIZED) {
            JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
            try {
                parseSubmission(submissionSet.baseCode, jPlagOptions.normalize, jPlagOptions.minimumTokenMatch, jPlagOptions.language)
            } catch(e: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(submissionSet.baseCode.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje predaje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
                submissionSet.baseCode.stepState = SubmissionStepState.ERROR_PARSING
                throw IllegalArgumentException("Provided base code submission cannot be parsed!")
            }

            JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
            submissionSet.baseCode.stepState = SubmissionStepState.PARSED
            JPlagService.incrementStepStatus("parsing", jPlagRun.id!!, "ovdje7")
        }


        parseSubmissions(submissionSet.submissions, jPlagOptions, if (whitelistFile != null) submissionSet.baseCode else null)
        //plagSuspendedTaskManager.saveSubmissions(submissionSet.submissions, submissionSet.baseCode, plagSuspendedTask, language.name, comparisonsCompleted, comparedSubmissions)
        val result = compareSubmissions(submissionSet, jPlagOptions, whitelistFile)
        jPlagRun = jPlagRun.copy(isPaused = false)
        jPlagRun = jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
        jPlagRun = jPlagMongoService.updateFailedToCompareSubmissions(jPlagRun.id!!, failedToCompareSubmissions)
        return result to jPlagRun
    }

    fun normalizeSubmissions(
        submissions: List<Submission>,
        baseCode: Submission?
    ) {
        if (baseCode != null && baseCode.stepState == SubmissionStepState.CREATED) {
            try {
                JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, baseCode.name)
                baseCode.normalize()
                baseCode.stepState = SubmissionStepState.NORMALIZED
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, baseCode.name)
                JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje8")
            } catch (ex: Exception) {
                baseCode.stepState = SubmissionStepState.ERROR_NORMALIZATION
                failedToParseSubmissions.add(FailedToParseSubmission(baseCode.name.substringAfterLast("/"), ex.message ?: "Neuspješno normaliziranje."))
                jPlagRun = jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
                plagSuspendedTaskManager.saveSubmissions(submissions.toSet(), baseCode, plagSuspendedTask, language.name, comparisonsCompleted, comparedSubmissions)
                throw ex
            }
        }
        val submissionsForNormalizing = submissions.filter { it.stepState == SubmissionStepState.CREATED }
        val futures = submissionsForNormalizing.mapNotNull { submission ->
            if (submission.stepState == SubmissionStepState.CREATED) {
                CompletableFuture.supplyAsync({
                    JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                    try {
                        if (CancellationManager.isCancelled()) {
                            CancellationManager.saveStateOnceForSupplier(jPlagRun.id!!) {
                                plagSuspendedTaskManager.saveSubmissions(submissions.toSet(), baseCode, plagSuspendedTask, language.name, comparisonsCompleted, comparedSubmissions)
                                jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
                                jPlagMongoService.updateFailedToCompareSubmissions(jPlagRun.id!!, failedToCompareSubmissions)
                            }
                            throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                        }
                        submission.normalize()
                        submission.stepState = SubmissionStepState.NORMALIZED
                        JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje9")
                        true
                    } catch (e: Exception) {
                        failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno normaliziranje."))
                        submission.stepState = SubmissionStepState.ERROR_NORMALIZATION
                        false
                    } finally {
                        JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                        if (CancellationManager.isCancelled()) {
                            throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                        }
                    }
                }, jPlagExecutorService.jPlagExecutorService())
            } else null
        }

        futures.forEachIndexed { idx, future ->
            try {
                future.get(TIMEOUT, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                val submission = submissionsForNormalizing[idx]
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), "Submission took too long to normalize."))
                submission.stepState = SubmissionStepState.ERROR_NORMALIZATION
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
            } catch (e: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(submissionsForNormalizing[idx].name.substringAfterLast("/"), e.message ?: ""))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${submissionsForNormalizing[idx].name}")
            } finally {
            JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${submissionsForNormalizing[idx].name}")
            if (CancellationManager.isCancelled()) {
                throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
            }
        }
        }
    }

    fun compareSubmissions(submissionSet: SubmissionSet, jPlagOptions: JPlagOptions, whitelistFile: File?): JPlagResult {
        val timeBeforeStartInMillis = System.currentTimeMillis()
        val tokenValueMapper = TokenValueMapper(submissionSet.submissions, if (whitelistFile != null) submissionSet.baseCode else null)
        val submissionsNotComparedToBasecode = submissionSet.submissions.filter { it.baseCodeComparison == null }
        val greedyStringTiling = GreedyStringTiling(jPlagOptions, tokenValueMapper)
        JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "Basecode usporedba.")
        if (whitelistFile != null) {
            handleBaseCode(SubmissionSet(submissionsNotComparedToBasecode, submissionSet.baseCode, jPlagOptions), greedyStringTiling)
        }
        JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "Basecode usporedba.")
        val tuples = buildComparisonTuples(submissionSet.submissions)
        val tuplesToCompare = tuples.filter { tuple ->
            val leftSubmission = tuple.left.name
            val rightSubmission = tuple.right.name
            !(comparedSubmissions.contains("$leftSubmission-$rightSubmission") ||
                    comparedSubmissions.contains("$rightSubmission-$leftSubmission"))
        }
        if (jPlagRun.comparisonTotal == null) {
            jPlagRun = jPlagMongoService.updateTotalNumbersForRun(jPlagRun, jPlagRun.submissionTotal, tuples.size)
        }
        val comparisonFutures = tuplesToCompare.map { tuple ->
            CompletableFuture.supplyAsync({
                JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
                try {
                    if (CancellationManager.isCancelled()) {
                        CancellationManager.saveStateOnceForSupplier(jPlagRun.id!!) {
                            plagSuspendedTaskManager.saveSubmissions(
                                submissionSet.submissions.toSet(),
                                submissionSet.baseCode,
                                plagSuspendedTask,
                                language.name,
                                comparisonsCompleted,
                                comparedSubmissions
                            )
                            jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
                            jPlagMongoService.updateFailedToCompareSubmissions(jPlagRun.id!!, failedToCompareSubmissions)
                        }
                        throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                    }
                    val comparison = compareTwoSubmissions(tuple.left, tuple.right, greedyStringTiling, jPlagOptions)
                    if (comparison != null) {
                        comparedSubmissions.add("${tuple.left.name}-${tuple.right.name}")
                        comparisonsCompleted.add(comparison)
                        JPlagService.incrementStepStatus("comparison", jPlagRun.id!!, "ovdje11")
                    }
                    comparison
                } catch (e: Exception) {
                    failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"), e.message ?: "Neuspješna usporedba."))
                    null
                } finally {
                    JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
                    if (CancellationManager.isCancelled()) {
                        throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                    }
                }
            }, jPlagExecutorService.jPlagExecutorService())
        }

        val comparisons = mutableListOf<JPlagComparison>()
        comparisonFutures.forEachIndexed { idx, future ->
            val tuple = tuplesToCompare[idx]
            try {
                val comparison = future.get(TIMEOUT, TimeUnit.MINUTES)
                if (comparison != null) {
                    comparisons.add(comparison)
                } else {
                    failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"),
                        "Neuspješna usporedba."))
                }
            } catch (e: TimeoutException) {
                failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"), "Prekinuta usporedba zbog isteka vremena."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
                future.cancel(true)
            } catch (e: Exception) {
                failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"), e.message ?: "Neuspješna usporedba."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
            } finally {
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
                if (CancellationManager.isCancelled()) {
                    throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                }
            }
        }
        val durationInMillis = System.currentTimeMillis() - timeBeforeStartInMillis
        val result = JPlagResult(comparisons, submissionSet, durationInMillis, jPlagOptions)
        result.clusteringResult = ClusteringFactory.getClusterings(result.allComparisons, jPlagOptions.clusteringOptions())
        return result
    }

    private fun makeCanonical(file: File, exceptionWrapper: (Exception) -> ExitException): File {
        return try {
            file.canonicalFile
        } catch (exception: IOException) {
            throw exceptionWrapper(exception)
        }
    }

    private fun isFileExcluded(file: File, jPlagOptions: JPlagOptions): Boolean {
        return jPlagOptions.excludedFiles().any { excludedName -> file.name.endsWith(excludedName) }
    }

    private fun hasValidSuffix(file: File, jPlagOptions: JPlagOptions): Boolean {
        val validSuffixes = jPlagOptions.fileSuffixes()
        if (validSuffixes == null || validSuffixes.isEmpty()) {
            return true
        }
        return validSuffixes.any { suffix -> file.name.endsWith(suffix) }
    }

    private fun processSubmission(
        submissionName: String,
        submissionFile: File,
        isNew: Boolean,
        jplagOptions: JPlagOptions
    ): Submission {
        var file = submissionFile
        if (file.isDirectory && jplagOptions.subdirectoryName() != null) {
            file = File(file, jplagOptions.subdirectoryName())
            if (!file.exists()) {
                throw SubmissionException(
                    "Submission $submissionName does not contain the given subdirectory '${jplagOptions.subdirectoryName()}'"
                )
            }
            if (!file.isDirectory) {
                throw SubmissionException("The given subdirectory '${jplagOptions.subdirectoryName()}' is not a directory!")
            }
        }
        file = makeCanonical(file) { SubmissionException("Cannot create submission: $submissionName", it) }
        return Submission(submissionName, file, isNew, parseFilesRecursively(file, jplagOptions), jplagOptions.language())
    }

    private fun parseFilesRecursively(file: File, jPlagOptions: JPlagOptions): Collection<File> {
        if (isFileExcluded(file, jPlagOptions)) {
            return emptyList()
        }
        if (file.isFile && hasValidSuffix(file, jPlagOptions)) {
            return listOf(file)
        }
        val nestedFileNames = file.list() ?: return emptyList()
        val files = mutableListOf<File>()
        for (fileName in nestedFileNames) {
            files += parseFilesRecursively(File(file, fileName), jPlagOptions)
        }
        return files
    }



    fun parseSubmissions(submissions: MutableList<Submission>, jPlagOptions: JPlagOptions, baseCode: Submission?) {
        if (submissions.isEmpty()) {
            return
        }
        val submisssionsForParsing = submissions.filter { it.stepState == SubmissionStepState.NORMALIZED }
        val futures = submisssionsForParsing
            .map { submission ->
                CompletableFuture.supplyAsync({
                    JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                    try {
                        if (CancellationManager.isCancelled()) {
                            CancellationManager.saveStateOnceForSupplier(jPlagRun.id!!) {
                                plagSuspendedTaskManager.saveSubmissions(
                                    submissions.toSet(),
                                    baseCode,
                                    plagSuspendedTask,
                                    language.name,
                                    comparisonsCompleted,
                                    comparedSubmissions
                                )
                                jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
                                jPlagMongoService.updateFailedToCompareSubmissions(jPlagRun.id!!, failedToCompareSubmissions)
                            }
                            throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                        }
                        try {
                            parseSubmission(
                                submission,
                                jPlagOptions.normalize,
                                jPlagOptions.minimumTokenMatch,
                                jPlagOptions.language
                            )
                        } catch (e: Exception) {
                            failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje predaje."))
                            submission.stepState = SubmissionStepState.ERROR_PARSING
                        }
                        submission.stepState = SubmissionStepState.PARSED
                        JPlagService.incrementStepStatus("parsing", jPlagRun.id!!, "ovdje10")
                    } catch (e: Exception) {
                        failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje predaje."))
                        submission.stepState = SubmissionStepState.ERROR_PARSING
                    } finally {
                        JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                        if (CancellationManager.isCancelled()) {
                            throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                        }
                    }
                }, jPlagExecutorService.jPlagExecutorService())
            }

        futures.forEachIndexed { idx, future ->
            val submission = submisssionsForParsing[idx]
            try {
                future.get(TIMEOUT, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), "Predaja je trajala predugo za parsiranje."))
                submission.stepState = SubmissionStepState.ERROR_PARSING
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                future.cancel(true)
            } catch (e: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje predaje."))
                submission.stepState = SubmissionStepState.ERROR_PARSING
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
            } finally {
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                if (CancellationManager.isCancelled()) {
                    throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                }
            }
        }
    }

    companion object {

        fun parseSubmission(submission: Submission, normalize: Boolean, minimalTokens: Int, language: Language) {
            if (submission.files == null || submission.files!!.isEmpty()) {
                throw IllegalStateException("Ova predaja nema datoteke!")
            }

            try {
                submission.tokenList = language.parse(submission.files.toSet(), normalize)
            } catch(e: Exception) {
                throw e
            }

            if (submission.tokenList.size < minimalTokens) {
                throw IllegalStateException("Predaja $submission ima premalo tokena!")
            }
            submission.tokenList = submission.tokenList.toList()
        }
    }
}
