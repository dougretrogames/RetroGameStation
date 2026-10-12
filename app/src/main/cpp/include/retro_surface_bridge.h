/**
 * Retro Game Station - Native Surface & Window Bridge
 * Orchestrates ANativeWindow lifecycle, Vulkan/GLES surface transitions, and thread synchronization.
 */

#pragma once

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <cstdint>

namespace retro {
namespace graphics {

enum class GraphicsApi {
    VULKAN,
    OPENGL_ES
};

enum class SurfaceLifecycleState {
    NO_SURFACE,
    SURFACE_CREATING,
    SURFACE_ACTIVE,
    SURFACE_CHANGING,
    SURFACE_DESTROYING
};

class RetroSurfaceBridge {
public:
    RetroSurfaceBridge();
    ~RetroSurfaceBridge();

    // Invoked from JNI / SurfaceHolder.Callback
    bool onSurfaceCreated(JNIEnv* env, jobject surface);
    void onSurfaceChanged(int32_t format, int32_t width, int32_t height);
    void onSurfaceDestroyed();

    // Invoked by the native core emulation render thread
    ANativeWindow* acquireWindowForRender();
    void releaseWindowAfterRender();

    // Render thread synchronization
    void pauseRendering();
    void resumeRendering();
    bool waitForSurfaceReady(uint32_t timeoutMillis = 1000);

    // Queries
    int32_t getWidth() const { return mWidth.load(); }
    int32_t getHeight() const { return mHeight.load(); }
    int32_t getFormat() const { return mFormat.load(); }
    SurfaceLifecycleState getState() const { return mState.load(); }
    bool isSurfaceReady() const { return mState.load() == SurfaceLifecycleState::SURFACE_ACTIVE; }

    void setGraphicsApi(GraphicsApi api) { mGraphicsApi = api; }
    GraphicsApi getGraphicsApi() const { return mGraphicsApi; }

private:
    ANativeWindow* mNativeWindow = nullptr;
    GraphicsApi mGraphicsApi = GraphicsApi::VULKAN;

    std::atomic<SurfaceLifecycleState> mState{SurfaceLifecycleState::NO_SURFACE};
    std::atomic<int32_t> mWidth{0};
    std::atomic<int32_t> mHeight{0};
    std::atomic<int32_t> mFormat{0};

    mutable std::mutex mWindowMutex;
    std::condition_variable mSurfaceCondition;

    std::atomic<bool> mRenderThreadActive{false};
    std::mutex mRenderSyncMutex;
    std::condition_variable mRenderSyncCondition;
};

} // namespace graphics
} // namespace retro
