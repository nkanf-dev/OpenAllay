/* Deterministic production-Tool fixture. The server-owner oracle owns acceptance.
 * Native 1.12 IDs are explicit custom inputs, not aliases or rewritten presets.
 */
var b=require("openallay_builder:building").open({seed:17,label:"OpenAllay E2E Builder legacy-shapes"});
var player=b.get_player_pos();
var anchor={x:Math.floor(player.x)+8,y:Math.floor(player.y)-1,z:Math.floor(player.z)+8};
var c=b.context(),roles={"build_simple_house":["air","chest_north_single","cobblestone","furnace_north","glass_pane","lantern_hanging","oak_fence","oak_log_y","oak_planks","oak_pressure_plate_unpowered","stone_brick_slab_bottom","stone_brick_stairs_south"],"build_skyscraper":["air","blue_stained_glass","cyan_stained_glass","iron_bars","iron_block","ladder_north","light_blue_stained_glass","lightning_rod_up","polished_andesite","sea_lantern","smooth_stone","smooth_stone_slab_bottom","stone_brick_stairs_south","stone_brick_wall","stone_bricks"],"build_cottage":["air","bricks","campfire_lit_north","cobblestone","cobblestone_stairs_south","dark_oak_slab_bottom","dark_oak_stairs_east","dark_oak_stairs_west","glass_pane","lantern_hanging","oak_log_x","oak_log_y","oak_log_z","oak_planks","spruce_planks"],"build_windmill":["air","cobblestone","oak_fence","oak_log_z","spruce_planks","stone_brick_stairs_south","stone_bricks","white_concrete","white_wool"],"build_farm":["air","beetroots_mature","carrots_mature","dirt","farmland_hydrated","oak_fence","oak_fence_gate_north","potatoes_mature","water_source","wheat_mature"],"build_dock":["air","lantern_standing","spruce_fence","spruce_log_y","spruce_planks"]},skipped=[],available=[];
if(!c.materialPalette || typeof c.materialPalette!=="object") throw new Error("Actual native palette missing");
Object.keys(roles).sort().forEach(function(name){
    var missing=roles[name].filter(function(role){return !Object.prototype.hasOwnProperty.call(c.materialPalette,role);});
    if(missing.length) skipped.push({name:name,status:"SKIPPED",reason:"missing_material_palette_role",roles:missing});
    else available.push(name);
});
// This bounded scenario has no independently checked preset baseline. Never claim an available preset passed.
if(available.length) throw new Error("Available preset needs its own native baseline: "+available.join(","));
if(c.version!=="1.12.2" || anchor.y<1 || anchor.y+4>=c.maxY) throw new Error("Exact legacy target/headroom required");
var x=anchor.x,y=anchor.y,z=anchor.z;
var stone={id:"minecraft:stonebrick",properties:{variant:"stonebrick"}};
var oak={id:"minecraft:planks",properties:{variant:"oak"}};
var spruce={id:"minecraft:planks",properties:{variant:"spruce"}};
var stair={id:"minecraft:oak_stairs",properties:{facing:"north",half:"bottom",shape:"straight"}};
var chest={id:"minecraft:chest",properties:{facing:"east"},blockEntity:'{id:"minecraft:chest",Items:[]}'};
b.build_box(x,y,z,x+2,y+2,z+2,stone,{hollow:true});
b.build_floor(x+4,y,z,x+6,z+2,oak,spruce);
b.build_box(x,y+1,z+4,x+4,y+3,z+4,"minecraft:air");
b.build_floor(x,y,z+4,x+4,z+4,"minecraft:dirt");
var path=b.build_path(x,z+4,x+4,z+4,y,{width:1,blocks:[stone],groundBlocks:["minecraft:dirt"],clearance:2,
    bounds:{x1:x,z1:z+4,x2:x+4,z2:z+4},minY:y,maxY:y+3,maxStep:0,seed:17});
if(path.status!=="built" || path.path.length!==5) throw new Error("Controlled native path failed");
b.build_floor(x,y,z+8,x+2,z+9,stone);
b.build_box(x,y+1,z+8,x+2,y+2,z+9,"minecraft:air");
b.place_block(x,y+1,z+8,stair);b.place_block(x+2,y+1,z+8,chest);
b.place_block(x+1,y+1,z+9,"minecraft:gold_block");
var name="openallay_e2e_legacy_shapes";
var template=b.scan_structure(x,y+1,z+8,x+2,y+1,z+9,{includeAir:true,metadata:{scenario:"builder_legacy_shapes"}});
b.save_template(template,name);var loaded=b.load_template(name),listed=b.list_templates();
if(JSON.stringify(template)!==JSON.stringify(loaded) || listed.indexOf(name)<0) throw new Error("Template persistence differs");
b.build_floor(x+10,y,z+8,x+11,z+10,stone);b.build_floor(x+16,y,z+8,x+18,z+9,stone);
b.build_box(x+10,y+1,z+8,x+11,y+2,z+10,"minecraft:air");
b.build_box(x+16,y+1,z+8,x+18,y+2,z+9,"minecraft:air");
b.place_block(x+10,y+1,z+8,"minecraft:diamond_block");b.place_block(x+18,y+1,z+9,"minecraft:diamond_block");
b.paste_structure(loaded,x+10,y+1,z+8,{rotation:90,mirror:"none",includeAir:true,replace:true});
var mirrorBeforeStatus=b.status();
try {
    b.paste_structure(loaded,x+16,y+1,z+8,{rotation:0,mirror:"front_back",includeAir:true,replace:true});
} catch (mirrorFailure) {
    // Capture detached native status without replacing the original failed Tool.
    var mirrorDiagnostic={stage:"template_front_back_paste",beforeStatus:mirrorBeforeStatus};
    try { mirrorDiagnostic.status=b.status(); }
    catch (statusFailure) { mirrorDiagnostic.statusObservationFailed=true; }
    try { mirrorDiagnostic.operations=b.list_operations().filter(function(row){
        return row.label==="OpenAllay E2E Builder legacy-shapes";
    }); }
    catch (journalFailure) { mirrorDiagnostic.journalObservationFailed=true; }
    try {
        var diagnosticTemplate=JSON.parse(JSON.stringify(loaded));
        diagnosticTemplate.metadata={scenario:"builder_legacy_shapes_mirror_diagnostic",diagnostic:mirrorDiagnostic};
        b.save_template(diagnosticTemplate,"openallay_e2e_legacy_mirror_diagnostic");
    } catch (diagnosticPersistenceFailure) {
        // Diagnostic I/O must never hide or change the original native failure.
    }
    throw mirrorFailure;
}
var positions=[{x:x+11,y:y+1,z:z+8},{x:x+11,y:y+1,z:z+10},{x:x+18,y:y+1,z:z+8},{x:x+16,y:y+1,z:z+8}];
var readback=b.get_blocks(positions);
var status=b.finish();
return JSON.stringify({scenario:"builder_legacy_shapes",anchor:anchor,context:{dimension:c.dimension,playerUuid:c.player.uuid},
    availablePresets:available,skipped:skipped,materialPalette:c.materialPalette,path:{status:path.status,length:path.path.length},
    template:{name:name,listed:true,persistedExact:true,value:loaded},readback:readback,status:status});
