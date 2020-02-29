package io.luna.net.security;


public interface RsaKeyPairReader {

    /**
     * Reads an RSA key pair from memory.
     */
    RsaKeyPair read();
}
