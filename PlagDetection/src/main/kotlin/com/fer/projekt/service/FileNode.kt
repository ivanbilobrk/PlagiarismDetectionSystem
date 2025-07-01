package com.fer.projekt.service

data class FileNode (
    val key: String,
    val label: String,
    val data: String,
    val icon: String = "pi pi-fw pi-file",
    val children: List<FileNode?>
)
