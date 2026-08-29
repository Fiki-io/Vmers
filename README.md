# Vmers 🚀
### High-Performance Universal Android-in-Android Container Virtual Machine
**Target Guest OS:** AOSP Android 15 (VanillaIceCream) ARM64-v8a  
**Host Requirement:** Android 8.0+ (ARM64-v8a) — **No Root Required**

---

## 🌟 Overview & Architecture

**Vmers** is an open, production-grade Android virtualization engine that runs a complete, isolated **AOSP Android 15 ARM64** user-space container directly inside non-rooted Android devices.

Unlike slow instruction emulators (QEMU TCG), Vmers utilizes **native ARM64 CPU execution** combined with **User-Mode Syscall Redirection**, an **Isolated Bionic Linker**, and an **Independent Property Service Daemon**.

```
+-------------------------------------------------------------------------------+
|                                  HOST ANDROID                                 |
|  +-------------------------------------------------------------------------+  |
|  |                 Vmers App (com.vmers.app - Kotlin / C++)                |  |
|  |  ├── UI: Floating Window, PIP, Multi-Instance Dashboard                 |  |
|  |  ├── VMSurfaceView: Low-Latency OpenGL ES Host Renderer                 |  |
|  |  ├── Touch / Input Forwarder (Linux input_event socket pipeline)        |  |
|  |  └── Audio / Network Forwarder (Virtual AudioTrack & Local VPN Service) |  |
|  +-------------------------------------------------------------------------+  |
|                                       │ UNIX Sockets & Ashmem                 |
|  +------------------------------------▼------------------------------------+  |
|  |             Vmers Native Container Engine (vmers_engine_arm64)          |  |
|  |  ├── Syscall Interceptor: Path redirection to /data/data/.../vm/vm0/fs/ |  |
|  |  ├── Property Service Daemon: /dev/socket/property_service (/build.prop)|  |
|  |  └── Zygote Spawner: Sets ANDROID_ROOT, BOOTCLASSPATH, execs app_process|  |
|  +-------------------------------------------------------------------------+  |
|                                       │ execve                                |
|  +------------------------------------▼------------------------------------+  |
|  |                       GUEST AOSP ANDROID 15 (ARM64)                     |  |
|  |  ├── /system/bin/app_process64 ──> Zygote ──> SystemServer              |  |
|  |  ├── Flattened APEX Modules: /apex/com.android.art, runtime, conscrypt  |  |
|  |  └── Virtual HALs: Ranchu Gralloc (gralloc.vm.so), libGLES_emulation.so |  |
|  +-------------------------------------------------------------------------+  |
+-------------------------------------------------------------------------------+
```

---

## 📂 Repository Structure

```
Vmers/
├── core_engine/                 # Native C++20 Container Engine & Daemons
│   ├── main.cpp                 # Standalone VM daemon entry point
│   ├── property_service.hpp/cpp # User-space Android property socket service
│   ├── vmlink_loader.hpp/cpp    # Rootfs initializer, sandbox & Zygote spawner
│   ├── Makefile                 # Cross-compilation for AArch64 and Host
│   └── CMakeLists.txt           # CMake configuration
│
├── host_app/                    # Android Studio Application (com.vmers.app)
│   ├── app/src/main/java/       # Kotlin UI, VMInstance, VMManager, Services
│   ├── app/src/main/cpp/        # JNI bindings (libvmers_jni.so)
│   ├── app/src/main/res/        # Modern dark themes, layouts & controls
│   └── build.gradle             # Android 15 (API 35) build script
│
├── rom_tools/                   # ROM Processing & APEX Flattening Pipeline
│   ├── prepare_a15_gsi.py       # Converts raw GSI system.img into container .7z
│   └── download_a15_gsi.py      # Automated A15 GSI downloader
│
├── .github/workflows/           # CI/CD Automated Build System
│   └── build.yml                # GitHub Actions workflow for APK & Engine
│
└── roms/                        # Target directory for packaged ROM archives
```

---

## 🛠️ How to Prepare Android 15 GSI ROM

1. **Obtain any official AOSP Android 15 GSI ARM64 image** (`system.img` or `system.img.xz`).
2. **Run the Vmers ROM Processor**:
   ```bash
   python3 rom_tools/prepare_a15_gsi.py \
       --input /path/to/system.img \
       --output roms/vmers_a15_arm64.7z
   ```
   *This tool automatically:*
   * Unpacks sparse/ext4 images.
   * Flattens all Android 15 `.apex` runtime & ART packages.
   * Injects container emulator properties into `build.prop`.
   * Sanitizes hardware-locking daemons (`vold`, `ueventd`).
   * Packages the rootfs into `vmers_a15_arm64.7z`.

---

## 🚀 Building & GitHub Actions CI

### Option 1: Automatic Cloud Build via GitHub Actions
Push this repository to GitHub. The included `.github/workflows/build.yml` will automatically:
1. Cross-compile `vmers_engine_arm64` using `aarch64-linux-gnu-g++`.
2. Build the Android Host APK (`Vmers-Debug-APK.apk`) via Gradle with NDK.
3. Publish all compiled binaries to GitHub Release Artifacts.

### Option 2: Local Compilation
```bash
# 1. Build Native ARM64 Engine
cd core_engine
make arm64

# 2. Build Android Host App
cd ../host_app
./gradlew assembleDebug
```

---

## 📄 License
Open-Source under the Apache 2.0 License.
Developed for the **Vmers Virtual Engine Project**.
