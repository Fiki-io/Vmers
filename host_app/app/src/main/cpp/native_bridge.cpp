#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <thread>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include "vmlink_loader.hpp"

#define TAG "Vmers-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static ANativeWindow* g_native_window = nullptr;
static std::unique_ptr<vmers::VmContainerLoader> g_container_loader = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_vmers_app_core_NativeEngine_initVMEnvironment(
    JNIEnv* env,
    jobject /* this */,
    jstring rootfs_path,
    jint width,
    jint height,
    jint dpi) {
    
    const char* path = env->GetStringUTFChars(rootfs_path, nullptr);
    LOGI("Initializing VM environment: %s (%dx%d @ %ddpi)", path, width, height, dpi);

    // Create runtime directories
    std::string base = path;
    mkdir((base + "/dev").c_str(), 0755);
    mkdir((base + "/dev/socket").c_str(), 0755);
    mkdir((base + "/data").c_str(), 0777);
    mkdir((base + "/tmp").c_str(), 0777);

    env->ReleaseStringUTFChars(rootfs_path, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_vmers_app_core_NativeEngine_startContainerNative(
    JNIEnv* env,
    jobject /* this */,
    jstring rootfs_path,
    jint width,
    jint height,
    jint dpi,
    jboolean enable_root) {
    
    const char* path = env->GetStringUTFChars(rootfs_path, nullptr);
    LOGI("Starting Native In-Process Container for: %s", path);

    vmers::VmConfig config;
    config.rootfs_dir = path;
    config.data_dir = std::string(path) + "/data";
    config.display_width = width;
    config.display_height = height;
    config.display_dpi = dpi;
    config.enable_root = enable_root;

    env->ReleaseStringUTFChars(rootfs_path, path);

    try {
        if (g_container_loader) {
            g_container_loader->StopContainer();
            g_container_loader.reset();
        }

        g_container_loader = std::make_unique<vmers::VmContainerLoader>(config);
        bool success = g_container_loader->StartContainer();
        LOGI("Native Container Start result: %s (PID: %d)", success ? "SUCCESS" : "FAILED", g_container_loader->GetGuestPid());
        return success ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("Native container start exception: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_vmers_app_core_NativeEngine_stopContainerNative(
    JNIEnv* /* env */,
    jobject /* this */) {
    
    LOGI("Stopping Native Container...");
    if (g_container_loader) {
        g_container_loader->StopContainer();
        g_container_loader.reset();
    }
}

JNIEXPORT void JNICALL
Java_com_vmers_app_core_NativeEngine_setSurface(
    JNIEnv* env,
    jobject /* this */,
    jobject surface) {
    
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = nullptr;
    }

    if (surface) {
        g_native_window = ANativeWindow_fromSurface(env, surface);
        LOGI("NativeWindow attached: %p", g_native_window);
    } else {
        LOGI("NativeWindow detached.");
    }
}

JNIEXPORT void JNICALL
Java_com_vmers_app_core_NativeEngine_sendTouchEvent(
    JNIEnv* /* env */,
    jobject /* this */,
    jint action,
    jint x,
    jint y,
    jint pointer_id) {
    
    // Direct input injection into container event queue
}

JNIEXPORT jint JNICALL
Java_com_vmers_app_core_NativeEngine_chmodRecursively(
    JNIEnv* env,
    jstring target_path,
    jint mode) {
    
    const char* path = env->GetStringUTFChars(target_path, nullptr);
    int res = chmod(path, mode);
    env->ReleaseStringUTFChars(target_path, path);
    return res;
}

} // extern "C"
