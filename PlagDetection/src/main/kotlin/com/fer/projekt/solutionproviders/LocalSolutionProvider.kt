package com.fer.projekt.solutionproviders

import com.fer.projekt.annotations.SolutionProvider
import com.fer.projekt.controller.SolutionProviderName
import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.fileproviders.disallowedExtensions
import com.fer.projekt.service.JPlagService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.zip.ZipFile

@Component
@SolutionProvider(SolutionProviderName.LOCAL)
class LocalSolutionProvider(
    private val fileUtils: FileUtils,
): RepositorySolutionProvider {

    val log = KotlinLogging.logger {}

    override fun saveRepoToResources(
        repoUrl: String?,
        branch: String?,
        zipFile: MultipartFile?,
        allowedExtensions: List<String>?,
        disallowedFiles: List<String>?,
        saveFile: File
    ) {
        try {
            unzipAndSaveFileV2(zipFile!!.inputStream, saveFile)
        }catch (e: Exception) {
            log.error { "Error while unzipping file: ${e.message}" }

        }
        if (disallowedFiles != null) {
            fileUtils.deleteNotNeededFiles(saveFile, disallowedFiles)
        }
        fileUtils.deleteNotNeededFiles(saveFile, disallowedFiles ?: emptyList())
        fileUtils.flattenSubmission(saveFile.absolutePath)
        if (allowedExtensions != null) {
            fileUtils.deleteFilesWithExtensionRecursive(saveFile, allowedExtensions)
        }
        log.info { "Saved solutions locally" }
    }

    fun unzipAndSaveFileV2(
        inputStream: InputStream,
        saveFile: File,
        disallowedExtensions: List<String> = emptyList()
    ) {
        val tempZipFile = File.createTempFile("upload_", ".zip")
        tempZipFile.outputStream().use { inputStream.copyTo(it) }

        val utf8Zip = ZipFile(tempZipFile, "UTF-8")
        utf8Zip.use { zip ->
            for (entry in zip.entries) {
                val entryName = entry.name

                val parts = entryName.split("/")
                if (parts.size == 2 && entryName.endsWith(".zip")) {
                    val studentDirName = parts[0]

                    val studentDir = File(saveFile, studentDirName)
                    if (!studentDir.exists()) studentDir.mkdirs()

                    val tempInnerZipFile = File.createTempFile("inner_", ".zip")
                    zip.getInputStream(entry).use { inp ->
                        tempInnerZipFile.outputStream().use { out -> inp.copyTo(out) }
                    }

                    unzipFlatCommons(tempInnerZipFile, studentDir, disallowedExtensions)

                    tempInnerZipFile.delete()
                }
            }
        }
        tempZipFile.delete()
        liftAllUnnecessaryDirectories(saveFile)
    }

    fun unzipFlatCommons(
        zipFile: File,
        outputDir: File,
        disallowedExtensions: List<String>
    ) {
        ZipFile(zipFile, "UTF-8").use { zip ->
            for (entry in zip.entries) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    val extensionName = entryName.substringAfterLast('.', "").lowercase()
                    if (!disallowedExtensions.contains(".$extensionName")) {
                        val newFile = File(outputDir, File(entryName).name)
                        zip.getInputStream(entry).use { input ->
                            newFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun unzipAndSaveFile(
        inputStream: InputStream,
        saveFile: File
    ) {
        val buffer = ByteArray(1024)
        ZipInputStream(inputStream).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(saveFile, zipEntry.name)
                if (disallowedExtensions.any { zipEntry?.name == it }) {
                    if (zipEntry.isDirectory) {
                        zis.closeEntry()
                        zipEntry = zis.nextEntry
                        while (zipEntry != null && zipEntry.name.startsWith(newFile.name)) {
                            zis.closeEntry()
                            zipEntry = zis.nextEntry
                        }
                    } else {
                        zis.closeEntry()
                        zipEntry = zis.nextEntry
                    }
                    continue
                } else if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    val extensionName = zipEntry.name.substringAfterLast('.').lowercase()
                    if (!disallowedExtensions.contains(".$extensionName")) {
                        if (!newFile.parentFile.exists()) newFile.parentFile.mkdirs()

                        newFile.outputStream().use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }

                        if (extensionName == "zip") {
                            newFile.inputStream().use { nestedInputStream ->
                                unzipAndSaveFile(nestedInputStream, saveFile)
                            }
                            newFile.delete()
                        }
                    }
                }
                zipEntry = zis.nextEntry
            }
        }
        liftAllUnnecessaryDirectories(saveFile)
    }

    fun saveAIResource(
        saveFile: File,
        aiSolution: String,
        language: String,
    ) {
        val jPlagLanguage = JPlagService.languageToLanguageObject(language, emptyList())
        val suffix = jPlagLanguage.suffixes().first()
        val fileNameWithExtension = "aiSolution$suffix"
        val fileWithExtension = File(saveFile, fileNameWithExtension)

        fileWithExtension.writeText(aiSolution)
    }

    private fun liftAllUnnecessaryDirectories(destinationDir: File) {
        while (true) {
            val childDirs = destinationDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
            val filesAtRoot = destinationDir.listFiles { file -> file.isFile }?.toList() ?: emptyList()
            if (childDirs.size == 1 && filesAtRoot.isEmpty()) {
                val onlyChild = childDirs.first()
                onlyChild.listFiles()?.forEach { child ->
                    val target = File(destinationDir, child.name)
                    if (!child.renameTo(target)) {
                        child.copyRecursively(target, overwrite = true)
                        child.deleteRecursively()
                    }
                }
                onlyChild.deleteRecursively()
            } else {
                break
            }
        }
    }
}
