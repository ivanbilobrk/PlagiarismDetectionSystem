package com.fer.projekt.config

import aws.sdk.kotlin.runtime.config.imds.EndpointConfiguration
import com.fer.projekt.models.CustomCodeSemantics
import com.fer.projekt.models.CustomJPlagComparison
import com.fer.projekt.models.CustomSubmission
import com.fer.projekt.models.CustomToken
import com.fer.projekt.models.CustomVariable
import com.fer.projekt.models.JPlagRunVariables
import com.fer.projekt.models.JPlagRunVariablesCollection
import com.fer.projekt.models.PlagSuspendedTask
import com.fer.projekt.models.PlagSuspendedTaskCollection
import com.fer.projekt.repository.CustomJPlagComparisonRepository
import com.fer.projekt.repository.CustomSubmissionsRepoistory
import com.fer.projekt.repository.PlagSuspendedTaskRepository
import com.fer.projekt.service.JPlagService
import de.jplag.JPlagComparison
import de.jplag.Language
import de.jplag.SharedTokenType
import de.jplag.Submission
import de.jplag.SubmissionState
import de.jplag.SubmissionStepState
import de.jplag.Token
import de.jplag.TokenType
import de.jplag.cpp.CPPTokenType
import de.jplag.java.JavaTokenType
import de.jplag.python3.Python3TokenType
import de.jplag.semantics.CodeSemantics
import de.jplag.semantics.Variable
import de.jplag.text.TextTokenType
import de.jplag.typescript.TypeScriptTokenType
import org.springframework.stereotype.Component
import java.io.File

@Component
class PlagSuspendedTaskManager(
    private val plagSuspendedTaskRepository: PlagSuspendedTaskRepository,
    private val submissionsRepoistory: CustomSubmissionsRepoistory,
    private val customSubmissionsRepoistory: CustomSubmissionsRepoistory,
    private val customJPlagComparisonRepository: CustomJPlagComparisonRepository
) {

    fun findByConfigNameAndProjectNameAndUserName(
        configName: String,
        projectName: String,
        userName: String
    ) = plagSuspendedTaskRepository.findByConfigNameAndProjectNameAndUserName(
        configName,
        projectName,
        userName
    )

    fun savePlagSuspendedTask(plagSuspendedTask: PlagSuspendedTask) =
        plagSuspendedTaskRepository.save(toMongo(plagSuspendedTask))

    fun toMongo(plagSuspendedTask: PlagSuspendedTask): PlagSuspendedTaskCollection {
        return PlagSuspendedTaskCollection(
            id = plagSuspendedTask.id,
            plagRunId = plagSuspendedTask.plagRunId,
            languageToVariables = plagSuspendedTask.languageToVariables.mapValues { entry ->
                JPlagRunVariablesCollection(
                    submissions = entry.value.submissions.map { customSubmissionsRepoistory.save(it).id!! },
                    baseCode = entry.value.baseCode?.let { customSubmissionsRepoistory.save(it).id },
                    comparedSubmissions = entry.value.comparedSubmissions,
                    comparisons = entry.value.comparisons.map { customJPlagComparisonRepository.save(it).id!! }
                )
            }.toMutableMap(),
            configName = plagSuspendedTask.configName,
            projectName = plagSuspendedTask.projectName,
            userName = plagSuspendedTask.userName
        )
    }

    fun toDomain(plagSuspendedTaskCollection: PlagSuspendedTaskCollection): PlagSuspendedTask {
        return PlagSuspendedTask(
            id = plagSuspendedTaskCollection.id,
            plagRunId = plagSuspendedTaskCollection.plagRunId,
            languageToVariables = plagSuspendedTaskCollection.languageToVariables.mapValues { entry ->
                JPlagRunVariables(
                    submissions = entry.value.submissions.map { customSubmissionsRepoistory.findById(it).orElseThrow() },
                    baseCode = entry.value.baseCode?.let { customSubmissionsRepoistory.findById(it).orElseThrow() },
                    comparedSubmissions = entry.value.comparedSubmissions,
                    comparisons = entry.value.comparisons.map { customJPlagComparisonRepository.findById(it).orElseThrow() }
                )
            }.toMutableMap(),
            configName = plagSuspendedTaskCollection.configName,
            projectName = plagSuspendedTaskCollection.projectName,
            userName = plagSuspendedTaskCollection.userName
        )
    }

//    fun saveParsedSubmissions(
//        submissions: List<Submission>,
//        baseCode: Submission?,
//        plagSuspendedTask: PlagSuspendedTask,
//        language: String
//    ) {
//        val customBaseCodesSubmission = if (baseCode != null) submissionToCustomSubmission(baseCode,
//            baseCode.state == SubmissionState.VALID
//        ) else null
//        val unparsedSubmissions = plagSuspendedTask.languageToVariables[language.lowercase()]!!.unparsedSubmissions
//        val parsedSubmissions = plagSuspendedTask.languageToVariables[language.lowercase()]!!.parsedSubmissions
//        val updatedUnparsedSubmissions = unparsedSubmissions.filter { unparsedSubmission -> !submissions.map { it.name }.any { unparsedSubmission.name == it } }
//        val jPlagRunVariables = JPlagRunVariables(
//            unparsedSubmissions = updatedUnparsedSubmissions,
//            parsedSubmissions = submissions.map { submissionToCustomSubmission(it, true) } + parsedSubmissions,
//            baseCode = customBaseCodesSubmission
//        )
//        plagSuspendedTask.languageToVariables[language.lowercase()] = jPlagRunVariables
//        val modifiedPlagSuspendedTask = plagSuspendedTask.copy(
//            languageToVariables = plagSuspendedTask.languageToVariables
//        )
//        plagSuspendedTaskRepository.save(modifiedPlagSuspendedTask)
//    }

    fun saveSubmissions(
        submissions: Set<Submission>,
        baseCode: Submission?,
        plagSuspendedTask: PlagSuspendedTask,
        language: String,
        comparisons: Set<JPlagComparison>,
        comparedSubmissions: Set<String>
    ) {
        val sasa = submissions.find { it.name.contains("Saša") }

        val customBaseCodesSubmission = if(baseCode != null) submissionToCustomSubmission(baseCode) else null
        val jPlagRunVariables = JPlagRunVariables(
            submissions = submissions.map { submissionToCustomSubmission(it) },
            baseCode = customBaseCodesSubmission,
            comparedSubmissions = comparedSubmissions.toList(),
            comparisons = comparisons.map { jPlagComparisonToCustomJPlagComparison(it) }
        )
        val languageToVariables = plagSuspendedTask.languageToVariables.toMutableMap()
        languageToVariables[language] = jPlagRunVariables
        val modifiedPlagSuspendedTask = plagSuspendedTask.copy(
            languageToVariables = languageToVariables,
        )
        plagSuspendedTaskRepository.save(toMongo(modifiedPlagSuspendedTask))
    }

    fun submissionToCustomSubmission(
        submission: Submission,
    ): CustomSubmission = CustomSubmission(
        name = submission.name,
        submissionRootFile = submission.root.absolutePath,
        isNew = submission.isNew,
        files = submission.files.map { it.absolutePath },
        language = submission.language.name,
        submissionState = submission.state,
        tokenList = if (submission.tokenList != null) submission.tokenList.map { tokenToCustomToken(it) } else null,
        baseCodeComparison = if (submission.baseCodeComparison != null) jPlagComparisonToCustomJPlagComparison(submission.baseCodeComparison) else null,
        fileTokenCount = if (submission.stepState == SubmissionStepState.PARSED) submission.tokenCountPerFile.mapKeys { it.key.absolutePath.replace(".", "[dot]") } else null,
        submissionStepState = submission.stepState
    )

    fun tokenToCustomToken(
        token: Token
    ): CustomToken {
        return CustomToken(
            line = token.line,
            column = token.column,
            length = token.length,
            file = token.file.absolutePath,
            tokenType = getTokenTypeEnumPrefix(token.type) + tokenSeparator  + token.type.description,
            codeSemantics = if (token.semantics != null) codeSemanticsToCustomCodeSemantics(token.semantics) else null
        )
    }

    private fun getTokenTypeEnumPrefix(
        tokenType: TokenType
    ): String {
        return when (tokenType) {
            is JavaTokenType -> "java"
            is Python3TokenType -> "python"
            is CPPTokenType -> "cpp"
            is TextTokenType -> "text"
            is TypeScriptTokenType -> "typescript"
            is SharedTokenType -> "shared"
            else -> throw IllegalArgumentException("Unsupported token type: $tokenType")
        }
    }

    fun codeSemanticsToCustomCodeSemantics(
        codeSemantics: CodeSemantics
    ) = CustomCodeSemantics(
        critical = codeSemantics.isCritical,
        positionSignificance = codeSemantics.positionSignificance,
        bidirectionalBlockDepthChange = codeSemantics.bidirectionalBlockDepthChange,
        reads = codeSemantics.reads().map { variableToCustomVariable(it) }.toSet(),
        writes = codeSemantics.writes().map { variableToCustomVariable(it) }.toSet()
    )

    fun variableToCustomVariable(
        variable: Variable
    ) = CustomVariable(
        name = variable.name,
        scope = variable.scope,
        isMutable = variable.isMutable
    )

    fun jPlagComparisonToCustomJPlagComparison(
        jPlagComparison: JPlagComparison,
    ) = CustomJPlagComparison(
        firstSubmission = submissionToCustomSubmission(jPlagComparison.firstSubmission),
        secondSubmission = submissionToCustomSubmission(jPlagComparison.secondSubmission),
        matches = jPlagComparison.matches,
        ignoredMatches = jPlagComparison.ignoredMatches,
    )

    fun customJPlagComparisonToJPlagComparison(
        customJPlagComparison: CustomJPlagComparison, suffixes: List<String>,
        language: Language
    ): JPlagComparison {
        return JPlagComparison(
            customSubmissionToSubmission(customJPlagComparison.firstSubmission, suffixes, language),
            customSubmissionToSubmission(customJPlagComparison.secondSubmission, suffixes, language),
            customJPlagComparison.matches,
            customJPlagComparison.ignoredMatches,
        )
    }

    fun customVariableToVariable(
        customVariable: CustomVariable
    ) = Variable(
        customVariable.name,
        customVariable.scope,
        customVariable.isMutable
    )

    fun customCodeSemanticsToCodeSemantics(
        customCodeSemantics: CustomCodeSemantics
    ) = CodeSemantics(
        customCodeSemantics.critical,
        customCodeSemantics.positionSignificance,
        customCodeSemantics.bidirectionalBlockDepthChange,
        customCodeSemantics.reads.map { customVariableToVariable(it) }.toSet(),
        customCodeSemantics.writes.map { customVariableToVariable(it) }.toSet()
    )

    fun customTokenToToken(
        customToken: CustomToken
    ): Token {
        return Token(
            getTokenType(customToken.tokenType),
            File(customToken.file),
            customToken.line,
            customToken.column,
            customToken.length,
            if (customToken.codeSemantics != null) customCodeSemanticsToCodeSemantics(customToken.codeSemantics) else null
        )
    }

    fun customSubmissionToSubmission(
        customSubmission: CustomSubmission,
        suffixes: List<String>,
        language: Language
    ): Submission {
        val submission = Submission(
            customSubmission.name,
            File(customSubmission.submissionRootFile),
            customSubmission.isNew,
            customSubmission.files.map { File(it) },
            language
        )
        submission.stepState = customSubmission.submissionStepState
        submission.state = customSubmission.submissionState
        submission.tokenList = customSubmission.tokenList?.map { customTokenToToken(it) } ?: emptyList()
        submission.baseCodeComparison = customSubmission.baseCodeComparison?.let { customJPlagComparisonToJPlagComparison(it, suffixes, language) }
        submission.fileTokenCount = customSubmission.fileTokenCount?.mapKeys { File(it.key.replace("[dot]", ".")) }
        return submission
    }

    fun getTokenType(
        description: String ,
    ): TokenType {
        val language = description.split(tokenSeparator).first()
        val tokenDescription = description.split(tokenSeparator).last()
        return when (language) {
            "java" -> JavaTokenType.getTokenType(tokenDescription)
            "python" -> Python3TokenType.getTokenType(tokenDescription)
            "cpp" -> CPPTokenType.getTokenType(tokenDescription)
            "text" -> TextTokenType.getTokenType(tokenDescription)
            "typescript" -> TextTokenType.getTokenType(tokenDescription)
            "shared" -> SharedTokenType.getTokenType(tokenDescription)
            else -> throw IllegalArgumentException("Unsupported language: $language")
        }
    }

    fun deletePlagSuspendedTask(
        plagSuspendedTaskId: String
    ) {
        plagSuspendedTaskRepository.deleteById(plagSuspendedTaskId)
    }

    companion object {
        const val tokenSeparator = "customtokenseparator"
    }
}
