# TomeWisp Phase 0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible Minecraft 26.2 Java 25 foundation for Fabric and NeoForge, with platform isolation, a typed read-only sample tool, development diagnostics, and CI.

**Architecture:** Use the dependency-free 26.2 MultiLoader Template with `common`, `fabric`, and `neoforge` modules. Common owns bootstrap and typed tool contracts; loader modules own entrypoints, platform discovery, and command registration. This plan intentionally excludes model SDKs, content integrations, search, reflective adapters, and Ponder.

**Tech Stack:** Java 25, Minecraft 26.2, Gradle Wrapper, Fabric Loom 1.16.3, Fabric API 0.152.1+26.2, NeoForge ModDevGradle 2.0.141, NeoForge 26.2.0.1-beta, JUnit Jupiter 5.11.4, GitHub Actions.

---

## Scope

This plan implements Phase 0 only. Koog, Skills, FTB Quests, Patchouli, recipes, private guides, reflective descriptors, search, Ponder, and the 1.21.1 branch each require later plans.

## File Map

- Build and metadata: root Gradle files, wrapper, `build-logic/**`, three module build files, loader metadata, `LICENSE`, and `README.md`.
- Common runtime: `TomeWispConstants`, `TomeWispBootstrap`, `TomeWispRuntime`.
- Platform boundary: `PlatformService`, `PlatformServices`, and one implementation/service binding per loader.
- Tool boundary: `Tool`, `ToolAccess`, `ToolDescriptor`, `ToolProvider`, `ToolRegistry`, and `ToolResult`.
- Development mode: `PlatformInfoTool`, `DevelopmentToolInspector`, `DevelopmentCommandHandler`, and loader command adapters.
- Verification: JUnit tests, an isolation test, GitHub Actions, and `docs/development.md`.

## Task 1: Import and brand the 26.2 MultiLoader project

**Files:**
- Create: `.gitattributes`, `.gitignore`, `LICENSE`, `README.md`, root Gradle files and wrapper.
- Create: `build-logic/**`, `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle`.
- Create: initial loader metadata and resources from the template.

- [ ] **Step 1: Import the template without Git history**

Run these commands from the TomeWisp root:

```bash
curl --retry 10 --retry-all-errors --connect-timeout 10 --max-time 180 -fL https://codeload.github.com/jaredlll08/MultiLoader-Template/zip/refs/heads/26.2 -o /tmp/tomewisp-template.zip
unzip -q -o /tmp/tomewisp-template.zip -d /tmp/tomewisp-template
rsync -a --exclude '.git' --exclude 'README.md' --exclude 'LICENSE' /tmp/tomewisp-template/MultiLoader-Template-26.2/ ./
```

Expected: `common`, `fabric`, `neoforge`, `build-logic`, and `gradlew` exist; `docs/` remains intact.

- [ ] **Step 2: Replace template settings**

Set `settings.gradle` to:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven { name = 'Fabric'; url = uri('https://maven.fabricmc.net') }
            }
            filter { includeGroupAndSubgroups('net.fabricmc') }
        }
    }
}
plugins { id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0' }
rootProject.name = 'TomeWisp'
includeBuild('build-logic')
include('common')
include('fabric')
include('neoforge')
```

Set `gradle.properties` to:

```properties
version=0.1.0-SNAPSHOT
group=dev.tomewisp
java_version=25
minecraft_version=26.2
mod_name=TomeWisp
mod_author=nkanf
mod_id=tomewisp
license=MIT
credits=
description=An in-game knowledge companion for modded Minecraft.
minecraft_version_range=[26.2, 26.3)
neo_form_version=26.2-1
fabric_version=0.152.1+26.2
fabric_loader_version=0.19.3
neoforge_version=26.2.0.1-beta
neoforge_loader_version_range=[4,)
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

- [ ] **Step 3: Enable common-module tests**

Add inside the existing `dependencies` block in `common/build.gradle`:

```groovy
testImplementation('org.junit.jupiter:junit-jupiter:5.11.4')
```

Add after that block:

```groovy
tasks.withType(Test).configureEach { useJUnitPlatform() }
```

- [ ] **Step 4: Add public repository docs**

Create an MIT `LICENSE` for `Copyright (c) 2026 nkanf`. Create `README.md` with TomeWisp's purpose, Minecraft 26.2, Java 25, Fabric/NeoForge targets, `./gradlew build`, and all four loader run tasks.

- [ ] **Step 5: Verify and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew projects
git add .
git commit -m "build: bootstrap Minecraft 26.2 multiloader project"
```

Expected: Gradle lists `:common`, `:fabric`, and `:neoforge`.

## Task 2: Replace template examples with a platform boundary

**Files:**
- Delete: all `com/example/examplemod/**` sources and example mixins.
- Create: `common/src/main/java/dev/tomewisp/TomeWispConstants.java`.
- Create: `common/src/main/java/dev/tomewisp/platform/PlatformService.java`.
- Create: `common/src/main/java/dev/tomewisp/platform/PlatformServices.java`.
- Create: one platform implementation and service binding per loader.
- Modify: `fabric.mod.json` and `META-INF/neoforge.mods.toml`.

- [ ] **Step 1: Create the platform contract**

```java
package dev.tomewisp.platform;

public interface PlatformService {
    String platformName();
    boolean isModLoaded(String modId);
    boolean isDevelopmentEnvironment();
}
```

- [ ] **Step 2: Create strict service loading**

```java
package dev.tomewisp.platform;

import java.util.List;
import java.util.ServiceLoader;

public final class PlatformServices {
    private PlatformServices() {}

    public static PlatformService load() {
        List<PlatformService> services = ServiceLoader.load(
                PlatformService.class, PlatformServices.class.getClassLoader())
                .stream().map(ServiceLoader.Provider::get).toList();
        if (services.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one PlatformService, found " + services.size());
        }
        return services.getFirst();
    }
}
```

- [ ] **Step 3: Create stable identity**

```java
package dev.tomewisp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TomeWispConstants {
    public static final String MOD_ID = "tomewisp";
    public static final String MOD_NAME = "TomeWisp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private TomeWispConstants() {}
}
```

- [ ] **Step 4: Implement loader services**

Create `FabricPlatformService`:

```java
package dev.tomewisp.fabric;

import dev.tomewisp.platform.PlatformService;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformService implements PlatformService {
    public String platformName() { return "Fabric"; }
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
```

Create `NeoForgePlatformService`:

```java
package dev.tomewisp.neoforge;

import dev.tomewisp.platform.PlatformService;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public final class NeoForgePlatformService implements PlatformService {
    public String platformName() { return "NeoForge"; }
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }
}
```

Fabric service file content is `dev.tomewisp.fabric.FabricPlatformService`. NeoForge service file content is `dev.tomewisp.neoforge.NeoForgePlatformService`.

- [ ] **Step 5: Remove mixins, update entrypoint metadata, compile, and commit**

Fabric entrypoint becomes `dev.tomewisp.fabric.TomeWispFabric`; NeoForge uses `dev.tomewisp.neoforge.TomeWispNeoForge`. Remove all example mixin declarations.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :fabric:compileJava :neoforge:compileJava
git add common fabric neoforge
git commit -m "feat: add cross-loader platform service"
```

Expected: both modules compile and `rg 'com.example.examplemod'` returns no matches.

## Task 3: Define typed tool contracts with TDD

**Files:**
- Create: `common/src/main/java/dev/tomewisp/tool/ToolAccess.java`.
- Create: `common/src/main/java/dev/tomewisp/tool/ToolDescriptor.java`.
- Create: `common/src/main/java/dev/tomewisp/tool/ToolResult.java`.
- Create: `common/src/main/java/dev/tomewisp/tool/Tool.java`.
- Create: `common/src/main/java/dev/tomewisp/tool/ToolProvider.java`.
- Test: `common/src/test/java/dev/tomewisp/tool/ToolDescriptorTest.java`.
- Test: `common/src/test/java/dev/tomewisp/tool/ToolResultTest.java`.

- [ ] **Step 1: Write failing descriptor tests**

```java
package dev.tomewisp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

final class ToolDescriptorTest {
    record Input(String value) {}
    record Output(String value) {}

    @Test void acceptsNamespacedId() {
        var descriptor = new ToolDescriptor<>("tomewisp:echo", "Echo",
                Input.class, Output.class, ToolAccess.READ_ONLY);
        assertEquals("tomewisp:echo", descriptor.id());
    }

    @Test void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new ToolDescriptor<>(
                "Echo Tool", "Echo", Input.class, Output.class, ToolAccess.READ_ONLY));
        assertThrows(IllegalArgumentException.class, () -> new ToolDescriptor<>(
                "tomewisp:echo", " ", Input.class, Output.class, ToolAccess.READ_ONLY));
    }
}
```

- [ ] **Step 2: Verify it fails**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test --tests dev.tomewisp.tool.ToolDescriptorTest
```

Expected: missing `ToolDescriptor` and `ToolAccess`.

- [ ] **Step 3: Implement descriptor types**

`ToolAccess.java`:

```java
package dev.tomewisp.tool;
public enum ToolAccess { READ_ONLY }
```

`ToolDescriptor.java`:

```java
package dev.tomewisp.tool;

import java.util.Objects;
import java.util.regex.Pattern;

public record ToolDescriptor<I, O>(String id, String description,
        Class<I> inputType, Class<O> outputType, ToolAccess access) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ToolDescriptor {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid tool id: " + id);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be blank");
        }
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(access, "access");
    }
}
```

- [ ] **Step 4: Implement result and provider contracts**

`ToolResult.java`:

```java
package dev.tomewisp.tool;

import java.util.Objects;

public sealed interface ToolResult<O> permits ToolResult.Success, ToolResult.Failure {
    record Success<O>(O value) implements ToolResult<O> {
        public Success { Objects.requireNonNull(value, "value"); }
    }

    record Failure<O>(String code, String message) implements ToolResult<O> {
        public Failure {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("Failure code and message are required");
            }
        }
    }
}
```

`Tool.java`:

```java
package dev.tomewisp.tool;

public interface Tool<I, O> {
    ToolDescriptor<I, O> descriptor();
    ToolResult<O> invoke(I input);
}
```

`ToolProvider.java`:

```java
package dev.tomewisp.tool;

import java.util.Collection;

public interface ToolProvider {
    String providerId();
    Collection<? extends Tool<?, ?>> tools();
}
```

- [ ] **Step 5: Add result invariant tests**

```java
package dev.tomewisp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

final class ToolResultTest {
    @Test void enforcesValues() {
        assertEquals("ok", new ToolResult.Success<>("ok").value());
        assertThrows(NullPointerException.class, () -> new ToolResult.Success<>(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolResult.Failure<String>("", "message"));
    }
}
```

- [ ] **Step 6: Test and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test
git add common/src/main/java/dev/tomewisp/tool common/src/test/java/dev/tomewisp/tool
git commit -m "feat: define typed read-only tool contracts"
```

Expected: all tool contract tests pass.

## Task 4: Implement deterministic ToolRegistry behavior

**Files:**
- Create: `common/src/main/java/dev/tomewisp/tool/ToolRegistry.java`.
- Test: `common/src/test/java/dev/tomewisp/tool/ToolRegistryTest.java`.

- [ ] **Step 1: Write failing registry tests**

```java
package dev.tomewisp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ToolRegistryTest {
    record Input() {}
    record Output(String value) {}

    @Test void ordersIdsAndRejectsDuplicates() {
        ToolRegistry registry = new ToolRegistry();
        Tool<Input, Output> beta = tool("test:beta");
        Tool<Input, Output> alpha = tool("test:alpha");
        registry.register("provider-a", List.of(beta, alpha));
        assertEquals(List.of("test:alpha", "test:beta"), registry.descriptors()
                .stream().map(ToolDescriptor::id).toList());
        assertThrows(IllegalStateException.class,
                () -> registry.register("provider-b", List.of(alpha)));
        assertEquals(alpha, registry.find("test:alpha").orElseThrow());
    }

    private static Tool<Input, Output> tool(String id) {
        return new Tool<>() {
            private final ToolDescriptor<Input, Output> descriptor =
                    new ToolDescriptor<>(id, "Test tool", Input.class,
                            Output.class, ToolAccess.READ_ONLY);
            public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            public ToolResult<Output> invoke(Input input) {
                return new ToolResult.Success<>(new Output(id));
            }
        };
    }
}
```

- [ ] **Step 2: Verify it fails**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test --tests dev.tomewisp.tool.ToolRegistryTest
```

Expected: missing `ToolRegistry`.

- [ ] **Step 3: Implement ToolRegistry**

```java
package dev.tomewisp.tool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class ToolRegistry {
    private final Map<String, RegisteredTool> tools = new TreeMap<>();

    public synchronized void register(
            String providerId, Collection<? extends Tool<?, ?>> newTools) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank");
        }
        List<? extends Tool<?, ?>> snapshot = List.copyOf(newTools);
        for (Tool<?, ?> tool : snapshot) {
            RegisteredTool existing = tools.get(tool.descriptor().id());
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool id "
                        + tool.descriptor().id() + " from " + providerId
                        + "; already registered by " + existing.providerId());
            }
        }
        for (Tool<?, ?> tool : snapshot) {
            tools.put(tool.descriptor().id(), new RegisteredTool(providerId, tool));
        }
    }

    public synchronized Optional<Tool<?, ?>> find(String id) {
        RegisteredTool value = tools.get(id);
        return value == null ? Optional.empty() : Optional.of(value.tool());
    }

    public synchronized List<ToolDescriptor<?, ?>> descriptors() {
        return tools.values().stream().map(RegisteredTool::tool)
                .map(Tool::descriptor).toList();
    }

    private record RegisteredTool(String providerId, Tool<?, ?> tool) {}
}
```

- [ ] **Step 4: Test and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test
git add common/src/main/java/dev/tomewisp/tool/ToolRegistry.java common/src/test/java/dev/tomewisp/tool/ToolRegistryTest.java
git commit -m "feat: add deterministic tool registry"
```

Expected: registry tests pass with stable ordering and fail-closed duplicates.

## Task 5: Bootstrap runtime and development inspection

**Files:**
- Create: `common/src/main/java/dev/tomewisp/TomeWispRuntime.java`.
- Create: `common/src/main/java/dev/tomewisp/TomeWispBootstrap.java`.
- Create: `common/src/main/java/dev/tomewisp/tool/builtin/PlatformInfoTool.java`.
- Create: `common/src/main/java/dev/tomewisp/devmode/DevelopmentToolInspector.java`.
- Create: `common/src/main/java/dev/tomewisp/devmode/DevelopmentCommandHandler.java`.
- Test: `common/src/test/java/dev/tomewisp/devmode/DevelopmentToolInspectorTest.java`.

- [ ] **Step 1: Write a failing inspector test**

```java
package dev.tomewisp.devmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import dev.tomewisp.tool.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DevelopmentToolInspectorTest {
    record Input() {}
    record Output(String value) {}

    @Test void listsAndInvokesNoArgumentTool() {
        Tool<Input, Output> tool = new Tool<>() {
            private final ToolDescriptor<Input, Output> descriptor =
                    new ToolDescriptor<>("test:ping", "Ping", Input.class,
                            Output.class, ToolAccess.READ_ONLY);
            public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            public ToolResult<Output> invoke(Input input) {
                return new ToolResult.Success<>(new Output("pong"));
            }
        };
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(tool));
        DevelopmentToolInspector inspector = new DevelopmentToolInspector(registry);
        assertEquals(List.of("test:ping - Ping"), inspector.listTools());
        assertInstanceOf(ToolResult.Success.class,
                inspector.invokeNoArgument("test:ping"));
    }
}
```

- [ ] **Step 2: Verify it fails**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test --tests dev.tomewisp.devmode.DevelopmentToolInspectorTest
```

Expected: missing `DevelopmentToolInspector`.

- [ ] **Step 3: Implement inspector**

```java
package dev.tomewisp.devmode;

import dev.tomewisp.tool.*;
import java.util.List;

public final class DevelopmentToolInspector {
    private final ToolRegistry registry;
    public DevelopmentToolInspector(ToolRegistry registry) { this.registry = registry; }

    public List<String> listTools() {
        return registry.descriptors().stream()
                .map(value -> value.id() + " - " + value.description()).toList();
    }

    public ToolResult<?> invokeNoArgument(String id) {
        Tool<?, ?> raw = registry.find(id).orElse(null);
        if (raw == null) {
            return new ToolResult.Failure<>("unknown_tool", "Unknown tool: " + id);
        }
        try {
            Object input = raw.descriptor().inputType().getDeclaredConstructor().newInstance();
            return invokeUnchecked(raw, input);
        } catch (ReflectiveOperationException exception) {
            return new ToolResult.Failure<>("input_required",
                    "Tool requires explicit input: " + id);
        }
    }

    @SuppressWarnings("unchecked")
    private static ToolResult<?> invokeUnchecked(Tool<?, ?> raw, Object input) {
        return ((Tool<Object, Object>) raw).invoke(input);
    }
}
```

- [ ] **Step 4: Implement built-in platform tool**

```java
package dev.tomewisp.tool.builtin;

import dev.tomewisp.platform.PlatformService;
import dev.tomewisp.tool.*;

public final class PlatformInfoTool implements Tool<PlatformInfoTool.Input, PlatformInfoTool.Output> {
    public record Input() {}
    public record Output(String platform, boolean developmentEnvironment) {}
    private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
            "tomewisp:platform_info", "Return active loader and environment",
            Input.class, Output.class, ToolAccess.READ_ONLY);
    private final PlatformService platform;
    public PlatformInfoTool(PlatformService platform) { this.platform = platform; }
    public ToolDescriptor<Input, Output> descriptor() { return DESCRIPTOR; }
    public ToolResult<Output> invoke(Input input) {
        return new ToolResult.Success<>(new Output(
                platform.platformName(), platform.isDevelopmentEnvironment()));
    }
}
```

- [ ] **Step 5: Implement runtime ownership**

`TomeWispRuntime.java`:

```java
package dev.tomewisp;
import dev.tomewisp.devmode.DevelopmentToolInspector;
import dev.tomewisp.platform.PlatformService;
import dev.tomewisp.tool.ToolRegistry;
public record TomeWispRuntime(PlatformService platform, ToolRegistry tools,
        DevelopmentToolInspector developmentTools) {}
```

`TomeWispBootstrap.java`:

```java
package dev.tomewisp;

import dev.tomewisp.devmode.DevelopmentToolInspector;
import dev.tomewisp.platform.*;
import dev.tomewisp.tool.ToolRegistry;
import dev.tomewisp.tool.builtin.PlatformInfoTool;
import java.util.List;

public final class TomeWispBootstrap {
    private static TomeWispRuntime runtime;
    private TomeWispBootstrap() {}

    public static synchronized TomeWispRuntime initialize() {
        if (runtime != null) return runtime;
        PlatformService platform = PlatformServices.load();
        ToolRegistry tools = new ToolRegistry();
        tools.register("tomewisp:builtins", List.of(new PlatformInfoTool(platform)));
        runtime = new TomeWispRuntime(platform, tools,
                new DevelopmentToolInspector(tools));
        TomeWispConstants.LOGGER.info("Initialized TomeWisp on {} with {} tool(s)",
                platform.platformName(), tools.descriptors().size());
        return runtime;
    }
}
```

- [ ] **Step 6: Implement command formatting**

```java
package dev.tomewisp.devmode;

import dev.tomewisp.tool.ToolResult;
import java.util.List;

public final class DevelopmentCommandHandler {
    private final DevelopmentToolInspector inspector;
    public DevelopmentCommandHandler(DevelopmentToolInspector inspector) {
        this.inspector = inspector;
    }
    public List<String> listTools() { return inspector.listTools(); }
    public String invoke(String id) {
        return switch (inspector.invokeNoArgument(id)) {
            case ToolResult.Success<?> success -> "SUCCESS " + success.value();
            case ToolResult.Failure<?> failure ->
                    "FAILURE " + failure.code() + ": " + failure.message();
        };
    }
}
```

- [ ] **Step 7: Test and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test
git add common/src/main/java/dev/tomewisp common/src/test/java/dev/tomewisp/devmode
git commit -m "feat: bootstrap tool runtime and development inspector"
```

Expected: inspector test passes and bootstrap owns one built-in tool.

## Task 6: Register loader entrypoints and development commands

**Files:**
- Create: `fabric/src/main/java/dev/tomewisp/fabric/TomeWispFabric.java`.
- Create: `fabric/src/main/java/dev/tomewisp/fabric/FabricDevelopmentCommands.java`.
- Create: `neoforge/src/main/java/dev/tomewisp/neoforge/TomeWispNeoForge.java`.
- Create: `neoforge/src/main/java/dev/tomewisp/neoforge/NeoForgeDevelopmentCommands.java`.
- Modify: both loader metadata files.

- [ ] **Step 1: Create Fabric entrypoint**

```java
package dev.tomewisp.fabric;

import dev.tomewisp.TomeWispBootstrap;
import dev.tomewisp.TomeWispRuntime;
import net.fabricmc.api.ModInitializer;

public final class TomeWispFabric implements ModInitializer {
    @Override public void onInitialize() {
        TomeWispRuntime runtime = TomeWispBootstrap.initialize();
        FabricDevelopmentCommands.register(runtime);
    }
}
```

- [ ] **Step 2: Create Fabric command adapter**

```java
package dev.tomewisp.fabric;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;
import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.devmode.DevelopmentCommandHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class FabricDevelopmentCommands {
    private FabricDevelopmentCommands() {}

    public static void register(TomeWispRuntime runtime) {
        var handler = new DevelopmentCommandHandler(runtime.developmentTools());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(literal("tomewisp").then(literal("dev")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("tools").executes(context -> {
                            handler.listTools().forEach(line -> context.getSource()
                                    .sendSuccess(() -> Component.literal(line), false));
                            return 1;
                        }))
                        .then(literal("invoke").then(argument("tool", greedyString())
                                .executes(context -> {
                                    String id = getString(context, "tool");
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(handler.invoke(id)), false);
                                    return 1;
                                }))))));
    }
}
```

- [ ] **Step 3: Create NeoForge entrypoint**

```java
package dev.tomewisp.neoforge;

import dev.tomewisp.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TomeWispConstants.MOD_ID)
public final class TomeWispNeoForge {
    public TomeWispNeoForge(IEventBus modBus) {
        TomeWispRuntime runtime = TomeWispBootstrap.initialize();
        NeoForgeDevelopmentCommands.register(runtime);
    }
}
```

- [ ] **Step 4: Create NeoForge command adapter**

```java
package dev.tomewisp.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.commands.Commands.*;
import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.devmode.DevelopmentCommandHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NeoForgeDevelopmentCommands {
    private NeoForgeDevelopmentCommands() {}

    public static void register(TomeWispRuntime runtime) {
        var handler = new DevelopmentCommandHandler(runtime.developmentTools());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                event.getDispatcher().register(literal("tomewisp").then(literal("dev")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("tools").executes(context -> {
                            handler.listTools().forEach(line -> context.getSource()
                                    .sendSuccess(() -> Component.literal(line), false));
                            return 1;
                        }))
                        .then(literal("invoke").then(argument("tool", greedyString())
                                .executes(context -> {
                                    String id = getString(context, "tool");
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(handler.invoke(id)), false);
                                    return 1;
                                }))))));
    }
}
```

- [ ] **Step 5: Compile against exact 26.2 APIs**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :fabric:compileJava :neoforge:compileJava
```

Expected: both compile against the 26.2 permission model. `Permissions.COMMANDS_GAMEMASTER` keeps both command adapters operator-only.

- [ ] **Step 6: Build and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :fabric:build :neoforge:build
git add fabric neoforge
git commit -m "feat: expose development tool commands on both loaders"
```

Expected: distributable jars exist in both loader `build/libs` directories.

## Task 7: Enforce common isolation and add CI

**Files:**
- Test: `common/src/test/java/dev/tomewisp/architecture/CommonLoaderIsolationTest.java`.
- Create: `.github/workflows/build.yml`.

- [ ] **Step 1: Add the isolation test**

```java
package dev.tomewisp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommonLoaderIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "import net.fabricmc.", "import net.neoforged.");

    @Test void commonProductionSourcesDoNotImportLoaderApis() throws IOException {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            List<Path> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(CommonLoaderIsolationTest::containsForbiddenImport).toList();
            assertTrue(violations.isEmpty(),
                    () -> "Common source imports loader APIs: " + violations);
    }

    private static boolean containsForbiddenImport(Path path) {
        try {
            String source = Files.readString(path);
            return FORBIDDEN.stream().anyMatch(source::contains);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}
```

- [ ] **Step 2: Verify the guard**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :common:test --tests dev.tomewisp.architecture.CommonLoaderIsolationTest
```

Expected: PASS.

- [ ] **Step 3: Add GitHub Actions**

Create `.github/workflows/build.yml`:

```yaml
name: build
on:
  push:
    branches: [main, "mc/**"]
  pull_request:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "25"
      - uses: gradle/actions/setup-gradle@v4
      - name: Test common
        run: ./gradlew :common:test
      - name: Build loaders
        run: ./gradlew :fabric:build :neoforge:build
      - uses: actions/upload-artifact@v4
        with:
          name: tomewisp-26.2
          path: |
            fabric/build/libs/*.jar
            neoforge/build/libs/*.jar
          if-no-files-found: error
```

- [ ] **Step 4: Run the CI-equivalent build and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew clean :common:test :fabric:build :neoforge:build
git add common/src/test/java/dev/tomewisp/architecture .github/workflows/build.yml
git commit -m "ci: verify common isolation and dual-loader builds"
```

Expected: `BUILD SUCCESSFUL` and both loader artifacts exist.

## Task 8: Smoke-test development commands and document handoff

**Files:**
- Create: `docs/development.md`.
- Modify: `README.md`.

- [ ] **Step 1: Run Fabric server and commands**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :fabric:runServer
```

At the interactive console run:

```text
tomewisp dev tools
tomewisp dev invoke tomewisp:platform_info
stop
```

Expected: initialization reports Fabric and one tool; list contains `tomewisp:platform_info`; invoke reports `platform=Fabric`.

- [ ] **Step 2: Run NeoForge server and commands**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :neoforge:runServer
```

Run the same commands. Expected: invoke reports `platform=NeoForge`.

- [ ] **Step 3: Document verified workflow**

Create `docs/development.md` with Java 25 requirements, wrapper build/run commands, both development commands, and the guarantee that development tools do not grant shell, arbitrary code, or world mutation. Link it from `README.md`.

- [ ] **Step 4: Verify jar contents**

```bash
jar tf "$(find fabric/build/libs -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)" | rg 'dev/tomewisp/TomeWispBootstrap|fabric.mod.json'
jar tf "$(find neoforge/build/libs -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)" | rg 'dev/tomewisp/TomeWispBootstrap|META-INF/neoforge.mods.toml'
```

Expected: each jar contains the common bootstrap and its loader metadata.

- [ ] **Step 5: Final verification and commit**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew clean build
git diff --check
git add README.md docs/development.md
git commit -m "docs: record dual-loader development workflow"
git status --short --branch
```

Expected: build succeeds and the worktree is clean after the commit.

## Phase 0 Completion Gate

- Clean checkout builds with Java 25.
- Fabric and NeoForge clients and dedicated servers start.
- Common production code imports no loader APIs.
- `tomewisp:platform_info` is registered and manually invokable on both loaders.
- Development commands are operator-only.
- Unit tests and CI pass.
- Both distributable jars contain common code and correct metadata.
