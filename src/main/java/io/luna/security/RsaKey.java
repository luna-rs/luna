package io.luna.security;

import java.math.BigInteger;

/**
 * An RSA key consisting of a modulus and exponent.
 * <p>
 * For more information on the RSA encryption algorithm,
 * read <a href="https://www.comparitech.com/blog/information-security/rsa-encryption/"> this article.</a>
 * <br>
 * <b>NOTE: </b> Luna and/or it's contributors have no affiliation with the website linked above.
 */
public final class RsaKey {
    /**
     * The private RSA modulus value.
     */
    public final BigInteger modulus;

    /**
     * The private RSA exponent value.
     */
    public final BigInteger exponent;

    RsaKey(BigInteger modulus, BigInteger exponent) {
        this.modulus = modulus;
        this.exponent = exponent;
    }
}
