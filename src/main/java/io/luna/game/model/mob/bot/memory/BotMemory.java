package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonObject;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonality;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * What a {@link Bot} remembers, kept across logouts and restarts.
 * <p>
 * The memory is a set of {@link BotMemoryPart parts}, one per subject. Each part is bounded and fades, so a bot holds
 * a limited, personal picture of its past and not a log. Parts are written where something happens to the bot and
 * read where it makes a choice. Nothing here runs every tick.
 * <p>
 * Memory changes what a bot chooses only by multiplying its usual interest, between {@link #LEAST_INTEREST} and
 * {@link #MOST_INTEREST} for everything it remembers together. It never rules a choice in or out.
 * <p>
 * Almost everything fades by the bot's own time online, which is saved with the account. What a bot saw of the world
 * rather than of itself, such as a crowded place, fades by real time instead. World ticks restart at zero on every boot
 * and are never stored here. Entries hold names and ids, never live players.
 *
 * @author TheLining
 */
public final class BotMemory {

    /**
     * The layout of the saved data.
     */
    private static final int VERSION = 1;

    /**
     * The lowest multiplier memory can put on a bot's interest in something.
     */
    public static final double LEAST_INTEREST = 0.25;

    /**
     * The highest multiplier memory can put on a bot's interest in something.
     */
    public static final double MOST_INTEREST = 3.0;

    /**
     * The bot's time online, in milliseconds.
     */
    private final LongSupplier clock;

    /**
     * The real time, in milliseconds since the epoch.
     */
    private final LongSupplier realClock;

    /**
     * The personality that sets how much the bot keeps and how fast it lets go.
     */
    private final Supplier<BotPersonality> personality;

    /**
     * Who the bot knows.
     */
    private final BotPeopleMemory people;

    /**
     * Which places the bot knows.
     */
    private final BotPlacesMemory places;

    /**
     * What the bot has been doing.
     */
    private final BotDiaryMemory diary;

    /**
     * Every part, in the order they are saved.
     */
    private final List<BotMemoryPart> parts;

    /**
     * Creates the memory of {@code bot}.
     *
     * @param bot The bot that remembers.
     */
    public BotMemory(Bot bot) {
        this(() -> bot.getTimePlayed().toMillis(), System::currentTimeMillis, bot::getPersonality);
    }

    /**
     * Creates a memory from its three inputs.
     *
     * @param clock The owner's time online, in milliseconds.
     * @param realClock The real time, in milliseconds since the epoch.
     * @param personality The owner's personality, read whenever it is needed.
     */
    public BotMemory(LongSupplier clock, LongSupplier realClock, Supplier<BotPersonality> personality) {
        this.clock = requireNonNull(clock);
        this.realClock = requireNonNull(realClock);
        this.personality = requireNonNull(personality);
        people = new BotPeopleMemory(this);
        places = new BotPlacesMemory(this);
        diary = new BotDiaryMemory(this);
        parts = List.of(people, places, diary);
    }

    /**
     * @return The bot's time online, in milliseconds. All memory is stamped with it and fades by it.
     */
    public long now() {
        return Math.max(0, clock.getAsLong());
    }

    /**
     * @return The real time, in milliseconds since the epoch. Memory of the world rather than of the bot fades by it.
     */
    public long realTime() {
        return realClock.getAsLong();
    }

    /**
     * @return The personality of the bot that remembers, or the default one before it is known.
     */
    public BotPersonality personality() {
        BotPersonality current = personality.get();
        return current == null ? BotPersonality.DEFAULT : current;
    }

    /**
     * @return Who the bot knows.
     */
    public BotPeopleMemory getPeople() {
        return people;
    }

    /**
     * @return Which places the bot knows.
     */
    public BotPlacesMemory getPlaces() {
        return places;
    }

    /**
     * @return What the bot has been doing.
     */
    public BotDiaryMemory getDiary() {
        return diary;
    }

    /**
     * @param activity The activity's name.
     * @param liking How much the bot likes the activity, from {@code 0.0} to {@code 1.0}.
     * @return The multiplier everything the bot remembers puts on its interest in the activity.
     */
    public double interestIn(String activity, double liking) {
        return Math.clamp(diary.interestIn(activity, liking), LEAST_INTEREST, MOST_INTEREST);
    }

    /**
     * @param skill The skill's id.
     * @param preferred Whether it is one of the bot's preferred skills.
     * @return The multiplier everything the bot remembers puts on its interest in the skill.
     */
    public double interestIn(int skill, boolean preferred) {
        return Math.clamp(diary.interestIn(skill, preferred), LEAST_INTEREST, MOST_INTEREST);
    }

    /**
     * @return Every part as JSON, keyed by {@link BotMemoryPart#key()}.
     */
    public JsonObject save() {
        JsonObject data = new JsonObject();
        data.addProperty("version", VERSION);
        for (BotMemoryPart part : parts) {
            data.add(part.key(), part.save());
        }
        return data;
    }

    /**
     * Replaces this memory with saved data. A part the data does not hold is left empty.
     *
     * @param data What {@link #save()} wrote, or {@code null} for an account that has none.
     */
    public void load(JsonObject data) {
        parts.forEach(BotMemoryPart::clear);
        if (data == null) {
            return;
        }
        for (BotMemoryPart part : parts) {
            if (data.has(part.key())) {
                part.load(data.get(part.key()));
            }
        }
    }

    /**
     * @return The whole memory in plain lines, for a developer reading one bot.
     */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("Time online: " + now() / 3_600_000 + " h");
        for (BotMemoryPart part : parts) {
            lines.addAll(part.describe());
        }
        return lines;
    }
}
