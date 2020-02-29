package io.luna.net.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TomlRsaPrivateKeyPairReaderTest {

    @Test
    void read() {
        RsaKeyPairReader reader = new TomlRsaPrivateKeyPairReader();
        RsaKeyPair keyPair = reader.read();

        assertNotNull(keyPair.modulus);
        assertNotNull(keyPair.exponent);
    }
}