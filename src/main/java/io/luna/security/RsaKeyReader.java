package io.luna.security;


public interface RsaKeyReader {

    /**
     * Reads an RSA key from memory.
     */
    RsaKey read();
}
