/**
 * Retro Game Station - Low Latency AAudio Stream Manager Implementation
 */

#include "retro_aaudio_stream.h"
#include <android/log.h>
#include <dlfcn.h>
#include <thread>
#include <vector>
#include <cmath>

#define LOG_TAG "RetroAAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retro {
namespace audio {

// Static C-style callback trampolines
static aaudio_data_callback_result_t dataCallbackTrampoline(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames) {
    auto* manager = static_cast<AAudioStreamManager*>(userData);
    if (!manager) return AAUDIO_CALLBACK_RESULT_STOP;
    return manager->onAudioData(stream, audioData, numFrames);
}

static void errorCallbackTrampoline(
        AAudioStream* stream,
        void* userData,
        aaudio_result_t error) {
    auto* manager = static_cast<AAudioStreamManager*>(userData);
    if (manager) {
        manager->onAudioError(stream, error);
    }
}

AAudioStreamManager::AAudioStreamManager() = default;

AAudioStreamManager::~AAudioStreamManager() {
    destroy();
}

bool AAudioStreamManager::init(const AudioStreamConfig& config) {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    mConfig = config;

    // Buffer capacity: ~16384 frames allows ~340ms safety margin, typical latency target is ~10-20ms
    mRingBuffer = std::make_unique<AudioRingBuffer>(16384, config.channelCount);

    return openStream();
}

bool AAudioStreamManager::openStream() {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create AAudio stream builder: %s", AAudio_convertResultToText(result));
        mState.store(AudioBackendState::ERROR_STATE);
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    
    // Attempt exclusive mode first for rock-bottom latency, fallback to shared
    if (mConfig.exclusiveModeRequested) {
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    } else {
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    }

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, mConfig.channelCount);
    AAudioStreamBuilder_setSampleRate(builder, mConfig.sampleRate);
    
    // Dynamic lookup for API 28+ AAudio attributes to maintain API 26 compatibility
    typedef void (*FnSetUsage)(AAudioStreamBuilder*, aaudio_usage_t);
    typedef void (*FnSetContentType)(AAudioStreamBuilder*, aaudio_content_type_t);
    static auto fnSetUsage = reinterpret_cast<FnSetUsage>(dlsym(RTLD_DEFAULT, "AAudioStreamBuilder_setUsage"));
    static auto fnSetContentType = reinterpret_cast<FnSetContentType>(dlsym(RTLD_DEFAULT, "AAudioStreamBuilder_setContentType"));
    if (fnSetUsage) {
        fnSetUsage(builder, AAUDIO_USAGE_GAME);
    }
    if (fnSetContentType) {
        fnSetContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    }

    AAudioStreamBuilder_setDataCallback(builder, dataCallbackTrampoline, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallbackTrampoline, this);

    result = AAudioStreamBuilder_openStream(builder, &mStream);
    if (result != AAUDIO_OK && mConfig.exclusiveModeRequested) {
        LOGW("Exclusive sharing mode failed (%s). Retrying with SHARED mode...", AAudio_convertResultToText(result));
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &mStream);
    }

    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !mStream) {
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
        mStream = nullptr;
        mState.store(AudioBackendState::ERROR_STATE);
        return false;
    }

    mActualSampleRate = AAudioStream_getSampleRate(mStream);
    mActualBurstSize = AAudioStream_getFramesPerBurst(mStream);
    
    // Set buffer size to burst * bufferCapacityInBursts for optimal low latency
    int32_t targetBufferFrames = mActualBurstSize * mConfig.bufferCapacityInBursts;
    mActualBufferSizeInFrames = AAudioStream_setBufferSizeInFrames(mStream, targetBufferFrames);

    LOGI("AAudio stream opened successfully. SampleRate: %d, Burst: %d frames, BufferSize: %d frames, Sharing: %s",
         mActualSampleRate, mActualBurstSize, mActualBufferSizeInFrames,
         AAudioStream_getSharingMode(mStream) == AAUDIO_SHARING_MODE_EXCLUSIVE ? "EXCLUSIVE" : "SHARED");

    mState.store(AudioBackendState::INITIALIZED);
    return true;
}

bool AAudioStreamManager::start() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream) return false;

    aaudio_stream_state_t streamState = AAudioStream_getState(mStream);
    if (streamState == AAUDIO_STREAM_STATE_STARTED) {
        mState.store(AudioBackendState::RUNNING);
        return true;
    }

    aaudio_result_t result = AAudioStream_requestStart(mStream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream: %s", AAudio_convertResultToText(result));
        return false;
    }

    mState.store(AudioBackendState::RUNNING);
    LOGI("AAudio stream started");
    return true;
}

bool AAudioStreamManager::pause() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream) return false;

    aaudio_stream_state_t streamState = AAudioStream_getState(mStream);
    if (streamState == AAUDIO_STREAM_STATE_PAUSED) {
        mState.store(AudioBackendState::PAUSED);
        return true;
    }

    aaudio_result_t result = AAudioStream_requestPause(mStream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to pause AAudio stream: %s", AAudio_convertResultToText(result));
        return false;
    }

    mState.store(AudioBackendState::PAUSED);
    LOGI("AAudio stream paused");
    return true;
}

bool AAudioStreamManager::flush() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mRingBuffer) {
        mRingBuffer->reset();
    }
    if (mStream) {
        AAudioStream_requestFlush(mStream);
    }
    return true;
}

void AAudioStreamManager::stop() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        AAudioStream_requestStop(mStream);
        mState.store(AudioBackendState::STOPPED);
        LOGI("AAudio stream stopped");
    }
}

void AAudioStreamManager::closeStream() {
    if (mStream) {
        AAudioStream_close(mStream);
        mStream = nullptr;
    }
}

void AAudioStreamManager::destroy() {
    mIsDestroyed.store(true);
    if (mRecoveryThread.joinable() && std::this_thread::get_id() != mRecoveryThread.get_id()) {
        mRecoveryThread.join();
    }
    std::lock_guard<std::mutex> lock(mStreamMutex);
    closeStream();
    if (mRingBuffer) {
        mRingBuffer->reset();
    }
    mState.store(AudioBackendState::UNINITIALIZED);
}

size_t AAudioStreamManager::writeSamples(const float* buffer, size_t frames) {
    if (!mRingBuffer || frames == 0) return 0;
    return mRingBuffer->write(buffer, frames);
}

size_t AAudioStreamManager::writeSamplesInterleavedI16(const int16_t* buffer, size_t frames) {
    if (!mRingBuffer || !buffer || frames == 0) return 0;

    const size_t totalSamples = frames * mConfig.channelCount;
    // Stack allocation for typical bursts, dynamic heap fallback for large chunks
    constexpr size_t STACK_LIMIT = 2048;
    float stackBuffer[STACK_LIMIT];
    std::vector<float> heapBuffer;
    float* floatTarget = stackBuffer;

    if (totalSamples > STACK_LIMIT) {
        heapBuffer.resize(totalSamples);
        floatTarget = heapBuffer.data();
    }

    // Convert int16 [-32768, 32767] to float [-1.0f, 1.0f]
    constexpr float scaleFactor = 1.0f / 32768.0f;
    for (size_t i = 0; i < totalSamples; ++i) {
        floatTarget[i] = static_cast<float>(buffer[i]) * scaleFactor;
    }

    return mRingBuffer->write(floatTarget, frames);
}

void AAudioStreamManager::setMuted(bool muted) {
    mIsMuted.store(muted, std::memory_order_relaxed);
}

bool AAudioStreamManager::isMuted() const {
    return mIsMuted.load(std::memory_order_relaxed);
}

void AAudioStreamManager::setVolumeGain(float gain) {
    mVolumeGain.store(std::max(0.0f, std::min(gain, 2.0f)), std::memory_order_relaxed);
}

void AAudioStreamManager::setFastForwardActive(bool active, bool muteOnFastForward) {
    mFastForwardMute.store(active && muteOnFastForward, std::memory_order_relaxed);
}

int32_t AAudioStreamManager::getXRunCount() const {
    if (!mStream) return mXRunCount.load();
    return AAudioStream_getXRunCount(mStream);
}

double AAudioStreamManager::getCurrentLatencyMillis() const {
    if (!mStream || mActualSampleRate <= 0) return 0.0;
    int32_t bufferSize = AAudioStream_getBufferSizeInFrames(mStream);
    return (static_cast<double>(bufferSize) / mActualSampleRate) * 1000.0;
}

aaudio_data_callback_result_t AAudioStreamManager::onAudioData(
        AAudioStream* /* stream */,
        void* audioData,
        int32_t numFrames) {
    auto* outBuffer = static_cast<float*>(audioData);

    if (mIsMuted.load(std::memory_order_relaxed) || mFastForwardMute.load(std::memory_order_relaxed)) {
        std::memset(outBuffer, 0, numFrames * mConfig.channelCount * sizeof(float));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    size_t framesRead = mRingBuffer->read(outBuffer, numFrames);

    // Dynamic rate control & underrun protection
    if (framesRead < static_cast<size_t>(numFrames)) {
        mXRunCount.fetch_add(1, std::memory_order_relaxed);
    }

    // Apply master volume gain if not 1.0f
    float gain = mVolumeGain.load(std::memory_order_relaxed);
    if (std::abs(gain - 1.0f) > 0.001f) {
        const size_t totalSamples = numFrames * mConfig.channelCount;
        for (size_t i = 0; i < totalSamples; ++i) {
            outBuffer[i] = std::max(-1.0f, std::min(outBuffer[i] * gain, 1.0f));
        }
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioStreamManager::onAudioError(AAudioStream* stream, aaudio_result_t error) {
    LOGW("AAudio stream error received: %s (code: %d)", AAudio_convertResultToText(error), error);

    if (error == AAUDIO_ERROR_DISCONNECTED) {
        LOGI("Audio device disconnected (headphones or Bluetooth). Initiating stream recovery...");
        recoverStream();
    }
}

void AAudioStreamManager::recoverStream() {
    if (mIsDestroyed.load()) return;

    bool expected = false;
    if (!mReconnecting.compare_exchange_strong(expected, true)) {
        return; // Already recovering
    }

    if (mRecoveryThread.joinable() && std::this_thread::get_id() != mRecoveryThread.get_id()) {
        mRecoveryThread.join();
    }

    mRecoveryThread = std::thread([this]() {
        LOGI("Stream recovery thread started");
        if (!mIsDestroyed.load()) {
            std::lock_guard<std::mutex> lock(mStreamMutex);
            if (!mIsDestroyed.load()) {
                closeStream();
                if (openStream()) {
                    if (mState.load() == AudioBackendState::RUNNING || mState.load() == AudioBackendState::INITIALIZED) {
                        AAudioStream_requestStart(mStream);
                        mState.store(AudioBackendState::RUNNING);
                        LOGI("Stream recovery successfully restored playback");
                    }
                } else {
                    LOGE("Stream recovery failed to reopen stream");
                }
            }
        }
        mReconnecting.store(false);
    });
}

} // namespace audio
} // namespace retro
