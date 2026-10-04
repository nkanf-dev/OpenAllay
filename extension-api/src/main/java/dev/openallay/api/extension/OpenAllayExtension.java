package dev.openallay.api.extension;

/** Startup-discovered Extension. Entrypoints have a public no-argument constructor. */
public interface OpenAllayExtension {
    /** Pure metadata lookup; must not touch host or native game state. */
    ExtensionDescriptor descriptor();
    /** Immutable declarations only; native world/session capture remains lazy. */
    ExtensionContribution contribution(ExtensionHost host);
}
