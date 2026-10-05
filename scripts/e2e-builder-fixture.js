/* OpenAllay deterministic loopback provider fixture. Not a live-model response.
 * Run only through production openallay:run_javascript in an authorized integrated world.
 * Coordinates below are declared expectations, not proof of successful native writes.
 * The real-client controller must reconstruct them and read the live server independently.
 * BEGIN BUILDER_ACCEPTANCE
 */
var b = require("openallay_builder:building").open({seed:17,label:"OpenAllay E2E Builder acceptance"});
var player = b.get_player_pos();
var anchor = {x:Math.floor(player.x)+8,y:Math.floor(player.y)-1,z:Math.floor(player.z)+8};
var checks = [], actions = [], operations = [], sites = {};
function point(x,y,z) { return {x:anchor.x+x,y:anchor.y+y,z:anchor.z+z}; }
function expect(name,x,y,z,id,properties) {
    var p = point(x,y,z);
    var check = {name:name,x:p.x,y:p.y,z:p.z,id:"minecraft:"+id};
    if (properties) check.properties = properties;
    checks.push(check);
}
function action(name,result) {
    var out = {name:name,operation:result.operation,writes:result.writes};
    if (result.status !== undefined) out.status = result.status;
    if (result.reason !== undefined) out.reason = result.reason;
    if (result.columns !== undefined) out.columns = result.columns;
    if (result.path) { out.pathLength=result.path.length; out.cost=result.cost; }
    if (result.size) out.size = result.size;
    if (result.bounds) out.bounds = result.bounds;
    actions.push(out);
    return result;
}
function phase(name) {
    var s = b.finish();
    operations.push({name:name,operationId:s.operationId || null,state:s.state,
        reads:s.reads,writes:s.writes,detail:s.detail || ""});
}
function site(name,x,z) { var p=point(x,0,z); sites[name]=p; return p; }
var c = b.context();
if (anchor.y-3 < c.minY || anchor.y+13 >= c.maxY) {
    throw new Error("Builder acceptance anchor lacks build-height headroom");
}

// Six presets use their documented structural minima and never retry a failure.
var p = site("house",0,0);
action("house",b.build_simple_house(p.x,p.y,p.z,{width:7,depth:7,height:4,facing:"north"}));
expect("house_floor",0,0,0,"oak_planks");
expect("house_door_lower",3,1,0,"oak_door",{facing:"north",half:"lower",hinge:"left"});
expect("house_door_upper",3,2,0,"oak_door",{facing:"north",half:"upper",hinge:"left"});
expect("house_bed_foot",1,1,5,"red_bed",{facing:"north",part:"foot"});
expect("house_bed_head",1,1,4,"red_bed",{facing:"north",part:"head"});
expect("house_chest",4,1,5,"chest",{facing:"north",type:"single"});
expect("house_lantern",3,4,3,"lantern",{hanging:"true"});
expect("house_lantern_support",3,5,3,"stone_brick_slab",{type:"bottom"});
phase("house");
p = site("skyscraper",12,0);
action("skyscraper",b.build_skyscraper(p.x,p.y,p.z,{width:5,depth:5,floors:2,floorHeight:3,foundationDepth:1,facing:"north"}));
expect("skyscraper_light_floor1",14,3,2,"sea_lantern");
expect("skyscraper_light_floor2",14,6,2,"sea_lantern");
expect("skyscraper_ladder",13,1,3,"ladder",{facing:"north"});
expect("skyscraper_ladder_support",13,1,4,"iron_block");
expect("skyscraper_rod",14,11,2,"lightning_rod",{facing:"up"});
phase("skyscraper");
p = site("cottage",24,0);
action("cottage",b.build_cottage(p.x,p.y,p.z,{width:5,depth:5,height:3,facing:"north",name:"OpenAllay E2E Cottage"}));
expect("cottage_beam",25,3,0,"oak_log",{axis:"x"});
expect("cottage_roof_ridge",26,7,-1,"dark_oak_slab",{type:"bottom"});
expect("cottage_door",26,1,0,"oak_door",{facing:"north",half:"lower"});
expect("cottage_lantern",25,2,1,"lantern",{hanging:"true"});
expect("cottage_lantern_support",25,3,1,"oak_log",{axis:"x"});
expect("cottage_campfire",27,10,3,"campfire",{lit:"true",facing:"north"});
expect("cottage_campfire_support",27,9,3,"bricks");
phase("cottage");
p = site("windmill",38,3);
action("windmill",b.build_windmill(p.x,p.y,p.z,{radius:2,height:6,bladeLength:1,facing:"north"}));
expect("windmill_door",38,1,1,"oak_door",{facing:"north",half:"lower"});
expect("windmill_axle",38,5,0,"oak_log",{axis:"z"});
expect("windmill_sail_fence",39,5,0,"oak_fence");
expect("windmill_sail_wool",39,6,0,"white_wool");
expect("windmill_roof",38,7,3,"spruce_planks");
phase("windmill");
p = site("farm",0,18);
action("farm",b.build_farm(p.x,p.y,p.z,{width:3,depth:3,crops:["beetroots"],facing:"north"}));
expect("farm_soil",0,0,18,"farmland",{moisture:"7"});
expect("farm_crop",0,1,18,"beetroots",{age:"3"});
expect("farm_water",-1,0,18,"water",{level:"0"});
expect("farm_gate",1,1,17,"oak_fence_gate",{facing:"north",open:"false"});
expect("farm_gate_support",1,0,17,"dirt");
phase("farm");
p = site("dock",14,18);
action("dock",b.build_dock(p.x,p.y,p.z,{width:2,length:4,pilingDepth:2,pilingSpacing:2,facing:"north"}));
expect("dock_deck_start",14,0,18,"spruce_planks");
expect("dock_deck_end",15,0,21,"spruce_planks");
expect("dock_piling",14,-2,18,"spruce_log",{axis:"y"});
expect("dock_left_lantern",13,3,21,"lantern",{hanging:"false"});
expect("dock_right_lantern",16,3,21,"lantern",{hanging:"false"});
expect("dock_lantern_support",13,2,21,"spruce_fence");
phase("dock");

// Eight geometry methods, each in its own small part of one composed site.
site("geometry_decoration",24,18);
action("geometry_clear",b.build_box(anchor.x+24,anchor.y+1,anchor.z+18,anchor.x+44,anchor.y+5,anchor.z+27,"air"));
action("geometry_box",b.build_box(anchor.x+24,anchor.y,anchor.z+18,anchor.x+26,anchor.y+2,anchor.z+20,"stone_bricks",{hollow:true}));
expect("geometry_box_corner",24,2,18,"stone_bricks");
expect("geometry_box_interior",25,1,19,"air");
b.build_floor(anchor.x+28,anchor.y,anchor.z+18,anchor.x+30,anchor.z+20,"smooth_stone");
action("geometry_walls",b.build_walls(anchor.x+28,anchor.y+1,anchor.z+18,anchor.x+30,anchor.y+2,anchor.z+20,"polished_andesite",{corner:"gold_block"}));
expect("geometry_wall_edge",29,1,18,"polished_andesite");
expect("geometry_wall_corner",28,2,18,"gold_block");
action("geometry_floor",b.build_floor(anchor.x+32,anchor.y,anchor.z+18,anchor.x+34,anchor.z+20,"quartz_block","black_concrete"));
expect("geometry_floor_parity",32,0,18,((anchor.x+32+anchor.z+18)%2!==0)?"black_concrete":"quartz_block");
action("geometry_circle",b.build_circle(anchor.x+37,anchor.y,anchor.z+19,1,"yellow_concrete"));
expect("geometry_circle_edge",36,0,19,"yellow_concrete");
action("geometry_cylinder",b.build_cylinder(anchor.x+40,anchor.y,anchor.z+19,1,3,"blue_concrete",{hollow:true,clearInterior:true}));
expect("geometry_cylinder_edge",39,2,19,"blue_concrete");
expect("geometry_cylinder_interior",40,1,19,"air");
action("geometry_cone",b.build_cone(anchor.x+43,anchor.y,anchor.z+19,1,3,"red_concrete"));
expect("geometry_cone_top",43,2,19,"red_concrete");
action("geometry_arch",b.build_arch(anchor.x+24,anchor.y+1,anchor.z+22,anchor.z+26,3,"bricks"));
expect("geometry_arch_end",24,1,22,"bricks");
expect("geometry_arch_top",24,3,24,"bricks");
action("geometry_roof",b.build_pitched_roof(anchor.x+28,anchor.y+2,anchor.z+23,anchor.x+30,anchor.z+25,"spruce_stairs","spruce_slab",{axis:"z"}));
expect("geometry_roof_stair",28,2,24,"spruce_stairs",{facing:"east",half:"bottom",shape:"straight"});
expect("geometry_roof_ridge",29,3,24,"spruce_slab",{type:"bottom"});

// Supports precede the paired/attached decorations and the final neighbor pass.
b.build_floor(anchor.x+32,anchor.y,anchor.z+23,anchor.x+35,anchor.z+26,"smooth_stone");
b.place_block(anchor.x+32,anchor.y,anchor.z+23,"dirt");
action("decoration_door",b.place_door(anchor.x+33,anchor.y+1,anchor.z+24,{material:"birch",facing:"east",hinge:"right"}));
action("decoration_bed",b.place_bed(anchor.x+34,anchor.y+1,anchor.z+25,{color:"blue",facing:"east"}));
action("decoration_flower",b.place_flower(anchor.x+32,anchor.y+1,anchor.z+23,{block:"poppy",seed:17}));
action("decoration_potted_flower",b.place_flower(anchor.x+32,anchor.y+1,anchor.z+24,{potted:true,block:"potted_dandelion",seed:17}));
b.build_floor(anchor.x+37,anchor.y,anchor.z+23,anchor.x+40,anchor.z+26,"stone_bricks");
b.build_walls(anchor.x+37,anchor.y+1,anchor.z+23,anchor.x+39,anchor.y+1,anchor.z+25,"stone_bricks");
action("decoration_windows",b.place_windows(anchor.x+37,anchor.y+1,anchor.z+23,anchor.x+39,anchor.y+1,anchor.z+25,{spacing:1,block:"glass_pane"}));
action("decoration_lantern_post",b.place_lantern_post(anchor.x+40,anchor.y+1,anchor.z+25,{height:2}));
b.place_block(anchor.x+43,anchor.y,anchor.z+25,"dirt");
action("decoration_tree",b.place_tree(anchor.x+43,anchor.y+1,anchor.z+25,{trunk_h:3,radius:1,seed:17}));
b.update_connections(anchor.x+24,anchor.y,anchor.z+18,anchor.x+44,anchor.y+5,anchor.z+27);
expect("decoration_door_lower",33,1,24,"birch_door",{facing:"east",half:"lower",hinge:"right"});
expect("decoration_door_upper",33,2,24,"birch_door",{facing:"east",half:"upper",hinge:"right"});
expect("decoration_door_support",33,0,24,"smooth_stone");
expect("decoration_bed_foot",34,1,25,"blue_bed",{facing:"east",part:"foot"});
expect("decoration_bed_head",35,1,25,"blue_bed",{facing:"east",part:"head"});
expect("decoration_bed_support",35,0,25,"smooth_stone");
expect("decoration_flower",32,1,23,"poppy");
expect("decoration_potted_flower",32,1,24,"potted_dandelion");
expect("decoration_window",38,1,23,"glass_pane",{east:"true",west:"true"});
expect("decoration_lantern",40,3,25,"lantern",{hanging:"false"});
expect("decoration_lantern_support",40,2,25,"oak_fence");
expect("decoration_tree_trunk",43,1,25,"oak_log",{axis:"y"});
expect("decoration_tree_crown",43,4,25,"oak_leaves",{persistent:"true"});
phase("geometry_decoration");

// Controlled 7x5 terrain. Numeric clearAbove never scans to the world ceiling.
site("terrain",0,32);
action("terrain_flatten",b.flatten_area(anchor.x,anchor.z+32,anchor.x+6,anchor.z+36,anchor.y,
    {surface:"grass_block",underground:"dirt",depth:1,clearAbove:3,blendRadius:0,seed:17}));
b.place_flower(anchor.x,anchor.y+1,anchor.z+32,{block:"dandelion"});
var scanOptions={minY:anchor.y,maxY:anchor.y+4};
var terrainScan=b.scan_terrain(anchor.x,anchor.z+32,anchor.x,anchor.z+32,scanOptions);
var groundScan=b.scan_ground(anchor.x,anchor.z+32,anchor.x,anchor.z+32,scanOptions);
var terrainBounds=b.get_terrain_bounds(groundScan);
// One column exercises the documented default full-dimension-height behavior.
var fullHeightScan=b.scan_ground(anchor.x,anchor.z+32,anchor.x,anchor.z+32);
b.place_block(anchor.x,anchor.y+1,anchor.z+33,"oak_log");
b.place_block(anchor.x+2,anchor.y+1,anchor.z+33,"stone");
action("terrain_clear_vegetation",b.clear_vegetation(anchor.x,anchor.y+1,anchor.z+32,anchor.x+2,anchor.y+1,anchor.z+33));
action("terrain_clear_all",b.clear_vegetation(anchor.x+2,anchor.y+1,anchor.z+33,anchor.x+2,anchor.y+1,anchor.z+33,{mode:"all"}));
b.place_block(anchor.x+3,anchor.y+1,anchor.z+34,"stone");
var straight=action("terrain_path",b.build_path(anchor.x,anchor.z+32,anchor.x+6,anchor.z+32,anchor.y,
    {width:1,blocks:["stone_bricks"],clearance:2,seed:17}));
var smart=action("terrain_smart_path",b.build_smart_path({x:anchor.x,z:anchor.z+34},{x:anchor.x+6,z:anchor.z+34},
    {bounds:{x1:anchor.x,z1:anchor.z+34,x2:anchor.x+6,z2:anchor.z+35},width:1,blocks:["polished_andesite"],
        minY:anchor.y,maxY:anchor.y+4,clearance:2,diagonal:false,maxStep:0,seed:17}));
if (straight.status!=="built" || smart.status!=="built") throw new Error("Controlled terrain path did not build");
expect("terrain_foundation",0,-1,32,"dirt");
expect("terrain_clear_flower",0,1,32,"air");
expect("terrain_clear_log",0,1,33,"air");
expect("terrain_clear_all",2,1,33,"air");
expect("terrain_path_start",0,0,32,"stone_bricks");
expect("terrain_path_end",6,0,32,"stone_bricks");
expect("terrain_smart_start",0,0,34,"polished_andesite");
expect("terrain_smart_end",6,0,34,"polished_andesite");
expect("terrain_smart_detour",3,0,35,"polished_andesite");
expect("terrain_obstacle",3,1,34,"stone");
expect("terrain_obstacle_ground",3,0,34,"grass_block");
phase("terrain");

// Small native-state template: explicit air, directional stair and typed chest BE.
site("template",14,32);
b.build_floor(anchor.x+14,anchor.y,anchor.z+32,anchor.x+16,anchor.z+34,"stone_bricks");
b.build_box(anchor.x+14,anchor.y+1,anchor.z+32,anchor.x+16,anchor.y+2,anchor.z+34,"air");
b.place_block(anchor.x+14,anchor.y+1,anchor.z+32,"oak_stairs",{facing:"north",half:"bottom",shape:"straight",waterlogged:"false"});
b.place_block(anchor.x+16,anchor.y+1,anchor.z+33,{id:"minecraft:chest",properties:{facing:"east",type:"single",waterlogged:"false"},blockEntity:'{id:"minecraft:chest",Items:[]}'});
b.place_block(anchor.x+15,anchor.y+1,anchor.z+34,"red_concrete");
b.update_connections(anchor.x+14,anchor.y,anchor.z+32,anchor.x+16,anchor.y+2,anchor.z+34);
var template=b.scan_structure(anchor.x+14,anchor.y,anchor.z+32,anchor.x+16,anchor.y+1,anchor.z+34,
    {includeAir:true,metadata:{scenario:"builder_acceptance",seed:17}});
var templateName="openallay_e2e_builder_native";
b.save_template(template,templateName);
var loaded=b.load_template(templateName);
var listed=b.list_templates();
if (listed.indexOf(templateName)<0) throw new Error("Saved acceptance template missing from template list");
// Mark each explicit-air destination before paste. Correct includeAir clears it.
var pastePlans=[{name:"rotation90",x:19,rotation:90,mirror:"none",airX:21,airZ:33},
    {name:"mirror_front_back",x:24,rotation:0,mirror:"front_back",airX:25,airZ:32},
    {name:"mirror_left_right",x:29,rotation:0,mirror:"left_right",airX:30,airZ:34},
    {name:"rotation90_front_back",x:34,rotation:90,mirror:"front_back",airX:36,airZ:33}];
for (var i=0;i<pastePlans.length;i++) {
    var plan=pastePlans[i];
    b.build_box(anchor.x+plan.x,anchor.y+1,anchor.z+32,anchor.x+plan.x+2,anchor.y+2,anchor.z+34,"air");
    b.place_block(anchor.x+plan.airX,anchor.y+1,anchor.z+plan.airZ,"gold_block");
    action("template_"+plan.name,b.paste_structure(loaded,anchor.x+plan.x,anchor.y,anchor.z+32,
        {rotation:plan.rotation,mirror:plan.mirror,includeAir:true,replace:true}));
}
expect("template_source_stair",14,1,32,"oak_stairs",{facing:"north",half:"bottom",shape:"straight"});
expect("template_source_chest",16,1,33,"chest",{facing:"east",type:"single"});
expect("template_rotation90_stair",21,1,32,"oak_stairs",{facing:"east",half:"bottom",shape:"straight"});
expect("template_rotation90_chest",20,1,34,"chest",{facing:"south",type:"single"});
expect("template_rotation90_marker",19,1,33,"red_concrete");
expect("template_rotation90_air",21,1,33,"air");
expect("template_front_back_stair",26,1,32,"oak_stairs",{facing:"north",half:"bottom",shape:"straight"});
expect("template_front_back_chest",24,1,33,"chest",{facing:"west",type:"single"});
expect("template_front_back_air",25,1,32,"air");
expect("template_left_right_stair",29,1,34,"oak_stairs",{facing:"south",half:"bottom",shape:"straight"});
expect("template_left_right_chest",31,1,33,"chest",{facing:"east",type:"single"});
expect("template_left_right_air",30,1,34,"air");
expect("template_combined_stair",36,1,34,"oak_stairs",{facing:"east",half:"bottom",shape:"straight"});
expect("template_combined_chest",35,1,32,"chest",{facing:"north",type:"single"});
expect("template_combined_air",36,1,33,"air");
phase("templates");

// Terminal-state checks use separate real sessions. Their markers stay inspectable.
function openLifecycle(name) {
    return require("openallay_builder:building").open({seed:17,label:"OpenAllay E2E Builder "+name});
}
function requireAir(session,x,z) {
    var state=session.get_block_full(anchor.x+x,anchor.y+1,anchor.z+z);
    if (state.id!=="minecraft:air") throw new Error("Lifecycle marker prerequisite is not air at "+[x,1,z]);
    return state;
}
var partial=openLifecycle("partial");
var partialBefore=[requireAir(partial,44,32),requireAir(partial,45,32)];
partial.place_block(anchor.x+44,anchor.y+1,anchor.z+32,"gold_block");
var partialFailure=null;
try { partial.place_block(anchor.x+45,anchor.y+1,anchor.z+32,"openallay_e2e:unknown_block"); }
catch (partialError) { partialFailure=String(partialError); }
if (partialFailure===null) throw new Error("Invalid native block unexpectedly succeeded");
var partialStatus=partial.status();
expect("lifecycle_partial_marker",44,1,32,"gold_block");
expect("lifecycle_partial_invalid_untouched",45,1,32,"air");

var cancelled=openLifecycle("cancel");
var cancelBefore=[requireAir(cancelled,44,34),requireAir(cancelled,45,34)];
cancelled.place_block(anchor.x+44,anchor.y+1,anchor.z+34,"diamond_block");
var cancelStatus=cancelled.cancel(), deniedAfterCancel=false, cancelFailure=null;
try { cancelled.place_block(anchor.x+45,anchor.y+1,anchor.z+34,"gold_block"); }
catch (cancelError) { deniedAfterCancel=true; cancelFailure=String(cancelError); }
if (!deniedAfterCancel) throw new Error("Cancelled native session unexpectedly accepted another write");
expect("lifecycle_cancel_marker",44,1,34,"diamond_block");
expect("lifecycle_cancel_untouched",45,1,34,"air");

var undoSession=openLifecycle("undo original");
var undoBefore=[requireAir(undoSession,44,36),requireAir(undoSession,45,36)];
undoSession.place_block(anchor.x+44,anchor.y+1,anchor.z+36,"gold_block");
undoSession.place_block(anchor.x+45,anchor.y+1,anchor.z+36,"gold_block");
var originalStatus=undoSession.finish();
var intervention=openLifecycle("undo intervention");
intervention.place_block(anchor.x+45,anchor.y+1,anchor.z+36,"diamond_block");
var interventionStatus=intervention.finish();
var undoResult=undoSession.undo(originalStatus.operationId);
var undoStatus=undoSession.finish();
expect("lifecycle_undo_restored",44,1,36,"air");
expect("lifecycle_undo_conflict_preserved",45,1,36,"diamond_block");
var lifecycle={partial:{failure:partialFailure,status:partialStatus,beforeImages:partialBefore},
    cancel:{deniedAfterCancel:deniedAfterCancel,failure:cancelFailure,status:cancelled.status(),beforeImages:cancelBefore},
    undo:{result:undoResult,status:undoStatus,originalStatus:originalStatus,interventionStatus:interventionStatus,beforeImages:undoBefore}};

// Return a complete scalar JSON receipt, not a sampled container array.
// Declared checks stay in this source; only independent native readback is the verdict.
return JSON.stringify({scenario:"builder_acceptance",provider:"deterministic_loopback_fixture_not_live_model",
    seed:17,anchor:anchor,sites:sites,actions:actions,operations:operations,
    status:b.status(),lifecycle:lifecycle,templates:{saved:[templateName],listed:listed.indexOf(templateName)>=0,
        size:loaded.size,blockCount:loaded.blocks.length,paletteSize:loaded.palette.length},
    terrain:{scanTerrain:terrainScan,scanGround:groundScan,bounds:terrainBounds,fullHeightGround:fullHeightScan}});
/* END BUILDER_ACCEPTANCE */
