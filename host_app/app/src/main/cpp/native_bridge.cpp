#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#define TAG "Vmers-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static ANativeWindow* g_native_window = nullptr;

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
    JNIEnv* env,
    jobject /* this */,
    jint action,
    jint x,
    jint y,
    jint pointer_id) {
    
    // Low latency Touch injection
    // Action: 0 = Down, 1 = Up, 2 = Move
    // In production, writes input_event struct to /dev/socket/input_vm
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
