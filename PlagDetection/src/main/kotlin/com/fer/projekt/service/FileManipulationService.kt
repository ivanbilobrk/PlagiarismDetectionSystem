package com.fer.projekt.service

import com.fer.projekt.fileproviders.RESOURCES_PATH
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import java.io.File

@Service
class FileManipulationService {

    fun getFileForDownload(filePath: String): FileSystemResource {
        val file = File(RESOURCES_PATH + filePath)
        return if (file.exists() && file.canRead()) {
            FileSystemResource(file)
        } else {
            throw IllegalArgumentException("File not found")
        }
    }

    fun getFileStructure(userName: String): List<FileNode> {
        val userDir = File("$RESOURCES_PATH/$userName/solutions")
        return if (userDir.exists() && userDir.canRead()) {
            val localRepoFile = File(userDir.absolutePath + "/local")
            val gitRepoFile = File(userDir.absolutePath + "/git")

            val localFileNode = if (localRepoFile.exists() && localRepoFile.canRead()) {
                buildFileTree(localRepoFile, "0")
            } else {
                null
            }
            val gitFileNode = if (gitRepoFile.exists() && gitRepoFile.canRead()) {
                buildFileTree(gitRepoFile, "1")
            } else {
                null
            }
            listOfNotNull(localFileNode, gitFileNode)
        } else {
            throw IllegalArgumentException("File not found")
        }
    }

    fun buildFileTree(file: File, currentKey: String): FileNode? {
        if (!file.isDirectory) {
            return null
        }

        val childrenNodes = file.listFiles()
            ?.filter { it.name != ".DS_Store" && it.isDirectory }
            ?.sortedBy { it.name }
            ?.mapIndexed { index, child ->
                buildFileTree(child, "$currentKey-$index")
            }
            ?.filterNotNull()
            ?: emptyList()

        return FileNode(
            key = currentKey,
            label = file.name,
            data = file.absolutePath.substringAfterLast("/src/main/resources"),
            children = childrenNodes
        )
    }

    fun createDirStructure(userName: String, path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    companion object {
        public const val PLAG_CONFIGS_BASE_PATH = "$RESOURCES_PATH/configs"
    }

}
