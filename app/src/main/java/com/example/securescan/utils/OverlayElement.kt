package com.example.securescan.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class OverlayType { TEXT, DRAWING, HIGHLIGHT }

data class OverlayElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: OverlayType,
    val position: Offset = Offset.Zero,
    val text: String = "",
    val originalText: String = "",
    val fontSize: Float = 16f,
    val color: Color = Color.Black,
    val paths: List<Offset> = emptyList(),
    val width: Float = 200f,
    val height: Float = 50f,
    val pageIndex: Int = 0
)