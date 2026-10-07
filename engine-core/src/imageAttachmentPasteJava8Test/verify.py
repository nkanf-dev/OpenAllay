#!/usr/bin/env python3
"""Remote-only complete managed-image attachment/clipboard/composer original modern vs genuine Java8 oracle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
for name in ['source-root', 'jdk17', 'jdk8', 'gson', 'junit-console', 'output']:
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args()
root = args.source_root.resolve()
contract_path = Path(__file__).parent / 'source-contract.json'
contract = json.loads(contract_path.read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha(Path(__file__).read_bytes()) == contract['runner_sha256'], 'Runner source differs'
def admit(path, pins):
    blob = path.read_bytes()
    digest = {'sha1': hashlib.sha1(blob).hexdigest(), 'sha256': sha(blob)}
    matches = [pin for pin in pins if len(blob) == pin['bytes'] and digest[pin['digest_algorithm']] == pin['digest']]
    assert len(matches) == 1, 'Dependency size/digest is not allowlisted: ' + str(path)
    return dict(matches[0], observed_sha256=sha(blob))
dependencies = {'gson': admit(args.gson, contract['dependency_pins']['gson']),
                'junit_console': admit(args.junit_console, contract['dependency_pins']['junit_console'])}
def run(command):
    result = subprocess.run(list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(map(str, command)) + '\n' + result.stdout + result.stderr)
    return result
args.output.mkdir(parents=True, exist_ok=False)
original = args.output / 'original'
for item in contract['files']:
    blob = subprocess.run(['git', '-C', str(root), 'cat-file', 'blob', item['git_blob']], check=True, stdout=subprocess.PIPE).stdout
    assert len(blob) == item['pre_bytes'] and sha(blob) == item['pre_sha256'], 'Original complete Git owner differs'
    path = original / item['path']
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(blob)
    assert sha((root / item['path']).read_bytes()) == item['post_sha256'], 'Applied owner differs: ' + item['path']
for item in contract['unchanged_compile_frontier'] + contract['fixture_artifacts'] + contract['selected_modern_tests'] + contract['staged_test_inputs']:
    assert sha((root / item['path']).read_bytes()) == item['sha256'], 'Pinned source differs: ' + item['path']
converter = contract['converter']
assert sha((root / converter['path']).read_bytes()) == converter['sha256'], 'Accepted converter differs'
java17, javac17 = args.jdk17 / 'bin/java', args.jdk17 / 'bin/javac'
java8, javac8 = args.jdk8 / 'bin/java', args.jdk8 / 'bin/javac'
versions = {}
for name, binary in [('java8', java8), ('javac8', javac8), ('java17', java17)]:
    result = run([binary, '-version'])
    versions[name] = result.stdout + result.stderr
assert '1.8.' in versions['java8'] and '1.8.' in versions['javac8'], 'Genuine Java8 compiler/runtime required'
tooling = args.output / 'converter-classes'
tooling.mkdir()
run([javac17, '--release', '17', '-d', tooling, root / converter['path']])
request = args.output / 'converter.tsv'
request.write_text('\n'.join('\t'.join([str(original / path), path, sha((original / path).read_bytes()), selected])
                             for path, selected in converter['selected'].items()) + '\n')
converted = args.output / 'converted'
run([java17, '-cp', tooling, 'dev.openallay.build.RecordValueSourceConverter', request, converted])
for item in contract['files']:
    path = item['path']
    source = (converted / 'candidate' / path if path in converter['selected'] else original / path).read_text()
    assert sha(source.encode()) == item['converter_expected_sha256'], 'Authenticated conversion differs: ' + path
    for change in contract['api_lowering'][path]:
        assert change['old'] in source, 'Missing exact lowering preimage: ' + path
        source = source.replace(change['old'], change['new'])
    source = re.sub(r'(?m)^[ \t]+$', '', source)
    assert source.encode() == (root / path).read_bytes(), 'Unproved production change: ' + path
staged = []
for item in contract['staged_test_inputs']:
    source = (root / item['path']).read_text()
    for change in item['substitutions']:
        assert change['old'] in source, 'Missing exact test staging preimage'
        source = source.replace(change['old'], change['new'])
    path = args.output / 'staged-test-inputs' / item['path']
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source)
    staged.append(path)
support = [root / item['path'] for item in contract['unchanged_compile_frontier']]
fixtures = [root / contract['fixture_path'], root / contract['junit_fixture_path']]
cp = os.pathsep.join(map(str, [args.gson, args.junit_console]))
vectors, majors, logs = {}, {}, {}
for flavor in ['modern', 'release8', 'java8']:
    classes = args.output / (flavor + '-classes')
    classes.mkdir()
    owners = [original / item['path'] if flavor == 'modern' else root / item['path'] for item in contract['files']]
    compiler = javac8 if flavor == 'java8' else javac17
    target = ['-source', '8', '-target', '8'] if flavor == 'java8' else ['--release', '17' if flavor == 'modern' else '8']
    result = run([compiler] + target + ['-encoding', 'UTF-8', '-classpath', cp, '-sourcepath', '', '-d', classes]
                 + owners + support + fixtures + staged)
    logs[flavor + '-compile'] = result.stdout + result.stderr
    majors[flavor] = {}
    for path in classes.rglob('*.class'):
        blob = path.read_bytes()
        assert blob[:4] == b'\xca\xfe\xba\xbe'
        major = int.from_bytes(blob[6:8], 'big')
        if flavor != 'modern': assert major == 52, str(path)
        majors[flavor][str(path.relative_to(classes))] = major
    vm = java17 if flavor == 'modern' else java8
    result = run([vm, '-Djava.awt.headless=true', '-cp', os.pathsep.join(map(str, [classes, args.gson])), 'dev.openallay.client.gui.ImageAttachmentPasteJava8Fixture'])
    vectors[flavor] = result.stdout
    logs[flavor + '-vectors'] = result.stdout + result.stderr
    names = contract['junit_classes']
    selection = [part for name in names for part in ['--select-class', name]]
    result = run([vm, '-Djava.awt.headless=true', '-jar', args.junit_console, '--class-path', os.pathsep.join(map(str, [classes, args.gson]))]
                 + selection + ['--reports-dir', args.output / (flavor + '-junit-results'), '--fail-if-no-tests'])
    logs[flavor + '-junit'] = result.stdout + result.stderr
    for name, text in logs.items(): (args.output / (name + '.log')).write_text(text)
assert vectors['modern'] == vectors['release8'] == vectors['java8'], 'Original modern and genuine8 behavior differs'
receipt = {'scope': contract['scope'], 'changed_complete_owner_count': len(contract['files']),
           'actual_production_compile_frontier_count': len(contract['files']) + len(support),
           'explicit_schema_count': contract['explicit_schema_count'], 'class_majors': majors,
           'jdk_versions': versions, 'dependency_admission': dependencies,
           'same_fixture_original_git_vs_actual8_vectors_equal': True,
           'behavior_vector_count': len(vectors['modern'].splitlines()),
           'source_contract_sha256': sha(contract_path.read_bytes()),
           'accepted_converter_source_sha256': converter['sha256'],
           'hash_oracle_contract': contract['hash_oracle_contract'],
           'selected_modern_tests_require_separate_native_project_execution': contract['selected_modern_tests']}
(args.output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print('PASS seven complete managed-image attachment/clipboard/composer owners; original modern parity, release8 and genuine javac8/java8')
