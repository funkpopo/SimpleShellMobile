package com.example.simpleshell.domain.model

data class SftpFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val permissions: String
)
