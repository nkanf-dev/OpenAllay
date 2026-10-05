package dev.openallay.adapter.minecraft.v26_2.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual native pane shape hooks; domain journal traversal/undo is tested separately. */
class NativeConnectionRepairTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    @Test void nativePaneShapesAcrossSectionBoundaryRetainBothConnectionsAndExactBeforeStateRestoration() {
        Map<BlockPos,BlockState> states=new HashMap<>();
        BlockPos west=new BlockPos(15,64,0),east=new BlockPos(16,64,0);
        states.put(west,Blocks.GLASS_PANE.defaultBlockState());states.put(east,Blocks.GLASS_PANE.defaultBlockState());
        String westBefore=NativeBlockCodec.stateJson(states.get(west));
        String eastBefore=NativeBlockCodec.stateJson(states.get(east));
        AtomicInteger notifications=new AtomicInteger();
        // Original interface-only detached native fixture. No live world or fake game
        // class, no direct chunk mutation, and no replacement native implementation.
        LevelAccessor level=(LevelAccessor)java.lang.reflect.Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),new Class<?>[]{LevelAccessor.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "getBlockState" -> states.getOrDefault((BlockPos)args[0],Blocks.AIR.defaultBlockState());
                case "getFluidState" -> states.getOrDefault((BlockPos)args[0],Blocks.AIR.defaultBlockState()).getFluidState();
                case "getRandom" -> net.minecraft.util.RandomSource.create(0);
                case "getMinY" -> -64;
                case "getHeight" -> 384;
                case "updateNeighborsAt", "updateNeighbourForOutputSignal" -> {notifications.incrementAndGet();yield null;}
                default -> throw new AssertionError("Unexpected detached native world call: "+method.getName());
            };
        });
        BlockState westRepaired=Block.updateFromNeighbourShapes(states.get(west),level,west);
        states.put(west,NativeBlockCodec.decode(NativeBlockCodec.stateJson(westRepaired)));
        BlockState eastRepaired=Block.updateFromNeighbourShapes(states.get(east),level,east);
        states.put(east,NativeBlockCodec.decode(NativeBlockCodec.stateJson(eastRepaired)));
        assertTrue(states.get(west).getValue(BlockStateProperties.EAST));
        assertTrue(states.get(east).getValue(BlockStateProperties.WEST));
        assertEquals(0,notifications.get());
        // The adapter test restores native canonical before images, not a domain
        // journal. Full journal undo is owned by shared Builder business acceptance.
        states.put(west,NativeBlockCodec.decode(westBefore));
        states.put(east,NativeBlockCodec.decode(eastBefore));
        assertFalse(states.get(west).getValue(BlockStateProperties.EAST));
        assertFalse(states.get(east).getValue(BlockStateProperties.WEST));
    }
}
