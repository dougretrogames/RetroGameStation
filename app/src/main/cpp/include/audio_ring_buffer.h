/**
 * Retro Game Station - Low Latency Audio Ring Buffer
 * Lock-free Single-Producer Single-Consumer (SPSC) Circular FIFO
 * Specially tuned for real-time audio sample streaming between emulation cores and AAudio.
 */

#pragma once

#include <atomic>
#include <vector>
#include <cstdint>
#include <cstring>
#include <algorithm>

namespace retro {
namespace audio {

class AudioRingBuffer {
public:
    explicit AudioRingBuffer(size_t capacityFrames = 8192, uint32_t channelCount = 2)
        : mChannelCount(channelCount),
          mCapacityFrames(capacityFrames),
          mTotalCapacitySamples(capacityFrames * channelCount),
          mBuffer(mTotalCapacitySamples, 0.0f),
          mReadIndex(0),
          mWriteIndex(0) {
    }

    void reset() {
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
    }

    size_t getAvailableReadFrames() const {
        size_t writeIdx = mWriteIndex.load(std::memory_order_acquire);
        size_t readIdx = mReadIndex.load(std::memory_order_relaxed);
        if (writeIdx >= readIdx) {
            return (writeIdx - readIdx) / mChannelCount;
        } else {
            return (mTotalCapacitySamples - (readIdx - writeIdx)) / mChannelCount;
        }
    }

    size_t getAvailableWriteFrames() const {
        return (mCapacityFrames - 1) - getAvailableReadFrames();
    }

    /**
     * Write float PCM samples into the circular buffer.
     * Called by emulation core thread (Libretro/PPSSPP/Mupen64Plus).
     */
    size_t write(const float* source, size_t frames) {
        if (!source || frames == 0) return 0;

        size_t samplesRequested = frames * mChannelCount;
        size_t writeIdx = mWriteIndex.load(std::memory_order_relaxed);
        size_t readIdx = mReadIndex.load(std::memory_order_acquire);

        size_t samplesFree = 0;
        if (writeIdx >= readIdx) {
            samplesFree = (mTotalCapacitySamples - 1) - (writeIdx - readIdx);
        } else {
            samplesFree = (readIdx - writeIdx) - 1;
        }

        size_t samplesToWrite = std::min(samplesRequested, samplesFree);
        if (samplesToWrite == 0) return 0;

        size_t firstChunk = std::min(samplesToWrite, mTotalCapacitySamples - writeIdx);
        std::memcpy(&mBuffer[writeIdx], source, firstChunk * sizeof(float));

        size_t secondChunk = samplesToWrite - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(&mBuffer[0], source + firstChunk, secondChunk * sizeof(float));
        }

        size_t newWriteIdx = (writeIdx + samplesToWrite) % mTotalCapacitySamples;
        mWriteIndex.store(newWriteIdx, std::memory_order_release);

        return samplesToWrite / mChannelCount;
    }

    /**
     * Read float PCM samples from the circular buffer.
     * Called by the AAudio real-time callback thread.
     */
    size_t read(float* destination, size_t frames) {
        if (!destination || frames == 0) return 0;

        size_t samplesRequested = frames * mChannelCount;
        size_t readIdx = mReadIndex.load(std::memory_order_relaxed);
        size_t writeIdx = mWriteIndex.load(std::memory_order_acquire);

        size_t samplesAvailable = 0;
        if (writeIdx >= readIdx) {
            samplesAvailable = writeIdx - readIdx;
        } else {
            samplesAvailable = (mTotalCapacitySamples - readIdx) + writeIdx;
        }

        size_t samplesToRead = std::min(samplesRequested, samplesAvailable);
        if (samplesToRead == 0) {
            std::memset(destination, 0, samplesRequested * sizeof(float));
            return 0;
        }

        size_t firstChunk = std::min(samplesToRead, mTotalCapacitySamples - readIdx);
        std::memcpy(destination, &mBuffer[readIdx], firstChunk * sizeof(float));

        size_t secondChunk = samplesToRead - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(destination + firstChunk, &mBuffer[0], secondChunk * sizeof(float));
        }

        if (samplesToRead < samplesRequested) {
            // Fill remaining with silence to prevent static / popping
            std::memset(destination + samplesToRead, 0, (samplesRequested - samplesToRead) * sizeof(float));
        }

        size_t newReadIdx = (readIdx + samplesToRead) % mTotalCapacitySamples;
        mReadIndex.store(newReadIdx, std::memory_order_release);

        return samplesToRead / mChannelCount;
    }

private:
    const uint32_t mChannelCount;
    const size_t mCapacityFrames;
    const size_t mTotalCapacitySamples;
    std::vector<float> mBuffer;
    std::atomic<size_t> mReadIndex;
    std::atomic<size_t> mWriteIndex;
};

} // namespace audio
} // namespace retro
