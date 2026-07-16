package dev.tomewisp;

import dev.tomewisp.devmode.DevelopmentToolInspector;
import dev.tomewisp.knowledge.KnowledgeRegistry;
import dev.tomewisp.integration.patchouli.PatchouliMultiblockStore;
import dev.tomewisp.platform.PlatformService;
import dev.tomewisp.skill.SkillRepository;
import dev.tomewisp.tool.ToolRegistry;
import dev.tomewisp.trace.minecraft.TraceReplayService;

public record TomeWispRuntime(
        PlatformService platform,
        ToolRegistry tools,
        KnowledgeRegistry knowledge,
        PatchouliMultiblockStore patchouliMultiblocks,
        SkillRepository skills,
        DevelopmentToolInspector developmentTools,
        TraceReplayService traceReplay) {}
