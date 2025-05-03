package io.luna.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TomlPrivateRsaKeyReaderTest {

    @Test
    void read() {
        RsaKeyReader reader = new TomlPrivateRsaKeyReader();
        RsaKey key = reader.read();

        assertNotNull(key.modulus);
        assertNotNull(key.exponent);
    }
}