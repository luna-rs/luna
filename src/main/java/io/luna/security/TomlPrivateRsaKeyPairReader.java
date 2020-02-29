package io.luna.security;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.math.BigInteger;

/**
 * Responsible for reading a RSA private key pair from a TOML file.
 */
public class TomlPrivateRsaKeyPairReader implements RsaKeyPairReader {

    private final String filePath = "./data/rsa/rsapriv.toml";

    @Override
    public RsaKeyPair read() {

        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalStateException("Could not find RSA file at location: " + filePath);
        }

        Toml reader = new Toml().read(file).getTable("key");

        BigInteger mod = new BigInteger(reader.getString("modulus"));
        BigInteger exp = new BigInteger(reader.getString("exponent"));
        return new RsaKeyPair(mod, exp);
    }
}
