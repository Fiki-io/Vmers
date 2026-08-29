import json
import os
import sys
import urllib.request
import requests

def upload_to_gofile(file_path):
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return None

    file_size = os.path.getsize(file_path) / (1024 * 1024)
    print(f"[*] Preparing to upload {file_path} ({file_size:.2f} MB) to GoFile...")

    # 1. Get best server
    try:
        req = urllib.request.Request('https://api.gofile.io/servers', headers={'User-Agent': 'Mozilla/5.0'})
        data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))
        server = data['data']['servers'][0]['name']
        print(f"[+] Selected GoFile server: {server}")
    except Exception as e:
        print(f"Failed to get GoFile server, fallback to store1: {e}")
        server = 'store1'

    upload_url = f"https://{server}.gofile.io/contents/uploadfile"
    print(f"[*] Uploading to {upload_url} ...")

    with open(file_path, 'rb') as f:
        files = {'file': (os.path.basename(file_path), f)}
        r = requests.post(upload_url, files=files)
        
    res = r.json()
    if res.get('status') == 'ok':
        download_page = res['data']['downloadPage']
        print(f"\n========================================================")
        print(f"[SUCCESS] Uploaded to GoFile successfully!")
        print(f"Download URL: {download_page}")
        print(f"========================================================\n")
        return download_page
    else:
        print(f"[ERROR] GoFile upload failed: {res}")
        return None

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "/root/Downloads/vm/Vmers/roms/vmers_a15_arm64.7z"
    upload_to_gofile(target)
