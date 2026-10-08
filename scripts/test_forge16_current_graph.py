"""No-network complete shared-owner and current-graph fixture checks."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('forge16_graph',ROOT/'scripts/prepare-legacy-forge-release.py')
m=module_from_spec(spec);spec.loader.exec_module(m)


def jar(path,values):
    with zipfile.ZipFile(path,'w') as z:
        for name,blob in values.items():z.writestr(name,blob)


class SharedOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.original={role:{role+'.class':('old '+role).encode()} for role in ('engine','sdk','rhino','commonmark','tables')}
        self.current={role:{role+'.class':('current '+role).encode()} for role in ('engine','sdk','rhino','commonmark')}
        self.current['commonmark']['tables.class']=b'current source tables'
        self.payload={'native.class':b'unchanged native','external.class':b'unchanged dependency'}
        self.rows=[]
        for role,entries in self.original.items():
            for name,data in entries.items():
                self.payload[name]=data
                self.rows.append({'name':name,'sha256':m.digest(data),'owners':[{'role':role,'name':name,'sha256':m.digest(data)}]})
        for name in ('native.class','external.class'):
            self.rows.append({'name':name,'sha256':m.digest(self.payload[name]),'owners':[{'role':'native-reobf','name':name,'sha256':m.digest(self.payload[name])}]})

    def test_all_shared_roles_replace_whole_entries_and_tables_have_one_owner(self):
        result,proof=m.replace_shared_owners(self.payload,self.rows,self.original,self.current)
        for role,values in self.current.items():
            for name,data in values.items():self.assertEqual(result[name],data)
        self.assertEqual(result['native.class'],b'unchanged native')
        self.assertEqual(result['external.class'],b'unchanged dependency')
        self.assertEqual(proof['historicalRoles'],['commonmark','engine','rhino','sdk','tables'])
        self.assertEqual(proof['currentRoles'],['engine','sdk','rhino','commonmark'])

    def test_unknown_changed_input_and_collision_fail_without_mutating_payload(self):
        previous=deepcopy(self.payload)
        self.original['rhino']['rhino.class']=b'fake baseline'
        with self.assertRaises(ValueError):m.replace_shared_owners(self.payload,self.rows,self.original,self.current)
        self.assertEqual(self.payload,previous)
        self.original['rhino']['rhino.class']=b'old rhino'
        self.current['engine']['external.class']=b'changed unrelated owner'
        with self.assertRaises(ValueError):m.replace_shared_owners(self.payload,self.rows,self.original,self.current)

    def test_runtime_classes_named_notice_are_not_legal_resources(self):
        self.current['engine']['dev/openallay/Notice.class']=b'class'
        result,_=m.replace_shared_owners(self.payload,self.rows,self.original,self.current)
        self.assertEqual(result['dev/openallay/Notice.class'],b'class')
        self.assertNotIn('META-INF/licenses/engine/dev/openallay/Notice.class',result)

    def test_services_preserve_native_contributors_and_replace_real_shared_provider(self):
        name='META-INF/services/example.Service'
        self.original['engine'][name]=b'old.Provider\n'
        self.current['engine'][name]=b'new.Provider\n'
        self.payload[name]=b'old.Provider\nnative.Provider\n'
        self.rows.append({'name':name,'sha256':m.digest(self.payload[name]),'owners':[
            {'role':'engine','name':name,'sha256':m.digest(b'old.Provider\n')},
            {'role':'native-reobf','name':name,'sha256':m.digest(b'native.Provider\n')}]})
        result,_=m.replace_shared_owners(self.payload,self.rows,self.original,self.current)
        self.assertEqual(result[name],b'native.Provider\nnew.Provider\n')

    def test_current_runtime_replaces_all_actual_roles_and_drops_old_tables(self):
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary)
            engine=base/'engine.jar';jar(engine,{'engine.class':b'same engine'})
            jsonjar=base/'json.jar';jar(jsonjar,{'json.class':b'json'})
            jars={'engine':engine,'json-proof':jsonjar}
            spec={'artifacts':[{'role':role,'coordinate':'old:'+role+':0.4.3','path':'old.jar','sha256':'0'*64}
                               for role in ['engine','sdk','rhino','commonmark','tables','json-proof','maven-proof','builder',
                                            'gson','guava','failureaccess','listenablefuture','jtokkit','sqlite','jsr305','checkerqual','errorprone','j2objc']]}
            identities=[('com.google.code.gson','gson'),('com.google.guava','guava'),('com.google.guava','failureaccess'),
                        ('com.google.guava','listenablefuture'),('com.knuddels','jtokkit'),('org.xerial','sqlite-jdbc'),
                        ('com.google.code.findbugs','jsr305'),('org.checkerframework','checker-qual'),
                        ('com.google.errorprone','error_prone_annotations'),('com.google.j2objc','j2objc-annotations')]
            runtime=[]
            for group,artifact in identities:
                path=base/'files-2.1'/group/artifact/'current'/'hash'/'actual.jar';path.parent.mkdir(parents=True)
                jar(path,{'runtime.class':artifact.encode()});runtime.append(str(path))
                role={'gson':'gson','guava':'guava','failureaccess':'failureaccess','listenablefuture':'listenablefuture','jtokkit':'jtokkit','sqlite-jdbc':'sqlite','jsr305':'jsr305','checker-qual':'checkerqual','error_prone_annotations':'errorprone','j2objc-annotations':'j2objc'}[artifact]
                next(row for row in spec['artifacts'] if row['role']==role)['sha256']=m.sha(path)
            for module in ('runtime-rhino','runtime-commonmark','extension-api'):
                path=base/module/'build/libs/current.jar';path.parent.mkdir(parents=True)
                jar(path,{'module.class':module.encode()});runtime.append(str(path))
            result=m.current_runtime(spec,jars,{'runtimeClasspath':runtime},'0.4.4')
            roles={row['role']:row for row in result}
            self.assertEqual(len(roles),15)
            self.assertNotIn('tables',roles)
            self.assertNotIn('json-proof',roles)
            self.assertNotIn('maven-proof',roles)
            self.assertEqual(roles['commonmark']['coordinate'],'dev.openallay:openallay-commonmark:0.28.0')
            self.assertEqual(roles['rhino']['sha256'],m.sha(Path(runtime[-3])))
            self.assertEqual(roles['sdk']['sha256'],m.sha(Path(runtime[-1])))
            self.assertEqual(roles['engine']['sha256'],m.sha(engine))
            changed=deepcopy(spec);next(row for row in changed['artifacts'] if row['role']=='sqlite')['sha256']='f'*64
            with self.assertRaises(ValueError):m.current_runtime(changed,jars,{'runtimeClasspath':runtime},'0.4.4')
            with self.assertRaises(ValueError):m.current_runtime(spec,jars,{'runtimeClasspath':runtime+[runtime[-1]]},'0.4.4')
            with self.assertRaises(ValueError):m.current_runtime(spec,jars,{'runtimeClasspath':runtime[:-1]},'0.4.4')

    def test_latest_normal_producer_keeps_original_native_proof_separate(self):
        source=(ROOT/'scripts/prepare-legacy-forge-release.py').read_text()
        self.assertIn("metadata['sourceRevision']==git('rev-parse','HEAD')",source)
        self.assertIn("'historicalProvider':PRODUCT16",source)
        self.assertNotIn('SDK/Rhino canonical class/API bytes changed',source)
        self.assertIn("currentRuntimeArtifacts=",source)
        build=(ROOT/'native-builds/forge16165/build.gradle').read_text()
        self.assertIn("gradle.gradleVersion != '8.4' || Runtime.version().feature() != 17",build)
        self.assertIn('closure.artifacts.size() != 15',build)
        self.assertIn("['engine', 'sdk', 'rhino', 'commonmark', 'jtokkit']",build)
        self.assertNotIn("'commonmark', 'tables', 'jtokkit'",build)


if __name__=='__main__':unittest.main()
