package io.luna.security;


public interface RsaKeyReader {

    /**
     * Reads an RSA key pair from memory.
     */
    RsaKey read();
}
