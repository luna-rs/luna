package io.luna.game.model.mob.bot;

import io.luna.game.model.World;
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
     * The maximum amount of username generation attempts can fail before an exception is thrown.
     */
    private static final int MAXIMUM_GENERATION_ATTEMPTS = 10;

    /**
     * The base generated name for all temporary bots.
     */
    private static final String BASE_USERNAME = "Bot";

    /**
     * Generates a random username, checking if the username currently exists.
     *
     * @return The generated username.
     */
    public static String generateUsername(World world, boolean temporary) {
        if (temporary) {
            // Generate dummy name for temporary bots.
            int attempts = 0;
            while (attempts < MAXIMUM_GENERATION_ATTEMPTS) {
                int random = ThreadLocalRandom.current().nextInt(1, 5000);
                int count = attempts == 0 ? world.getBots().getTemporaryCount() : random;
                String genUsername = BASE_USERNAME + count;
                if (world.getPlayerMap().containsKey(genUsername) || world.getBots().contains(genUsername)) {
                    attempts++;
                } else {
                    return genUsername;
                }
            }
            throw new IllegalStateException("Username generation for bot failed, too many attempts!");
        } else {
            return world.getBotCredentials().request();
        }
    }

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
