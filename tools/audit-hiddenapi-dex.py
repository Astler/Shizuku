#!/usr/bin/env python3
"""List scanner-relevant class definitions in an APK/AAB, including existing R8 mappings."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

PREFIXES = ('org.lsposed.hiddenapibypass.', 'rikka.hidden.compat.', 'dev.rikka.tools.refine.')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dexdump', required=True, type=Path)
parser.add_argument('--artifact', required=True, type=Path)
parser.add_argument('--mapping', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()

definitions = set()
with tempfile.TemporaryDirectory(prefix='shizuku-dex-') as directory:
    with zipfile.ZipFile(args.artifact) as archive:
        entries = sorted(name for name in archive.namelist() if name.endswith('.dex'))
        if not entries:
            raise SystemExit('No dex files found')
        for index, name in enumerate(entries):
            dex = Path(directory) / f'{index}.dex'
            dex.write_bytes(archive.read(name))
            result = subprocess.run([str(args.dexdump), str(dex)], text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            definitions.update(value.replace('/', '.') for value in
                               re.findall(r"Class descriptor\s*:\s*'L([^;]+);'", result.stdout))

mapping = {}
inline = set()
current_target = None
for line in args.mapping.read_text().splitlines():
    match = re.fullmatch(r'(\S+) -> (\S+):', line)
    if match:
        mapping[match[1]] = match[2]
        current_target = match[2]
    elif current_target in definitions and '(' in line:
        # A removed helper can still have its methods inlined into a surviving server class.
        for prefix in PREFIXES:
            if prefix in line:
                inline.add((current_target, line.strip()))

lines = [f'Artifact: {args.artifact}',
         f'SHA-256: {hashlib.sha256(args.artifact.read_bytes()).hexdigest()}',
         f'Dex entries: {", ".join(entries)}',
         f'Class definitions: {len(definitions)}',
         'Existing release R8 rules are unchanged. Names alone do not prove code removal.']
for prefix in PREFIXES:
    literal = sorted(name for name in definitions if name.startswith(prefix))
    originals = sorted((source, target) for source, target in mapping.items()
                       if source.startswith(prefix) and target in definitions)
    inlined = sorted((target, method) for target, method in inline if prefix in method)
    lines += ['', prefix, f'  Literal dex class definitions: {len(literal)}']
    lines += [f'    {name}' for name in literal]
    lines += [f'  Surviving mapped class definitions: {len(originals)}']
    lines += [f'    {source} -> {target}' for source, target in originals]
    lines += [f'  Mapping entries for inlined methods in surviving classes: {len(inlined)}']
    lines += [f'    {target}: {method}' for target, method in inlined]
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text('\n'.join(lines) + '\n')
print('\n'.join(line for line in lines if not line.startswith('    ')))
