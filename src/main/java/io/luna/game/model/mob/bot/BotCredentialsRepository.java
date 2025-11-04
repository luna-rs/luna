package io.luna.game.model.mob.bot;

import com.google.common.collect.Sets;
import io.luna.LunaContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A model representing a repository of unused usernames that can be assigned to persistent {@link Bot} types. Usernames
 * are stored locally in a text file, which is updated whenever a new name is requested.
 *
 * @author lare96
 */
public final class BotCredentialsRepository {
 // todo lazy removals from cache? use scheduler to serialize and just remove multiple from cache at a time
    /**
     * The path to the username cache.
     */
    private static final Path USERNAME_CACHE = Paths.get("data", "game", "bots", "username_cache.txt");

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The username set.
     */
    private final Set<String> usernameSet = Sets.newConcurrentHashSet();

    /**
     * The context.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link BotCredentialsRepository}.
     *
     * @param context The context.
     */
    public BotCredentialsRepository(LunaContext context) {
        this.context = context;
    }

    /**
     * Loads all pre-generated usernames from the local cache.
     */
    public void load() {
        try (Scanner scanner = new Scanner(USERNAME_CACHE)) {
            while (scanner.hasNextLine()) {
                String nextLine = scanner.nextLine();
                if(nextLine.length() <= 12) {
                    usernameSet.add(nextLine);
                }
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Saves all loaded pre-generated usernames back into the local cache.
     *
     * @return {@code true} if saved successfully.
     * @throws IOException If any errors occur.
     */
    public boolean save() {
        StringBuilder sb = new StringBuilder();
        for (String username : usernameSet) {
            sb.append(username).append('\n');
        }
        sb.setLength(sb.length() - 1);
        try {
            Files.writeString(USERNAME_CACHE, sb);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Requests a pre-generated username from the local cache. Throws an exception if no usernames are left.
     *
     * @return The username.
     */
    public String request() {
        Iterator<String> it = usernameSet.iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("No usernames left in the repository.");
        }
        String username = it.next();
        it.remove();
        int size = usernameSet.size();
        String display = "Only {} usernames left in the local cache!";
        if (size == 200 || size == 175 || size == 150 || size == 125 || size == 100) {
            logger.warn(display, box(size));
        } else if (size < 100) {
            logger.error(display, box(size));
        }
        context.getGame().submit(this::save);
        return username;
    }
}
