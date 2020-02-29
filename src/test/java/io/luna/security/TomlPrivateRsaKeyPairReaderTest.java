package io.luna.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TomlPrivateRsaKeyPairReaderTest {

    @Test
    void read() {
        RsaKeyPairReader reader = new TomlPrivateRsaKeyPairReader();
        RsaKeyPair keyPair = reader.read();

        assertNotNull(keyPair.modulus);
        assertNotNull(keyPair.exponent);
    }
}