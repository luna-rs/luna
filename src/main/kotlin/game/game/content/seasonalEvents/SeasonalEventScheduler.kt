package game.content.seasonalEvents

import api.bot.zone.SubZone
import api.predef.ext.addItem
import api.predef.ext.toTicks
import api.predef.rand
import api.predef.world
import com.google.common.collect.HashMultiset
import io.luna.Luna
import io.luna.game.model.item.Item
import java.time.MonthDay
import kotlin.time.Duration.Companion.hours

/**
 * Schedules seasonal events and periodically distributes their items throughout the game world.
 *
 * Seasonal events become active when their configured month matches the current month. Multiple events may be
 * active simultaneously, with each event maintaining its own wave timer and persistent pickup quota.
 *
 * Active events periodically generate item waves according to the configured wave interval. Generated items are
 * distributed across random [SubZone] instances while [taggedZones] limits excessive placement within the same
 * zones during a scheduler cycle.
 *
 * @author lare96
 */
object SeasonalEventScheduler {

    /**
     * The interval between seasonal event scheduler updates.
     */
    private val UPDATE_RATE = 1.hours.toTicks()

    /**
     * The configured seasonal event settings.
     */
    private val settings = Luna.settings().events()

    /**
     * Tracks active seasonal events and their progress toward the next item wave.
     *
     * The count associated with each event represents the number of hourly scheduler updates accumulated during
     * its current wave cycle.
     */
    private val activeEvents = HashMultiset.create<SeasonalEvent>()

    /**
     * Tracks successful seasonal item placements within each subzone during the current scheduler cycle.
     *
     * A zone stops being considered for additional event waves once its placement count reaches the number of
     * currently active seasonal events. All counts are cleared after the scheduler cycle completes.
     */
    private val taggedZones = HashMultiset.create<SubZone>()

    /**
     * The number of game ticks elapsed since the previous scheduler update.
     */
    private var lastUpdate = 0

    /**
     * Advances the seasonal event scheduler.
     *
     * Once [UPDATE_RATE] has elapsed, every seasonal event is checked against the current month. Newly activated
     * events are registered and have their pickup count reset when lifetime quotas are disabled.
     *
     * Existing active events generate another item wave once their configured wave interval has elapsed. Events
     * whose configured month no longer matches the current month are removed from [activeEvents].
     *
     * Zone placement counts are cleared after all events have been processed so each scheduler cycle starts with
     * every subzone eligible again.
     */
    fun update() {
        if (++lastUpdate >= UPDATE_RATE) {
            lastUpdate = 0
            val currentMonth = MonthDay.now().month

            for (event in SeasonalEvent.entries) {
                val eventMonth = event.month()

                if (eventMonth == currentMonth) {
                    val current = activeEvents.add(event, 1) + 1

                    if (current == 1) {
                        // Newly active event and quota is per-event. Reset the quota.
                        if (!settings.lifetimeQuota()) {
                            SeasonalEventPersistence.resetPickups(event)
                        }
                        continue
                    }

                    if (current >= settings.waveIntervalHours() + 1) {
                        // Active event and wave interval reached, drop items.
                        dropItems(event)
                        activeEvents.setCount(event, 1)
                    }
                } else {
                    // Event is no longer active, remove from multiset.
                    activeEvents.setCount(event, 0)
                }
            }

            // Clear tagged zones for next cycle.
            taggedZones.clear()
        }
    }

    /**
     * Distributes a new item wave for [event] throughout eligible subzones.
     *
     * No additional items are spawned once the event's pickup quota has been reached. Candidate zones whose
     * placement count has reached the number of active events are skipped for the remainder of the scheduler cycle.
     *
     * Each candidate zone receives a number of placement attempts based on its area, with a minimum of one and a
     * maximum of 25 attempts. Items are removed from the pending drop list only after a valid, unblocked tile has
     * been found.
     *
     * @param event The active seasonal event whose items should be distributed.
     */
    private fun dropItems(event: SeasonalEvent) {
        val amount = SeasonalEventPersistence.getPickups(event)

        if (amount >= settings.quota()) {
            // Reached or exceeded the drop quota, stop dropping items.
            return
        }

        val candidates = ArrayList<SubZone>(SubZone.entries.size)
        val maxTags = activeEvents.elementSet().size

        for (zone in SubZone.entries) {
            if (taggedZones.count(zone) >= maxTags) {
                continue
            }
            candidates += zone
        }

        candidates.shuffle()
        val dropItems = computeDropItems(event)

        for (zone in candidates) {
            if (dropItems.isEmpty()) {
                // All drop items have been exhausted.
                break
            }

            val tries = (zone.area.size() / 100).coerceIn(1, 25)

            for (loop in 0 until tries) {
                if (dropItems.isEmpty()) {
                    // All drop items have been exhausted.
                    break
                }

                val nextPosition = zone.area.randomPosition()

                // Pick random item and position, drop it if tile is not blocked.
                if (!world.collisionManager.isBlocked(nextPosition, false)) {
                    val nextItem = if (dropItems.size == 1) {
                        dropItems.removeFirst()
                    } else {
                        dropItems.removeAt(rand().nextInt(dropItems.size))
                    }

                    world.addItem(SeasonalEventGroundItem(nextItem.id, nextItem.amount, nextPosition, event))
                    taggedZones.add(zone)
                }
            }
        }
    }

    /**
     * Generates the items to distribute during the next wave for [event].
     *
     * The event's drop table is rolled until the configured number of non-consumable drops has been generated or
     * the maximum number of table rolls has been reached.
     *
     * Consumable drops are included in the resulting list but do not count toward the configured drops-per-wave
     * total. The repeat limit prevents tables that repeatedly produce consumables from being rolled indefinitely.
     *
     * A single table roll may produce multiple non-consumable items, so the resulting number of non-consumable
     * drops may exceed the configured target when the final roll returns multiple qualifying items.
     *
     * @param event The seasonal event whose drop table should be rolled.
     * @return The items generated for the event's next drop wave.
     */
    private fun computeDropItems(event: SeasonalEvent): ArrayList<Item> {
        var total = settings.dropsPerWave()
        var maxRepeats = total
        val dropList = ArrayList<Item>()

        // Roll until we've dropped [total] non-consumables or reach [maxRepeats].
        while (total > 0 && maxRepeats-- > 0) {
            val drops = event.table.roll(null, null)

            for (item in drops) {
                // Only non-consumables count towards the drops per wave.
                if (item.id !in event.consumables) {
                    total--
                }
                dropList += item
            }
        }

        return dropList
    }
}