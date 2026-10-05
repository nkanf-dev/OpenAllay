package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.extension.JavascriptHostBinding;
import dev.openallay.extension.JavascriptHostMethod;
import dev.openallay.extension.JavascriptHostValueType;
import dev.openallay.extension.JavascriptInvocationScope;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.fixture.JavaAccessFixture;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Executes the actual Rhino bridge against synthetic classes, not reflection mocks. */
final class UnrestrictedJavaAccessTest {
    private static final String FIXTURE = "dev.openallay.script.fixture.JavaAccessFixture";
    private static final String PARENT = "dev.openallay.script.fixture.JavaAccessParent";
    private static final String PRELUDE = "const Fixture = Java.type('" + FIXTURE + "');\n";
    private final RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();

    @Test
    void preservesPublicWrapperCallsWhileNativeClassOfAvoidsHiddenClassProperties() {
        assertEquals("public:ok", execute(PRELUDE
                + "return Fixture.publicStaticMethod('ok');").getAsString());
        assertThrows(JavascriptExecutionException.class,
                () -> execute(PRELUDE + "return Fixture.class;"));
        assertThrows(JavascriptExecutionException.class,
                () -> execute(PRELUDE + "return Fixture.create().getClass();"));
        assertJson("""
                {"type":"dev.openallay.script.fixture.JavaAccessFixture",
                 "instance":"dev.openallay.script.fixture.JavaAccessFixture",
                 "declaredParent":"dev.openallay.script.fixture.JavaAccessFixture",
                 "nativeClass":"dev.openallay.script.fixture.JavaAccessFixture",
                 "arrayClass":"[I","staticValue":"public-static"}
                """, execute(PRELUDE + """
                return {
                  type: Java.inspect(Java.classOf(Fixture)).name,
                  instance: Java.inspect(Java.classOf(Fixture.create())).name,
                  declaredParent: Java.inspect(Java.classOf(Fixture.asParent())).name,
                  nativeClass: Java.inspect(Java.classOf(Fixture.nativeClass())).name,
                  arrayClass: Java.inspect(Java.classOf(Java.get(Fixture.create(), "numbers"))).name,
                  staticValue: Java.get(Fixture, "publicStatic")
                };
                """));
    }

    @Test
    void nativeInvocationHandlesCallerSensitiveClassForNameWithoutGuestLookup() {
        assertThrows(JavascriptExecutionException.class, () -> execute(PRELUDE
                + "return Java.type('java.lang.Class').forName('" + FIXTURE + "');"));
        assertEquals(FIXTURE, execute("""
                const Class = Java.type("java.lang.Class");
                const loaded = Java.invoke(Class, "forName", ["java.lang.String"],
                  ["dev.openallay.script.fixture.JavaAccessFixture"]);
                return Java.inspect(Java.classOf(loaded)).name;
                """).getAsString());
    }

    @Test
    void readsAndWritesPrivateProtectedPackageAndInheritedFields() {
        assertJson("""
                {"before":["player_value",2,3,"inherited"],
                 "after":["changed",5,6,"changed-parent"],"setType":"undefined"}
                """, execute(PRELUDE + """
                const value = Fixture.create();
                const before = [Java.get(value, "privateValue"), Java.get(value, "protectedValue"),
                  Java.get(value, "packageValue"), Java.get(value, "inherited")];
                const setType = typeof Java.set(value, "privateValue", "changed");
                Java.set(value, "protectedValue", 5);
                Java.set(value, "packageValue", 6);
                Java.set(value, "inherited", "changed-parent");
                return {before, after: [Java.get(value, "privateValue"), Java.get(value, "protectedValue"),
                  Java.get(value, "packageValue"), Java.get(value, "inherited")], setType};
                """));
    }

    @Test
    void nearestFieldAndDetachedDeclaringDescriptorDisambiguateShadowing() {
        assertJson("""
                {"nearest":"child-changed","parent":"parent-changed","owners":2}
                """, execute(PRELUDE + """
                const value = Fixture.create();
                const fields = Java.inspect(value).fields.filter(field => field.name === "hidden");
                const parent = fields.find(field => field.declaringClass ===
                  "dev.openallay.script.fixture.JavaAccessParent");
                Java.set(value, parent, "parent-changed");
                Java.set(value, "hidden", "child-changed");
                return {nearest: Java.get(value, "hidden"), parent: Java.get(value, parent), owners: fields.length};
                """));
    }

    @Test
    void staticFieldsAndFinalMetadataKeepStandardReflectionLimits() {
        assertJson("""
                {"before":"private-static","after":"modified-static","finalValue":7,"constant":"constant"}
                """, execute(PRELUDE + """
                const before = Java.get(Fixture, "privateStatic");
                Java.set(Fixture, "privateStatic", "modified-static");
                const after = Java.get(Fixture, "privateStatic");
                Java.set(Fixture, "privateStatic", before);
                return {before, after, finalValue: Java.get(Fixture.create(), "finalValue"),
                  constant: Java.get(Fixture, "STATIC_FINAL")};
                """));
        JsonObject metadata = execute(PRELUDE
                + "return Java.inspect(Fixture);").getAsJsonObject();
        assertTrue(findField(metadata, "finalValue", FIXTURE).get("final").getAsBoolean());
        JsonObject constant = findField(metadata, "STATIC_FINAL", FIXTURE);
        assertTrue(constant.get("static").getAsBoolean());
        assertTrue(constant.get("final").getAsBoolean());
        assertFailure("javascript_java_inaccessible", "STATIC_FINAL", PRELUDE
                + "Java.set(Fixture, 'STATIC_FINAL', 'not-written'); return 1;");
        assertEquals("constant", execute(PRELUDE
                + "return Java.get(Fixture, 'STATIC_FINAL');").getAsString());
    }

    @Test
    void instanceFinalWritesFollowTheJdkRatherThanAnExtraFacadePolicy() {
        assertJson("""
                {"before":7,"after":9}
                """, execute(PRELUDE + """
                const value = Fixture.create();
                const before = Java.get(value, "finalValue");
                Java.set(value, "finalValue", 9);
                return {before, after: Java.get(value, "finalValue")};
                """));
    }

    @Test
    void classOfPrimitiveAndArrayTargetsDescribeTheirActualType() {
        assertJson("""
                {"primitive":{"name":"int","primitive":true,"array":false,"componentType":null},
                 "array":{"name":"[I","primitive":false,"array":true,"componentType":"int"}}
                """, execute("""
                const primitive = Java.inspect(Java.classOf(Java.type("int")));
                const array = Java.inspect(Java.classOf(Java.type("int[]")));
                return {primitive: {name: primitive.name, primitive: primitive.primitive,
                  array: primitive.array, componentType: primitive.componentType},
                  array: {name: array.name, primitive: array.primitive,
                    array: array.array, componentType: array.componentType}};
                """));
    }

    @Test
    void exactPrivateConstructorsConvertPrimitiveClassStringArrayAndNullTypes() {
        assertJson("""
                ["empty","int:4","string:ok","string:null","strings:a|b"]
                """, execute(PRELUDE + """
                const Integer = Java.type("java.lang.Integer");
                const String = Java.type("java.lang.String");
                return [
                  Java.get(Java.construct(Fixture, [], []), "label"),
                  Java.get(Java.construct(Fixture, [Java.get(Integer, "TYPE")], [4]), "label"),
                  Java.get(Java.construct(Fixture, [String], ["ok"]), "label"),
                  Java.get(Java.construct(Fixture, ["java.lang.String"], [null]), "label"),
                  Java.get(Java.construct(Fixture, ["java.lang.String[]"], [["a", "b"]]), "label")
                ];
                """));
    }

    @Test
    void exactOverloadsAndInheritedPrivateMethodsDoNotGuessFromValues() {
        assertJson("""
                ["int:2","long:2","string:2","object:2","string:null","object:null",
                 "parent-method","default:ok","static:2","instance:2"]
                """, execute(PRELUDE + """
                const value = Fixture.create();
                return [
                  Java.invoke(value, "overload", ["int"], [2]),
                  Java.invoke(value, "overload", ["long"], [2]),
                  Java.invoke(value, "overload", [Java.type("java.lang.String")], ["2"]),
                  Java.invoke(value, "overload", ["java.lang.Object"], ["2"]),
                  Java.invoke(value, "overload", ["java.lang.String"], [null]),
                  Java.invoke(value, "overload", ["java.lang.Object"], [null]),
                  Java.invoke(value, "parentMethod", [], []),
                  Java.invoke(value, "defaultMethod", ["java.lang.String"], ["ok"]),
                  Java.invoke(Fixture, "staticMethod", ["int"], [2]),
                  Java.invoke(value, "instanceMethod", ["int"], [2])
                ];
                """));
    }

    @Test
    void primitiveArrayMultidimensionalAndExplicitVarargsSignaturesUseRhinoConversion() {
        assertJson("""
                {"sum":6,"matrix":10,"jvmArray":6,"join":"values:a|b","invert":false,"next":"b","half":1.5}
                """, execute(PRELUDE + """
                const value = Fixture.create();
                return {
                  sum: Java.invoke(value, "sum", ["int[]"], [[1, 2, 3]]),
                  matrix: Java.invoke(value, "matrix", ["int[][]"], [[[1, 2], [3, 4]]]),
                  jvmArray: Java.invoke(value, "sum", ["[I"], [[1, 2, 3]]),
                  join: Java.invoke(value, "join", ["java.lang.String", "java.lang.String[]"],
                    ["values:", ["a", "b"]]),
                  invert: Java.invoke(value, "invert", ["boolean"], [true]),
                  next: String(Java.invoke(value, "next", ["char"], ["a"])),
                  half: Java.invoke(value, "half", ["double"], [3])
                };
                """));
    }

    @Test
    void longPrecisionUsesAnExactJavaLongRatherThanAnUnsafeJavascriptNumber() {
        assertEquals("long:9007199254740993", execute(PRELUDE + """
                const longValue = Java.invoke(Java.type("java.lang.Long"), "valueOf",
                  ["java.lang.String"], ["9007199254740993"]);
                return Java.invoke(Fixture.create(), "overload", ["long"], [longValue]);
                """).getAsString());
    }

    @Test
    void decimalLongInputUsesTheJdkParserForTheFullLongRange() {
        assertEquals("long:9223372036854775807", execute(PRELUDE + """
                return Java.invoke(Fixture.create(), "overload", ["long"], ["9223372036854775807"]);
                """).getAsString());
        assertFailure("javascript_java_conversion_error", "NumberFormatException", PRELUDE + """
                return Java.invoke(Fixture.create(), "overload", ["long"], ["9223372036854775808"]);
                """);
    }

    @Test
    void nullAndVoidPreserveExistingJsonSemanticsWithoutValueScanning() {
        assertJson("""
                {"field":null,"result":null,"voidResult":null,"voidType":"undefined","value":"player_value",
                 "array":[null,"player_value"]}
                """, execute(PRELUDE + """
                const value = Fixture.create();
                Java.set(value, "nullable", null);
                const voidResult = Java.invoke(value, "update", ["java.lang.String"], ["player_value"]);
                return {field: Java.get(value, "nullable"),
                  result: Java.invoke(value, "identity", ["java.lang.Object"], [null]),
                  voidResult, voidType: typeof voidResult, value: Java.get(value, "privateValue"),
                  array: [Java.get(value, "nullable"), Java.get(value, "privateValue")]};
                """));
        assertEquals("javascript_result_invalid", assertThrows(JavascriptExecutionException.class,
                () -> execute(PRELUDE + "return Java.invoke(Fixture.create(), 'update', ['java.lang.String'], ['x']);"))
                .code(), "Returning undefined at the top level remains a normalizer error");
    }

    @Test
    void memberResolutionUsesTheActualIsolatedTargetLoaderForClassAndArraySignatures() {
        assertJson("""
                {"name":"dev.openallay.script.fixture.LoaderFixture","same":"same-loader","count":2,"classMatches":true,"loaderDiffers":true}
                """, execute(PRELUDE + """
                const value = Fixture.createIsolated();
                const actual = Java.inspect(Java.classOf(value));
                const application = Java.inspect(Java.type("dev.openallay.script.fixture.LoaderFixture"));
                return {name: actual.name,
                  same: Java.invoke(value, "identify", ["dev.openallay.script.fixture.LoaderFixture"], [value]),
                  count: Java.invoke(value, "count", ["dev.openallay.script.fixture.LoaderFixture[]"], [[value, value]]),
                  classMatches: Java.invoke(value, "identifyClass", ["java.lang.Class"],
                    ["dev.openallay.script.fixture.LoaderFixture"]),
                  loaderDiffers: actual.classLoader.identity !== application.classLoader.identity};
                """));
    }

    @Test
    void inheritedSignaturesUseTheirDeclaringLoaderDespiteSameNameChildParameters() {
        assertJson("""
                {"plain":"parent-parameter","selected":"parent-parameter","classMatches":true,"differentIdentity":true}
                """, execute(PRELUDE + """
                const value = Fixture.createShadowHierarchy();
                const application = Fixture.applicationParameter();
                const shadow = value.shadowParameter();
                const method = Java.inspect(value).methods.find(member => member.name === "shadow"
                  && member.declaringClass === "dev.openallay.script.fixture.ShadowParentFixture");
                const classMethod = Java.inspect(value).methods.find(member => member.name === "parameterClass"
                  && member.declaringClass === "dev.openallay.script.fixture.ShadowParentFixture");
                return {
                  plain: Java.invoke(value, "shadow", [Java.classOf(application)], [application]),
                  selected: Java.invoke(value, method, ["dev.openallay.script.fixture.ShadowParameter"], [application]),
                  classMatches: Java.invoke(value, classMethod, ["java.lang.Class"],
                    ["dev.openallay.script.fixture.ShadowParameter"]),
                  differentIdentity: Java.inspect(Java.classOf(application)).classLoader.identity !==
                    Java.inspect(Java.classOf(shadow)).classLoader.identity
                };
                """));
        assertFailure("javascript_java_conversion_error", "ShadowParameter", PRELUDE + """
                const value = Fixture.createShadowHierarchy();
                const method = Java.inspect(value).methods.find(member => member.name === "shadow"
                  && member.declaringClass === "dev.openallay.script.fixture.ShadowParentFixture");
                return Java.invoke(value, method, ["dev.openallay.script.fixture.ShadowParameter"],
                  [value.shadowParameter()]);
                """);
    }

    @Test
    void failedClassInitializationKeepsTheActualTargetFailure() {
        JavascriptExecutionException failure = assertFailure("javascript_java_target_error",
                "fixture initialization failed: player_value", PRELUDE + """
                const type = Fixture.failingInitializerClass();
                return Java.construct(type, [], []);
                """);
        assertTrue(failure.getMessage().contains("IllegalStateException"), failure.getMessage());
        assertFalse(failure.getMessage().contains("ClassNotFoundException"), failure.getMessage());
        ExceptionInInitializerError original = assertInstanceOf(ExceptionInInitializerError.class, failure.getCause());
        assertEquals(IllegalStateException.class, original.getCause().getClass());
        assertEquals("fixture initialization failed: player_value", original.getCause().getMessage());
    }

    @Test
    void recordFinalFieldWritesFollowTheJdkAccessFailure() {
        assertFailure("javascript_java_inaccessible", "value", """
                const Type = Java.type("dev.openallay.script.fixture.JavaAccessFixture$FinalRecord");
                const value = Java.construct(Type, ["java.lang.String"], ["original"]);
                Java.set(value, "value", "changed");
                return 1;
                """);
    }

    @Test
    void methodDescriptorsSelectBridgeReturnSignaturesWithoutDuplicateMetadata() {
        JsonObject result = execute("""
                const Type = Java.type("dev.openallay.script.fixture.JavaAccessFixture$StringValue");
                const value = Java.construct(Type, [], []);
                const methods = Java.inspect(value).methods.filter(method => method.name === "item"
                  && method.declaringClass === "dev.openallay.script.fixture.JavaAccessFixture$StringValue");
                const exact = methods.find(method => method.returnType === "java.lang.String");
                const bridge = methods.find(method => method.returnType === "java.lang.Object");
                return {count: methods.length, exactBridge: exact.bridge, bridge: bridge.bridge,
                  exact: Java.invoke(value, exact, [], []), bridged: Java.invoke(value, bridge, [], [])};
                """).getAsJsonObject();
        assertJson("""
                {"count":2,"exactBridge":false,"bridge":true,"exact":"bridge-value","bridged":"bridge-value"}
                """, result);
    }

    @Test
    void inspectReturnsCompleteDetachedMetadataWithoutReadingPrivateValues() {
        JsonObject metadata = execute(PRELUDE + "return Java.inspect(Fixture.create());").getAsJsonObject();
        assertEquals(Set.of("name", "typeName", "superclass", "interfaces", "primitive", "array",
                "componentType", "modifiers", "modifierBits", "module", "classLoader", "fields", "methods",
                "constructors"), metadata.keySet());
        assertEquals(FIXTURE, metadata.get("name").getAsString());
        assertEquals(PARENT, metadata.get("superclass").getAsString());
        assertFalse(metadata.get("primitive").getAsBoolean());
        assertFalse(metadata.get("array").getAsBoolean());
        assertTrue(metadata.get("componentType").isJsonNull());
        assertEquals(Set.of("name", "named", "automatic", "packageName", "packageOpenToBridge"),
                metadata.getAsJsonObject("module").keySet());
        assertEquals(Set.of("name", "type", "identity"), metadata.getAsJsonObject("classLoader").keySet());
        assertEquals("dev.openallay.script.fixture", metadata.getAsJsonObject("module").get("packageName").getAsString());
        Set<String> fieldSignatures = new HashSet<>();
        for (JsonElement entry : metadata.getAsJsonArray("fields")) {
            JsonObject field = entry.getAsJsonObject();
            assertEquals(Set.of("name", "declaringClass", "type", "modifiers", "modifierBits", "static", "final", "synthetic"),
                    field.keySet());
            assertTrue(fieldSignatures.add(field.get("declaringClass") + ":" + field.get("name")), "Duplicate field descriptor");
        }
        assertEquals("private", findField(metadata, "privateValue", FIXTURE).get("modifiers").getAsString());
        Set<String> signatures = new HashSet<>();
        for (JsonElement entry : metadata.getAsJsonArray("methods")) {
            JsonObject method = entry.getAsJsonObject();
            assertEquals(Set.of("name", "declaringClass", "parameterTypes", "returnType", "modifiers", "modifierBits",
                    "static", "varArgs", "bridge", "synthetic"), method.keySet());
            assertTrue(signatures.add(method.get("declaringClass") + ":" + method.get("name")
                    + method.get("parameterTypes") + ":" + method.get("returnType")), "Duplicate method signature");
        }
        signatures.clear();
        for (JsonElement entry : metadata.getAsJsonArray("constructors")) {
            JsonObject constructor = entry.getAsJsonObject();
            assertEquals(Set.of("declaringClass", "parameterTypes", "modifiers", "modifierBits", "varArgs", "synthetic"),
                    constructor.keySet());
            assertEquals(FIXTURE, constructor.get("declaringClass").getAsString());
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.get("modifierBits").getAsInt()));
            assertTrue(signatures.add(constructor.get("parameterTypes").toString()), "Duplicate constructor signature");
        }
        assertEquals(4, metadata.getAsJsonArray("constructors").size());
        assertFalse(metadata.toString().contains("player_value"), "Inspect must not automatically read private field values");
        assertEquals("player_value", execute(PRELUDE
                + "return Java.get(Fixture.create(), 'privateValue');").getAsString());
        assertEquals("dev.openallay.script.fixture.JavaAccessFixture$MetadataOnly", execute("""
                const Type = Java.type("dev.openallay.script.fixture.JavaAccessFixture$MetadataOnly");
                return Java.inspect(Type.create()).name;
                """).getAsString(), "Metadata must not stringify a private field value");
        assertJson("""
                {"memberClass":"undefined","constructorClass":"undefined","fieldValue":"undefined"}
                """, execute(PRELUDE + """
                const info = Java.inspect(Fixture);
                return {memberClass: typeof info.methods[0].getClass,
                  constructorClass: typeof info.constructors[0].getClass,
                  fieldValue: typeof info.fields.find(field => field.name === "privateValue").value};
                """));
    }

    @Test
    void inspectOfNativeClassDoesNotInitializeItWhileJavaTypeKeepsItsExistingInitialization() {
        JavaAccessFixture.InitializationProbe.count = 0;
        assertJson("""
                {"before":0,"inspected":0,"afterType":1,"name":"dev.openallay.script.fixture.JavaAccessFixture$InspectionOnly"}
                """, execute(PRELUDE + """
                const Probe = Java.type("dev.openallay.script.fixture.JavaAccessFixture$InitializationProbe");
                const before = Java.get(Probe, "count");
                const info = Java.inspect(Fixture.inspectionOnlyClass());
                const inspected = Java.get(Probe, "count");
                Java.type("dev.openallay.script.fixture.JavaAccessFixture$InspectionOnly");
                return {before, inspected, afterType: Java.get(Probe, "count"), name: info.name};
                """));
    }

    @Test
    void closedJdkModuleIsReportedHonestlyWithoutClaimingMissingClassOrMember() {
        JsonObject info = execute("return Java.inspect(Java.type('java.lang.String'));").getAsJsonObject();
        JsonObject module = info.getAsJsonObject("module");
        assertEquals("java.base", module.get("name").getAsString());
        assertTrue(module.get("named").getAsBoolean());
        assertFalse(module.get("automatic").getAsBoolean());
        assertEquals("java.lang", module.get("packageName").getAsString());
        assertFalse(module.get("packageOpenToBridge").getAsBoolean());
        assertTrue(info.get("classLoader").isJsonNull());
        assertTrue(info.getAsJsonArray("fields").asList().stream()
                .anyMatch(field -> field.getAsJsonObject().get("name").getAsString().equals("value")));
        assertFailure("javascript_java_inaccessible", "java.lang.String", "return Java.get('text', 'value');");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Java.get(Fixture.create(), 'notAField')",
        "Java.set(Fixture.create(), 'notAField', 1)",
        "Java.invoke(Fixture.create(), 'notAMethod', [], [])",
        "Java.invoke(Fixture.create(), 'overload', ['double'], [2])",
        "Java.construct(Fixture, ['double'], [2])"
    })
    void missingMembersAndExactOverloadsHaveAUsefulMemberCategory(String expression) {
        assertFailure("javascript_java_member_unavailable", "JavaAccessFixture", PRELUDE + "return " + expression + ";");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Java.get(Fixture, 'privateValue')",
        "Java.set(Fixture, 'privateValue', 'x')",
        "Java.invoke(Fixture, 'instanceMethod', ['int'], [2])",
        "Java.classOf(null)",
        "Java.classOf(undefined)",
        "Java.get(null, 'field')",
        "Java.inspect(undefined)",
        "Java.inspect({})",
        "Java.classOf([])",
        "Java.classOf(mc)",
        "Java.invoke(Fixture.create(), 'overload', ['int'], [])",
        "Java.invoke(Fixture.create(), 'overload', ['int'], [1, 2])",
        "Java.construct(Fixture, ['int'], [])",
        "Java.invoke(Fixture.create(), 'overload', 'int', [2])",
        "Java.invoke(Fixture.create(), 'overload', ['int'], 2)",
        "Java.invoke(Fixture.create(), {name:'overload',declaringClass:'dev.openallay.script.fixture.JavaAccessFixture',parameterTypes:['int']}, ['long'], [2])"
    })
    void invalidTargetsArgumentListsAndCountsDoNotBecomeMissingMembers(String expression) {
        assertFailure("javascript_java_invalid", "", PRELUDE + "return " + expression + ";");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Java.invoke(Fixture.create(), 'instanceMethod', ['int'], ['not-an-integer'])",
        "Java.invoke(Fixture.create(), 'instanceMethod', ['int'], [null])",
        "Java.construct(Fixture, ['int'], [null])",
        "Java.set(Fixture.create(), 'protectedValue', null)",
        "Java.set(Fixture.create(), 'protectedValue', 'not-an-integer')"
    })
    void incompatibleValuesIncludingNullForPrimitivesHaveAConversionCategory(String expression) {
        assertFailure("javascript_java_conversion_error", "", PRELUDE + "return " + expression + ";");
    }

    @Test
    void closedHostViewsAreNotJavaTargetsEvenInUnrestrictedExecution() {
        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                () -> runtime.execute("return Java.inspect(mc.player);",
                        Map.of("player", Map.of("name", "Synthetic player", "position", Map.of("x", 1))),
                        Map.of(), Map.of(), new CancellationSignal(), null, null, true));
        assertEquals("javascript_java_invalid", failure.code());
    }

    @Test
    void unavailableParameterClassIsNotConfusedWithAMissingOverload() {
        assertFailure("javascript_class_unavailable", "example.no_such_class", PRELUDE
                + "return Java.invoke(Fixture.create(), 'overload', ['example.no_such_class'], [null]);");
    }

    @Test
    void targetExceptionKeepsTheActualMethodFailureNotInvocationTargetException() {
        JavascriptExecutionException failure = assertFailure("javascript_java_target_error", "fixture method failed: player_value",
                PRELUDE + "return Java.invoke(Fixture.create(), 'explode', [], []);");
        assertTrue(failure.getMessage().contains("IllegalStateException"), failure.getMessage());
        assertFalse(failure.getMessage().contains("InvocationTargetException"), failure.getMessage());
        assertFalse(failure.getMessage().contains(".java:"), failure.getMessage());
        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals("fixture method failed: player_value", failure.getCause().getMessage());
    }

    @Test
    void constructorTargetExceptionKeepsItsActualFailure() {
        JavascriptExecutionException failure = assertFailure("javascript_java_target_error", "fixture constructor failed: player_value",
                """
                return Java.construct(Java.type("dev.openallay.script.fixture.JavaAccessFixture$FailingConstructor"),
                  ["java.lang.String"], ["player_value"]);
                """);
        assertTrue(failure.getMessage().contains("IllegalArgumentException"), failure.getMessage());
        assertFalse(failure.getMessage().contains("InvocationTargetException"), failure.getMessage());
        assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
    }

    @Test
    void targetCancellationRetainsItsControlCategory() {
        ModelClientException failure = assertThrows(ModelClientException.class,
                () -> execute(PRELUDE + "return Java.invoke(Fixture.create(), 'cancelTarget', [], []);"));
        assertEquals("agent_cancelled", failure.failure().code());
    }

    @Test
    void cancellationDuringNativeInvocationIsObservedAfterItsReturn() throws Exception {
        JavaAccessFixture.Blocking.entered = new CountDownLatch(1);
        JavaAccessFixture.Blocking.release = new CountDownLatch(1);
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<JsonElement> work = CompletableFuture.supplyAsync(() -> execute("""
                const Blocking = Java.type("dev.openallay.script.fixture.JavaAccessFixture$Blocking");
                return Java.invoke(Blocking, "waitForRelease", [], []);
                """, cancellation));
        try {
            assertTrue(JavaAccessFixture.Blocking.entered.await(5, TimeUnit.SECONDS));
            cancellation.cancel();
            JavaAccessFixture.Blocking.release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> work.get(5, TimeUnit.SECONDS));
            assertEquals("agent_cancelled", assertInstanceOf(ModelClientException.class, failure.getCause()).failure().code());
        } finally {
            JavaAccessFixture.Blocking.release.countDown();
        }
    }

    @Test
    void safeUnrestrictedSafeOrderDoesNotExposePrivateMembersThroughSharedWrappers() {
        String safeSource = "return {java: typeof Java, packages: typeof Packages, "
                + "getClass: typeof mc.fixture.getClass, privateValue: typeof mc.fixture.privateValue};";
        JsonElement expected = JsonParser.parseString("""
                {"java":"undefined","packages":"undefined","getClass":"undefined","privateValue":"undefined"}
                """);
        for (int pass = 0; pass < 2; pass++) {
            assertEquals(expected, runtime.execute(safeSource, Map.of("fixture", Map.of("visible", "ok")),
                    Map.of(), new CancellationSignal()).value());
            assertEquals("player_value", execute(PRELUDE
                    + "return Java.get(Fixture.create(), 'privateValue');").getAsString());
            assertThrows(JavascriptExecutionException.class,
                    () -> execute(PRELUDE + "return Fixture.create().privateValue;"),
                    "The facade must not make Rhino's shared public wrapper cache private-accessible");
            assertEquals(expected, runtime.execute(safeSource, Map.of("fixture", Map.of("visible", "ok")),
                    Map.of(), new CancellationSignal()).value());
        }
    }

    @Test
    void scopedExtensionHostModeStaysTypedAndWithoutJavaAfterUnrestrictedExecution() {
        execute(PRELUDE + "return Java.get(Fixture.create(), 'privateValue');");
        JavascriptModuleCatalog modules = new JavascriptModuleCatalog(Map.of());
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.2"),
                new JavascriptDataModuleRegistry(), modules, new SkillRepository(new SkillParser(), List.of()), Set.of());
        JavascriptHostMethod echo = new JavascriptHostMethod("echo", List.of(JavascriptHostValueType.STRING),
                JavascriptHostValueType.STRING, (context, arguments) -> arguments.getFirst());
        assertEquals(OpenAllayExtensionState.ACTIVE, registry.register(new OpenAllayExtension() {
            @Override public OpenAllayExtensionDescriptor descriptor() {
                return new OpenAllayExtensionDescriptor("test:java_access", "Test", "1.0.0", "Test", "Typed host test",
                        Set.of("fabric"), "[26.2,26.3)", "[0.2.2,0.3)", "test");
            }
            @Override public OpenAllayExtensionContribution contribution() {
                return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new JavascriptHostBinding("test_java_access:methods", List.of(echo))));
            }
        }).state());
        CancellationSignal cancellation = new CancellationSignal();
        try (JavascriptInvocationScope scope = registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("java-access-host"), cancellation)) {
            scope.open(ignored -> {});
            JsonElement result = runtime.execute("""
                    const host = require("test_java_access:methods");
                    return {java: typeof Java, getClass: typeof host.getClass, value: host.echo("player_value")};
                    """, Map.of(), Map.of(), Map.of(), Map.of(), ignored -> {}, cancellation, null, null, false, scope).value();
            assertJson("""
                    {"java":"undefined","getClass":"undefined","value":"player_value"}
                    """, result);
            JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class,
                    () -> runtime.execute("return require('test_java_access:methods').echo(2);",
                            Map.of(), Map.of(), Map.of(), Map.of(), ignored -> {}, cancellation, null, null, false, scope));
            assertEquals("javascript_extension_host_invalid", failure.code());
        }
        assertEquals(0, registry.activeJavascriptInvocations());
    }

    private JsonElement execute(String source) {
        return execute(source, new CancellationSignal());
    }

    private JsonElement execute(String source, CancellationSignal cancellation) {
        return runtime.execute(source, Map.of(), Map.of(), Map.of(), cancellation, null, null, true).value();
    }

    private JavascriptExecutionException assertFailure(String code, String detail, String source) {
        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class, () -> execute(source));
        assertEquals(code, failure.code(), failure.getMessage());
        assertFalse(failure.getMessage().isBlank());
        assertTrue(failure.getMessage().contains(detail), failure.getMessage());
        assertFalse(failure.getMessage().contains(".java:"), failure.getMessage());
        return failure;
    }

    private static JsonObject findField(JsonObject metadata, String name, String declaringClass) {
        return metadata.getAsJsonArray("fields").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(field -> field.get("name").getAsString().equals(name)
                        && field.get("declaringClass").getAsString().equals(declaringClass))
                .findFirst().orElseThrow();
    }

    private static void assertJson(String expected, JsonElement actual) {
        assertEquals(JsonParser.parseString(expected), actual);
    }
}
