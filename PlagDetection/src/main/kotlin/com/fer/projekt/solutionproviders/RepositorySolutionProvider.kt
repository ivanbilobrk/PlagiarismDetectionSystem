package com.fer.projekt.solutionproviders

import org.springframework.web.multipart.MultipartFile
import java.io.File

interface RepositorySolutionProvider {
    fun saveRepoToResources(
        repoUrl: String?,
        branch: String?,
        zipFile: MultipartFile?,
        allowedExtensions: List<String>?,
        disallowedFiles: List<String>?,
        saveFile: File
        )
}
