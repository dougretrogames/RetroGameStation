/**
 * Retro Game Station - JNI Bridge Implementation
 * Connects Android Lifecycle, AAudio, Surface, and Input subsystems to C++ native engines.
 */

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include "retro_surface_bridge.h"
#include "retro_aaudio_stream.h"
#include "retro_native_bridge.h"

#define LOG_TAG "RetroJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace retro::graphics;
using namespace retro::audio;

extern "C" {

// ============================================================================
// Surface & Graphic Stack JNI Methods
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeSurfaceCreated(
        JNIEnv* env,
        jobject /* thiz */,
        jobject surface) {
    LOGI("nativeSurfaceCreated called from Kotlin");
    auto* bridge = new RetroSurfaceBridge();
    if (bridge->onSurfaceCreated(env, surface)) {
        return reinterpret_cast<jlong>(bridge);
    } else {
        delete bridge;
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeSurfaceChanged(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong bridgePtr,
        jint format,
        jint width,
        jint height) {
    auto* bridge = reinterpret_cast<RetroSurfaceBridge*>(bridgePtr);
    if (bridge) {
        bridge->onSurfaceChanged(format, width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeSurfaceDestroyed(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong bridgePtr) {
    LOGI("nativeSurfaceDestroyed called from Kotlin");
    auto* bridge = reinterpret_cast<RetroSurfaceBridge*>(bridgePtr);
    if (bridge) {
        bridge->onSurfaceDestroyed();
        delete bridge;
    }
}

// ============================================================================
// AAudio Subsystem JNI Methods
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioInit(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint sampleRate,
        jint channels,
        jint framesPerBurst,
        jint bufferBursts,
        jboolean exclusive) {
    LOGI("nativeAudioInit: sr=%d, ch=%d, burst=%d, bursts=%d, exclusive=%d",
         sampleRate, channels, framesPerBurst, bufferBursts, exclusive);

    auto* audioMgr = new AAudioStreamManager();
    AudioStreamConfig config;
    config.sampleRate = sampleRate;
    config.channelCount = channels;
    config.optimalFramesPerBurst = framesPerBurst;
    config.bufferCapacityInBursts = bufferBursts;
    config.exclusiveModeRequested = (exclusive == JNI_TRUE);

    if (audioMgr->init(config)) {
        return reinterpret_cast<jlong>(audioMgr);
    } else {
        delete audioMgr;
        return 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioStart(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (!audioMgr) return JNI_FALSE;
    return audioMgr->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioPause(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (!audioMgr) return JNI_FALSE;
    return audioMgr->pause() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioFlush(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (!audioMgr) return JNI_FALSE;
    return audioMgr->flush() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioDestroy(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (audioMgr) {
        audioMgr->destroy();
        delete audioMgr;
    }
}

JNIEXPORT jint JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioWriteSamples(
        JNIEnv* env,
        jobject /* thiz */,
        jlong audioPtr,
        jfloatArray samples,
        jint frames) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (!audioMgr || !samples || frames <= 0) return 0;

    jfloat* data = env->GetFloatArrayElements(samples, nullptr);
    if (!data) return 0;

    size_t written = audioMgr->writeSamples(data, frames);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    return static_cast<jint>(written);
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioSetMute(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr,
        jboolean mute) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (audioMgr) {
        audioMgr->setMuted(mute == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioSetVolumeGain(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr,
        jfloat gain) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (audioMgr) {
        audioMgr->setVolumeGain(gain);
    }
}

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioSetFastForward(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr,
        jboolean active,
        jboolean muteOnFastForward) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    if (audioMgr) {
        audioMgr->setFastForwardActive(active == JNI_TRUE, muteOnFastForward == JNI_TRUE);
    }
}

JNIEXPORT jdouble JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioGetLatencyMs(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    return audioMgr ? audioMgr->getCurrentLatencyMillis() : 0.0;
}

JNIEXPORT jint JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeAudioGetXRuns(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong audioPtr) {
    auto* audioMgr = reinterpret_cast<AAudioStreamManager*>(audioPtr);
    return audioMgr ? audioMgr->getXRunCount() : 0;
}

// ============================================================================
// Scoped Storage Direct File Descriptor Access
// ============================================================================

JNIEXPORT jboolean JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeOpenDirectRomFd(
        JNIEnv* env,
        jobject /* thiz */,
        jint fd,
        jstring pathStr) {
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    LOGI("Received direct native file descriptor %d for path %s", fd, path ? path : "null");

    // Test seekability and read access on direct file descriptor
    off_t offset = lseek(fd, 0, SEEK_CUR);
    if (offset < 0) {
        LOGE("Passed file descriptor %d is not seekable (errno=%d)", fd, errno);
        if (path) env->ReleaseStringUTFChars(pathStr, path);
        return JNI_FALSE;
    }

    off_t fileSize = lseek(fd, 0, SEEK_END);
    lseek(fd, offset, SEEK_SET); // restore
    LOGI("Direct FD %d verified seekable. Detected size: %lld bytes", fd, (long long)fileSize);

    if (path) env->ReleaseStringUTFChars(pathStr, path);
    return JNI_TRUE;
}

// ============================================================================
// Hotkey & Action Dispatching
// ============================================================================

JNIEXPORT void JNICALL
Java_com_retro_gamestation_core_NativeEngineBridge_nativeTriggerHotkeyAction(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint actionId,
        jint slot) {
    LOGI("Native Hotkey Action Triggered: action=%d, slot=%d", actionId, slot);
    // In production, this dispatches directly to libretro core / PPSSPP / Mupen64Plus command queues
}

// ============================================================================
// JNI Lifecycle DSO Registration
// ============================================================================

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* /* vm */, void* /* reserved */) {
    LOGI("retro_native DSO loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /* vm */, void* /* reserved */) {
    LOGI("retro_native DSO unloaded");
}

} // extern "C"
