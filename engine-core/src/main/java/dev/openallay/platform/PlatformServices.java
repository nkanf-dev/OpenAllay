package dev.openallay.platform;

import java.util.List;
import java.util.ServiceLoader;

public final class PlatformServices {
    private PlatformServices() {}

    public static PlatformService load() {
        java.util.ArrayList<PlatformService> discovered = new java.util.ArrayList<>();
        for (PlatformService service : ServiceLoader.load(PlatformService.class, PlatformServices.class.getClassLoader())) {
            discovered.add(service);
        }
        List<PlatformService> services = dev.openallay.util.Java8Collections.listCopyOf(discovered);
        if (services.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one PlatformService, found " + services.size());
        }
        return services.get(0);
    }
}
