package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonElement;

import java.util.List;

/**
 * One compartment of a {@link BotMemory}: a bounded set of things a bot remembers about a single subject.
 * <p>
 * A part owns its entries, their limits and how they fade. The memory only saves, restores and lists its parts, so a
 * new subject is a new part and nothing else.
 *
 * @author TheLining
 */
public interface BotMemoryPart {

    /**
     * @return The name this part is saved under. It never changes once saves hold it.
     */
    String key();

    /**
     * @return This part as JSON, holding only what is still worth remembering.
     */
    JsonElement save();

    /**
     * Replaces everything in this part with saved data. Entries that cannot be read are skipped.
     *
     * @param data What {@link #save()} wrote.
     */
    void load(JsonElement data);

    /**
     * Forgets everything in this part.
     */
    void clear();

    /**
     * @return This part in plain lines, for a developer reading one bot's memory.
     */
    List<String> describe();
}
