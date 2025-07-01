package com.fer.projekt.service

import com.fer.projekt.controller.SolutionProviderName
import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.fileproviders.disallowedExtensions
import com.fer.projekt.solutionproviders.RepositorySolutionProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class SolutionUploadService(
    @Qualifier("solutionProvidersMap") private val solutionProvidersMap: Map<SolutionProviderName, RepositorySolutionProvider>,
    private val fileUtils: FileUtils
) {

    fun uploadSolution(
        zipFile: MultipartFile?,
        repoUrl: String?,
        branch: String?,
        solutionProvider: SolutionProviderName,
        userName: String,
        resourceName: String,
        disallowedFiles: List<String>?,
        allowedExtensions: List<String>?
    ): Long {
        val provider = solutionProvidersMap[solutionProvider]
            ?: throw IllegalArgumentException("Solution provider $solutionProvider not found")

        val saveFile = if (zipFile != null) {
            FileUtils.getZipRepoAsFile(
                resourceName = resourceName,
                userName = userName,
            )
        }  else {
            FileUtils.getGitRepoAsFile(
                resourceName = resourceName,
                userName = userName,
            )
        }
        
        provider.saveRepoToResources(repoUrl, branch, zipFile, allowedExtensions, disallowedFiles, saveFile)

        return 1L
    }
}
