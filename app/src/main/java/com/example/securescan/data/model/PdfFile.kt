package com.example.securescan.data.model

data class PdfFile(
    val id: Long = 0,
    val name: String,
    val uri: String,
    val size: Long = 0,
    val pageCount: Int = 1,
    val isPasswordProtected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)