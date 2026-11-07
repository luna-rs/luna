package game.content.seasonalEvents

import api.predef.ext.toTicks
import api.predef.gameService
import com.google.gson.JsonArray
import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * Handles persistent pickup state for seasonal events.
 *
 * Each [SeasonalEvent] maintains its own pickup count, which is kept in memory while the server is running and
 * periodically written to disk when the state changes.
 *
 * Disk writes are submitted asynchronously through [gameService] to avoid performing file I/O on the game thread.
 *
 * @author lare96
 */
object SeasonalEventPersistence {

    /**
     * The path used to store persistent seasonal event pickup data.
     */
    private val DATA_PATH = Paths.get("data", "game", "world", "seasonal_events.json")

    /**
     * The minimum number of game ticks between persistent saves.
     */
    private val SAVE_RATE = 5.minutes.toTicks()

    /**
     * The number of ticks elapsed since the previous save completed.
     */
    private val lastSave = AtomicInteger()

    /**
     * Whether the in-memory seasonal event state has changed and should be persisted.
     */
    private val saveRequested = AtomicBoolean()

    /**
     * The currently running asynchronous save operation.
     *
     * A completed future is used initially so the first requested save can be scheduled immediately once the save
     * interval has elapsed.
     */
    private var saveTask: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    /**
     * The current pickup count for each seasonal event.
     */
    private val pickups = EnumMap<SeasonalEvent, Int>(SeasonalEvent::class.java)

    /**
     * Loads previously persisted seasonal event pickup counts from disk.
     *
     * Each stored event name is resolved back into a [SeasonalEvent] and its associated pickup count is restored
     * into [pickups].
     */
    fun load() {
        if (Files.exists(DATA_PATH)) {
            val loaded = GsonUtils.readAsType(DATA_PATH, JsonArray::class.java)

            if (loaded != null) {
                for (json in loaded) {
                    val obj = json.asJsonObject
                    val name = SeasonalEvent.valueOf(obj["key"].asString)
                    val count = obj["value"].asInt
                    pickups[name] = count
                }
            }
        }
    }

    /**
     * Advances the persistence timer and schedules an asynchronous save when necessary.
     *
     * A save is scheduled only when:
     *
     * - The configured save interval has elapsed.
     * - The previous save operation has completed.
     * - At least one seasonal event pickup count has changed.
     *
     * If the write fails, another save is requested so the current state can be retried on a later update.
     */
    fun save() {
        if (lastSave.incrementAndGet() >= SAVE_RATE &&
            saveTask.isDone &&
            saveRequested.compareAndSet(true, false)
        ) {
            saveTask = gameService.submit {
                try {
                    GsonUtils.writeJson(pickups, DATA_PATH)
                } catch (e: Exception) {
                    saveRequested.set(true)
                    throw e
                } finally {
                    lastSave.set(0)
                }
            }
        }
    }

    /**
     * Resets the pickup count for [event] and marks the state for persistence.
     *
     * @param event The seasonal event whose pickup count should be reset.
     */
    fun resetPickups(event: SeasonalEvent) {
        pickups[event] = 0
        saveRequested.set(true)
    }

    /**
     * Increments the pickup count for [event] and marks the state for persistence.
     *
     * @param event The seasonal event whose pickup count should be incremented.
     */
    fun incrementPickups(event: SeasonalEvent) {
        pickups[event] = getPickups(event) + 1
        saveRequested.set(true)
    }

    /**
     * Returns the current pickup count for [event].
     *
     * Events without a previously recorded pickup count are treated as having zero pickups.
     *
     * @param event The seasonal event whose pickup count should be retrieved.
     * @return The current pickup count for the event.
     */
    fun getPickups(event: SeasonalEvent): Int {
        return pickups[event] ?: 0
    }
}