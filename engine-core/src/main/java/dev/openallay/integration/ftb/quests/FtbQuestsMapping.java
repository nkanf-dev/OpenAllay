package dev.openallay.integration.ftb.quests;

import java.lang.invoke.MethodHandle;

@dev.openallay.value.ValueType(FtbQuestsMapping.ValueSchemaProvider.class)
final class FtbQuestsMapping {
    private final MethodHandle api;
    private final MethodHandle getQuestFile;
    FtbQuestsMapping(MethodHandle api, MethodHandle getQuestFile) {
        this.api = api;
        this.getQuestFile = getQuestFile;
    }
    public MethodHandle api() { return api; }
    public MethodHandle getQuestFile() { return getQuestFile; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FtbQuestsMapping)) return false;
        FtbQuestsMapping that = (FtbQuestsMapping) other;
        return java.util.Objects.equals(api, that.api) && java.util.Objects.equals(getQuestFile, that.getQuestFile);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(api);
        hash = 31 * hash + java.util.Objects.hashCode(getQuestFile);
        return hash;
    }
    @Override public String toString() { return "FtbQuestsMapping[api=" + api + ", getQuestFile=" + getQuestFile + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<FtbQuestsMapping> schema() {
            return new dev.openallay.value.ValueSchema<>(FtbQuestsMapping.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<FtbQuestsMapping>>asList(new dev.openallay.value.ValueSchema.Component<>(FtbQuestsMapping.class, "api", FtbQuestsMapping::api), new dev.openallay.value.ValueSchema.Component<>(FtbQuestsMapping.class, "getQuestFile", FtbQuestsMapping::getQuestFile)), arguments -> new FtbQuestsMapping((MethodHandle) arguments[0], (MethodHandle) arguments[1]));
        }
    }
}
