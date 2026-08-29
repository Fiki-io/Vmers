cmake_minimum_required(VERSION 3.22.1)
project("vmers_jni")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    vmers_jni
    SHARED
    native_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(
    vmers_jni
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
)
