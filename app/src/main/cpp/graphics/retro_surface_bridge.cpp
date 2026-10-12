/**
 * Retro Game Station - Native Surface & Window Bridge Implementation
 */

#include "retro_surface_bridge.h"
#include <android/log.h>
#include <chrono>

#define LOG_TAG "RetroSurfaceBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retro {
namespace graphics {

RetroSurfaceBridge::RetroSurfaceBridge() = default;

RetroSurfaceBridge::~RetroSurfaceBridge() {
    onSurfaceDestroyed();
}

bool RetroSurfaceBridge::onSurfaceCreated(JNIEnv* env, jobject surface) {
    std::lock_guard<std::mutex> lock(mWindowMutex);

    if (mNativeWindow != nullptr) {
        LOGW("Previous ANativeWindow was not cleanly released, releasing now");
        ANativeWindow_release(mNativeWindow);
        mNativeWindow = nullptr;
    }

    if (!surface) {
        LOGE("Passed null surface object to onSurfaceCreated");
        mState.store(SurfaceLifecycleState::NO_SURFACE);
        return false;
    }

    mNativeWindow = ANativeWindow_fromSurface(env, surface);
    if (!mNativeWindow) {
        LOGE("Failed to obtain ANativeWindow from Surface");
        mState.store(SurfaceLifecycleState::NO_SURFACE);
        return false;
    }

    mWidth.store(ANativeWindow_getWidth(mNativeWindow));
    mHeight.store(ANativeWindow_getHeight(mNativeWindow));
    mFormat.store(ANativeWindow_getFormat(mNativeWindow));

    mState.store(SurfaceLifecycleState::SURFACE_ACTIVE);
    LOGI("ANativeWindow acquired successfully: %p (%dx%d, format: %d)",
         mNativeWindow, mWidth.load(), mHeight.load(), mFormat.load());

    mSurfaceCondition.notify_all();
    return true;
}

void RetroSurfaceBridge::onSurfaceChanged(int32_t format, int32_t width, int32_t height) {
    std::lock_guard<std::mutex> lock(mWindowMutex);
    mState.store(SurfaceLifecycleState::SURFACE_CHANGING);

    mWidth.store(width);
    mHeight.store(height);
    mFormat.store(format);

    if (mNativeWindow != nullptr) {
        // Re-configure buffer geometry for Vulkan/GLES swapchain alignment
        ANativeWindow_setBuffersGeometry(mNativeWindow, width, height, format);
    }

    mState.store(SurfaceLifecycleState::SURFACE_ACTIVE);
    LOGI("Surface changed: %dx%d, format: %d", width, height, format);
    mSurfaceCondition.notify_all();
}

void RetroSurfaceBridge::onSurfaceDestroyed() {
    LOGI("Surface destruction requested by OS. Halting render thread before releasing window...");

    // 1. Signal destroying state so render thread exits its loop or fences
    mState.store(SurfaceLifecycleState::SURFACE_DESTROYING);

    // 2. Wait for active rendering frame to conclude
    {
        std::unique_lock<std::mutex> lock(mRenderSyncMutex);
        mRenderSyncCondition.wait_for(lock, std::chrono::milliseconds(200), [this] {
            return !mRenderThreadActive.load();
        });
    }

    // 3. Atomically release ANativeWindow
    {
        std::lock_guard<std::mutex> lock(mWindowMutex);
        if (mNativeWindow != nullptr) {
            LOGI("Releasing ANativeWindow: %p", mNativeWindow);
            ANativeWindow_release(mNativeWindow);
            mNativeWindow = nullptr;
        }
        mWidth.store(0);
        mHeight.store(0);
        mState.store(SurfaceLifecycleState::NO_SURFACE);
    }

    LOGI("Surface destroyed and ANativeWindow safely released without leaks.");
}

ANativeWindow* RetroSurfaceBridge::acquireWindowForRender() {
    std::lock_guard<std::mutex> lock(mWindowMutex);
    if (mState.load() != SurfaceLifecycleState::SURFACE_ACTIVE || !mNativeWindow) {
        return nullptr;
    }

    mRenderThreadActive.store(true, std::memory_order_release);
    return mNativeWindow;
}

void RetroSurfaceBridge::releaseWindowAfterRender() {
    mRenderThreadActive.store(false, std::memory_order_release);
    mRenderSyncCondition.notify_one();
}

void RetroSurfaceBridge::pauseRendering() {
    LOGI("Pausing render pipeline");
}

void RetroSurfaceBridge::resumeRendering() {
    LOGI("Resuming render pipeline");
}

bool RetroSurfaceBridge::waitForSurfaceReady(uint32_t timeoutMillis) {
    std::unique_lock<std::mutex> lock(mWindowMutex);
    if (mState.load() == SurfaceLifecycleState::SURFACE_ACTIVE && mNativeWindow != nullptr) {
        return true;
    }

    return mSurfaceCondition.wait_for(lock, std::chrono::milliseconds(timeoutMillis), [this] {
        return mState.load() == SurfaceLifecycleState::SURFACE_ACTIVE && mNativeWindow != nullptr;
    });
}

} // namespace graphics
} // namespace retro
