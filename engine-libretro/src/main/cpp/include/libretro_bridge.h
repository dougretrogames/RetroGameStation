#pragma once

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <vector>
#include <mutex>
#include <atomic>
#include <memory>
#include <string>
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

    // Callbacks invoked by active libretro core
    bool handleEnvironment(unsigned cmd, void* data);
    void handleVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch);
    void handleAudioSample(int16_t left, int16_t right);
    size_t handleAudioSampleBatch(const int16_t* data, size_t frames);
    void handleInputPoll();
    int16_t handleInputState(unsigned port, unsigned device, unsigned index, unsigned id);

    // Helpers
    const char* getSystemDirectory() const { return systemDirectory_.c_str(); }
    const char* getSaveDirectory() const { return saveDirectory_.c_str(); }
    void setDirectories(const char* systemDir, const char* saveDir);

private:
    LibretroBridge();
    ~LibretroBridge();

    void unloadCore();

    JavaVM* jvm_{nullptr};
    jobject globalBridgeObj_{nullptr};
    jmethodID audioCallbackMethod_{nullptr};

    ANativeWindow* nativeWindow_{nullptr};
    std::mutex renderMutex_;
    std::mutex stateMutex_;

    int sampleRate_{44100};
    std::atomic<bool> isRunning_{false};
    std::atomic<bool> isPaused_{false};
    bool isGameLoaded_{false};

    enum retro_pixel_format pixelFormat_{RETRO_PIXEL_FORMAT_RGB565};
    uint16_t joypadState_[2][16]{};
    int16_t analogAxes_[2][4]{}; // LX, LY, RX, RY

    std::string systemDirectory_;
    std::string saveDirectory_;
    std::string currentCorePath_;

    // Dynamic Libretro Core Handle & Function Pointers
    void* coreHandle_{nullptr};
    void (*core_init_)(void){nullptr};
    void (*core_deinit_)(void){nullptr};
    unsigned (*core_api_version_)(void){nullptr};
    void (*core_get_system_info_)(struct retro_system_info*){nullptr};
    void (*core_get_system_av_info_)(struct retro_system_av_info*){nullptr};
    void (*core_set_environment_)(retro_environment_t){nullptr};
    void (*core_set_video_refresh_)(retro_video_refresh_t){nullptr};
    void (*core_set_audio_sample_)(retro_audio_sample_t){nullptr};
    void (*core_set_audio_sample_batch_)(retro_audio_sample_batch_t){nullptr};
    void (*core_set_input_poll_)(retro_input_poll_t){nullptr};
    void (*core_set_input_state_)(retro_input_state_t){nullptr};
    void (*core_set_controller_port_device_)(unsigned, unsigned){nullptr};
    void (*core_reset_)(void){nullptr};
    void (*core_run_)(void){nullptr};
    size_t (*core_serialize_size_)(void){nullptr};
    bool (*core_serialize_)(void*, size_t){nullptr};
    bool (*core_unserialize_)(const void*, size_t){nullptr};
    bool (*core_load_game_)(const struct retro_game_info*){nullptr};
    void (*core_unload_game_)(void){nullptr};
};

} // namespace retrostation::libretro
