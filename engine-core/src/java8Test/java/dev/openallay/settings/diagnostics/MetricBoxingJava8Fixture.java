package dev.openallay.settings.diagnostics;

import dev.openallay.value.RecordMetadata;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Actual whole diagnostic-card owner, original records vs repaired-converter Java8 values. */
public final class MetricBoxingJava8Fixture {
    private static final List<String> VECTORS = new ArrayList<String>();
    private static void check(boolean pass, String name) { if (!pass) throw new AssertionError(name); VECTORS.add(name+"=true"); }
    private interface Action { void run() throws Exception; }
    private static void fails(Class<? extends Throwable> type, String name, Action action) {
        try {action.run();}catch(Throwable e){if(!type.isInstance(e))throw new AssertionError(name,e);VECTORS.add(name+"="+e.getClass().getName());return;}throw new AssertionError(name);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void value(Object instance, String... expected) throws Exception {
        Class<?> owner=instance.getClass();String name=owner.getSimpleName();ArrayList<String> names=new ArrayList<String>(),types=new ArrayList<String>();ArrayList<Object> args=new ArrayList<Object>();Object rebuilt;
        if(ValueSchemas.supports(owner)) {
            ValueSchema schema=ValueSchemas.of(owner);
            for(Object raw:schema.components()){ValueSchema.Component c=(ValueSchema.Component)raw;names.add(c.name());types.add(c.genericType().getTypeName());args.add(c.read(instance));}
            rebuilt=schema.construct(args.toArray());
            check(Modifier.isPublic(owner.getAnnotation(dev.openallay.value.ValueType.class).value().getConstructor().getModifiers()),name+".providerPublic");
        } else {
            List<RecordMetadata.Component> components=RecordMetadata.components(owner);Class<?>[] raw=new Class<?>[components.size()];
            for(int i=0;i<components.size();i++){RecordMetadata.Component c=components.get(i);names.add(c.name());types.add(c.genericType().getTypeName());args.add(c.accessorMetadata().invoke(instance));raw[i]=c.rawType();}
            Constructor<?> ctor=owner.getConstructor(raw);rebuilt=ctor.newInstance(args.toArray());check(Modifier.isPublic(ctor.getModifiers()),name+".providerPublic");
        }
        check(names.equals(Arrays.asList(expected)),name+".componentOrder");VECTORS.add(name+".genericTypes="+types);
        check(instance.equals(rebuilt)&&instance.hashCode()==rebuilt.hashCode(),name+".reconstruction");
        check(Modifier.isPublic(owner.getModifiers())&&Modifier.isFinal(owner.getModifiers()),name+".publicFinalABI");
        int hash=0;StringBuilder text=new StringBuilder(name).append('[');
        for(int i=0;i<expected.length;i++) {Field field=owner.getDeclaredField(expected[i]);check(Modifier.isPrivate(field.getModifiers())&&Modifier.isFinal(field.getModifiers()),name+".privateFinal."+expected[i]);field.setAccessible(true);Object part=field.get(instance);hash=31*hash+(part==null?0:part.hashCode());if(i>0)text.append(", ");text.append(expected[i]).append('=').append(part);}
        check(instance.hashCode()==hash,name+".rawFieldZero31Hash");check(instance.toString().equals(text.append(']').toString()),name+".rawFieldToString");
        check(instance.equals(instance)&&!instance.equals(null)&&!instance.equals(new Object()),name+".equalityBoundary");
    }
    public static String[] vectors() throws Exception {
        VECTORS.clear();
        SettingsDiagnosticCard.Metric primitive=new SettingsDiagnosticCard.Metric("metric.count",7L);
        SettingsDiagnosticCard.Metric boxed=new SettingsDiagnosticCard.Metric("metric.count",Long.valueOf(7L));
        SettingsDiagnosticCard.Metric unknown=new SettingsDiagnosticCard.Metric("metric.count",(Long)null);
        SettingsDiagnosticCard.Metric zero=new SettingsDiagnosticCard.Metric("metric.count",0L);
        value(primitive,"labelKey","value");check(primitive.equals(boxed)&&primitive.hashCode()==boxed.hashCode(),"Metric.boxingOverloadEquality");
        check(unknown.value()==null&&!unknown.equals(zero),"Metric.unknownDistinctFromKnownZero");value(unknown,"labelKey","value");
        check(SettingsDiagnosticCard.Metric.class.getConstructor(String.class,long.class).getParameterTypes()[1]==long.class,"Metric.primitiveConstructorABI");
        check(SettingsDiagnosticCard.Metric.class.getConstructor(String.class,Long.class).getParameterTypes()[1]==Long.class,"Metric.boxedCanonicalABI");
        check(SettingsDiagnosticCard.Metric.class.getMethod("value").getReturnType()==Long.class,"Metric.boxedAccessorABI");
        check(primitive.hashCode()==31*"metric.count".hashCode()+Long.hashCode(7L),"Metric.exactDeterministicHash");
        fails(IllegalArgumentException.class,"Metric.primitiveNegative",()->new SettingsDiagnosticCard.Metric("metric.count",-1L));
        fails(IllegalArgumentException.class,"Metric.boxedNegative",()->new SettingsDiagnosticCard.Metric("metric.count",Long.valueOf(-1L)));
        fails(IllegalArgumentException.class,"Metric.labelBeforeValue",()->new SettingsDiagnosticCard.Metric(null,Long.valueOf(-1L)));
        fails(IllegalArgumentException.class,"Metric.invalidLabel",()->new SettingsDiagnosticCard.Metric("../bad",1L));
        ArrayList<String> notes=new ArrayList<String>();notes.add("note.ready");ArrayList<SettingsDiagnosticCard.Metric> metrics=new ArrayList<SettingsDiagnosticCard.Metric>();metrics.add(primitive);
        SettingsDiagnosticCard card=new SettingsDiagnosticCard(SettingsDiagnosticCard.Domain.MODELS,SettingsDiagnosticCard.FriendlyStatus.READY,"title.models","status.ready",notes,metrics);
        value(card,"domain","friendlyStatus","titleKey","statusKey","noteKeys","metrics");notes.clear();metrics.clear();check(card.noteKeys().equals(Collections.singletonList("note.ready"))&&card.metrics().get(0)==primitive,"Card.defensiveSnapshotsPreserveMetricIdentity");
        fails(UnsupportedOperationException.class,"Card.notesImmutable",()->card.noteKeys().clear());fails(UnsupportedOperationException.class,"Card.metricsImmutable",()->card.metrics().clear());
        fails(NullPointerException.class,"Card.domainFirst",()->new SettingsDiagnosticCard(null,null,null,null,null,null));
        fails(IllegalArgumentException.class,"Card.titleValidation",()->new SettingsDiagnosticCard(SettingsDiagnosticCard.Domain.MODELS,SettingsDiagnosticCard.FriendlyStatus.READY,"../bad","status.ready",Collections.emptyList(),Collections.emptyList()));
        fails(IllegalArgumentException.class,"Card.noteValidation",()->new SettingsDiagnosticCard(SettingsDiagnosticCard.Domain.MODELS,SettingsDiagnosticCard.FriendlyStatus.READY,"title.models","status.ready",Arrays.asList("bad/key"),Collections.emptyList()));
        fails(NullPointerException.class,"Card.nullMetric",()->new SettingsDiagnosticCard(SettingsDiagnosticCard.Domain.MODELS,SettingsDiagnosticCard.FriendlyStatus.READY,"title.models","status.ready",Collections.emptyList(),Arrays.asList((SettingsDiagnosticCard.Metric)null)));
        VECTORS.add("Card.Domain="+Arrays.toString(SettingsDiagnosticCard.Domain.values()));VECTORS.add("Card.Status="+Arrays.toString(SettingsDiagnosticCard.FriendlyStatus.values()));
        return VECTORS.toArray(new String[0]);
    }
    public static void main(String[] args) throws Exception {for(String value:vectors())System.out.println(value);}
}
