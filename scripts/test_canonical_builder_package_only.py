"""Truthful package-only fixtures: no compiler, game, network or original product copies."""
from copy import deepcopy
from importlib.util import spec_from_file_location,module_from_spec
from pathlib import Path
import base64,hashlib,io,json,tempfile,unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('package_only_fixture',ROOT/'scripts/package_canonical_builder.py');p=module_from_spec(spec);spec.loader.exec_module(p)


def jar(entries):
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w') as z:
  for name,raw in entries.items():z.writestr(name,raw)
 return out.getvalue()

class PackageOnlyTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.old=b'original Builder fixture';self.builder=b'canonical Builder fixture';self.oldsha=p.sha(self.old);self.newsha=p.sha(self.builder)
  self.prov={'source':{'repository':'https://github.com/nkanf-dev/OpenAllay-Extensions.git','revision':p.SOURCE,'dirty':False,'pinned':True},
    'project':'extensions/minecraft-builder','version':'0.4.0','extensionId':'openallay:builder','openAllayApiVersion':'0.4.0',
    'artifact':{'path':p.RESOURCE,'sha256':self.oldsha}}
  self.entries={p.RESOURCE:self.old,p.PROVENANCE:json.dumps(self.prov).encode(),'Engine.class':b'genuine engine class',
                'Native.class':b'genuine native class','META-INF/services/Service':b'actual.Provider','LICENSE':b'legal','resource':b'actual'}
  self.original=jar(self.entries)
  self.patches=[patch.object(p,'BUILDER_SHA',self.newsha),patch.object(p,'OLD_BUILDER_SHA',self.oldsha)]
  for value in self.patches:value.start();self.addCleanup(value.stop)
 def test_only_two_exact_entries_replaced_all_others_byte_equal(self):
  current=p.replacement(self.original,self.builder);before=p.archive(self.original);after=p.archive(current)
  self.assertEqual(set(before),set(after));self.assertEqual(after[p.RESOURCE],self.builder)
  self.assertEqual(json.loads(after[p.PROVENANCE]),{**self.prov,'artifact':{'path':p.RESOURCE,'sha256':self.newsha}})
  self.assertTrue(all(before[n]==after[n] for n in before if n not in (p.RESOURCE,p.PROVENANCE)))
  self.assertTrue(p.verify_replacement(self.original,current,self.builder))
 def test_changed_class_native_legal_service_or_resource_rejected(self):
  good=p.archive(p.replacement(self.original,self.builder))
  for name in ('Engine.class','Native.class','LICENSE','META-INF/services/Service','resource'):
   changed=dict(good);changed[name]=b'changed'
   with self.subTest(name=name),self.assertRaises(ValueError):p.verify_replacement(self.original,jar(changed),self.builder)
 def test_added_deleted_and_wrong_provenance_fields_rejected(self):
  good=p.archive(p.replacement(self.original,self.builder))
  for changed in (dict(good,Extra=b'extra'),{k:v for k,v in good.items() if k!='resource'}):
   with self.assertRaises(ValueError):p.verify_replacement(self.original,jar(changed),self.builder)
  value=json.loads(good[p.PROVENANCE]);value['source']['revision']='e'*40;good[p.PROVENANCE]=json.dumps(value).encode()
  with self.assertRaises(ValueError):p.verify_replacement(self.original,jar(good),self.builder)
 def test_wrong_original_or_canonical_builder_rejected(self):
  with self.assertRaises(ValueError):p.replacement(self.original,b'wrong provider')
  changed=dict(self.entries);changed[p.RESOURCE]=b'unknown original'
  with self.assertRaises(ValueError):p.replacement(jar(changed),self.builder)
 def test_unsafe_zip_and_duplicate_entry_rejected(self):
  for name in ('../escape','/absolute','a\\b','a:b'):
   with self.assertRaises(ValueError):p.archive(jar({name:b'bad'}))
  out=io.BytesIO()
  with zipfile.ZipFile(out,'w') as z:
   import warnings
   with warnings.catch_warnings():
    warnings.simplefilter('ignore');z.writestr('same',b'1');z.writestr('same',b'2')
  with self.assertRaises(ValueError):p.archive(out.getvalue())
 def test_receipt_kind_cannot_pretend_compile_or_ignore_original_proof(self):
  with self.assertRaises(ValueError):p.verify_receipt(self.root,{},Path('none'),{'kind':'compile-package'},Path('none'),'a'*40)
  text=(ROOT/'scripts/package_canonical_builder.py').read_text()
  self.assertIn("receipt['kind']=='package-only'",text)
  self.assertIn("sha(receipt_raw)==row['receiptSha256']",text)
  self.assertIn("sha(raw)==row['artifactSha256']==receipt['artifactSha256']",text)
  self.assertIn("base64.b64decode(receipt['originalReceiptBase64'],validate=True)==oldreceipt",text)
 def test_original_provider_source_receipt_and_engine_identity_are_hash_authenticated(self):
  family={'id':'fabric-test','buildTarget':'test','packagingRecipe':'nested-mod','filenameTemplate':'product-{version}.jar'}
  directory=self.root/'original';(directory/'build-receipts').mkdir(parents=True)
  receipt={'kind':'compile-package','outcome':'passed','family':family,'version':'0.4.4','sourceSha':'a'*40,'sourceRunId':'42','sourceRunAttempt':'1','artifactSha256':p.sha(self.original),'engineManifestSha256':p.sha(b'engine')}
  rr=json.dumps(receipt).encode();(directory/'build-receipts/fabric-test.json').write_bytes(rr);(directory/'build-receipts/engine-manifest.json').write_bytes(b'engine');(directory/'product-0.4.4.jar').write_bytes(self.original)
  group={'sourceSha':'a'*40,'runId':42,'runAttempt':1,'engineManifestSha256':p.sha(b'engine'),'familyArtifacts':[{'id':'fabric-test','receiptSha256':p.sha(rr),'artifactSha256':p.sha(self.original)}]}
  with patch.object(p,'originals',return_value={'test':group}):
   p.authenticate_original(self.root,family,directory)
   (directory/'product-0.4.4.jar').write_bytes(self.original+b'bad')
   with self.assertRaises(ValueError):p.authenticate_original(self.root,family,directory)
   (directory/'product-0.4.4.jar').write_bytes(self.original);(directory/'build-receipts/fabric-test.json').write_bytes(rr+b' ')
   with self.assertRaises(ValueError):p.authenticate_original(self.root,family,directory)
   (directory/'build-receipts/fabric-test.json').write_bytes(rr);(directory/'build-receipts/engine-manifest.json').write_bytes(b'changed')
   with self.assertRaises(ValueError):p.authenticate_original(self.root,family,directory)

 def test_frozen_original20_and_exact33_mode_plan(self):
  original=p.originals(ROOT);self.assertEqual(len(original),20)
  build=(ROOT/'scripts/build-minecraft-artifacts.py').read_text();workflow=(ROOT/'.github/workflows/minecraft-native.yml').read_text()
  self.assertIn('len(result)==18',build);self.assertIn('==33',build)
  block=workflow.split('  package-only-products:',1)[1].split('  stage:',1)[0]
  for forbidden in ('setup-java','setup-gradle','gradlew','runClient','runServer'):self.assertNotIn(forbidden,block)
  self.assertIn('builder-package-originals.json',block)
 def test_duplicate_nonfinite_and_formatted_provenance_rejected(self):
  for raw in (b'{"same":1,"same":2}',b'{"value":NaN}'):
   with self.assertRaises(ValueError):p.decode(raw)
  good=p.archive(p.replacement(self.original,self.builder));value=p.decode(good[p.PROVENANCE]);good[p.PROVENANCE]=json.dumps(value).encode()
  with self.assertRaises(ValueError):p.verify_replacement(self.original,jar(good),self.builder)
 def test_canonical_cached_provider_identity_is_not_hash_only(self):
  spec=spec_from_file_location('canonical_fixture',ROOT/'scripts/canonical_builder_provider.py');c=module_from_spec(spec);spec.loader.exec_module(c)
  pin={'provider':{'artifactId':11447976342,'runId':37538595660,'sourceRevision':'89d34cf3b5118a5cc555e4802f094af667ded0d1','sha256':'6fbbd89f669fef2db5c145f9a994534da1eef7ee6c1d7dddab05d8be95d2b41a'}}
  ppin=pin['provider'];meta={'id':ppin['artifactId'],'size_in_bytes':600000,'expired':False,'digest':'sha256:'+ppin['sha256'],'workflow_run':{'id':ppin['runId'],'head_sha':ppin['sourceRevision']}}
  run={'id':ppin['runId'],'head_sha':ppin['sourceRevision'],'status':'completed','event':'workflow_dispatch','path':'.github/workflows/minecraft-native.yml','repository':{'full_name':'owner/project'},'head_repository':{'full_name':'owner/project'}}
  proof={'source':c.SOURCE,'coreRunnerSource':ppin['sourceRevision'],'jarSha256':c.BUILDER_SHA,'retainedSdkSha256':'53fffa91059247a6f191f6ed77d1e7e74ac78318d0122c9ff503e2fcde18590a','sdkRebuilt':False,'nativeExecuted':False}
  c.validate_provider(pin,meta,run,proof,'owner/project')
  for changed in (dict(run,path='.github/workflows/native-adaptation.yml'),dict(run,event='push'),dict(run,head_sha='e'*40)):
   with self.assertRaises(ValueError):c.validate_provider(pin,meta,changed,proof,'owner/project')
  with self.assertRaises(ValueError):c.validate_provider(pin,dict(meta,expired=True),run,proof,'owner/project')
  with self.assertRaises(ValueError):c.validate_provider(pin,meta,run,dict(proof,retainedSdkSha256='e'*64),'owner/project')

 def test_truthful_new_receipt_original_chain_and_run_validation(self):
  family={'id':'fabric-test','buildTarget':'test','packagingRecipe':'nested-mod','filenameTemplate':'product-{version}.jar'}
  source='b'*40;raw=p.replacement(self.original,self.builder);path=self.root/'derived.jar';path.write_bytes(raw)
  oldreceipt=json.dumps({'sourceSha':'a'*40,'kind':'compile-package'}).encode();engine=b'engine';group={'sourceSha':'a'*40}
  pin={'provider':{'id':'authenticated fixture'}};(self.root/'distribution').mkdir();(self.root/'distribution/builder-candidate-provider.json').write_text(json.dumps(pin))
  value={'kind':'package-only','outcome':'passed','sourceSha':source,'sourceRunId':'43','sourceRunAttempt':'1','version':'0.4.4','family':family,
    'artifactSha256':p.sha(raw),'engineManifestSha256':p.sha(engine),'commands':[{'command':['package-only-fixture'],'runtime':'package-only'}],
    'originalGroup':group,'originalReceiptBase64':base64.b64encode(oldreceipt).decode(),'originalJarSha256':p.sha(self.original),'originalReceiptSha256':p.sha(oldreceipt),
    'canonicalBuilderProvider':pin,'outsideReplacementEntriesSha256':p.verify_replacement(self.original,raw,self.builder)}
  with patch.object(p,'authenticate_original',return_value=(group,oldreceipt,self.original,engine)),patch.object(p,'command',return_value=value['commands']):
   self.assertEqual(p.verify_receipt(self.root,family,path,value,self.root,source,self.builder)['sourceSha'],'a'*40)
   for key,bad in [('kind','compile-package'),('sourceSha','c'*40),('sourceRunId',43),('originalReceiptSha256','e'*64),('outsideReplacementEntriesSha256','e'*64),('canonicalBuilderProvider',{})]:
    changed={**value,key:bad}
    with self.subTest(key=key),self.assertRaises(ValueError):p.verify_receipt(self.root,family,path,changed,self.root,source,self.builder)
   forged={**value,'commands':[{'command':['gradlew',':compileJava'],'runtime':'root'}]}
   with self.assertRaises(ValueError):p.verify_receipt(self.root,family,path,forged,self.root,source,self.builder)

 def test_original_resolver_accepts_mapping_or_callable_without_cache_copies(self):
  text=(ROOT/'scripts/build-minecraft-artifacts.py').read_text()
  self.assertIn('if callable(resolver):',text)
  self.assertIn('elif isinstance(resolver,dict):',text)
  self.assertIn('resolver[family["buildTarget"]]',text)
  workflow=(ROOT/'.github/workflows/minecraft-native.yml').read_text()
  self.assertIn('!cancelled() && !inputs.package_only &&',workflow)

 def test_release_and_development_builder_paths_are_separate(self):
  text=(ROOT/'gradle/distribution.gradle').read_text()
  self.assertIn('bundleExtensions && !testBundledExtensions',text)
  self.assertIn('canonical_builder_provider.py',text)
  self.assertIn('} else if (bundleExtensions) {',text)
  self.assertIn("testBundledExtensions ? 'build' : 'assemble'",text)

if __name__=='__main__':unittest.main()
