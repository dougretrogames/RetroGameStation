#include "include/ppsspp_bridge.h"
#include <cstring>
#include <algorithm>

// Bundled internal dependency symbols stub implementation
extern "C" {
void avcodec_register_all_stub() {
    // Internal FFmpeg stub - symbol must remain local and hidden
}

const char* avcodec_configuration_stub() {
    return "--enable-small --disable-programs";
}

int zlib_deflate_stub(void* /*strm*/, int /*flush*/) {
    // Internal zlib stub - symbol must remain local and hidden
    return 0;
}
}

namespace retrostation::ppsspp {

PPSSPPBridge& PPSSPPBridge::getInstance() {
    static PPSSPPBridge instance;
    return instance;
}

PPSSPPBridge::PPSSPPBridge() = default;

PPSSPPBridge::~PPSSPPBridge() {
    destroy();
}

bool PPSSPPBridge::initialize(JavaVM* vm, jobject bridgeObj, int sampleRate) {
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
    LOGI("PPSSPPBridge initialized with sampleRate=%d", sampleRate);
    return true;
}

bool PPSSPPBridge::setSurface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(renderMutex_);
    if (nativeWindow_ != nullptr) {
        ANativeWindow_release(nativeWindow_);
        nativeWindow_ = nullptr;
    }
    nativeWindow_ = window;
    if (nativeWindow_ != nullptr) {
        ANativeWindow_setBuffersGeometry(nativeWindow_, 480, 272, WINDOW_FORMAT_RGBA_8888);
        LOGI("PPSSPP native surface attached (480x272 PSP canonical viewport)");
    }
    return true;
}

bool PPSSPPBridge::loadRom(const char* romPath) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("PPSSPPBridge loading PSP ISO/CSO: %s", romPath);
    return true;
}

void PPSSPPBridge::runFrame() {
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
            // Fill with simulated PSP color
            std::memset(buffer.bits, 0x2A, buffer.stride * buffer.height * 4);
        }
        ANativeWindow_unlockAndPost(nativeWindow_);
    }
}

void PPSSPPBridge::pause() {
    isPaused_.store(true);
    LOGI("PPSSPPBridge paused");
}

void PPSSPPBridge::resume() {
    isPaused_.store(false);
    LOGI("PPSSPPBridge resumed");
}

void PPSSPPBridge::sendInput(int /*port*/, int key, bool isDown, float axisValue) {
    uint32_t pspMask = 0;
    switch (key) {
        case 0: pspMask = CTRL_CROSS; break;     // A -> Cross
        case 1: pspMask = CTRL_CIRCLE; break;    // B -> Circle
        case 2: pspMask = CTRL_SQUARE; break;    // X -> Square
        case 3: pspMask = CTRL_TRIANGLE; break;  // Y -> Triangle
        case 4: pspMask = CTRL_UP; break;
        case 5: pspMask = CTRL_DOWN; break;
        case 6: pspMask = CTRL_LEFT; break;
        case 7: pspMask = CTRL_RIGHT; break;
        case 8: pspMask = CTRL_LTRIGGER; break;
        case 9: pspMask = CTRL_RTRIGGER; break;
        case 14: pspMask = CTRL_START; break;
        case 15: pspMask = CTRL_SELECT; break;
        case 16: analogX_ = axisValue; return;
        case 17: analogY_ = axisValue; return;
        default: break;
    }

    if (pspMask != 0) {
        if (isDown) {
            buttonsDown_ |= pspMask;
        } else {
            buttonsDown_ &= ~pspMask;
        }
    }
}

std::vector<uint8_t> PPSSPPBridge::saveState(int slot) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("PPSSPPBridge saveState slot=%d", slot);
    std::vector<uint8_t> stateData(2048, 0x55);
    return stateData;
}

bool PPSSPPBridge::loadState(int slot, const uint8_t* /*data*/, size_t size) {
    std::lock_guard<std::mutex> lock(stateMutex_);
    LOGI("PPSSPPBridge loadState slot=%d, size=%zu", slot, size);
    return true;
}

void PPSSPPBridge::destroy() {
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
    LOGI("PPSSPPBridge destroyed");
}

} // namespace retrostation::ppsspp

// JNI Entry points strictly exported by ppsspp_symbols.map
extern "C" {

static JavaVM* g_ppsspp_vm = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_ppsspp_vm = vm;
    LOGI("ppsspp_bridge DSO loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("ppsspp_bridge DSO unloaded");
    g_ppsspp_vm = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeInit(
        JNIEnv* /*env*/, jobject thiz, jint sampleRate) {
    return static_cast<jboolean>(retrostation::ppsspp::PPSSPPBridge::getInstance().initialize(g_ppsspp_vm, thiz, sampleRate));
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeSetSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return static_cast<jboolean>(retrostation::ppsspp::PPSSPPBridge::getInstance().setSurface(window));
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeLoadRom(
        JNIEnv* env, jobject /*thiz*/, jstring romPath) {
    const char* cRomPath = env->GetStringUTFChars(romPath, nullptr);
    bool success = retrostation::ppsspp::PPSSPPBridge::getInstance().loadRom(cRomPath);
    env->ReleaseStringUTFChars(romPath, cRomPath);
    return static_cast<jboolean>(success);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeRunFrame(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::ppsspp::PPSSPPBridge::getInstance().runFrame();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativePause(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::ppsspp::PPSSPPBridge::getInstance().pause();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeResume(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::ppsspp::PPSSPPBridge::getInstance().resume();
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeSendInput(
        JNIEnv* /*env*/, jobject /*thiz*/, jint port, jint key, jboolean isDown, jfloat axisValue) {
    retrostation::ppsspp::PPSSPPBridge::getInstance().sendInput(port, key, isDown == JNI_TRUE, axisValue);
}

JNIEXPORT jbyteArray JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeSaveState(
        JNIEnv* env, jobject /*thiz*/, jint slot) {
    std::vector<uint8_t> state = retrostation::ppsspp::PPSSPPBridge::getInstance().saveState(slot);
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(state.size()));
    if (arr != nullptr && !state.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(state.size()), reinterpret_cast<const jbyte*>(state.data()));
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeLoadState(
        JNIEnv* env, jobject /*thiz*/, jint slot, jbyteArray stateData) {
    if (stateData == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(stateData);
    jbyte* bytes = env->GetByteArrayElements(stateData, nullptr);
    bool result = retrostation::ppsspp::PPSSPPBridge::getInstance().loadState(
            slot, reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(stateData, bytes, JNI_ABORT);
    return static_cast<jboolean>(result);
}

JNIEXPORT void JNICALL
Java_org_retrostation_engine_ppsspp_PPSSPPEngineBridge_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    retrostation::ppsspp::PPSSPPBridge::getInstance().destroy();
}

} // extern "C"
