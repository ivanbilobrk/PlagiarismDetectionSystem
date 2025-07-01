package com.fer.projekt.service

import com.fer.projekt.CancelPlagException
import com.fer.projekt.config.JPlagExecutorServiceConfig
import com.fer.projekt.languages.CustomNaturalLanguage
import com.fer.projekt.models.FailedToCompareSubmissions
import com.fer.projekt.models.FailedToParseSubmission
import com.fer.projekt.models.JPlagRun
import com.fer.projekt.service.JPlagConfigTaskSupplier.Companion.parseSubmission
import de.jplag.JPlagResult
import de.jplag.Language
import de.jplag.SubmissionSetBuilder
import de.jplag.exceptions.ExitException
import de.jplag.options.JPlagOptions
import mu.KotlinLogging
import java.io.File
import de.jplag.*
import de.jplag.clustering.ClusteringFactory
import de.jplag.comparison.GreedyStringTiling
import de.jplag.comparison.SubmissionTuple
import de.jplag.comparison.TokenValueMapper
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class JPlagTaskSupplier(
    private val jPlagLanguage: Language,
    private val solutionPaths: Set<File?>,
    private val whitelistFile: File?,
    private val oldSubmissionsPaths: Set<File?>,
    private val suffixes: List<String>,
    private var jPlagRun: JPlagRun,
    private val jPlagMongoService: JPlagMongoService,
    private val jPlagExecutorService: JPlagExecutorServiceConfig
) : Supplier<JPlagResult?> {

    private val log = KotlinLogging.logger {  }
    val TIMEOUT = 5L

    private val failedToParseSubmissions: MutableSet<FailedToParseSubmission> = Collections.synchronizedSet(mutableSetOf())
    private val failedToCompareSubmissions: MutableSet<FailedToCompareSubmissions> = Collections.synchronizedSet(mutableSetOf())

    override fun get(): JPlagResult? {
        var jPlagOptions = JPlagOptions(jPlagLanguage, solutionPaths, emptySet())
            .withNormalize(true)
        if (whitelistFile != null) {
            jPlagOptions = jPlagOptions.withBaseCodeSubmissionDirectory(whitelistFile)
        }
        if (oldSubmissionsPaths.isNotEmpty()) {
            jPlagOptions = jPlagOptions.withOldSubmissionDirectories(oldSubmissionsPaths)
        }

        val builder = SubmissionSetBuilder(jPlagOptions)
        val submissionSet = builder.buildSubmissionSet()
        val submissionCount = submissionSet.numberOfSubmissions()
        if (submissionCount < 2) {
            throw IllegalArgumentException("You have submitted less than 2 solutions! Please select at least 2 solutions to compare.")
        }
        jPlagRun = jPlagMongoService.updateTotalNumbersForRun(
            jPlagRunOld = jPlagRun,
            comparisonTotal = null,
            submissionTotal = submissionSet.submissions.size + if (whitelistFile != null) 1 else 0
        )

        if (jPlagOptions.normalize() && jPlagOptions.language().supportsNormalization() && jPlagOptions.language()
                .requiresCoreNormalization()) {
            normalizeSubmissions(submissionSet.submissions, if (whitelistFile != null) submissionSet.baseCode else null)
        } else {
            if (whitelistFile != null) {
                JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje5")
            }
            submissionSet.submissions.forEach { submission ->
                JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje6")
            }
        }


        if (whitelistFile != null) {
            JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
            try {
                parseSubmission(submissionSet.baseCode, jPlagOptions.normalize, jPlagOptions.minimumTokenMatch, jPlagOptions.language)
            } catch (e: ExitException) {
                failedToParseSubmissions.add(FailedToParseSubmission(submissionSet.baseCode.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
                throw IllegalArgumentException("Provided base code submission cannot be parsed!")
            }
            JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submissionSet.baseCode.name)
            JPlagService.incrementStepStatus("parsing", jPlagRun.id!!, "ovdje7")
        }

        parseSubmissions(submissionSet.submissions, jPlagOptions)

        val result = compareSubmissions(submissionSet, jPlagOptions)
        jPlagRun = jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
        jPlagRun = jPlagMongoService.updateFailedToCompareSubmissions(jPlagRun.id!!, failedToCompareSubmissions)
        return result
    }

    companion object {

        fun compareTwoSubmissions(first: Submission, second: Submission, greedyStringTiling: GreedyStringTiling, jPlagOptions: JPlagOptions): JPlagComparison? {
            val comparison = greedyStringTiling.compare(first, second)
            return if (jPlagOptions.similarityMetric().isAboveThreshold(comparison, jPlagOptions.similarityThreshold())) {
                comparison
            } else {
                null
            }
        }

        fun handleBaseCode(submissionSet: SubmissionSet, greedyStringTiling: GreedyStringTiling) {
            if (submissionSet.baseCode != null) {
                val baseCodeSubmission = submissionSet.baseCode
                for (currentSubmission in submissionSet.submissions) {
                    val baseCodeComparison = greedyStringTiling.generateBaseCodeMarking(currentSubmission, baseCodeSubmission)
                    currentSubmission.baseCodeComparison = baseCodeComparison
                }
            }
        }

        fun buildComparisonTuples(submissions: List<Submission>): List<SubmissionTuple> {
            val tuples = mutableListOf<SubmissionTuple>()
            for (i in 0 until submissions.size - 1) {
                val first = submissions[i]
                for (j in i + 1 until submissions.size) {
                    val second = submissions[j]
                    if (first.isNew || second.isNew) {
                        tuples.add(SubmissionTuple(first, second))
                    }
                }
            }
            return tuples
        }
    }

    fun compareSubmissions(submissionSet: SubmissionSet, jPlagOptions: JPlagOptions): JPlagResult {
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
        if (jPlagRun.comparisonTotal == null) {
            jPlagRun = jPlagMongoService.updateTotalNumbersForRun(jPlagRun, jPlagRun.submissionTotal, tuples.size)
        }
        val comparisonFutures = tuples.map { tuple ->
            CompletableFuture.supplyAsync({
                JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${tuple.left.name}-${tuple.right.name}")
                try {
                    val comparison = compareTwoSubmissions(tuple.left, tuple.right, greedyStringTiling, jPlagOptions)
                    if (comparison != null) {
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
            val tuple = tuples[idx]
            try {
                val comparison = future.get(TIMEOUT, TimeUnit.MINUTES)
                if (comparison != null) {
                    comparisons.add(comparison)
                } else {
                    failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"), "Neuspješna usporedba."))
                }
            } catch (e: TimeoutException) {
                failedToCompareSubmissions.add(FailedToCompareSubmissions(tuple.left.name.substringAfterLast("/"), tuple.right.name.substringAfterLast("/"), e.message ?: "Usporedba je predugo trajala."))
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

    fun normalizeSubmissions(
        submissions: List<Submission>,
        baseCode: Submission?,
    ) {
        if (baseCode != null) {
            try {
                JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, baseCode.name)
                baseCode.normalize()
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, baseCode.name)
                JPlagService.incrementStepStatus("normalization", jPlagRun.id!!, "ovdje8")
            } catch (ex: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(baseCode.name.substringAfterLast("/"), ex.message ?: "Neuspješno normaliziranje."))
                jPlagRun = jPlagMongoService.updateFailedToParseSubmissions(jPlagRun.id!!, failedToParseSubmissions)
                throw ex
            }
        }
        val futures = submissions.mapNotNull { submission ->
            CompletableFuture.supplyAsync({
                JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                try {
                    submission.normalize()
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
        }

        futures.forEachIndexed { idx, future ->
            try {
                future.get(TIMEOUT, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                val submission = submissions[idx]
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno normaliziranje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
            } catch (e: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(submissions[idx].name.substringAfterLast("/"), e.message ?: "Neuspješno normaliziranje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${submissions[idx].name}")
            } finally {
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, "${submissions[idx].name}")
                if (CancellationManager.isCancelled()) {
                    throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                }
            }
        }
    }

    fun parseSubmissions(submissions: MutableList<Submission>, jPlagOptions: JPlagOptions) {
        if (submissions.isEmpty()) {
            return
        }
        val futures = submissions
            .map { submission ->
                CompletableFuture.supplyAsync({
                    JPlagService.addCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                    try {
                        parseSubmission(
                            submission,
                            jPlagOptions.normalize,
                            jPlagOptions.minimumTokenMatch,
                            jPlagOptions.language
                        )
                        JPlagService.incrementStepStatus("parsing", jPlagRun.id!!, "ovdje10")
                    } catch (e: Exception) {
                        failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje."))
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
            val submission = submissions[idx]
            try {
                future.get(TIMEOUT, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                future.cancel(true)
            } catch (e: Exception) {
                failedToParseSubmissions.add(FailedToParseSubmission(submission.name.substringAfterLast("/"), e.message ?: "Neuspješno parsiranje."))
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
            } finally {
                JPlagService.removeCurrentlyProcessingSubmissionsForRun(jPlagRun.id!!, submission.name)
                if (CancellationManager.isCancelled()) {
                    throw CancelPlagException("Time is up, have to finish JPlagRun until midnight.")
                }
            }
        }
    }
}
