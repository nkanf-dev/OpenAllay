package dev.openallay.runtime.forge1122.dimension;
import java.lang.invoke.MethodHandle;import java.lang.invoke.MethodHandles;import java.lang.invoke.MethodType;import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;import java.lang.reflect.Field;import java.lang.reflect.Modifier;
import java.util.Arrays;import java.util.Map;
import java.nio.file.Files;import java.nio.file.Paths;import java.nio.charset.StandardCharsets;import java.nio.file.StandardOpenOption;

/** Real enum constructor/values/cache operations, scoped to authenticated DimensionType. */
public final class DimensionEnumRuntime {
    private static final String OWNER="net.minecraft.world.DimensionType";
    private static final Class<?>[] ARGS={int.class,String.class,String.class,Class.class};
    private static final VarHandle ENUM_CONSTANTS,ENUM_DIRECTORY;
    static {
        try {
            MethodHandles.Lookup classLookup=MethodHandles.privateLookupIn(Class.class,MethodHandles.lookup());
            ENUM_CONSTANTS=classLookup.findVarHandle(Class.class,"enumConstants",Object[].class);
            ENUM_DIRECTORY=classLookup.findVarHandle(Class.class,"enumConstantDirectory",Map.class);
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    private static MethodHandles.Lookup checked(Class<?> type) throws Exception {
        if(!OWNER.equals(type.getName())||!type.isEnum()||type.getClassLoader()==null
                ||!"net.minecraft.launchwrapper.LaunchClassLoader".equals(type.getClassLoader().getClass().getName()))
            throw new IllegalArgumentException("Only actual stock DimensionType enum allowed");
        java.net.URL source=type.getProtectionDomain().getCodeSource().getLocation();
        if("jar".equals(source.getProtocol()))source=((java.net.JarURLConnection)source.openConnection()).getJarFileURL();
        if(!new java.io.File(source.toURI()).getCanonicalFile().equals(new java.io.File(System.getProperty("openallay.objectholder.client")).getCanonicalFile()))
            throw new IllegalArgumentException("Exact client enum source required");
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup());
    }
    private static MethodHandle constructor(Class<?> type,Class<?>[] args)throws Exception {
        if(!Arrays.equals(ARGS,args))throw new NoSuchMethodException("Exact DimensionType args int,String,String,Class required");
        Class<?>[] all={String.class,int.class,int.class,String.class,String.class,Class.class};
        return checked(type).findConstructor(type,MethodType.methodType(void.class,all));
    }
    private static VarHandle values(Class<?> type)throws Exception {
        String exact=System.getProperty("openallay.dimension.valuesField");
        Field field=type.getDeclaredField(exact);
        if(field.getType()!=Array.newInstance(type,0).getClass()||!field.isSynthetic()||!Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers()))
            throw new IllegalStateException("Exact admitted synthetic enum values array required");
        return checked(type).findStaticVarHandle(type,exact,field.getType());
    }
    private static void clear(Class<?> type){ENUM_CONSTANTS.setVolatile(type,null);ENUM_DIRECTORY.setVolatile(type,null);}
    public static void testEnum(Class<?> type,Class<?>[] args) throws Exception { constructor(type,args);values(type); }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static synchronized Enum<?> addEnum(Class<?> type,String name,Class<?>[] args,Object... additional) {
        Object previous=null;VarHandle field=null;
        try {
            MethodHandle ctor=constructor(type,args);field=values(type);previous=field.get();
            if(additional==null||additional.length!=4)throw new IllegalArgumentException("Exact real DimensionType constructor argument count required");
            int length=Array.getLength(previous);
            Object[] call={name,length,additional[0],additional[1],additional[2],additional[3]};
            Enum<?> created=(Enum<?>)ctor.invokeWithArguments(call);
            Object replacement=Array.newInstance(type,length+1);System.arraycopy(previous,0,replacement,0,length);Array.set(replacement,length,created);
            field.setVolatile(replacement);clear(type);
            if(type.getEnumConstants().length!=length+1||Enum.valueOf((Class)type,name)!=created)throw new IllegalStateException("Actual enum values/valueOf diverged");
            return created;
        }catch(Throwable error){
            if(previous!=null&&field!=null){try{field.setVolatile(previous);clear(type);}catch(Throwable restore){error.addSuppressed(restore);throw new IllegalStateException("Fatal real enum restore failed",error);}}
            throw new IllegalStateException("Error adding actual DimensionType enum",error);
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static synchronized void fixture(Class<?> type)throws Exception {
        VarHandle field=values(type);Object previous=field.get();Enum<?>[] before=(Enum<?>[])type.getEnumConstants();
        try {
            for(Enum<?> value:before)if(Enum.valueOf((Class)type,value.name())!=value)throw new AssertionError("Existing enum lookup identity changed");
            testEnum(type,ARGS);
            try{testEnum(type,new Class<?>[]{String.class});throw new AssertionError("Missing constructor test accepted");}catch(NoSuchMethodException expected){}
            Class<?> provider=Class.forName("net.minecraft.world.WorldProviderSurface",false,type.getClassLoader());
            Enum<?> added=addEnum(type,"openallay_dimension_fixture",ARGS,new Object[]{1876501,"openallay_dimension_fixture","_openallay",provider});
            if(type.getEnumConstants().length!=before.length+1||added.ordinal()!=before.length||Enum.valueOf((Class)type,added.name())!=added)throw new AssertionError("Real added enum lookup/ordinal wrong");
            for(Enum<?> value:before)if(Enum.valueOf((Class)type,value.name())!=value)throw new AssertionError("Existing enum lookup altered");
        }finally{field.setVolatile(previous);clear(type);
            if(type.getEnumConstants().length!=before.length)throw new IllegalStateException("Fatal fixture enum restore failed");
            for(Enum<?> value:before)if(Enum.valueOf((Class)type,value.name())!=value)throw new IllegalStateException("Fatal fixture lookup restore failed");}
        Files.write(Paths.get(System.getProperty("openallay.dimension.fixtureReceipt")),
            "{\"actualConstructorTest\":true,\"missingConstructorRejected\":true,\"realEnumAdded\":true,\"valuesValueOfIdentity\":true,\"existingLookupsPreserved\":true,\"restoredFinally\":true}\n".getBytes(StandardCharsets.UTF_8));
    }
}
