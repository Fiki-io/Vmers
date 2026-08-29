#!/usr/bin/env python3
"""
Vmers - GSI Android 15 Preparation & Container Rootfs Packaging Engine
This script converts any raw/sparse AOSP Android 15 GSI system.img into an isolated,
flattened APEX container rootfs ready to be executed by the Vmers native engine.
"""

import os
import sys
import subprocess
import shutil
import json
import argparse

def log(msg):
    print(f"[Vmers-ROM] {msg}")

def check_dependencies():
    tools = ['simg2img', '7z']
    for t in tools:
        if not shutil.which(t):
            sys.exit(f"Error: Required tool '{t}' is not installed.")

def unpack_gsi(input_img, work_dir):
    os.makedirs(work_dir, exist_ok=True)
    raw_img = os.path.join(work_dir, "system_raw.img")
    
    # Check if sparse image
    log(f"Checking image type for {input_img}...")
    try:
        log("Attempting simg2img conversion...")
        subprocess.check_call(['simg2img', input_img, raw_img])
        img_to_extract = raw_img
    except Exception:
        log("Image is already raw ext4/erofs, using directly.")
        img_to_extract = input_img

    rootfs_dir = os.path.join(work_dir, "rootfs")
    os.makedirs(rootfs_dir, exist_ok=True)
    
    log(f"Extracting {img_to_extract} to {rootfs_dir} via 7z...")
    subprocess.check_call(['7z', 'x', '-y', img_to_extract, f"-o{rootfs_dir}"])
    
    if os.path.exists(raw_img) and raw_img != input_img:
        os.remove(raw_img)
        
    return rootfs_dir

def flatten_apex_modules(rootfs_dir):
    log("Flattening APEX packages for Android 15 user-mode container...")
    apex_dir = os.path.join(rootfs_dir, "system", "apex")
    target_apex_root = os.path.join(rootfs_dir, "apex")
    os.makedirs(target_apex_root, exist_ok=True)

    if not os.path.exists(apex_dir):
        log("No system/apex directory found. Checking root /apex...")
        return

    for item in os.listdir(apex_dir):
        if item.endswith(".apex"):
            pkg_name = item[:-5] # remove .apex
            apex_file = os.path.join(apex_dir, item)
            out_target = os.path.join(target_apex_root, pkg_name)
            os.makedirs(out_target, exist_ok=True)
            
            temp_extract = os.path.join("/tmp", f"vmers_apex_{pkg_name}")
            os.makedirs(temp_extract, exist_ok=True)
            
            log(f"  -> Flattening {item} -> /apex/{pkg_name}")
            try:
                subprocess.check_call(['7z', 'x', '-y', apex_file, f"-o{temp_extract}"], stdout=subprocess.DEVNULL)
                payload_img = os.path.join(temp_extract, "apex_payload.img")
                if os.path.exists(payload_img):
                    subprocess.check_call(['7z', 'x', '-y', payload_img, f"-o{out_target}"], stdout=subprocess.DEVNULL)
                else:
                    # Compressed payload or direct files
                    for sub in os.listdir(temp_extract):
                        s = os.path.join(temp_extract, sub)
                        d = os.path.join(out_target, sub)
                        if os.path.isdir(s):
                            shutil.copytree(s, d, dirs_exist_ok=True)
                        else:
                            shutil.copy2(s, d)
            except Exception as e:
                log(f"  [!] Warning on {item}: {e}")
            finally:
                shutil.rmtree(temp_extract, ignore_errors=True)

def patch_build_properties(rootfs_dir):
    log("Patching build.prop for container emulator mode & permissive security...")
    prop_path = os.path.join(rootfs_dir, "system", "build.prop")
    if not os.path.exists(prop_path):
        prop_path = os.path.join(rootfs_dir, "build.prop")
        
    patches = {
        "ro.kernel.qemu": "1",
        "ro.kernel.qemu.gles": "1",
        "ro.boot.selinux": "permissive",
        "ro.build.selinux": "0",
        "ro.hardware.gralloc": "vm",
        "ro.hardware.audio": "vm",
        "ro.hardware.camera": "vm",
        "ro.hardware.sensors": "vm",
        "ro.hardware.vulkan": "0",
        "debug.sf.disable_hwc": "1",
        "debug.sf.enable_gl_backpressure": "0",
        "debug.sf.latch_unsignaled": "1",
        "persist.sys.timezone": "Asia/Jakarta",
        "ro.vmers.version": "1.0.0",
        "ro.vmers.target_arch": "arm64-v8a"
    }

    content = ""
    if os.path.exists(prop_path):
        with open(prop_path, "r", errors="ignore") as f:
            content = f.read()

    lines = content.splitlines()
    existing_keys = set()
    new_lines = []
    
    for l in lines:
        if "=" in l and not l.strip().startswith("#"):
            k, v = l.split("=", 1)
            k = k.strip()
            if k in patches:
                new_lines.append(f"{k}={patches[k]}")
                existing_keys.add(k)
                continue
        new_lines.append(l)

    for k, v in patches.items():
        if k not in existing_keys:
            new_lines.append(f"{k}={v}")

    with open(prop_path, "w") as f:
        f.write("\n".join(new_lines) + "\n")
    log("build.prop patched successfully.")

def sanitize_daemons(rootfs_dir):
    log("Sanitizing hardware daemons (vold, ueventd, healthd)...")
    # Replace crashing hardware daemons with dummy exit scripts if invoked
    bin_dir = os.path.join(rootfs_dir, "system", "bin")
    if os.path.exists(bin_dir):
        dummy_script = "#!/system/bin/sh\nexit 0\n"
        for daemon in ['vold', 'ueventd', 'healthd', 'netd']:
            target = os.path.join(bin_dir, daemon)
            if os.path.exists(target):
                backup = target + ".orig"
                if not os.path.exists(backup):
                    try:
                        shutil.move(target, backup)
                        with open(target, "w") as f:
                            f.write(dummy_script)
                        os.chmod(target, 0o755)
                        log(f"  -> Replaced {daemon} with container-safe stub")
                    except Exception as e:
                        log(f"  [!] Could not replace {daemon}: {e}")

def package_rom(rootfs_dir, output_path, rom_meta):
    log(f"Packaging processed rootfs into {output_path}...")
    meta_file = os.path.join(rootfs_dir, "rom_info.json")
    with open(meta_file, "w") as f:
        json.dump(rom_meta, f, indent=2)

    out_dir = os.path.dirname(output_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
        
    if os.path.exists(output_path):
        os.remove(output_path)

    cmd = ['7z', 'a', '-t7z', '-mx=5', output_path, '.']
    subprocess.check_call(cmd, cwd=rootfs_dir)
    log(f"ROM package created successfully: {output_path} ({os.path.getsize(output_path) / 1024 / 1024:.2f} MB)")

def main():
    parser = argparse.ArgumentParser(description="Vmers AOSP GSI Android 15 Modifier")
    parser.add_argument("--input", "-i", required=True, help="Path to input system.img (raw or sparse)")
    parser.add_argument("--output", "-o", default="/root/Downloads/vm/Vmers/roms/vmers_a15_arm64.7z", help="Output 7z path")
    parser.add_argument("--work-dir", "-w", default="/root/Downloads/vm/Vmers/rom_tools/work", help="Working directory")
    args = parser.parse_args()

    check_dependencies()
    log("==================================================")
    log("       Vmers - Android 15 GSI ROM Processor       ")
    log("==================================================")
    
    rootfs = unpack_gsi(args.input, args.work_dir)
    flatten_apex_modules(rootfs)
    patch_build_properties(rootfs)
    sanitize_daemons(rootfs)
    
    meta = {
        "name": "Android 15 AOSP Vanilla (ARM64)",
        "id": "aosp_15_arm64_vmers",
        "api_level": 35,
        "os_version": "15.0",
        "arch": "arm64-v8a",
        "created_by": "Vmers Engineering Studio"
    }
    
    package_rom(rootfs, args.output, meta)
    log("[SUCCESS] GSI Android 15 ROM is fully prepared for Vmers!")

if __name__ == "__main__":
    main()
