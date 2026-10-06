package io.luna.util;

/** Integer hash mixing without allocating temporary objects. */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Applies the MurmurHash3 32-bit finalizer to spread packed values across hash-table buckets.
     * This is a hash value, not a coordinate identifier.
     */
    public static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x85ebca6b;
        value ^= value >>> 13;
        value *= 0xc2b2ae35;
        return value ^ (value >>> 16);
    }
}
