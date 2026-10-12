#pragma once

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <cstdint>
#include <vector>
#include <mutex>
#include <atomic>
#include "ppsspp_core.h"

#define LOG_TAG "RetroStation_PPSSPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace retrostation::ppsspp {

class PPSSPPBridge {
public:
    static PPSSPPBridge& getInstance();

    bool initialize(JavaVM* vm, jobject bridgeObj, int sampleRate);
    bool setSurface(ANativeWindow* window);
    bool loadRom(const char* romPath);
    void runFrame();
    void pause();
    void resume();
    void sendInput(int port, int key, bool isDown, float axisValue);
    std::vector<uint8_t> saveState(int slot);
    bool loadState(int slot, const uint8_t* data, size_t size);
    void destroy();

private:
    PPSSPPBridge();
    ~PPSSPPBridge();

    JavaVM* jvm_{nullptr};
    jobject globalBridgeObj_{nullptr};
    jmethodID audioCallbackMethod_{nullptr};

    ANativeWindow* nativeWindow_{nullptr};
    std::mutex renderMutex_;
    std::mutex stateMutex_;

    int sampleRate_{44100};
    std::atomic<bool> isRunning_{false};
    std::atomic<bool> isPaused_{false};

    uint32_t buttonsDown_{0};
    float analogX_{0.0f};
    float analogY_{0.0f};
};

} // namespace retrostation::ppsspp
