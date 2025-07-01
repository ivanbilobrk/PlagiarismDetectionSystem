package com.fer.projekt.fileproviders

import com.fer.projekt.models.Resource
import com.fer.projekt.models.ResourceType
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.text.Normalizer

@Component
class FileUtils {

    private val log = KotlinLogging.logger {}

    fun normalizePath(path: String): String =
        Normalizer.normalize(path, Normalizer.Form.NFC)

    fun normalizePath(path: Path): Path =
        Paths.get(Normalizer.normalize(path.toString(), Normalizer.Form.NFC))

    fun getNumberOfSubmissions(resources: List<Resource>): Int {
        return resources.sumOf { resource ->
            val rootPath = Paths.get(normalizePath(resource.path))
            Files.newDirectoryStream(rootPath).use { dirs ->
                dirs.count { dir ->
                    val normDir = normalizePath(dir)
                    if (Files.isDirectory(normDir)) {
                        try {
                            Files.walk(normDir).anyMatch { file ->
                                Files.isRegularFile(normalizePath(file))
                            }
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        }
    }

    fun deleteByPath(path: String) {
        val solutionsDir = File(RESOURCES_PATH + path)
        if (solutionsDir.exists()) {
            solutionsDir.deleteRecursively()
            log.info { "Deleted solutions for $path" }
        }
    }

    fun deleteFilesWithExtensionRecursive(directory: File, allowedExtensions: List<String>) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteFilesWithExtensionRecursive(file, allowedExtensions)
                } else if (file.isFile && !allowedExtensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                    file.delete()
                }
            }
        }
    }

    fun flattenSubmission(rootDirectory: String) {
        val baseDir = Paths.get(rootDirectory)

        Files.newDirectoryStream(baseDir).use { studentDirs ->
            for (studentDir in studentDirs) {
                if (Files.isDirectory(studentDir)) {
                    // Move all files from subdirectories to the student directory
                    Files.walkFileTree(studentDir, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            if (file.parent != studentDir) {
                                try {
                                    Files.move(file, studentDir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
                                } catch (e: IOException) {
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                            if (dir != studentDir) {
                                try {
                                    Files.delete(dir)
                                } catch (e: IOException) {
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            }
        }
    }

    fun filterInsideFolder(folderPath: String, filter: List<String>) {
        filter.forEach{
            val fileToRemove = File(folderPath, it)
            if (fileToRemove.exists()) {
                fileToRemove.deleteRecursively()
                log.info { "Deleted file $it" }
            }
        }
    }

    fun deleteNotNeededFiles(folderToFilter: File, filter: List<String>) {
        folderToFilter.listFiles()?.forEach { file ->
            if (file == folderToFilter || folderToFilter.startsWith(file) || file.startsWith(folderToFilter)) {
                if (file.isFile && (file.extension in disallowedExtensions || file.name in filter)) {
                    file.delete()
                } else if (file.isDirectory && file.name in filter) {
                    file.deleteRecursively()
                } else if (file.isDirectory) {
                    deleteNotNeededFiles(file, filter)
                }
            } else {
                file.deleteRecursively()
            }
        }
    }

    companion object {
        fun getRepoConfigFile(subject: String, userName: String, academicYear: String, projectName: String, configName: String, resourceType: ResourceType): File {
            return File("$RESOURCES_PATH/$userName/$configName/$subject/${projectName.replace(" ", "-").replace("/", "-")}/$academicYear/${resourceType}-${academicYear}")
        }

        fun getZipRepoAsFile(resourceName: String, userName: String): File {
            return File("$RESOURCES_PATH/$userName/solutions/local/$resourceName$/")
        }

        fun getGitRepoAsFile(resourceName: String, userName: String): File {
            return File("$RESOURCES_PATH/$userName/solutions/git/$resourceName$/")
        }
    }
}

val disallowedExtensions = listOf(
    ".jpg", ".jpeg", ".svg", ".gitignore", ".json", ".png", ".webp", ".ico", ".lock", ".eot", ".ttf", ".woff", ".woff2", ".editorconfig", ".idea/", ".vscode/",
    "node_modules/", ".git/", ".cert", ".firebase", ".css"
)
const val RESOURCES_PATH = "src/main/resources"
const val TEMP_RESOURCES_PATH = "src/main/resources/temp"
const val TEMP_HASH_CALCULATION_PATH = "src/main/resources/temp/hashCalculation"
