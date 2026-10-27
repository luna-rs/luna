package engine.npc.sound

import api.attr.Attr
import api.predef.*
import com.google.common.base.Preconditions.checkArgument
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalSound
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Npc

/**
 * Manages periodic ambient sound playback for registered NPC types.
 *
 * Profiles define the available sounds, playback radius, and randomized delays. Each NPC maintains its own countdown,
 * allowing NPCs of the same type to share a profile without sharing playback timing.
 *
 * Call [process] once per game tick for each participating NPC.
 *
 * @author lare96
 */
object NpcAmbientSound {

    /**
     * Defines the ambient sounds and timing configuration for an NPC type.
     *
     * Argument validation is performed by [register], not by this constructor.
     *
     * @param sounds The sounds available for random selection. Must not be empty.
     * @param radius The radius used for the nearby-listener check and positional sound playback.
     * @param minTicks The minimum delay between playback attempts, inclusive.
     * @param maxTicks The maximum delay between playback attempts, inclusive.
     */
    class NpcAmbientSoundProfile(
        val sounds: Set<Sound>,
        val radius: Int,
        val minTicks: Int,
        val maxTicks: Int
    ) {

        /**
         * Selects a random ambient sound from this profile.
         *
         * @return The selected sound.
         * @throws NoSuchElementException If [sounds] is empty.
         */
        fun nextSound(): Sound {
            return sounds.random()
        }

        /**
         * Selects a random delay between [minTicks] and [maxTicks], inclusive.
         *
         * @return The number of ticks until the next playback attempt.
         */
        fun nextDelay(): Int {
            return rand().nextInt(minTicks, maxTicks + 1)
        }
    }

    /**
     * Ambient sound profiles keyed by NPC type identifier.
     */
    private val profiles = HashMap<Int, NpcAmbientSoundProfile>()

    /**
     * The remaining ticks until this NPC's next ambient sound attempt.
     *
     * This countdown advances only when [process] is called and the NPC has a registered profile.
     */
    private var Npc.remainingTicks by Attr.int()

    /**
     * Registers an ambient sound profile for an NPC type, replacing any existing profile for that identifier.
     *
     * The maximum delay must be less than [Integer.MAX_VALUE] so it can safely be converted to an exclusive upper bound.
     *
     * @param npc The non-negative NPC type identifier.
     * @param sounds The non-empty set of ambient sounds available for selection.
     * @param radius The sound radius, between 0 and 15 inclusive.
     * @param minTicks The positive minimum delay between playback attempts, inclusive.
     * @param maxTicks The maximum delay, inclusive. Must be at least [minTicks] and less than [Integer.MAX_VALUE].
     * @throws IllegalArgumentException If any argument violates these constraints.
     */
    internal fun register(npc: Int, sounds: Set<Sound>, radius: Int, minTicks: Int, maxTicks: Int) {
        checkArgument(npc >= 0, "Invalid NPC id.")
        checkArgument(radius >= 0 && radius <= 15, "Invalid sound radius.")
        checkArgument(
            minTicks > 0 && maxTicks >= minTicks && maxTicks < Integer.MAX_VALUE,
            "Invalid ambient sound delay."
        )
        checkArgument(sounds.isNotEmpty(), "At least one ambient sound is required.")
        profiles[npc] = NpcAmbientSoundProfile(sounds, radius, minTicks, maxTicks)
    }

    /**
     * Registers the same ambient sound configuration for each supplied NPC type.
     *
     * Each identifier is registered individually, replacing any existing profile. If a registration fails, profiles
     * registered earlier in the list remain registered.
     *
     * @param npcs The NPC type identifiers to register.
     * @param sounds The non-empty set of ambient sounds available for selection.
     * @param radius The sound radius, between 0 and 15 inclusive.
     * @param minTicks The positive minimum delay between playback attempts, inclusive.
     * @param maxTicks The maximum delay, inclusive. Must be at least [minTicks] and less than [Integer.MAX_VALUE].
     * @throws IllegalArgumentException If any individual registration fails validation.
     */
    internal fun register(npcs: List<Int>, sounds: Set<Sound>, radius: Int, minTicks: Int, maxTicks: Int) {
        for (id in npcs) {
            register(id, sounds, radius, minTicks, maxTicks)
        }
    }

    /**
     * Advances an NPC's ambient sound countdown and attempts playback when the countdown reaches zero or below.
     *
     * NPCs without a registered profile are ignored without modifying their countdown. When an attempt becomes due,
     * the next delay is scheduled before checking whether playback is allowed. Skipped attempts therefore still
     * consume the newly scheduled delay.
     *
     * Playback is suppressed while the NPC is inactive, locked, or in combat. It is also suppressed when the NPC has
     * positive total health but its current health is zero or below.
     *
     * A positional sound is dispatched only if at least one active player in the NPC's local human view is within
     * the profile's radius.
     *
     * @param npc The NPC whose ambient sound countdown and playback eligibility are processed.
     */
    fun process(npc: Npc) {
        val profile = profiles[npc.id]
        if (profile == null || --npc.remainingTicks > 0) {
            return
        }

        npc.remainingTicks = profile.nextDelay()

        if (npc.state != EntityState.ACTIVE || npc.isLocked ||
            npc.combat.inCombat() || (npc.totalHealth > 0 && npc.health <= 0)
        ) {
            return
        }

        val audible = npc.localHumans.any {
            it.state == EntityState.ACTIVE && npc.position.isWithinDistance(it.position, profile.radius)
        }

        if (audible) {
            LocalSound.of(
                ctx,
                profile.nextSound(),
                npc.position,
                ChunkUpdatableView.globalView(),
                profile.radius,
                1
            ).display()
        }
    }
}