package io.luna.security;


public interface RsaKeyPairReader {

    /**
     * Reads an RSA key pair from memory.
     */
    RsaKeyPair read();
}
