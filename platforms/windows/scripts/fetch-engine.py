"""Download a pinned official engine, verifying the fixed release checksum."""
from pathlib import Path
import hashlib,json,urllib.request
VERSION='2026.08.19'
EXPECTED='66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a'
root=Path(__file__).resolve().parents[1]/'vendor';root.mkdir(exist_ok=True)
data=urllib.request.urlopen(f'https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp.exe',timeout=120).read()
if hashlib.sha256(data).hexdigest()!=EXPECTED:raise SystemExit('Engine checksum mismatch; refusing to write file.')
(root/'yt-dlp.exe').write_bytes(data)
(root/'version.json').write_text(json.dumps({'version':VERSION,'sha256':EXPECTED}))
print('Verified yt-dlp',VERSION)
