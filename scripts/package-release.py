#!/usr/bin/env python3
"""Package the verified Android APK, matching source and test report for a GitHub release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_SIGNER = '213034870b96e6cd98d1b56225e668c5f81f1b198f3f4b4a28d4f686481f844e'
EXCLUDED_DIRS = {'.git', '.gradle', '.kotlin', 'build', 'dist', 'release-files', '__pycache__', '.idea'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.pem', '.key', '.p12', '.apk', '.aar', '.dex', '.class', '.log', '.pyc'}


def version():
    source = (ROOT / 'app/build.gradle.kts').read_text()
    name = re.search(r'versionName\s*=\s*"([^"\n]+)"', source).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', source).group(1))
    return name, code


def validate_tag(tag, name, tv=False):
    prefix = "android-tv-v" if tv else "v"
    if not re.fullmatch(re.escape(prefix) + re.escape(name) + r'(?:-preview\.[1-9]\d*)?', tag):
        raise ValueError(f'Tag must match {prefix}{name} or {prefix}{name}-preview.N')


def source_files():
    for path in sorted(ROOT.rglob('*')):
        relative = path.relative_to(ROOT)
        if not path.is_file() or path.is_symlink() or set(relative.parts) & EXCLUDED_DIRS:
            continue
        if path.name == 'local.properties' or path.name.startswith('.env') or path.suffix in PRIVATE_SUFFIXES:
            continue
        yield path, relative.as_posix()


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate-tag')
    parser.add_argument('--tag')
    parser.add_argument('--tv', action='store_true', help='Package the separate Android TV flavor')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'release-files')
    args = parser.parse_args()
    name, code = version()
    tag = args.validate_tag or args.tag
    if not tag:
        parser.error('--tag or --validate-tag is required')
    validate_tag(tag, name, tv=args.tv)
    if args.validate_tag:
        print(f'Valid release tag: {tag}')
        return

    variant = 'tv' if args.tv else 'foss'
    application_id = 'io.sourcify.player.tv' if args.tv else 'io.sourcify.player.uitest'
    label = 'Sourcify TV' if args.tv else 'Sourcify'
    input_apk = args.apk or ROOT / f'app/build/outputs/apk/{variant}/debug/app-{variant}-debug.apk'
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    build_tools = sdk / 'build-tools/37.0.0'
    cert = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--print-certs', str(input_apk)], text=True)
    if EXPECTED_SIGNER not in cert:
        raise ValueError('APK signer does not match the existing Sourcify installation')
    badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(input_apk)], text=True)
    for expected in [f"name='{application_id}'", f"versionCode='{code}'", f"versionName='{name}'", f"application-label:'{label}'"]:
        if expected not in badging:
            raise ValueError(f'APK metadata mismatch: {expected}')

    if args.tv and ("leanback-launchable-activity:" not in badging or "uses-feature: name='android.software.leanback'" not in badging):
        raise ValueError('APK is missing the Android TV launcher or required leanback feature')
    suites = [ET.parse(path).getroot().attrib for path in sorted((ROOT / ('app/build/test-results/testTvDebugUnitTest' if args.tv else 'app/build/test-results/testFossDebugUnitTest')).glob('TEST-*.xml'))]
    if not suites or any(int(s.get('failures', 0)) or int(s.get('errors', 0)) for s in suites):
        raise ValueError('A complete successful test result is required')
    count = sum(int(s['tests']) for s in suites)
    if count == 0:
        raise ValueError('No tests ran')

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    apk = output / ('Sourcify-TV.apk' if args.tv else 'Sourcify.apk')
    shutil.copy2(input_apk, apk)
    archive = output / (f'Sourcify-TV-{name}-source.zip' if args.tv else f'Sourcify-{name}-source.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path, relative in source_files():
            if output == path.parent or output in path.parents:
                continue
            z.write(path, f'Sourcify/{relative}')
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:
            raise ValueError('Source ZIP verification failed')

    report = {
        'version_name': name, 'version_code': code, 'tag': tag,
        'application_id': application_id, 'application_label': label,
        'variant': variant + 'Debug', 'platform': 'Android TV' if args.tv else 'Android', 'prerelease': True, 'hardware_tested': False,
        'certificate_sha256': EXPECTED_SIGNER, 'test_count': count,
        'tests': [{k: s[k] for k in ('name', 'tests', 'failures', 'errors', 'skipped')} for s in suites],
        'files': {p.name: {'bytes': p.stat().st_size, 'sha256': digest(p)} for p in (apk, archive)},
        'commands': [':app:assembleTvDebug', ':app:testTvDebugUnitTest'] if args.tv else [':app:assembleFossDebug', ':app:testFossDebugUnitTest'],
        'limitations': ['No physical Android TV device test.' if args.tv else 'No physical Android device test.', 'FOSS debug build with the existing update-compatible signer.', 'YouTube Music availability determines streams and recommendations.'],
    }
    report_path = output / 'build-report.json'
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in (apk, archive, report_path)))
    print(json.dumps({'output': str(output), 'tests': count, 'files': report['files']}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'Release packaging failed: {error}', file=sys.stderr)
        sys.exit(1)
