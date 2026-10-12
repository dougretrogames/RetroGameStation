#pragma once

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <cstdint>
#include <vector>
#include <mutex>
#include <atomic>
#include <memory>
#include "libretro.h"

#define LOG_TAG "RetroStation_Libretro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace retrostation::libretro {

class LibretroBridge {
public:
    static LibretroBridge& getInstance();

    bool initialize(JavaVM* vm, jobject bridgeObj, int sampleRate);
    bool setSurface(ANativeWindow* window);
    bool loadRom(const char* romPath, const char* corePath);
    void runFrame();
    void pause();
    void resume();
    void sendInput(int port, int key, bool isDown, float axisValue);
    std::vector<uint8_t> saveState(int slot);
    bool loadState(int slot, const uint8_t* data, size_t size);
    void destroy();

    // Callbacks from libretro core
    bool handleEnvironment(unsigned cmd, void* data);
    void handleVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch);
    size_t handleAudioSampleBatch(const int16_t* data, size_t frames);
    int16_t handleInputState(unsigned port, unsigned device, unsigned index, unsigned id);

private:
    LibretroBridge();
    ~LibretroBridge();

    JavaVM* jvm_{nullptr};
    jobject globalBridgeObj_{nullptr};
    jmethodID audioCallbackMethod_{nullptr};

    ANativeWindow* nativeWindow_{nullptr};
    std::mutex renderMutex_;
    std::mutex stateMutex_;

    int sampleRate_{44100};
    std::atomic<bool> isRunning_{false};
    std::atomic<bool> isPaused_{false};

    enum retro_pixel_format pixelFormat_{RETRO_PIXEL_FORMAT_RGB565};
    uint16_t joypadState_[2][16]{};
    int16_t analogAxes_[2][4]{}; // LX, LY, RX, RY
};

} // namespace retrostation::libretro
