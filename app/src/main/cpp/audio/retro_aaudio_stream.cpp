/**
 * Retro Game Station - Low Latency AAudio Stream Manager Implementation
 * Dynamically loads libaaudio.so at runtime to ensure full binary compatibility
 * with Android API 24+ (Android 7.0 Nougat) while providing peak low-latency audio on API 26+.
 */

#include "retro_aaudio_stream.h"
#include <android/log.h>
#include <dlfcn.h>
#include <thread>
#include <vector>
#include <cmath>
#include <cstring>

#define LOG_TAG "RetroAAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retro {
namespace audio {

namespace {

struct AAudioDispatch {
    void* handle = nullptr;

    aaudio_result_t (*createStreamBuilder)(AAudioStreamBuilder**) = nullptr;
    const char* (*convertResultToText)(aaudio_result_t) = nullptr;
    void (*builder_setDirection)(AAudioStreamBuilder*, aaudio_direction_t) = nullptr;
    void (*builder_setPerformanceMode)(AAudioStreamBuilder*, aaudio_performance_mode_t) = nullptr;
    void (*builder_setSharingMode)(AAudioStreamBuilder*, aaudio_sharing_mode_t) = nullptr;
    void (*builder_setFormat)(AAudioStreamBuilder*, aaudio_format_t) = nullptr;
    void (*builder_setChannelCount)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*builder_setSampleRate)(AAudioStreamBuilder*, int32_t) = nullptr;
    void (*builder_setUsage)(AAudioStreamBuilder*, aaudio_usage_t) = nullptr;
    void (*builder_setContentType)(AAudioStreamBuilder*, aaudio_content_type_t) = nullptr;
    void (*builder_setDataCallback)(AAudioStreamBuilder*, AAudioStream_dataCallback, void*) = nullptr;
    void (*builder_setErrorCallback)(AAudioStreamBuilder*, AAudioStream_errorCallback, void*) = nullptr;
    aaudio_result_t (*builder_openStream)(AAudioStreamBuilder*, AAudioStream**) = nullptr;
    aaudio_result_t (*builder_delete)(AAudioStreamBuilder*) = nullptr;

    int32_t (*stream_getSampleRate)(AAudioStream*) = nullptr;
    int32_t (*stream_getFramesPerBurst)(AAudioStream*) = nullptr;
    int32_t (*stream_setBufferSizeInFrames)(AAudioStream*, int32_t) = nullptr;
    aaudio_sharing_mode_t (*stream_getSharingMode)(AAudioStream*) = nullptr;
    aaudio_stream_state_t (*stream_getState)(AAudioStream*) = nullptr;
    aaudio_result_t (*stream_requestStart)(AAudioStream*) = nullptr;
    aaudio_result_t (*stream_requestPause)(AAudioStream*) = nullptr;
    aaudio_result_t (*stream_requestFlush)(AAudioStream*) = nullptr;
    aaudio_result_t (*stream_requestStop)(AAudioStream*) = nullptr;
    aaudio_result_t (*stream_close)(AAudioStream*) = nullptr;
    int32_t (*stream_getXRunCount)(AAudioStream*) = nullptr;
    int32_t (*stream_getBufferSizeInFrames)(AAudioStream*) = nullptr;

    bool load() {
        if (handle) return true;
        handle = dlopen("libaaudio.so", RTLD_NOW);
        if (!handle) {
            return false;
        }

        #define LOAD_AAUDIO_SYM(target, name) \
            target = reinterpret_cast<decltype(target)>(dlsym(handle, name));

        LOAD_AAUDIO_SYM(createStreamBuilder, "AAudio_createStreamBuilder");
        LOAD_AAUDIO_SYM(convertResultToText, "AAudio_convertResultToText");
        LOAD_AAUDIO_SYM(builder_setDirection, "AAudioStreamBuilder_setDirection");
        LOAD_AAUDIO_SYM(builder_setPerformanceMode, "AAudioStreamBuilder_setPerformanceMode");
        LOAD_AAUDIO_SYM(builder_setSharingMode, "AAudioStreamBuilder_setSharingMode");
        LOAD_AAUDIO_SYM(builder_setFormat, "AAudioStreamBuilder_setFormat");
        LOAD_AAUDIO_SYM(builder_setChannelCount, "AAudioStreamBuilder_setChannelCount");
        LOAD_AAUDIO_SYM(builder_setSampleRate, "AAudioStreamBuilder_setSampleRate");
        LOAD_AAUDIO_SYM(builder_setUsage, "AAudioStreamBuilder_setUsage");
        LOAD_AAUDIO_SYM(builder_setContentType, "AAudioStreamBuilder_setContentType");
        LOAD_AAUDIO_SYM(builder_setDataCallback, "AAudioStreamBuilder_setDataCallback");
        LOAD_AAUDIO_SYM(builder_setErrorCallback, "AAudioStreamBuilder_setErrorCallback");
        LOAD_AAUDIO_SYM(builder_openStream, "AAudioStreamBuilder_openStream");
        LOAD_AAUDIO_SYM(builder_delete, "AAudioStreamBuilder_delete");

        LOAD_AAUDIO_SYM(stream_getSampleRate, "AAudioStream_getSampleRate");
        LOAD_AAUDIO_SYM(stream_getFramesPerBurst, "AAudioStream_getFramesPerBurst");
        LOAD_AAUDIO_SYM(stream_setBufferSizeInFrames, "AAudioStream_setBufferSizeInFrames");
        LOAD_AAUDIO_SYM(stream_getSharingMode, "AAudioStream_getSharingMode");
        LOAD_AAUDIO_SYM(stream_getState, "AAudioStream_getState");
        LOAD_AAUDIO_SYM(stream_requestStart, "AAudioStream_requestStart");
        LOAD_AAUDIO_SYM(stream_requestPause, "AAudioStream_requestPause");
        LOAD_AAUDIO_SYM(stream_requestFlush, "AAudioStream_requestFlush");
        LOAD_AAUDIO_SYM(stream_requestStop, "AAudioStream_requestStop");
        LOAD_AAUDIO_SYM(stream_close, "AAudioStream_close");
        LOAD_AAUDIO_SYM(stream_getXRunCount, "AAudioStream_getXRunCount");
        LOAD_AAUDIO_SYM(stream_getBufferSizeInFrames, "AAudioStream_getBufferSizeInFrames");

        #undef LOAD_AAUDIO_SYM

        return (createStreamBuilder && builder_openStream && stream_requestStart && stream_close);
    }
};

static AAudioDispatch g_aaudio;

} // anonymous namespace

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
    if (!g_aaudio.load()) {
        LOGW("libaaudio.so is not available on this platform (requires Android 8.0+ / API 26+). Audio will be disabled gracefully.");
        mState.store(AudioBackendState::ERROR_STATE);
        return false;
    }

    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = g_aaudio.createStreamBuilder(&builder);
    if (result != AAUDIO_OK || !builder) {
        const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(result) : "Unknown Error";
        LOGE("Failed to create AAudio stream builder: %s", errText);
        mState.store(AudioBackendState::ERROR_STATE);
        return false;
    }

    g_aaudio.builder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    g_aaudio.builder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    
    // Attempt exclusive mode first for rock-bottom latency, fallback to shared
    if (mConfig.exclusiveModeRequested) {
        g_aaudio.builder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    } else {
        g_aaudio.builder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    }

    g_aaudio.builder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    g_aaudio.builder_setChannelCount(builder, mConfig.channelCount);
    g_aaudio.builder_setSampleRate(builder, mConfig.sampleRate);
    
    if (g_aaudio.builder_setUsage) {
        g_aaudio.builder_setUsage(builder, AAUDIO_USAGE_GAME);
    }
    if (g_aaudio.builder_setContentType) {
        g_aaudio.builder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    }

    g_aaudio.builder_setDataCallback(builder, dataCallbackTrampoline, this);
    g_aaudio.builder_setErrorCallback(builder, errorCallbackTrampoline, this);

    result = g_aaudio.builder_openStream(builder, &mStream);
    if (result != AAUDIO_OK && mConfig.exclusiveModeRequested) {
        const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(result) : "Unknown";
        LOGW("Exclusive sharing mode failed (%s). Retrying with SHARED mode...", errText);
        g_aaudio.builder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = g_aaudio.builder_openStream(builder, &mStream);
    }

    g_aaudio.builder_delete(builder);

    if (result != AAUDIO_OK || !mStream) {
        const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(result) : "Unknown";
        LOGE("Failed to open AAudio stream: %s", errText);
        mStream = nullptr;
        mState.store(AudioBackendState::ERROR_STATE);
        return false;
    }

    mActualSampleRate = g_aaudio.stream_getSampleRate(mStream);
    mActualBurstSize = g_aaudio.stream_getFramesPerBurst(mStream);
    
    // Set buffer size to burst * bufferCapacityInBursts for optimal low latency
    int32_t targetBufferFrames = mActualBurstSize * mConfig.bufferCapacityInBursts;
    mActualBufferSizeInFrames = g_aaudio.stream_setBufferSizeInFrames(mStream, targetBufferFrames);

    LOGI("AAudio stream opened successfully. SampleRate: %d, Burst: %d frames, BufferSize: %d frames, Sharing: %s",
         mActualSampleRate, mActualBurstSize, mActualBufferSizeInFrames,
         g_aaudio.stream_getSharingMode(mStream) == AAUDIO_SHARING_MODE_EXCLUSIVE ? "EXCLUSIVE" : "SHARED");

    mState.store(AudioBackendState::INITIALIZED);
    return true;
}

bool AAudioStreamManager::start() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream || !g_aaudio.stream_getState) return false;

    aaudio_stream_state_t streamState = g_aaudio.stream_getState(mStream);
    if (streamState == AAUDIO_STREAM_STATE_STARTED) {
        mState.store(AudioBackendState::RUNNING);
        return true;
    }

    aaudio_result_t result = g_aaudio.stream_requestStart(mStream);
    if (result != AAUDIO_OK) {
        const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(result) : "Unknown";
        LOGE("Failed to start AAudio stream: %s", errText);
        return false;
    }

    mState.store(AudioBackendState::RUNNING);
    LOGI("AAudio stream started");
    return true;
}

bool AAudioStreamManager::pause() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream || !g_aaudio.stream_getState) return false;

    aaudio_stream_state_t streamState = g_aaudio.stream_getState(mStream);
    if (streamState == AAUDIO_STREAM_STATE_PAUSED) {
        mState.store(AudioBackendState::PAUSED);
        return true;
    }

    aaudio_result_t result = g_aaudio.stream_requestPause(mStream);
    if (result != AAUDIO_OK) {
        const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(result) : "Unknown";
        LOGE("Failed to pause AAudio stream: %s", errText);
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
    if (mStream && g_aaudio.stream_requestFlush) {
        g_aaudio.stream_requestFlush(mStream);
    }
    return true;
}

void AAudioStreamManager::stop() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream && g_aaudio.stream_requestStop) {
        g_aaudio.stream_requestStop(mStream);
        mState.store(AudioBackendState::STOPPED);
        LOGI("AAudio stream stopped");
    }
}

void AAudioStreamManager::closeStream() {
    if (mStream && g_aaudio.stream_close) {
        g_aaudio.stream_close(mStream);
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
    if (!mStream || !g_aaudio.stream_getXRunCount) return mXRunCount.load();
    return g_aaudio.stream_getXRunCount(mStream);
}

double AAudioStreamManager::getCurrentLatencyMillis() const {
    if (!mStream || mActualSampleRate <= 0 || !g_aaudio.stream_getBufferSizeInFrames) return 0.0;
    int32_t bufferSize = g_aaudio.stream_getBufferSizeInFrames(mStream);
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
    const char* errText = g_aaudio.convertResultToText ? g_aaudio.convertResultToText(error) : "Unknown";
    LOGW("AAudio stream error received: %s (code: %d)", errText, error);

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
                        if (g_aaudio.stream_requestStart) {
                            g_aaudio.stream_requestStart(mStream);
                        }
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
