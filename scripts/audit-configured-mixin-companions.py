#!/usr/bin/env python3
"""Audit every configured Mixin in the actual native JAR before final packaging."""
import argparse
import json
from pathlib import Path
import zipfile


def audit(jar, output):
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        owners = []
        for name in sorted(names):
            if name.endswith('.mixins.json'):
                config = json.loads(archive.read(name))
                for group in ('mixins', 'client', 'server'):
                    for owner in config.get(group, []):
                        owners.append((config['package'] + '.' + owner).replace('.', '/'))
        if not owners or len(owners) != len(set(owners)):
            raise ValueError('Configured native Mixin owners must be nonempty and unique')
        failures = []
        rows = []
        for owner in owners:
            entry = owner + '.class'
            if entry not in names:
                failures.append({'owner': owner, 'failure': 'configured class absent'})
                continue
            companions = sorted(name for name in names if name.startswith(owner + '$') and name.endswith('.class'))
            generated = [name for name in companions if '$oaPattern' in name or '$oaSwitch' in name]
            if generated:
                failures.append({'owner': owner, 'failure': 'generated injection companion classes', 'entries': generated})
            rows.append({'owner': owner, 'companions': companions})
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'jar': str(jar), 'owners': rows, 'failures': failures,
        'accepted': not failures, 'gameAccepted': False}, indent=2) + '\n')
    if failures:
        raise ValueError('Configured Mixin generated companion classes are not legal injected runtime dependencies')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    audit(args.jar, args.output)


if __name__ == '__main__':
    main()
