"""Finite reviewed nonselected native leaves; exact actual Forge12 selection custody."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

from forge1122_source_selection import selected_native_units

POLICY_PATH="distribution/stock8-nonselected-native-deltas.json"
POLICY_SHA256="17f2c977364cfc123b9a2c60a282bede70a919858c6dc8b62e5e1b7837aad1ba"
CONTROL_PATHS=frozenset(('gradle/minecraft-source-selection.gradle','native-builds/forge16165/source-selection.json',
    'native-builds/forge1122-census/source-retirements.json','native-builds/forge1122-census/core-compile-profile.json',
    'native-builds/forge1122-census/curated-classes.tsv','scripts/forge1122_source_selection.py'))


def require(ok,message):
    if not ok:raise ValueError(message)


def sha(raw):return hashlib.sha256(raw).hexdigest()


def policy(root):
    raw=(root/POLICY_PATH).read_bytes()
    require(sha(raw)==POLICY_SHA256, 'Reviewed finite native custody policy bytes changed')
    value=json.loads(raw)
    require(set(value)=={'reviewedPatchSha256','files','shadowEvidence','selectorInputs','approvedGroupChanges'} and
            value['reviewedPatchSha256']=='693e961f7904b4ac222f27c0cc04d93c379e4f02c2fa28d54cac080e082ebe85',
            'Exact reviewed finite native cohort required')
    rows=value['files'];evidence=value['shadowEvidence']
    require(len(rows)==22 and len({row['path'] for row in rows})==22 and
            {row['path'] for row in rows}=={row['changedPath'] for row in evidence}, 'Exact22 cohort/shadow evidence required')
    require(len(value['selectorInputs'])==6 and {row['path'] for row in value['selectorInputs']}==CONTROL_PATHS, 'Exact native selector input scope required')
    for row in rows:
        require(set(row)=={'path','preSha256','postSha256','bytes'} and row['path'].startswith('common/src/targets/') and
                '/java/' in row['path'] and row['path'].endswith('.java') and
                (row['preSha256'] is None or re.fullmatch(r'[0-9a-f]{64}',row['preSha256'])) and
                (row['postSha256'] is None or re.fullmatch(r'[0-9a-f]{64}',row['postSha256'])), 'Exact cohort raw source row required')
    repairs=value['approvedGroupChanges']
    require(len(repairs)==9 and len({row['path'] for row in repairs})==9 and {row['path'] for row in repairs}.issubset({row['path'] for row in rows}), 'Exact9 accepted-group repair operations required')
    for row in repairs:
        require(set(row)=={'path','preSha256','postSha256'} and all(row[key] is None or re.fullmatch(r'[0-9a-f]{64}',row[key]) for key in ('preSha256','postSha256')), 'Exact accepted-group raw pair required')
    for row in value['selectorInputs']:
        require(set(row)=={'path','sha256','gitSha256'} and all(re.fullmatch(r'[0-9a-f]{64}',row[key]) for key in ('sha256','gitSha256')), 'Exact selector Git/physical hash pair required')
    return value


def verify_unselected_pair(root,path,original,current):
    value=policy(root);rows={row['path']:row for row in value['files']}
    require(path in rows,'Unknown native production change cannot reuse stock8')
    row=rows[path]
    require((None if original is None else sha(original))==row['preSha256'] and (None if current is None else sha(current))==row['postSha256'],
            'Nonselected native leaf differs from exact reviewed raw before/after: '+path)


def group_change_paths(root):
    return {row['path'] for row in policy(root)['approvedGroupChanges']}


def verify_group_pair(root,path,original,current):
    rows={row['path']:row for row in policy(root)['approvedGroupChanges']}
    require(len(rows)==9 and path in rows, 'Unknown accepted-group native repair')
    row=rows[path]
    require((None if original is None else sha(original))==row['preSha256'] and
            (None if current is None else sha(current))==row['postSha256'], 'Accepted-group repair raw pair differs')


def verify_control_bytes(row,raw):
    require(set(row)=={'path','sha256','gitSha256'}, 'Exact selector control fields required')
    allowed={row['sha256']}
    if row['path']=='gradle/minecraft-source-selection.gradle':
        require(row['sha256']=='6dbd09965a89a6c8ec833b340fead887bb4be44b1a4b518b0217c1769e8a8559' and
                row['gitSha256']=='f7f76ffb700100fbb4911790fb2e07a0f3b1de7b5d225f45547ec6fa04eeb25b',
                'Exact declared Gradle checkout CRLF/Git LF pair required')
        allowed.add(row['gitSha256'])
    require(sha(raw) in allowed, 'Forge12 selector/profile input changed: '+row['path'])


def verify_selection(root,original_selected,native_source):
    value=policy(root)
    require(type(original_selected) is dict and len(original_selected)==263,'Exact original263 selected source ledger required')
    for row in value['selectorInputs']:
        verify_control_bytes(row,(root/row['path']).read_bytes())
        if row['path']!='scripts/forge1122_source_selection.py':
            original=subprocess.check_output(['git','-C',str(root),'show',native_source+':'+row['path']])
            require(sha(original)==row['gitSha256'], 'Original native selector/profile custody differs')
    java,projection=selected_native_units(root)
    selected={row['origin']:row['sha256'] for row in java+projection['resources']}
    require(len(java)==251 and len(projection['resources'])==12 and selected==original_selected,
            'Effective Forge12 selected source origin/hash ledger changed')
    logical={row['logicalPath']:(row['origin'],row['sha256']) for row in java}
    for row in value['shadowEvidence']:
        require(row['changedPath'] not in selected and row['selectedStock12Before']==row['selectedStock12After'] and
                row['selectedBytesUnchanged'] is True and
                logical[row['changedPath'].split('/java/',1)[1]]==(row['selectedStock12After'],row['selectedSha256']),
                'Reviewed native leaf is not shadowed by exact unchanged Forge12 owner')
    return {'selectedSources':263,'selectedJava':251,'selectedResources':12,'nonselectedCohortLeaves':22}
