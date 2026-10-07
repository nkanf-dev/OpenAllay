#!/usr/bin/env python3
"""Focused player packet tests. No downloads, Java compilation, Gradle or game."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

P = Path(__file__).with_name('package-legacy-forge-release.py')
spec = importlib.util.spec_from_file_location('legacy', P)
legacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(legacy)

def jar(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as z:
        for name, data in entries.items(): z.writestr(name, data)
    return out.getvalue()

class PlayerPacketTests(unittest.TestCase):
    def test_duplicate_and_traversal_reject(self):
        for name in ['../outside', '/absolute', 'a\\b', 'a/../b']:
            with self.assertRaises(ValueError): legacy.archive(jar({name: b'x'}))
        with self.assertRaises(ValueError): legacy.archive(jar({'A': b'x', 'a': b'y'}))

    def test_metadata_is_actual_not_filename(self):
        bad = jar({'mcmod.info': b'[{"modid":"openallay","version":"0.4.3"}]'})
        with self.assertRaises(ValueError): legacy.mod_version(bad, 'forge1122', '0.4.4')
        good = jar({'mcmod.info': b'[{"modid":"openallay","version":"0.4.4"}]'})
        legacy.mod_version(good, 'forge1122', '0.4.4')

    def test_profile_preserves_auth_and_stock_ownership(self):
        stock = {'id': legacy.STOCK_PROFILE, 'mainClass': legacy.MAIN,
                 'inheritsFrom': '1.12.2', 'minecraftArguments': legacy.GAME_ARGUMENTS,
                 'libraries': []}
        profile = legacy.player_profile(stock, 'player-profile', [])
        self.assertEqual(profile['inheritsFrom'], legacy.STOCK_PROFILE)
        self.assertEqual(profile['mainClass'], legacy.MAIN)
        self.assertEqual(profile['javaVersion']['majorVersion'], 17)
        args = profile['minecraftArguments']
        for token in ['${auth_player_name}', '${auth_uuid}', '${auth_access_token}',
                      'net.minecraftforge.fml.common.launcher.FMLTweaker',
                      'org.spongepowered.asm.launch.MixinTweaker']:
            self.assertIn(token, args)
        self.assertNotIn('auth_access_token=0', str(profile))
        self.assertNotIn('openallay.e2e', str(profile))
        self.assertNotIn('--add-opens', str(profile))
        self.assertNotIn('config/', str(profile))

    def test_profile_mismatch_refuses(self):
        with self.assertRaises(ValueError): legacy.player_profile({'id': 'other'}, 'player', [])
        with self.assertRaises(ValueError): legacy.player_profile({'id': legacy.STOCK_PROFILE}, '../escape', [])

    def test_class_entries_cannot_be_silently_rewritten(self):
        raw = jar({'A.class': b'\xca\xfe\xba\xbe\0\0\0\x34rest'})
        inv = legacy.inventory(raw)
        self.assertEqual(inv['A.class']['major'], 52)
        with self.assertRaises(ValueError): legacy.check_inventory(raw, {'A.class': {'sha256': '0'*64}})

    def test_remote_only_admission(self):
        with self.assertRaises(ValueError): legacy.require_remote({})
        legacy.require_remote({'GITHUB_ACTIONS': 'true'})

    def test_source_fixture_gate_is_exact(self):
        raw = 'prefix ' + legacy.FIXTURE_HOOK + ' suffix'
        changed = legacy.gate_dimension_fixture(raw)
        self.assertIn('fixtureIfEnabled', changed)
        runtime=legacy.gate_dimension_runtime('public static synchronized void fixture(Class<?> type)throws Exception { }')
        self.assertIn('Boolean.getBoolean("openallay.e2e.enabled")',runtime)
        with self.assertRaises(ValueError): legacy.gate_dimension_fixture('different')
        with self.assertRaises(ValueError): legacy.gate_dimension_fixture(raw + raw)

    def test_new_file_refuses_existing_and_symlink(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'file'
            legacy.write_new(p, b'first')
            with self.assertRaises(FileExistsError): legacy.write_new(p, b'next')
            self.assertEqual(p.read_bytes(), b'first')
            symlink = Path(td) / 'link'; symlink.symlink_to(p)
            with self.assertRaises(FileExistsError): legacy.write_new(symlink, b'next')

    def test_public_installer_compiles_and_is_not_game_launcher(self):
        compile(legacy.INSTALLER, 'install-openallay.py', 'exec')
        self.assertNotIn('access_token', legacy.INSTALLER)
        self.assertNotIn('offline_uuid', legacy.INSTALLER)
        self.assertIn("'xb'", legacy.INSTALLER)
        self.assertIn('--install', legacy.INSTALLER)
        self.assertNotIn('launchwrapper.Launch', legacy.INSTALLER)


    def test_installer_dry_run_install_and_second_install_refuses(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as td:
            base=Path(td); packet=base/'packet'; packet.mkdir()
            minecraft=base/'minecraft'; minecraft.mkdir(); game=base/'game'
            stock={'id':legacy.STOCK_PROFILE,'mainClass':legacy.MAIN,
                   'inheritsFrom':'1.12.2','minecraftArguments':legacy.GAME_ARGUMENTS}
            stock_path=minecraft/'versions'/legacy.STOCK_PROFILE/(legacy.STOCK_PROFILE+'.json')
            stock_path.parent.mkdir(parents=True); stock_path.write_text(json.dumps(stock))
            rawpins={b'forge':legacy.FORGE_SHA,b'wrapper':legacy.WRAPPER_SHA,b'asm':legacy.ASM_SHA}
            fixtures=[('net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864.jar',b'forge'),
                      ('net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar',b'wrapper'),
                      ('org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar',b'asm')]
            for relative,raw in fixtures:
                path=minecraft/'libraries'/relative;path.parent.mkdir(parents=True);path.write_bytes(raw)
            client=minecraft/'versions/1.12.2/1.12.2.jar';client.parent.mkdir();client.write_bytes(b'client')
            profile=legacy.player_profile(stock,'new-profile',[])
            payload={'profile-template.json':legacy.encoded(profile),
                     'mods/openallay-feature-core.jar':b'product',
                     'openallay-runtime/agent.jar':b'agent'}
            for relative,raw in payload.items():
                path=packet/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
            (packet/'SHA256SUMS').write_text(''.join(legacy.sha(raw)+'  '+name+'\n' for name,raw in payload.items()))
            scope={'__file__':str(packet/'install-openallay.py'),'__name__':'installer_test'}
            exec(compile(legacy.INSTALLER,'installer','exec'),scope)
            scope['sha']=lambda raw:rawpins[raw] if raw in rawpins else legacy.sha(raw)
            scope['hashlib']=SimpleNamespace(sha1=lambda raw:SimpleNamespace(hexdigest=lambda:'0f275bc1547d01fa5f56ba34bdc87d981ee12daf'))
            args=['installer','--minecraft-root',str(minecraft),'--game-directory',str(game),'--profile-id','new-profile']
            with patch('sys.argv',args):scope['main']()
            self.assertFalse(game.exists())
            self.assertFalse((minecraft/'versions/new-profile').exists())
            with patch('sys.argv',args+['--install']):scope['main']()
            installed=json.loads((minecraft/'versions/new-profile/new-profile.json').read_text())
            self.assertEqual(installed['inheritsFrom'],legacy.STOCK_PROFILE)
            self.assertEqual((game/'mods/openallay-feature-core.jar').read_bytes(),b'product')
            self.assertEqual(json.loads(stock_path.read_text()),stock)
            with patch('sys.argv',args+['--install']):
                with self.assertRaises(FileExistsError):scope['main']()


    def test_runtime_agent_only_public_instrumentation_with_exact_source_guards(self):
        self.assertIn('LaunchWrapperJava17Bridge.premain',legacy.PUBLIC_AGENT)
        for flag in ['openallay.pack200.enabled','openallay.objectholder.enabled','openallay.capability.enabled']:
            self.assertIn(flag,legacy.PUBLIC_AGENT)
        self.assertNotIn('setProperty("openallay.e2e.enabled"',legacy.PUBLIC_AGENT)
        self.assertNotIn('Unsafe',legacy.PUBLIC_AGENT.replace('Unsafe or test enable flag',''))
        self.assertNotIn('new URLClassLoader',legacy.PUBLIC_AGENT)
        self.assertEqual(legacy.SOURCES['bridge/DimensionEnumBridge.java'],
                         '462eab9dcf961efdf2d6122fb60613e6455ce5cce123902641897337a4dc1b5c')
        self.assertIn('bridge/pack200/DimensionConstructorFailure.java',legacy.SOURCES)

if __name__ == '__main__': unittest.main()
