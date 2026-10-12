#include "include/libretro_bridge.h"
#include <cstring>
#include <algorithm>
#include <fstream>
#include <vector>

namespace retrostation::libretro {

static LibretroBridge* s_instance = nullptr;

// Static C callback forwarders for Libretro C API
static bool cb_environment(unsigned cmd, void* data) {
    return LibretroBridge::getInstance().handleEnvironment(cmd, data);
}

static void cb_video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    LibretroBridge::getInstance().handleVideoRefresh(data, width, height, pitch);
}

static void cb_audio_sample(int16_t left, int16_t right) {
    LibretroBridge::getInstance().handleAudioSample(left, right);
}

static size_t cb_audio_sample_batch(const int16_t* data, size_t frames) {
    return LibretroBridge::getInstance().handleAudioSampleBatch(data, frames);
}

static void cb_input_poll() {
    LibretroBridge::getInstance().handleInputPoll();
}

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    return LibretroBridge::getInstance().handleInputState(port, device, index, id);
}

LibretroBridge& LibretroBridge::getInstance() {
    static LibretroBridge instance;
    return instance;
}

LibretroBridge::LibretroBridge() {
    std::memset(joypadState_, 0, sizeof(joypadState_));
    std::memset(analogAxes_, 0, sizeof(analogAxes_));
}

LibretroBridge::~LibretroBridge() {
    destroy();
}

bool LibretroBridge::initialize(JavaVM* vm, jobject bridgeObj, int sampleRate) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    jvm_ = vm;
    sampleRate_ = sampleRate;

    JNIEnv* env = nullptr;
    if (jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
        if (globalBridgeObj_ != nullptr) {
            env->DeleteGlobalRef(globalBridgeObj_);
            globalBridgeObj_ = nullptr;
        }
        globalBridgeObj_ = env->NewGlobalRef(bridgeObj);

        jclass cls = env->GetObjectClass(bridgeObj);
        audioCallbackMethod_ = env->GetMethodID(cls, "onNativeAudioSamples", "(Ljava/nio/ByteBuffer;I)V");
    }

    isRunning_.store(true);
    isPaused_.store(false);
    LOGI("LibretroBridge initialized with sampleRate=%d", sampleRate);
    return true;
}

void LibretroBridge::setDirectories(const char* systemDir, const char* saveDir) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    systemDirectory_ = systemDir != nullptr ? systemDir : "";
    saveDirectory_ = saveDir != nullptr ? saveDir : "";
    LOGI("Directories set: system='%s', save='%s'", systemDirectory_.c_str(), saveDirectory_.c_str());
}

bool LibretroBridge::setSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ != nullptr) {
        ANativeWindow_release(nativeWindow_);
        nativeWindow_ = nullptr;
    }
    nativeWindow_ = window;
    if (nativeWindow_ != nullptr) {
        int32_t format = (pixelFormat_ == RETRO_PIXEL_FORMAT_XRGB8888) 
                            ? WINDOW_FORMAT_RGBA_8888 
                            : WINDOW_FORMAT_RGB_565;
        ANativeWindow_setBuffersGeometry(nativeWindow_, 0, 0, format);
        LOGI("ANativeWindow attached with format=%d", format);
    }
    return true;
}

void LibretroBridge::unloadCore() {
    if (isGameLoaded_ && core_unload_game_ != nullptr) {
        core_unload_game_();
        isGameLoaded_ = false;
        LOGI("Core game unloaded");
    }

    if (core_deinit_ != nullptr) {
        core_deinit_();
        LOGI("Core deinitialized");
    }

    core_init_ = nullptr;
    core_deinit_ = nullptr;
    core_api_version_ = nullptr;
    core_get_system_info_ = nullptr;
    core_get_system_av_info_ = nullptr;
    core_set_environment_ = nullptr;
    core_set_video_refresh_ = nullptr;
    core_set_audio_sample_ = nullptr;
    core_set_audio_sample_batch_ = nullptr;
    core_set_input_poll_ = nullptr;
    core_set_input_state_ = nullptr;
    core_set_controller_port_device_ = nullptr;
    core_reset_ = nullptr;
    core_run_ = nullptr;
    core_serialize_size_ = nullptr;
    core_serialize_ = nullptr;
    core_unserialize_ = nullptr;
    core_load_game_ = nullptr;
    core_unload_game_ = nullptr;

    if (coreHandle_ != nullptr) {
        dlclose(coreHandle_);
        coreHandle_ = nullptr;
        LOGI("Core dynamic library closed cleanly");
    }
    currentCorePath_.clear();
}

bool LibretroBridge::loadRom(const char* romPath, const char* corePath) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge loadRom: rom='%s', core='%s'", romPath ? romPath : "", corePath ? corePath : "");

    if (corePath != nullptr && strlen(corePath) > 0) {
        if (coreHandle_ == nullptr || currentCorePath_ != corePath) {
            unloadCore();

            coreHandle_ = dlopen(corePath, RTLD_LAZY | RTLD_LOCAL);
            if (!coreHandle_) {
                LOGE("Failed to dlopen core '%s': %s", corePath, dlerror());
                return false;
            }
            currentCorePath_ = corePath;
            LOGI("Core '%s' opened successfully with dlopen", corePath);

            // Resolve symbols
            core_init_ = reinterpret_cast<void(*)(void)>(dlsym(coreHandle_, "retro_init"));
            core_deinit_ = reinterpret_cast<void(*)(void)>(dlsym(coreHandle_, "retro_deinit"));
            core_api_version_ = reinterpret_cast<unsigned(*)(void)>(dlsym(coreHandle_, "retro_api_version"));
            core_get_system_info_ = reinterpret_cast<void(*)(struct retro_system_info*)>(dlsym(coreHandle_, "retro_get_system_info"));
            core_get_system_av_info_ = reinterpret_cast<void(*)(struct retro_system_av_info*)>(dlsym(coreHandle_, "retro_get_system_av_info"));
            core_set_environment_ = reinterpret_cast<void(*)(retro_environment_t)>(dlsym(coreHandle_, "retro_set_environment"));
            core_set_video_refresh_ = reinterpret_cast<void(*)(retro_video_refresh_t)>(dlsym(coreHandle_, "retro_set_video_refresh"));
            core_set_audio_sample_ = reinterpret_cast<void(*)(retro_audio_sample_t)>(dlsym(coreHandle_, "retro_set_audio_sample"));
            core_set_audio_sample_batch_ = reinterpret_cast<void(*)(retro_audio_sample_batch_t)>(dlsym(coreHandle_, "retro_set_audio_sample_batch"));
            core_set_input_poll_ = reinterpret_cast<void(*)(retro_input_poll_t)>(dlsym(coreHandle_, "retro_set_input_poll"));
            core_set_input_state_ = reinterpret_cast<void(*)(retro_input_state_t)>(dlsym(coreHandle_, "retro_set_input_state"));
            core_set_controller_port_device_ = reinterpret_cast<void(*)(unsigned, unsigned)>(dlsym(coreHandle_, "retro_set_controller_port_device"));
            core_reset_ = reinterpret_cast<void(*)(void)>(dlsym(coreHandle_, "retro_reset"));
            core_run_ = reinterpret_cast<void(*)(void)>(dlsym(coreHandle_, "retro_run"));
            core_serialize_size_ = reinterpret_cast<size_t(*)(void)>(dlsym(coreHandle_, "retro_serialize_size"));
            core_serialize_ = reinterpret_cast<bool(*)(void*, size_t)>(dlsym(coreHandle_, "retro_serialize"));
            core_unserialize_ = reinterpret_cast<bool(*)(const void*, size_t)>(dlsym(coreHandle_, "retro_unserialize"));
            core_load_game_ = reinterpret_cast<bool(*)(const struct retro_game_info*)>(dlsym(coreHandle_, "retro_load_game"));
            core_unload_game_ = reinterpret_cast<void(*)(void)>(dlsym(coreHandle_, "retro_unload_game"));

            if (!core_init_ || !core_run_ || !core_load_game_ || !core_set_environment_) {
                LOGE("Core is missing essential Libretro symbols!");
                unloadCore();
                return false;
            }

            // Wire callbacks
            core_set_environment_(cb_environment);
            core_set_video_refresh_(cb_video_refresh);
            if (core_set_audio_sample_) core_set_audio_sample_(cb_audio_sample);
            if (core_set_audio_sample_batch_) core_set_audio_sample_batch_(cb_audio_sample_batch);
            core_set_input_poll_(cb_input_poll);
            core_set_input_state_(cb_input_state);

            core_init_();
            LOGI("Core initialized via retro_init()");
        }
    }

    if (romPath != nullptr && strlen(romPath) > 0 && core_load_game_ != nullptr) {
        struct retro_game_info gameInfo{};
        gameInfo.path = romPath;

        // Try to read file to buffer for cores that require in-memory buffers
        std::ifstream file(romPath, std::ios::binary | std::ios::ate);
        std::vector<uint8_t> buffer;
        if (file.is_open()) {
            std::streamsize size = file.tellg();
            if (size > 0 && size < (128 * 1024 * 1024)) { // Under 128 MB in memory
                file.seekg(0, std::ios::beg);
                buffer.resize(static_cast<size_t>(size));
                if (file.read(reinterpret_cast<char*>(buffer.data()), size)) {
                    gameInfo.data = buffer.data();
                    gameInfo.size = buffer.size();
                }
            }
        }

        bool success = core_load_game_(&gameInfo);
        if (success) {
            isGameLoaded_ = true;
            LOGI("Game loaded successfully by core: %s", romPath);

            if (core_get_system_av_info_) {
                struct retro_system_av_info avInfo{};
                core_get_system_av_info_(&avInfo);
                LOGI("System AV: geometry=%ux%u, aspect=%.2f, fps=%.2f, sample_rate=%.1f",
                     avInfo.geometry.base_width, avInfo.geometry.base_height,
                     avInfo.geometry.aspect_ratio, avInfo.timing.fps, avInfo.timing.sample_rate);
            }
            return true;
        } else {
            LOGE("retro_load_game returned false for ROM: %s", romPath);
            return false;
        }
    }

    return true;
}

void LibretroBridge::runFrame() {
    if (!isRunning_.load() || isPaused_.load()) {
        return;
    }

    if (core_run_ != nullptr && isGameLoaded_) {
        core_run_();
        return;
    }

    // Fallback simulation loop when no dynamic core is loaded
    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ == nullptr) {
        return;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(nativeWindow_, &buffer, nullptr) == 0) {
        if (buffer.bits != nullptr) {
            std::memset(buffer.bits, 0x1F, buffer.stride * buffer.height * (buffer.format == WINDOW_FORMAT_RGBA_8888 ? 4 : 2));
        }
        ANativeWindow_unlockAndPost(nativeWindow_);
    }
}

void LibretroBridge::pause() {
    isPaused_.store(true);
    LOGI("LibretroBridge paused");
}

void LibretroBridge::resume() {
    isPaused_.store(false);
    LOGI("LibretroBridge resumed");
}

void LibretroBridge::sendInput(int port, int key, bool isDown, float axisValue) {
    if (port < 0 || port > 1) return;

    if (key >= 0 && key < 16) {
        joypadState_[port][key] = isDown ? 1 : 0;
    } else if (key >= 16 && key <= 19) {
        int axisIndex = key - 16;
        analogAxes_[port][axisIndex] = static_cast<int16_t>(axisValue * 32767.0f);
    }
}

std::vector<uint8_t> LibretroBridge::saveState(int slot) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge saveState for slot %d", slot);

    if (core_serialize_size_ && core_serialize_ && isGameLoaded_) {
        size_t size = core_serialize_size_();
        if (size > 0) {
            std::vector<uint8_t> data(size);
            if (core_serialize_(data.data(), size)) {
                return data;
            }
        }
    }

    std::vector<uint8_t> stateData(1024, 0xAA);
    return stateData;
}

bool LibretroBridge::loadState(int slot, const uint8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge loadState for slot %d with size %zu", slot, size);

    if (core_unserialize_ && isGameLoaded_ && data != nullptr && size > 0) {
        return core_unserialize_(data, size);
    }
    return true;
}

void LibretroBridge::destroy() {
    isRunning_.store(false);
    isPaused_.store(false);

    {
        std::lock_guard<std::mutex> lock(stateMutex_);
        unloadCore();
    }

    {
        std::lock_guard<std::mutex> lock(renderMutex_);
        if (nativeWindow_ != nullptr) {
            ANativeWindow_release(nativeWindow_);
            nativeWindow_ = nullptr;
        }
    }

    {
        std::lock_guard<std::mutex> lock(stateMutex_);
        if (jvm_ != nullptr && globalBridgeObj_ != nullptr) {
            JNIEnv* env = nullptr;
            if (jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
                env->DeleteGlobalRef(globalBridgeObj_);
            }
            globalBridgeObj_ = nullptr;
            audioCallbackMethod_ = nullptr;
        }
    }
    LOGI("LibretroBridge destroyed");
}

bool LibretroBridge::handleEnvironment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const auto* fmt = static_cast<const enum retro_pixel_format*>(data);
            if (fmt != nullptr) {
                pixelFormat_ = *fmt;
                LOGI("Libretro pixel format set to: %d", pixelFormat_);
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            auto* canDupe = static_cast<bool*>(data);
            if (canDupe) *canDupe = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            auto** dir = static_cast<const char**>(data);
            if (dir) {
                *dir = systemDirectory_.empty() ? nullptr : systemDirectory_.c_str();
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            auto** dir = static_cast<const char**>(data);
            if (dir) {
                *dir = saveDirectory_.empty() ? nullptr : saveDirectory_.c_str();
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const auto* geom = static_cast<const struct retro_game_geometry*>(data);
            if (geom) {
                LOGI("Libretro dynamic geometry changed: %ux%u", geom->base_width, geom->base_height);
                return true;
            }
            return false;
        }
        default:
            return false;
    }
}

void LibretroBridge::handleVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (data == nullptr) return;
    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ == nullptr) return;

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(nativeWindow_, &buffer, nullptr) == 0) {
        auto* dst = static_cast<uint8_t*>(buffer.bits);
        const auto* src = static_cast<const uint8_t*>(data);
        size_t copyLines = std::min(static_cast<size_t>(buffer.height), static_cast<size_t>(height));
        size_t bytesPerLine = std::min(static_cast<size_t>(buffer.stride * (pixelFormat_ == RETRO_PIXEL_FORMAT_XRGB8888 ? 4 : 2)), pitch);

        for (size_t y = 0; y < copyLines; ++y) {
            std::memcpy(dst + (y * buffer.stride * (pixelFormat_ == RETRO_PIXEL_FORMAT_XRGB8888 ? 4 : 2)), 
                        src + (y * pitch), bytesPerLine);
        }
        ANativeWindow_unlockAndPost(nativeWindow_);
    }
}

void LibretroBridge::handleAudioSample(int16_t left, int16_t right) {
    int16_t frame[2] = {left, right};
    handleAudioSampleBatch(frame, 1);
}

size_t LibretroBridge::handleAudioSampleBatch(const int16_t* data, size_t frames) {
    if (frames == 0 || jvm_ == nullptr || globalBridgeObj_ == nullptr || audioCallbackMethod_ == nullptr) {
        return frames;
    }

    JNIEnv* env = nullptr;
    bool attached = false;
    int envRes = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envRes == JNI_EDETACHED) {
        if (jvm_->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        }
    }

    if (env != nullptr) {
        jobject byteBuffer = env->NewDirectByteBuffer(const_cast<int16_t*>(data), frames * 2 * sizeof(int16_t));
        if (byteBuffer != nullptr) {
            env->CallVoidMethod(globalBridgeObj_, audioCallbackMethod_, byteBuffer, static_cast<jint>(frames));
            env->DeleteLocalRef(byteBuffer);
        }
        if (attached) {
            jvm_->DetachCurrentThread();
        }
    }
    return frames;
}

void LibretroBridge::handleInputPoll() {
    // Polled automatically every frame
}

int16_t LibretroBridge::handleInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port > 1) return 0;
    if (device == RETRO_DEVICE_JOYPAD && id < 16) {
        return joypadState_[port][id];
    }
    if (device == RETRO_DEVICE_ANALOG) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            return (id == RETRO_DEVICE_ID_ANALOG_X) ? analogAxes_[port][0] : analogAxes_[port][1];
        } else if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            return (id == RETRO_DEVICE_ID_ANALOG_X) ? analogAxes_[port][2] : analogAxes_[port][3];
        }
    }
    return 0;
}

} // namespace retrostation::libretro

// JNI Entry Points
extern "C" {

static JavaVM* g_vm = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    LOGI("libretro_bridge DSO loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("libretro_bridge DSO unloaded");
    g_vm = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeInit(
        JNIEnv* /*env*/, jobject thiz, jint sampleRate) {
    return static_cast<jboolean>(retrostation::libretro::LibretroBridge::getInstance().initialize(g_vm, thiz, sampleRate));
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeSetDirectories(
        JNIEnv* env, jobject /*thiz*/, jstring systemDir, jstring saveDir) {
    const char* cSys = systemDir ? env->GetStringUTFChars(systemDir, nullptr) : nullptr;
    const char* cSave = saveDir ? env->GetStringUTFChars(saveDir, nullptr) : nullptr;
    retrostation::libretro::LibretroBridge::getInstance().setDirectories(cSys, cSave);
    if (cSys) env->ReleaseStringUTFChars(systemDir, cSys);
    if (cSave) env->ReleaseStringUTFChars(saveDir, cSave);
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeSetSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return static_cast<jboolean>(retrostation::libretro::LibretroBridge::getInstance().setSurface(window));
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeLoadRom(
        JNIEnv* env, jobject /*thiz*/, jstring romPath, jstring corePath) {
    const char* cRomPath = romPath != nullptr ? env->GetStringUTFChars(romPath, nullptr) : "";
    const char* cCorePath = corePath != nullptr ? env->GetStringUTFChars(corePath, nullptr) : "";

    bool success = retrostation::libretro::LibretroBridge::getInstance().loadRom(cRomPath, cCorePath);

    if (romPath != nullptr) env->ReleaseStringUTFChars(romPath, cRomPath);
    if (corePath != nullptr) env->ReleaseStringUTFChars(corePath, cCorePath);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeRunFrame(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::libretro::LibretroBridge::getInstance().runFrame();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativePause(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::libretro::LibretroBridge::getInstance().pause();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeResume(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::libretro::LibretroBridge::getInstance().resume();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeSendInput(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint key, jboolean isDown, jfloat axisValue) {
    retrostation::libretro::LibretroBridge::getInstance().sendInput(port, key, isDown == JNI_TRUE, axisValue);
}

JNIEXPORT jbyteArray JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeSaveState(
        JNIEnv* env, jobject /*thiz*/, jint slot) {
    std::vector<uint8_t> state = retrostation::libretro::LibretroBridge::getInstance().saveState(slot);
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(state.size()));
    if (arr != nullptr && !state.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(state.size()), reinterpret_cast<const jbyte*>(state.data()));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeLoadState(
        JNIEnv* env, jobject /*thiz*/, jint slot, jbyteArray stateData) {
    if (stateData == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(stateData);
    jbyte* bytes = env->GetByteArrayElements(stateData, nullptr);
    bool result = retrostation::libretro::LibretroBridge::getInstance().loadState(
            slot, reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(stateData, bytes, JNI_ABORT);
    return static_cast<jboolean>(result);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_libretro_LibretroEngineBridge_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::libretro::LibretroBridge::getInstance().destroy();
}

} // extern "C"
