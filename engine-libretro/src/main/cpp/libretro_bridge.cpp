#include "include/libretro_bridge.h"
#include <cstring>
#include <algorithm>

namespace retrostation::libretro {

static LibretroBridge* s_instance = nullptr;

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

bool LibretroBridge::loadRom(const char* romPath, const char* /*corePath*/) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge loading ROM: %s", romPath);
    return true;
}

void LibretroBridge::runFrame() {
    if (!isRunning_.load() || isPaused_.load()) {
        return;
    }

    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ == nullptr) {
        return;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(nativeWindow_, &buffer, nullptr) == 0) {
        // Clear or draw simulated frame
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
        if (isDown) {
            joypadState_[port][key] = 1;
        } else {
            joypadState_[port][key] = 0;
        }
    } else if (key >= 16 && key <= 19) {
        int axisIndex = key - 16;
        analogAxes_[port][axisIndex] = static_cast<int16_t>(axisValue * 32767.0f);
    }
}

std::vector<uint8_t> LibretroBridge::saveState(int slot) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge saveState for slot %d", slot);
    std::vector<uint8_t> stateData(1024, 0xAA);
    return stateData;
}

bool LibretroBridge::loadState(int slot, const uint8_t* /*data*/, size_t size) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("LibretroBridge loadState for slot %d with size %zu", slot, size);
    return true;
}

void LibretroBridge::destroy() {
    isRunning_.store(false);
    isPaused_.store(false);

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
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            auto* canDupe = static_cast<bool*>(data);
            if (canDupe) *canDupe = true;
            return true;
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
        size_t bytesPerLine = std::min(static_cast<size_t>(buffer.stride * 2), pitch);

        for (size_t y = 0; y < copyLines; ++y) {
            std::memcpy(dst + (y * buffer.stride * 2), src + (y * pitch), bytesPerLine);
        }
        ANativeWindow_unlockAndPost(nativeWindow_);
    }
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

// JNI Entry Points strictly exported by libretro_symbols.map
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
    const char* cRomPath = env->GetStringUTFChars(romPath, nullptr);
    const char* cCorePath = corePath != nullptr ? env->GetStringUTFChars(corePath, nullptr) : "";

    bool success = retrostation::libretro::LibretroBridge::getInstance().loadRom(cRomPath, cCorePath);

    env->ReleaseStringUTFChars(romPath, cRomPath);
    if (corePath != nullptr) {
        env->ReleaseStringUTFChars(corePath, cCorePath);
    }
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
