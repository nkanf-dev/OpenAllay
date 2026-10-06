package dev.openallay.runtime.forge1122.pack200;
import net.minecraftforge.fml.common.EnhancedRuntimeException;
public final class DimensionConstructorFailure extends EnhancedRuntimeException {
    private final Class<?> type;private final Class<?>[] arguments;
    public DimensionConstructorFailure(Class<?> type,Class<?>[] arguments,Throwable cause) {
        super("Could not find constructor for Enum "+type.getName(),cause);this.type=type;this.arguments=arguments.clone();
    }
    protected void printStackTrace(WrappedPrintStream stream) {
        stream.println("Target Arguments:");stream.println("    java.lang.String, int, "+java.util.Arrays.toString(arguments));
        stream.println("Found Constructors:");
        for(java.lang.reflect.Constructor<?> constructor:type.getDeclaredConstructors())stream.println("    "+java.util.Arrays.toString(constructor.getParameterTypes()));
    }
}
