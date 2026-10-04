package dev.openallay.extension;

/** Loader-discovered immutable declaration of one OpenAllay Extension. */
public interface OpenAllayExtension {
    OpenAllayExtensionDescriptor descriptor();

    OpenAllayExtensionContribution contribution();
}
