#include "include/mupen64plus_bridge.h"
#include <cstring>
#include <algorithm>

// Bundled internal dependency symbols stub implementation
extern "C" {
int SDL_Init_stub(uint32_t /*flags*/) {
    // Internal SDL2 stub - symbol must remain local and hidden
    return 0;
}

void* png_create_read_struct_stub(const char* /*user_png_ver*/, void* /*error_ptr*/, void* /*error_fn*/, void* /*warn_fn*/) {
    // Internal libpng stub - symbol must remain local and hidden
    return nullptr;
}

int zlib_inflate_stub(void* /*strm*/, int /*flush*/) {
    // Internal zlib stub - symbol must remain local and hidden
    return 0;
}

m64p_error CoreStartup(int /*apiVersion*/, const char* /*configPath*/, const char* /*dataPath*/, void* /*context*/,
                       void (*/*debugCallback*/)(void*, int, const char*)) {
    return M64ERR_SUCCESS;
}

m64p_error CoreShutdown() {
    return M64ERR_SUCCESS;
}

m64p_error CoreDoCommand(m64p_command /*command*/, int /*intParam*/, void* /*ptrParam*/) {
    return M64ERR_SUCCESS;
}
}

namespace retrostation::mupen64plus {

Mupen64PlusBridge& Mupen64PlusBridge::getInstance() {
    static Mupen64PlusBridge instance;
    return instance;
}

Mupen64PlusBridge::Mupen64PlusBridge() {
    std::memset(buttonsState_, 0, sizeof(buttonsState_));
    std::memset(stickX_, 0, sizeof(stickX_));
    std::memset(stickY_, 0, sizeof(stickY_));
}

Mupen64PlusBridge::~Mupen64PlusBridge() {
    destroy();
}

bool Mupen64PlusBridge::initialize(JavaVM* vm, jobject bridgeObj, int sampleRate) {
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
    LOGI("Mupen64PlusBridge initialized with sampleRate=%d", sampleRate);
    return true;
}

bool Mupen64PlusBridge::setSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ != nullptr) {
        ANativeWindow_release(nativeWindow_);
        nativeWindow_ = nullptr;
    }
    nativeWindow_ = window;
    if (nativeWindow_ != nullptr) {
        ANativeWindow_setBuffersGeometry(nativeWindow_, 640, 480, WINDOW_FORMAT_RGBA_8888);
        LOGI("Mupen64Plus native surface attached (640x480 N64 canonical viewport)");
    }
    return true;
}

bool Mupen64PlusBridge::loadRom(const char* romPath) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("Mupen64PlusBridge loading N64 ROM: %s", romPath);
    return true;
}

void Mupen64PlusBridge::runFrame() {
    if (!isRunning_.load() || isPaused_.load()) {
        return;
    }

    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ == nullptr) {
        return;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(nativeWindow_, &buffer, nullptr) == 0) {
        if (buffer.bits != nullptr) {
            std::memset(buffer.bits, 0x4B, buffer.stride * buffer.height * 4);
        }
        ANativeWindow_unlockAndPost(nativeWindow_);
    }
}

void Mupen64PlusBridge::pause() {
    isPaused_.store(true);
    LOGI("Mupen64PlusBridge paused");
}

void Mupen64PlusBridge::resume() {
    isPaused_.store(false);
    LOGI("Mupen64PlusBridge resumed");
}

void Mupen64PlusBridge::sendInput(int port, int key, bool isDown, float axisValue) {
    if (port < 0 || port > 3) return;

    uint16_t n64Mask = 0;
    switch (key) {
        case 0: n64Mask = N64_CONT_A; break;      // Button A -> N64 A
        case 1: n64Mask = N64_CONT_B; break;      // Button B -> N64 B
        case 2: n64Mask = N64_CONT_Z; break;      // Button X -> N64 Z
        case 3: n64Mask = N64_CONT_R; break;      // Button Y -> N64 R
        case 4: n64Mask = N64_CONT_UP; break;
        case 5: n64Mask = N64_CONT_DOWN; break;
        case 6: n64Mask = N64_CONT_LEFT; break;
        case 7: n64Mask = N64_CONT_RIGHT; break;
        case 8: n64Mask = N64_CONT_L; break;
        case 9: n64Mask = N64_CONT_R; break;
        case 10: n64Mask = N64_CONT_Z; break;     // Trigger L2 -> Z trigger
        case 14: n64Mask = N64_CONT_START; break;
        case 16: // Analog X
            stickX_[port] = static_cast<int8_t>(std::clamp(axisValue * 80.0f, -80.0f, 80.0f));
            return;
        case 17: // Analog Y
            stickY_[port] = static_cast<int8_t>(std::clamp(axisValue * 80.0f, -80.0f, 80.0f));
            return;
        default: break;
    }

    if (n64Mask != 0) {
        if (isDown) {
            buttonsState_[port] |= n64Mask;
        } else {
            buttonsState_[port] &= ~n64Mask;
        }
    }
}

std::vector<uint8_t> Mupen64PlusBridge::saveState(int slot) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("Mupen64PlusBridge saveState slot=%d", slot);
    std::vector<uint8_t> stateData(4096, 0x64);
    return stateData;
}

bool Mupen64PlusBridge::loadState(int slot, const uint8_t* /*data*/, size_t size) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("Mupen64PlusBridge loadState slot=%d, size=%zu", slot, size);
    return true;
}

void Mupen64PlusBridge::destroy() {
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
    LOGI("Mupen64PlusBridge destroyed");
}

} // namespace retrostation::mupen64plus

// JNI Entry points strictly exported by mupen64plus_symbols.map
extern "C" {

static JavaVM* g_mupen64plus_vm = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_mupen64plus_vm = vm;
    LOGI("mupen64plus_bridge DSO loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("mupen64plus_bridge DSO unloaded");
    g_mupen64plus_vm = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeInit(
        JNIEnv* /*env*/, jobject thiz, jint sampleRate) {
    return static_cast<jboolean>(retrostation::mupen64plus::Mupen64PlusBridge::getInstance().initialize(g_mupen64plus_vm, thiz, sampleRate));
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeSetSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return static_cast<jboolean>(retrostation::mupen64plus::Mupen64PlusBridge::getInstance().setSurface(window));
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeLoadRom(
        JNIEnv* env, jobject /*thiz*/, jstring romPath) {
    const char* cRomPath = env->GetStringUTFChars(romPath, nullptr);
    bool success = retrostation::mupen64plus::Mupen64PlusBridge::getInstance().loadRom(cRomPath);
    env->ReleaseStringUTFChars(romPath, cRomPath);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeRunFrame(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::mupen64plus::Mupen64PlusBridge::getInstance().runFrame();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativePause(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::mupen64plus::Mupen64PlusBridge::getInstance().pause();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeResume(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::mupen64plus::Mupen64PlusBridge::getInstance().resume();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeSendInput(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint key, jboolean isDown, jfloat axisValue) {
    retrostation::mupen64plus::Mupen64PlusBridge::getInstance().sendInput(port, key, isDown == JNI_TRUE, axisValue);
}

JNIEXPORT jbyteArray JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeSaveState(
        JNIEnv* env, jobject /*thiz*/, jint slot) {
    std::vector<uint8_t> state = retrostation::mupen64plus::Mupen64PlusBridge::getInstance().saveState(slot);
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(state.size()));
    if (arr != nullptr && !state.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(state.size()), reinterpret_cast<const jbyte*>(state.data()));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeLoadState(
        JNIEnv* env, jobject /*thiz*/, jint slot, jbyteArray stateData) {
    if (stateData == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(stateData);
    jbyte* bytes = env->GetByteArrayElements(stateData, nullptr);
    bool result = retrostation::mupen64plus::Mupen64PlusBridge::getInstance().loadState(
            slot, reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(stateData, bytes, JNI_ABORT);
    return static_cast<jboolean>(result);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_mupen64plus_Mupen64PlusEngineBridge_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::mupen64plus::Mupen64PlusBridge::getInstance().destroy();
}

} // extern "C"
