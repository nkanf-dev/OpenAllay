package dev.openallay.runtime.forge1122.pack200;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

/** Real injection for exactly five native NONFINAL @CapabilityInject fields. */
public final class CapabilityRuntime {
    private static final String[][] FIELDS={
        {"net.minecraftforge.items.CapabilityItemHandler","ITEM_HANDLER_CAPABILITY","net.minecraftforge.items.IItemHandler"},
        {"net.minecraftforge.fluids.capability.CapabilityFluidHandler","FLUID_HANDLER_CAPABILITY","net.minecraftforge.fluids.capability.IFluidHandler"},
        {"net.minecraftforge.fluids.capability.CapabilityFluidHandler","FLUID_HANDLER_ITEM_CAPABILITY","net.minecraftforge.fluids.capability.IFluidHandlerItem"},
        {"net.minecraftforge.common.model.animation.CapabilityAnimation","ANIMATION_CAPABILITY","net.minecraftforge.common.model.animation.IAnimationStateMachine"},
        {"net.minecraftforge.energy.CapabilityEnergy","ENERGY","net.minecraftforge.energy.IEnergyStorage"}
    };
    public static void setField(Field field,Object target,Object value) throws Exception {
        String expected=null;
        for(String[] row:FIELDS)if(row[0].equals(field.getDeclaringClass().getName())&&row[1].equals(field.getName()))expected=row[2];
        if(expected==null||field.getModifiers()!=(Modifier.PUBLIC|Modifier.STATIC)||field.getType()!=Capability.class
                ||target!=null||!(value instanceof Capability))throw new IllegalArgumentException("Foreign or invalid capability field");
        java.net.URL location=field.getDeclaringClass().getProtectionDomain().getCodeSource().getLocation();
        if("jar".equals(location.getProtocol()))location=((java.net.JarURLConnection)location.openConnection()).getJarFileURL();
        if(!new File(location.toURI()).getCanonicalFile().equals(new File(System.getProperty("openallay.pack200.forge")).getCanonicalFile()))
            throw new IllegalArgumentException("Capability owner must be exact official Forge source");
        if(!"net.minecraft.launchwrapper.LaunchClassLoader".equals(field.getDeclaringClass().getClassLoader().getClass().getName()))
            throw new IllegalArgumentException("Capability owner must use stock loader");
        CapabilityInject annotation=field.getAnnotation(CapabilityInject.class);
        if(annotation==null||!expected.equals(annotation.value().getName())||!expected.equals(((Capability<?>)value).getName()))
            throw new IllegalArgumentException("Actual CapabilityInject identity/value differs");
        field.set(null,value);
        if(field.get(null)!=value)throw new IllegalStateException("Actual capability reference write failed");
        String receipt=field.getDeclaringClass().getName()+"\t"+field.getName()+"\t"+expected+"\tmodifiers="+field.getModifiers()+"\treadBackIdentity=true\n";
        Files.write(Paths.get(System.getProperty("openallay.capability.writes")),receipt.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static Object FOREIGN;
    public static void main(String[] args) throws Exception {
        try{setField(CapabilityRuntime.class.getDeclaredField("FOREIGN"),null,new Object());throw new AssertionError("Foreign field accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("PASS foreign field/value reject; exact five native source fields remain NONFINAL");
    }
}
