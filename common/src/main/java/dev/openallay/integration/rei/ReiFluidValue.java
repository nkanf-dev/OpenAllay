package dev.openallay.integration.rei;

/** Detached fluid facts; native and independently published API types stay in the entry helper. */
record ReiFluidValue(String id, long amount, boolean customData, boolean amountRepresentable) {}
