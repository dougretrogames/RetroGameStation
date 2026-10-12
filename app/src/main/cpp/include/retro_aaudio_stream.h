/**
 * Retro Game Station - Low Latency AAudio Stream Manager
 * High performance, zero-allocation real-time audio pipeline for Libretro, PPSSPP, and Mupen64Plus.
 */

#pragma once

#include <aaudio/AAudio.h>
#include <memory>
#include <atomic>
#include <mutex>
#include <thread>
#include <functional>
#include "audio_ring_buffer.h"

namespace retro {
namespace audio {

enum class AudioBackendState {
    UNINITIALIZED,
    INITIALIZED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPED,
    ERROR_STATE
};

struct AudioStreamConfig {
    int32_t sampleRate = 48000;
    int32_t channelCount = 2; // Stereo
    int32_t optimalFramesPerBurst = 192;
    int32_t bufferCapacityInBursts = 2; // Low-latency double buffering
    bool exclusiveModeRequested = true;
    bool dynamicRateControl = true;
};

class AAudioStreamManager {
public:
    AAudioStreamManager();
    ~AAudioStreamManager();

    bool init(const AudioStreamConfig& config);
    bool start();
    bool pause();
    bool flush();
    void stop();
    void destroy();

    // Emulation core writes audio PCM samples here
    size_t writeSamples(const float* buffer, size_t frames);
    size_t writeSamplesInterleavedI16(const int16_t* buffer, size_t frames);

    // Dynamic controls
    void setMuted(bool muted);
    bool isMuted() const;
    void setVolumeGain(float gain); // 0.0f - 2.0f
    void setFastForwardActive(bool active, bool muteOnFastForward = false);

    // Diagnostics & metrics
    int32_t getXRunCount() const;
    double getCurrentLatencyMillis() const;
    int32_t getSampleRate() const { return mActualSampleRate; }
    AudioBackendState getState() const { return mState.load(); }

    // Callback handlers
    aaudio_data_callback_result_t onAudioData(AAudioStream* stream, void* audioData, int32_t numFrames);
    void onAudioError(AAudioStream* stream, aaudio_result_t error);

private:
    bool openStream();
    void closeStream();
    void recoverStream();

    AAudioStream* mStream = nullptr;
    AudioStreamConfig mConfig;
    std::unique_ptr<AudioRingBuffer> mRingBuffer;

    std::atomic<AudioBackendState> mState{AudioBackendState::UNINITIALIZED};
    std::atomic<bool> mIsMuted{false};
    std::atomic<bool> mFastForwardMute{false};
    std::atomic<float> mVolumeGain{1.0f};
    std::atomic<int32_t> mXRunCount{0};

    int32_t mActualSampleRate = 48000;
    int32_t mActualBurstSize = 192;
    int32_t mActualBufferSizeInFrames = 384;

    mutable std::mutex mStreamMutex;
    std::atomic<bool> mReconnecting{false};
    std::atomic<bool> mIsDestroyed{false};
    std::thread mRecoveryThread;
};

} // namespace audio
} // namespace retro
