package org.retrostation.core.engine

import java.nio.ByteBuffer

/**
 * High performance PCM audio frame callback invoked by native audio bridges.
 */
fun interface AudioFrameCallback {
    /**
     * @param buffer Direct ByteBuffer containing 16-bit stereo interleaved PCM samples.
     * @param sampleCount Total number of stereo sample frames in this batch.
     */
    fun onAudioSamples(buffer: ByteBuffer, sampleCount: Int)
}
