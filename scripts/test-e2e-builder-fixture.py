#!/usr/bin/env python3
"""Source contract checks for the real-client Builder JS fixture.

These tests do not simulate a world and do not certify native acceptance. The
real-client controller must execute the file through production run_javascript
and read fixed landmarks from the live integrated server.
"""

import json
import re
import shutil
import subprocess
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("e2e-builder-fixture.js")
SOURCE = SCRIPT_PATH.read_text(encoding="utf-8")
EXPECTATIONS = {
    match.group(1): (int(match.group(2)), int(match.group(3)),
                     int(match.group(4)), match.group(5))
    for match in re.finditer(
        r'expect\("([^"\n]+)",(-?\d+),(-?\d+),(-?\d+),"([^"\n]+)"', SOURCE)
}


class BuilderFixtureSourceTests(unittest.TestCase):
    def test_opens_the_real_extension_and_labels_the_deterministic_provider(self):
        self.assertIn('require("openallay_builder:building").open({seed:17,', SOURCE)
        self.assertIn('label:"OpenAllay E2E Builder acceptance"', SOURCE)
        self.assertIn('provider:"deterministic_loopback_fixture_not_live_model"', SOURCE)
        self.assertIn('scenario:"builder_acceptance"', SOURCE)
        for forbidden in (".create(", "Java.type(", "__backend", "fetch(",
                          "XMLHttpRequest", "java.net", "region/", "level.dat"):
            self.assertNotIn(forbidden, SOURCE)

    def test_uses_the_agreed_anchor_and_all_six_compact_sites(self):
        self.assertIn('x:Math.floor(player.x)+8,y:Math.floor(player.y)-1,', SOURCE)
        self.assertIn('z:Math.floor(player.z)+8', SOURCE)
        sites = dict((name, (int(x), int(z))) for name, x, z in re.findall(
            r'site\("([^"\n]+)",(-?\d+),(-?\d+)\)', SOURCE))
        self.assertEqual((0, 0), sites["house"])
        self.assertEqual((12, 0), sites["skyscraper"])
        self.assertEqual((24, 0), sites["cottage"])
        self.assertEqual((38, 3), sites["windmill"])
        self.assertEqual((0, 18), sites["farm"])
        self.assertEqual((14, 18), sites["dock"])
        self.assertEqual((24, 18), sites["geometry_decoration"])
        self.assertEqual((0, 32), sites["terrain"])
        self.assertEqual((14, 32), sites["template"])
        for preset in ("simple_house", "skyscraper", "cottage", "windmill", "farm", "dock"):
            self.assertEqual(1, SOURCE.count("b.build_" + preset + "("))
        self.assertIn('width:7,depth:7,height:4,facing:"north"', SOURCE)
        self.assertIn('width:5,depth:5,floors:2,floorHeight:3', SOURCE)
        self.assertIn('width:5,depth:5,height:3,facing:"north"', SOURCE)
        self.assertIn('radius:2,height:6,bladeLength:1', SOURCE)
        self.assertIn('width:3,depth:3,crops:[{block:"beetroots",age:3}]', SOURCE)
        self.assertIn('width:2,length:4,pilingDepth:2', SOURCE)

    def test_declares_concrete_preset_landmarks_and_linked_supports(self):
        fixed = {
            "house_door_lower": (3, 1, 0, "oak_door"),
            "house_door_upper": (3, 2, 0, "oak_door"),
            "house_bed_foot": (1, 1, 5, "red_bed"),
            "house_bed_head": (1, 1, 4, "red_bed"),
            "house_lantern_support": (3, 5, 3, "stone_brick_slab"),
            "skyscraper_light_floor1": (14, 3, 2, "sea_lantern"),
            "skyscraper_light_floor2": (14, 6, 2, "sea_lantern"),
            "skyscraper_ladder_support": (13, 1, 4, "iron_block"),
            "cottage_roof_ridge": (26, 7, -1, "dark_oak_slab"),
            "cottage_campfire_support": (27, 9, 3, "bricks"),
            "windmill_sail_wool": (39, 6, 0, "white_wool"),
            "farm_crop": (0, 1, 18, "beetroots"),
            "farm_gate_support": (1, 0, 17, "dirt"),
            "dock_lantern_support": (13, 2, 21, "spruce_fence"),
        }
        for name, expected in fixed.items():
            with self.subTest(name=name):
                self.assertEqual(expected, EXPECTATIONS[name])

    def test_all_geometry_and_decoration_methods_are_covered(self):
        for method in ("build_box", "build_walls", "build_floor", "build_circle",
                       "build_cylinder", "build_cone", "build_arch", "build_pitched_roof",
                       "place_door", "place_bed", "place_windows", "place_lantern_post",
                       "place_tree", "place_flower"):
            self.assertIn("b." + method + "(", SOURCE)
        for name in ("geometry_box_interior", "geometry_cylinder_interior",
                     "decoration_door_support", "decoration_bed_support",
                     "decoration_lantern_support", "decoration_tree_crown"):
            self.assertIn(name, EXPECTATIONS)
        # Native dependency repair happens after both door halves and bed cells exist.
        geometry = SOURCE[SOURCE.index('// Eight geometry methods'):SOURCE.index('// Controlled 7x5 terrain')]
        self.assertGreater(geometry.rfind("b.update_connections("), geometry.index("b.place_bed("))
        self.assertLess(geometry.index("b.build_floor(anchor.x+32"), geometry.index("b.place_door("))

    def test_terrain_windows_are_small_and_only_one_scan_is_full_height(self):
        self.assertIn('var scanOptions={minY:anchor.y,maxY:anchor.y+4}', SOURCE)
        self.assertIn('depth:1,clearAbove:3,blendRadius:0,seed:17', SOURCE)
        self.assertNotIn("clearAbove:true", SOURCE)
        self.assertIn('var fullHeightScan=b.scan_ground(anchor.x,anchor.z+32,anchor.x,anchor.z+32)', SOURCE)
        self.assertIn('minY:anchor.y,maxY:anchor.y+4,clearance:2,diagonal:false,maxStep:0', SOURCE)
        self.assertIn('straight.status!=="built" || smart.status!=="built"', SOURCE)
        self.assertEqual((3, 0, 35, "polished_andesite"), EXPECTATIONS["terrain_smart_detour"])
        self.assertEqual((3, 1, 34, "stone"), EXPECTATIONS["terrain_obstacle"])

    def test_template_positions_follow_mirror_then_clockwise_rotation(self):
        # Reconstruct the advertised transforms independently from the fixture.
        source_stair = (0, 1, 0)
        source_chest = (2, 1, 1)
        plans = (("rotation90", 19, 90, "none"),
                 ("front_back", 24, 0, "front_back"),
                 ("left_right", 29, 0, "left_right"),
                 ("combined", 34, 90, "front_back"))
        for name, x0, rotation, mirror in plans:
            for kind, pos, block in (("stair", source_stair, "oak_stairs"),
                                      ("chest", source_chest, "chest")):
                x, y, z = pos
                if mirror == "front_back":
                    x = 2 - x
                elif mirror == "left_right":
                    z = 2 - z
                if rotation == 90:
                    x, z = 2 - z, x
                self.assertEqual((x0 + x, y, 32 + z, block),
                                 EXPECTATIONS["template_" + name + "_" + kind])
        self.assertIn('blockEntity:\'{id:"minecraft:chest",Items:[]}\'', SOURCE)
        self.assertIn('var templateName="openallay_e2e_builder_native"', SOURCE)
        for method in ("scan_structure", "save_template", "load_template", "list_templates", "paste_structure"):
            self.assertIn("b." + method + "(", SOURCE)
        self.assertIn('rotation:plan.rotation,mirror:plan.mirror,includeAir:true,replace:true', SOURCE)
        for name in ("rotation90", "front_back", "left_right", "combined"):
            self.assertEqual("air", EXPECTATIONS["template_" + name + "_air"][3])

    def test_summary_is_compact_and_preserves_native_operation_status(self):
        self.assertIn("var s = b.finish()", SOURCE)
        self.assertIn("operationId:s.operationId || null,state:s.state", SOURCE)
        phases = re.findall(r'phase\("([^"\n]+)"\)', SOURCE)
        self.assertEqual(["house", "skyscraper", "cottage", "windmill", "farm", "dock",
                          "geometry_decoration", "terrain", "templates"], phases)
        self.assertIn("status:b.status()", SOURCE)
        self.assertIn("templates:{saved:[templateName]", SOURCE)
        self.assertIn("baselineOperations=b.list_operations()", SOURCE)
        self.assertNotIn("success:true", SOURCE)
        summary = SOURCE[SOURCE.index('// Return completed build evidence'):]
        self.assertNotIn("template:template", summary)
        self.assertNotIn("loaded:loaded", summary)
        self.assertIn('return JSON.stringify({scenario:"builder_acceptance"', summary)
        self.assertNotIn("checks:checks", summary)
        self.assertIn("actions:actions,operations:operations", summary)
        self.assertIn("lifecycle:lifecycle", summary)

    def test_superflat_height_and_forced_south_detour_fit_the_native_world(self):
        self.assertIn("anchor.y-3 < c.minY", SOURCE)
        self.assertNotIn("anchor.y-4 < c.minY", SOURCE)
        self.assertIn("bounds:{x1:anchor.x,z1:anchor.z+34,x2:anchor.x+6,z2:anchor.z+35}", SOURCE)

    @unittest.skipUnless(shutil.which("node"), "optional Node contract executor is not installed")
    def test_actual_palette_branch_skips_only_skyscraper_without_native_calls(self):
        source = SOURCE[:SOURCE.index('// Eight geometry methods')]
        source += 'return {skipped:skipped,operations:operations,actions:actions,sites:sites};'
        program = ("const source=JSON.parse(require('fs').readFileSync(0,'utf8'));"
                   "for(const present of [false,true]) { let calls=[],n=0;"
                   "const b={get_player_pos:()=>({x:0,y:64,z:0}),"
                   "context:()=>({minY:0,maxY:256,materialPalette:present?{lightning_rod_up:{}}:{}}),"
                   "finish:()=>({operationId:'unit-'+(++n),state:'completed',reads:1,writes:1})};"
                   "for(const name of ['simple_house','skyscraper','cottage','windmill','farm','dock'])"
                   "b['build_'+name]=()=>{calls.push(name);return {operation:name,writes:1}};"
                   "const result=new Function('require',source)(()=>({open:()=>b}));"
                   "if(calls.length!==(present?6:5)||calls.includes('skyscraper')!==present)throw Error('preset calls');"
                   "if(result.operations.length!==(present?6:5)||result.skipped.length!==(present?0:1))throw Error('receipt counts');"
                   "if(Object.hasOwn(result.sites,'skyscraper')!==present)throw Error('skipped site');"
                   "if(!present&&result.skipped[0].status!=='SKIPPED')throw Error('skip status');"
                   "} console.log('Palette branch contract: PASS');")
        result = subprocess.run([shutil.which("node"), "-e", program], input=json.dumps(source),
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_skyscraper_alone_is_guarded_by_actual_native_palette(self):
        self.assertIn('Object.prototype.hasOwnProperty.call(c.materialPalette,"lightning_rod_up")', SOURCE)
        branch = SOURCE[SOURCE.index('if (!Object.prototype.hasOwnProperty.call(c.materialPalette'):SOURCE.index('p = site("cottage"')]
        self.assertIn('status:"SKIPPED"', branch)
        self.assertIn('reason:"missing_material_palette_role",role:"lightning_rod_up"', branch)
        self.assertEqual(1, branch.count('b.build_skyscraper('))
        self.assertNotIn('b.build_cottage(', branch)
        self.assertIn('operations:operations,skipped:skipped', SOURCE)

    def test_build_finishes_all_nine_operations_before_separate_terminal_failure_tools(self):
        tail = SOURCE[SOURCE.index('// Capture prerequisites'):]
        self.assertIn('stage:"build"', tail)
        self.assertIn('probeToken:fixtureProbeToken', tail)
        self.assertIn('context:{dimension:c.dimension,playerUuid:c.player.uuid}', tail)
        self.assertIn('partial:lifecyclePrerequisite(44,32)', tail)
        self.assertIn('cancel:lifecyclePrerequisite(44,34)', tail)
        self.assertIn('undo:lifecyclePrerequisite(44,36)', tail)
        self.assertIn('images[i].id!=="minecraft:air"', tail)
        self.assertIn('positions:positions,beforeImages:images', tail)
        self.assertNotIn('catch', tail)
        self.assertNotIn('.cancel()', SOURCE)
        self.assertNotIn('unknown_block', SOURCE)
        self.assertNotIn('.undo(', SOURCE)
        self.assertGreater(SOURCE.index('baselineOperations=b.list_operations()'), SOURCE.index('phase("templates")'))

    @unittest.skipUnless(shutil.which("node"), "optional Node syntax parser not installed")
    def test_script_is_valid_javascript_syntax(self):
        result = subprocess.run([shutil.which("node"), "--check", str(SCRIPT_PATH)],
                                check=False, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
