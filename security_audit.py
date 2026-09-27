#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest_path = root / 'app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text(encoding='utf-8')
failures = []

for xml in root.glob('app/src/main/res/**/*.xml'):
    try:
        ET.parse(xml)
    except Exception as e:
        failures.append(f'invalid XML {xml}: {e}')
try:
    ET.parse(manifest_path)
except Exception as e:
    failures.append(f'invalid manifest XML: {e}')

for forbidden in [
    'android.permission.INTERNET',
    'android.permission.READ_EXTERNAL_STORAGE',
    'android.permission.WRITE_EXTERNAL_STORAGE',
    'android.permission.READ_MEDIA_IMAGES',
    'android.permission.CAMERA',
]:
    if forbidden in manifest:
        failures.append(f'forbidden permission present: {forbidden}')

required = [
    'android:allowBackup="false"',
    'android:fullBackupContent="false"',
    'android:usesCleartextTraffic="false"',
    'android:networkSecurityConfig="@xml/network_security_config"',
]
for token in required:
    if token not in manifest:
        failures.append(f'missing manifest hardening: {token}')

java = '\n'.join(p.read_text(encoding='utf-8') for p in root.glob('app/src/main/java/**/*.java'))
for pattern in [r'android\.util\.Log', r'printStackTrace\s*\(', r'WebView', r'DexClassLoader', r'loadLibrary\s*\(']:
    if re.search(pattern, java):
        failures.append(f'risky API/pattern found: {pattern}')

gradle = (root/'app/build.gradle').read_text(encoding='utf-8')
for token in ['minifyEnabled true', 'shrinkResources true', 'debuggable false']:
    if token not in gradle:
        failures.append(f'missing release hardening: {token}')

if failures:
    print('SECURITY PREFLIGHT: FAIL')
    for f in failures: print(' -', f)
    sys.exit(1)
print('SECURITY PREFLIGHT: PASS')
