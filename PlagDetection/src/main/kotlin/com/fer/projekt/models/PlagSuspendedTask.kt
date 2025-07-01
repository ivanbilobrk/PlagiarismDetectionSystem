package com.fer.projekt.models

import de.jplag.Match
import de.jplag.SubmissionState
import de.jplag.SubmissionStepState
import de.jplag.TokenType
import de.jplag.semantics.*
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "suspended_tasks")
data class PlagSuspendedTaskCollection(
    @Id val id: String? = null,
    val plagRunId: String,
    val languageToVariables: MutableMap<String, JPlagRunVariablesCollection>,
    val configName: String,
    val projectName: String,
    val userName: String,
)

data class PlagSuspendedTask(
    val id: String?,
    val plagRunId: String,
    val languageToVariables: MutableMap<String, JPlagRunVariables>,
    val configName: String,
    val projectName: String,
    val userName: String,
)

data class JPlagRunVariablesCollection(
    val submissions: List<String>,
    val baseCode: String?,
    val comparedSubmissions: List<String>,
    val comparisons: List<String>
)

data class JPlagRunVariables(
    val submissions: List<CustomSubmission>,
    val baseCode: CustomSubmission?,
    val comparedSubmissions: List<String>,
    val comparisons: List<CustomJPlagComparison>
)

@Document(collection = "custom_submissions")
data class CustomSubmission(
    @Id val id: String? = null,
    val name: String,
    val submissionRootFile: String,
    val isNew: Boolean,
    val files: List<String>,
    val language: String,
    val submissionState: SubmissionState,
    val tokenList: List<CustomToken>?,
    val baseCodeComparison: CustomJPlagComparison?,
    val fileTokenCount: Map<String, Int>?,
    val submissionStepState: SubmissionStepState
)

data class CustomToken(
    val line: Int,
    val column: Int,
    val length: Int,
    val file: String,
    val tokenType: String,
    val codeSemantics: CustomCodeSemantics?
)

data class CustomCodeSemantics(
    val critical: Boolean,
    val positionSignificance: PositionSignificance,
    val bidirectionalBlockDepthChange: Int,
    val reads: Set<CustomVariable>,
    val writes: Set<CustomVariable>,

)

data class CustomVariable(
    val name: String,
    val scope: VariableScope,
    val isMutable: Boolean,
)

@Document(collection = "custom_comparisons")
data class CustomJPlagComparison(
    @Id val id: String? = null,
    val firstSubmission: CustomSubmission,
    val secondSubmission: CustomSubmission,
    val matches : List<Match>,
    val ignoredMatches: List<Match>,
)
