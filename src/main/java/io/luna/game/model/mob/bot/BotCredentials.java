package io.luna.game.model.mob.bot;

import io.luna.util.RandomUtils;
import io.luna.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A utility class that generates random credentials for {@link Bot} types.
 *
 * @author lare96
 */
public final class BotCredentials {

    /**
     * Generates a random non-secure password. This should only be used to generate credentials for bots.
     *
     * @return The generated password.
     */
    public static String generatePassword() {
        int minLength = 4;
        int maxLength = 8;
        int length = ThreadLocalRandom.current().nextInt(minLength, maxLength);
        StringBuilder sb = new StringBuilder(length);
        for (int count = 0; count < length; count++) {
            char next = RandomUtils.random(StringUtils.VALID_CHARACTERS);
            if (ThreadLocalRandom.current().nextBoolean()) {
                sb.append(Character.toUpperCase(next));
            } else {
                sb.append(next);
            }
        }
        return sb.toString();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private BotCredentials() {
    }
}
