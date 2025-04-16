package io.luna.security;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.math.BigInteger;

/**
 * Responsible for reading a RSA private key from a TOML file.
 */
public class TomlPrivateRsaKeyReader implements RsaKeyReader {

    private final String filePath = "./data/rsa/rsapriv.toml";

    @Override
    public RsaKey read() {

        File file = new File(filePath);

        if (!file.exists()) {
            throw new IllegalStateException("Could not find RSA key file at location: " + filePath);
        }

        Toml reader = new Toml().read(file).getTable("key");

        BigInteger mod = new BigInteger(reader.getString("modulus"));
        BigInteger exp = new BigInteger(reader.getString("exponent"));
        return new RsaKey(mod, exp);
    }
}
