cmake_minimum_required(VERSION 3.22.1)
project("vmers")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Ensure 16KB / 64KB page alignment for Android 15 compatibility & Bionic TLS alignment >= 64
add_compile_options(-O3 -fPIC)
add_link_options("-Wl,-z,max-page-size=65536" "-Wl,-z,common-page-size=65536")

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

# 1. JNI Native Bridge Library
add_library(
    vmers_jni
    SHARED
    native_bridge.cpp
)

target_include_directories(
    vmers_jni
    PRIVATE
    ../../../../../core_engine
)

target_link_libraries(
    vmers_jni
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
)

# 2. Vmers Container Engine (Shared Library for NativeLibraryDir execution)
add_library(
    vmers_engine
    SHARED
    ../../../../../core_engine/main.cpp
    ../../../../../core_engine/property_service.cpp
    ../../../../../core_engine/vmlink_loader.cpp
)

target_include_directories(
    vmers_engine
    PRIVATE
    ../../../../../core_engine
)

target_link_libraries(
    vmers_engine
    ${log-lib}
)

# 3. Vmers Syscall Interception Shim Library
add_library(
    vmlink_shim
    SHARED
    ../../../../../core_engine/vmlink_shim.cpp
)

target_include_directories(
    vmlink_shim
    PRIVATE
    ../../../../../core_engine
)

target_link_libraries(
    vmlink_shim
    ${log-lib}
)
