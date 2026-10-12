package org.retrostation.core.engine

/**
 * Representation of video surface or buffer properties.
 */
data class VideoFrame(
    val width: Int,
    val height: Int,
    val pitch: Int,
    val format: PixelFormat = PixelFormat.RGB565
) {
    enum class PixelFormat {
        RGB565,
        RGBA8888,
        XRGB8888
    }
}
