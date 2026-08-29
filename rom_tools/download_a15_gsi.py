#!/usr/bin/env python3
"""
Vmers - AOSP Android 15 GSI Downloader & Verifier
Downloads standard official AOSP Android 15 GSI ARM64 images.
"""

import os
import sys
import urllib.request
import gzip
import shutil
import hashlib

A15_GSI_SOURCES = [
    {
        "name": "AOSP Android 15 (VanillaIceCream) ARM64 - Official CI Build",
        "url": "https://dl.google.com/developers/android/vic/images/gsi/aosp_arm64-exp-AP3A.241005.015-12453664-884ddfd6.zip",
        "filename": "aosp_arm64_a15.zip"
    },
    {
        "name": "AOSP Android 15 GSI ARM64 (Generic System Image)",
        "url": "https://github.com/phhusson/treble_experimentations/releases/download/v15.0-preview/system-arm64-ab-vanilla.img.xz",
        "filename": "system-arm64-ab-vanilla.img.xz"
    }
]

def download_with_progress(url, dest_path):
    print(f"[*] Downloading from: {url}")
    print(f"[*] Saving to: {dest_path}")
    
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    with urllib.request.urlopen(req) as response, open(dest_path, 'wb') as out_file:
        total_size = int(response.info().get('Content-Length', -1))
        downloaded = 0
        chunk_size = 1024 * 1024 # 1MB chunks
        
        while True:
            chunk = response.read(chunk_size)
            if not chunk:
                break
            out_file.write(chunk)
            downloaded += len(chunk)
            if total_size > 0:
                percent = downloaded * 100 / total_size
                print(f"\rProgress: {downloaded / 1024 / 1024:.1f} MB / {total_size / 1024 / 1024:.1f} MB ({percent:.1f}%)", end="", flush=True)
            else:
                print(f"\rDownloaded: {downloaded / 1024 / 1024:.1f} MB", end="", flush=True)
    print("\n[+] Download complete!")

if __name__ == "__main__":
    out_dir = "/root/Downloads/vm/Vmers/roms"
    os.makedirs(out_dir, exist_ok=True)
    print("Vmers GSI Downloader initialized.")
