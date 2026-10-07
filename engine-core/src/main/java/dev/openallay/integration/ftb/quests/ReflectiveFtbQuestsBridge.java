package dev.openallay.integration.ftb.quests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ReflectiveFtbQuestsBridge implements FtbQuestsBridge {
    public static final String API_CLASS = "dev.ftb.mods.ftbquests.api.FTBQuestsAPI";

    private final ClassLoader loader;
    private final String apiClassName;
    private volatile FtbQuestsMapping mapping;
    private final Map<MethodKey, MethodHandle> methods = new HashMap<>();

    public ReflectiveFtbQuestsBridge(ClassLoader loader) {
        this(loader, API_CLASS);
    }

    public ReflectiveFtbQuestsBridge(ClassLoader loader, String apiClassName) {
        this.loader = loader;
        this.apiClassName = apiClassName;
    }

    @Override
    public FtbQuestSnapshot.Result snapshot(Object player, boolean clientSide) {
        try {
            FtbQuestsMapping root = root();
            Object api = root.api().invoke();
            Object file = root.getQuestFile().invoke(api, clientSide);
            Optional<?> team = optional(invoke(file, "getTeamData", player));
            if (team.isEmpty()) {
                return FtbQuestSnapshot.Result.unavailable(
                        "ftb_team_data_unavailable", "FTB Quests has no visible team data for this player");
            }
            Object teamData = team.orElseThrow();
            List<Object> chapters = list(invoke(file, "getVisibleChapters", teamData));
            List<RawQuest> raw = new ArrayList<>();
            Set<String> visibleIds = new HashSet<>();
            for (Object chapter : chapters) {
                String chapterId = id(chapter);
                String chapterTitle = component(invoke(chapter, "getTitle"));
                for (Object quest : list(invoke(chapter, "getQuests"))) {
                    if (!(boolean) invoke(quest, "isVisible", teamData)) {
                        continue;
                    }
                    String questId = id(quest);
                    visibleIds.add(questId);
                    raw.add(new RawQuest(
                            quest,
                            questId,
                            chapterId,
                            chapterTitle,
                            component(invoke(quest, "getTitle")),
                            components(list(invoke(quest, "getDescription"))),
                            (boolean) invoke(teamData, "isCompleted", quest)));
                }
            }
            List<FtbQuestSnapshot> result = new ArrayList<>();
            for (RawQuest quest : raw) {
                Set<String> dependencies = new java.util.TreeSet<>();
                try (Stream<?> stream = stream(invoke(quest.raw, "streamDependencies"))) {
                    stream.map(this::id).filter(visibleIds::contains).forEach(dependencies::add);
                }
                result.add(new FtbQuestSnapshot(
                        quest.id,
                        quest.chapterId,
                        quest.chapterTitle,
                        quest.title,
                        quest.description,
                        dependencies,
                        quest.completed,
                        "ftbquests:public-api+validated-method-handles"));
            }
            result.sort(java.util.Comparator.comparing(FtbQuestSnapshot::chapterId)
                    .thenComparing(FtbQuestSnapshot::questId));
            return FtbQuestSnapshot.Result.available(result);
        } catch (ClassNotFoundException failure) {
            return FtbQuestSnapshot.Result.unavailable(
                    "integration_unavailable", "FTB Quests API is not installed");
        } catch (Throwable failure) {
            Throwable cause = failure;
            while (cause.getCause() != null
                    && (cause instanceof java.lang.reflect.InvocationTargetException
                            || cause instanceof java.lang.invoke.WrongMethodTypeException)) {
                cause = cause.getCause();
            }
            return FtbQuestSnapshot.Result.unavailable(
                    "integration_shape_mismatch",
                    "FTB Quests public API shape is unsupported: "
                            + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
        }
    }

    private FtbQuestsMapping root() throws ClassNotFoundException, IllegalAccessException {
        FtbQuestsMapping current = mapping;
        if (current != null) return current;
        synchronized (this) {
            if (mapping != null) return mapping;
            Class<?> apiClass = Class.forName(apiClassName, false, loader);
            Method api = unique(apiClass, "api", 0, true);
            MethodHandle apiHandle = MethodHandles.publicLookup().unreflect(api);
            Class<?> apiType = api.getReturnType();
            Method getQuestFile = unique(apiType, "getQuestFile", 1, false);
            if (getQuestFile.getParameterTypes()[0] != boolean.class) {
                throw new IllegalArgumentException("getQuestFile must accept boolean");
            }
            mapping = new FtbQuestsMapping(
                    apiHandle, MethodHandles.publicLookup().unreflect(getQuestFile));
            return mapping;
        }
    }

    private Object invoke(Object receiver, String name, Object... arguments) throws Throwable {
        MethodKey key = new MethodKey(receiver.getClass(), name, arguments.length);
        MethodHandle handle;
        synchronized (methods) {
            handle = methods.get(key);
            if (handle == null) {
                Method method = unique(receiver.getClass(), name, arguments.length, false);
                handle = MethodHandles.publicLookup().unreflect(method);
                methods.put(key, handle);
            }
        }
        List<Object> values = new ArrayList<>(arguments.length + 1);
        values.add(receiver);
        values.addAll(List.of(arguments));
        return handle.invokeWithArguments(values);
    }

    private static Method unique(Class<?> type, String name, int arity, boolean requireStatic) {
        List<Method> matches = Stream.of(type.getMethods())
                .filter(method -> method.getName().equals(name)
                        && method.getParameterCount() == arity
                        && Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers()) == requireStatic)
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected one public " + name + "/" + arity + " on " + type.getName());
        }
        return matches.get(0);
    }

    private String id(Object object) {
        try {
            Object value = invoke(object, "getCodeString");
            return String.valueOf(value);
        } catch (Throwable failure) {
            try {
                return Long.toHexString(((Number) invoke(object, "getId")).longValue());
            } catch (Throwable nested) {
                throw new IllegalArgumentException("Quest object lacks a public stable ID", nested);
            }
        }
    }

    private String component(Object component) throws Throwable {
        return String.valueOf(invoke(component, "getString"));
    }

    private String components(List<Object> values) throws Throwable {
        List<String> lines = new ArrayList<>();
        for (Object value : values) lines.add(component(value));
        return String.join("\n", lines);
    }

    private static Optional<?> optional(Object value) {
        if (!(value instanceof Optional<?> optional)) {
            throw new IllegalArgumentException("Expected Optional from getTeamData");
        }
        return optional;
    }

    private static List<Object> list(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("Expected Collection from FTB Quests API");
        }
        return new ArrayList<>(collection);
    }

    private static Stream<?> stream(Object value) {
        if (!(value instanceof Stream<?> stream)) {
            throw new IllegalArgumentException("Expected Stream from streamDependencies");
        }
        return stream;
    }

    @dev.openallay.value.ValueType(MethodKey.ValueSchemaProvider.class)
private static final class MethodKey {
    private final Class<?> type;
    private final String name;
    private final int arity;
    private MethodKey(Class<?> type, String name, int arity) {
        this.type = type;
        this.name = name;
        this.arity = arity;
    }
    public Class<?> type() { return type; }
    public String name() { return name; }
    public int arity() { return arity; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MethodKey)) return false;
        MethodKey that = (MethodKey) other;
        return java.util.Objects.equals(type, that.type) && java.util.Objects.equals(name, that.name) && arity == that.arity;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + Integer.hashCode(arity);
        return hash;
    }
    @Override public String toString() { return "MethodKey[type=" + type + ", name=" + name + ", arity=" + arity + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MethodKey> schema() {
            return new dev.openallay.value.ValueSchema<>(MethodKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MethodKey>>asList(new dev.openallay.value.ValueSchema.Component<>(MethodKey.class, "type", MethodKey::type), new dev.openallay.value.ValueSchema.Component<>(MethodKey.class, "name", MethodKey::name), new dev.openallay.value.ValueSchema.Component<>(MethodKey.class, "arity", MethodKey::arity)), arguments -> new MethodKey((Class) arguments[0], (String) arguments[1], (Integer) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(RawQuest.ValueSchemaProvider.class)
private static final class RawQuest {
    private final Object raw;
    private final String id;
    private final String chapterId;
    private final String chapterTitle;
    private final String title;
    private final String description;
    private final boolean completed;
    private RawQuest(Object raw, String id, String chapterId, String chapterTitle, String title, String description, boolean completed) {
        this.raw = raw;
        this.id = id;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.title = title;
        this.description = description;
        this.completed = completed;
    }
    public Object raw() { return raw; }
    public String id() { return id; }
    public String chapterId() { return chapterId; }
    public String chapterTitle() { return chapterTitle; }
    public String title() { return title; }
    public String description() { return description; }
    public boolean completed() { return completed; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RawQuest)) return false;
        RawQuest that = (RawQuest) other;
        return java.util.Objects.equals(raw, that.raw) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(chapterId, that.chapterId) && java.util.Objects.equals(chapterTitle, that.chapterTitle) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && completed == that.completed;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(raw);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(chapterId);
        hash = 31 * hash + java.util.Objects.hashCode(chapterTitle);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + Boolean.hashCode(completed);
        return hash;
    }
    @Override public String toString() { return "RawQuest[raw=" + raw + ", id=" + id + ", chapterId=" + chapterId + ", chapterTitle=" + chapterTitle + ", title=" + title + ", description=" + description + ", completed=" + completed + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RawQuest> schema() {
            return new dev.openallay.value.ValueSchema<>(RawQuest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RawQuest>>asList(new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "raw", RawQuest::raw), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "id", RawQuest::id), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "chapterId", RawQuest::chapterId), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "chapterTitle", RawQuest::chapterTitle), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "title", RawQuest::title), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "description", RawQuest::description), new dev.openallay.value.ValueSchema.Component<>(RawQuest.class, "completed", RawQuest::completed)), arguments -> new RawQuest((Object) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (Boolean) arguments[6]));
        }
    }
}
}
