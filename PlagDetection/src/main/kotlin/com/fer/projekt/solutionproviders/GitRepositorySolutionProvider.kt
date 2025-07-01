package com.fer.projekt.solutionproviders

import com.fer.projekt.annotations.SolutionProvider
import com.fer.projekt.controller.SolutionProviderName
import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.fileproviders.RESOURCES_PATH
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.File


@Component
@SolutionProvider(SolutionProviderName.GIT)
class GitRepositorySolutionProvider(
    @Value("\${github.token}") private val token: String,
    private val fileUtils: FileUtils,
): RepositorySolutionProvider {
    val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")

    override fun saveRepoToResources(
        repoUrl: String?,
        branch: String?,
        zipFile: MultipartFile?,
        allowedExtensions: List<String>?,
        disallowedFiles: List<String>?,
        saveFile: File
    ) {
        if (!saveFile.exists()) {
            saveFile.mkdirs()
        }

        val git = Git.cloneRepository()
            .setURI(repoUrl)
            .setBranch(branch)
            .setDirectory(saveFile)
            .setCredentialsProvider(credentialsProvider)
            .call()
        git.close()
        if (disallowedFiles != null) {
            fileUtils.deleteNotNeededFiles(saveFile, disallowedFiles)
        }
        fileUtils.flattenSubmission(saveFile.absolutePath)
        if (allowedExtensions != null) {
            fileUtils.deleteFilesWithExtensionRecursive(saveFile, allowedExtensions)
        }
    }
}
