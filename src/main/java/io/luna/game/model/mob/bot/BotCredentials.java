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
     * The base name for all persistent bots.
     */
    private static final String BASE_USERNAME = "Bot";

    /**
     * The base name for all temporary bots.
     */
    private static final String BASE_TEMPORARY_USERNAME = "TBot";

    /**
     * Generates a random username, checking if the username currently exists.
     *
     * @return The generated username.
     */
    public static String generateUsername(World world, boolean temporary) {
        // TODO https://github.com/luna-rs/luna/issues/380
        int attempts = 0;
        String username = null;
        while (username == null) {
            if (attempts >= MAXIMUM_GENERATION_ATTEMPTS) {
                throw new IllegalStateException("Username generation for bot failed, too many attempts!");
            }
            int random = ThreadLocalRandom.current().nextInt(1, 100);
            String baseUsername = temporary ? BASE_TEMPORARY_USERNAME : BASE_USERNAME;
            int count = (temporary ? world.getBots().getTemporaryCount() :
                    world.getBots().getPersistentCount()) + 1 + attempts + random;
            String genUsername = baseUsername + count;
            if (world.getPlayerMap().containsKey(genUsername) || world.getBots().contains(genUsername)) {
                attempts++;
            } else {
                username = genUsername;
            }
        }
        return username;
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
