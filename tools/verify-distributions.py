#!/usr/bin/env python3
"""Check signed APK manifests and source-set isolation without touching a device."""
import argparse
import difflib
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--aapt2', required=True, type=Path)
parser.add_argument('--github-apk', required=True, type=Path)
parser.add_argument('--play-apk', required=True, type=Path)
parser.add_argument('--baseline-permissions', type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
root = Path(__file__).resolve().parents[1]


def dump(*arguments):
    return subprocess.check_output([str(args.aapt2), 'dump', *map(str, arguments)], text=True)


permissions = {}
manifests = {}
for flavor, apk in [('github', args.github_apk), ('play', args.play_apk)]:
    permissions[flavor] = dump('permissions', apk)
    manifests[flavor] = dump('xmltree', '--file', 'AndroidManifest.xml', apk)
    (args.output / f'{flavor}-permissions.txt').write_text(permissions[flavor])
    (args.output / f'{flavor}-manifest.txt').write_text(manifests[flavor])
    (args.output / f'{flavor}-badging.txt').write_text(dump('badging', apk))
    assert 'package: pressf.gg.access\n' in permissions[flavor]
    assert 'moe.shizuku.manager.permission.' not in manifests[flavor]
    assert 'android.permission.WRITE_SECURE_SETTINGS' in permissions[flavor]
    assert 'android.permission.ACCESS_LOCAL_NETWORK' in permissions[flavor]
    assert 'android.permission.FOREGROUND_SERVICE\'' in permissions[flavor]

restricted = [
    'REQUEST_INSTALL_PACKAGES', 'REQUEST_DELETE_PACKAGES',
    'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS', 'FOREGROUND_SERVICE_SPECIAL_USE',
    'AdbPairingAccessibilityService', 'WatchdogService', 'StealthTutorialActivity',
    'BIND_ACCESSIBILITY_SERVICE', 'android.accessibilityservice',
    'PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
]
for name in restricted:
    assert name not in manifests['play'], f'Play manifest contains {name}'
    if name not in ('BIND_ACCESSIBILITY_SERVICE', 'android.accessibilityservice', 'PROPERTY_SPECIAL_USE_FGS_SUBTYPE'):
        assert name in manifests['github'], f'GitHub lost {name}'
assert 'AdbPairingService' in manifests['play']
# aapt2 prints compiled enum/flag values numerically (shortService = 0x800).
assert re.search(r'foregroundServiceType\([^)]*\)=0x0*800\b', manifests['play'])
if args.baseline_permissions:
    diff = ''.join(difflib.unified_diff(
        args.baseline_permissions.read_text().splitlines(True),
        permissions['github'].splitlines(True), fromfile='baseline', tofile='github'))
    (args.output / 'github-permissions.diff').write_text(diff)
    assert not diff, diff

# Check actual UI definitions and shared callsites, not only the flavor flag.
forbidden = r'UpdateHelper|StealthTutorial|StealthViewHolder|showAccessibilityDialog|AdbPairingAccessibilityService|AdbPairDialogFragment|btnUpdate|createStubApk|installPackage\(|WatchdogService'
command = ['rg', '-n', forbidden, 'manager/src/main/java', 'manager/src/play/java', 'manager/src/play/res']
result = subprocess.run(command, cwd=root, text=True, capture_output=True)
proof = '$ ' + shlex.join(command) + '\n' + result.stdout + result.stderr
assert result.returncode == 1, proof
proof += 'No matches (exit 1). Restricted implementations exist only in src/github.\n'
android = '{http://schemas.android.com/apk/res/android}'
for flavor in ['github', 'play']:
    preferences = ET.parse(root / f'manager/src/{flavor}/res/xml/settings.xml')
    keys = {node.get(android + 'key') for node in preferences.iter()}
    assert 'privacy_policy' in keys
    optional = {'update_mode', 'watchdog', 'legacy_pairing'}
    assert optional <= keys if flavor == 'github' else not optional & keys
    about = (root / f'manager/src/{flavor}/res/layout/about_dialog.xml').read_text()
    assert ('btnUpdate' in about) == (flavor == 'github')
proof += 'Play settings omit update_mode/watchdog/legacy_pairing; About omits btnUpdate.\n'
proof += 'Both settings resources include privacy_policy.\n'
(args.output / 'source-isolation-proof.txt').write_text(proof)
print('PASS: APK manifests, baseline permissions, source isolation and privacy entries')
