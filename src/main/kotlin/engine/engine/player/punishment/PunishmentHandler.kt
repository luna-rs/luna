package engine.player.punishment

import api.predef.*
import com.google.common.util.concurrent.ListenableFuture
import io.luna.game.model.mob.Player
import io.luna.game.persistence.PersistenceService
import io.luna.game.persistence.PlayerData
import io.luna.util.logging.LoggingSettings.FileOutputType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A global singleton that handles all player punishments such as bans, mutes, and IP blacklisting.
 *
 * Provides async persistence through [PersistenceService] and logs all punishments through
 * [FileOutputType.PUNISHMENT].
 *
 * @author lare96
 */
object PunishmentHandler {

    /**
     * The date-time formatter used for punishment logs.
     */
    val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy @ hh:mm a").withZone(ZoneId.of("UTC"))

    /**
     * The path to the persistent IP ban list file.
     */
    internal val IP_BANS: Path = Path.of("data", "net", "blacklisted_addresses.txt")

    /**
     * The logger instance for all punishment-related actions.
     */
    private val logger = FileOutputType.PUNISHMENT.logger

    /**
     * The logging level used for punishment entries.
     */
    private val PUNISHMENT = FileOutputType.PUNISHMENT.level

    /**
     * Loads persistent player data for the given [username] and applies the [transform] function.
     *
     * @param username The player username to load.
     * @param transform A lambda to transform the player’s [PlayerData] object.
     * @return A [ListenableFuture] that completes once persistence is updated.
     */
    private fun loadData(username: String, transform: PlayerData.() -> Unit): ListenableFuture<Void> {
        return world.persistenceService.transform(username) { transform(it) }
    }

    /**
     * IP-bans the given [username]. Writes the IP address to [IP_BANS] and forcibly logs out the player if online.
     *
     * @param punisher The staff member applying the ban.
     * @param username The username to IP ban.
     * @return A [ListenableFuture] representing completion of the async write operation.
     */
    fun ipBan(punisher: Player, username: String): ListenableFuture<Void> {
        val dataFuture = world.persistenceService.load(username)
        return gameThread.submit {
            val data = dataFuture.get() ?: throw IllegalStateException("Player data not found for $username.")

            val ip = data.lastIp ?: throw IllegalStateException("No IP address recorded for $username.")

            if (server.channelFilter.addToBlacklist(ip)) {
                Files.writeString(
                    IP_BANS,
                    "$ip\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.SYNC
                )
            } else {
                throw IllegalStateException("Player $username has already been IP banned.")
            }

            gameThread.sync {
                world.getPlayer(username).ifPresent { it.forceLogout() }
                punisher.sendMessage("You have IP banned $username.")
                logger.log(PUNISHMENT, "Staff member {} has IP banned {}.", punisher.username, username)
            }
        }
    }

    /**
     * Bans [username] until a specific [Instant] in the future.
     *
     * @param punisher The staff member applying the ban.
     * @param username The target username.
     * @param until The timestamp when the ban expires (use [futureDate] for permanent).
     */
    fun ban(punisher: Player, username: String, until: Instant) {
        val formatted = if (isPermanent(until))
            "permanently banned $username"
        else
            "banned $username until ${FORMATTER.format(until)}"

        val onComplete = {
            world.getPlayer(username).ifPresent { it.forceLogout() }
            punisher.sendMessage("You have $formatted.")
            logger.log(PUNISHMENT, "Staff member {} has {}.", punisher.username, formatted)
        }

        loadData(username) {
            unbanInstant = until
        }.addListener(onComplete, gameThread.executor)
    }

    /**
     * Bans [username] for a duration of [days], [hours], and [minutes]. Leaving each value at `0` will result in
     * a permanent ban.
     */
    fun ban(punisher: Player, username: String, days: Long = 0, hours: Long = 0, minutes: Long = 0) {
        val until = if (days == 0L && hours == 0L && minutes == 0L) futureDate() else now()
            .plus(minutes, ChronoUnit.MINUTES)
            .plus(hours, ChronoUnit.HOURS)
            .plus(days, ChronoUnit.DAYS)
        ban(punisher, username, until)
    }

    /**
     * Permanently bans [username].
     */
    fun permBan(punisher: Player, username: String) {
        ban(punisher, username)
    }

    /**
     * Unbans [username] immediately by setting their [PlayerData.unbanInstant] to the past.
     */
    fun unban(punisher: Player, username: String) {
        loadData(username) {
            unbanInstant = pastDate()
        }.addListener({
                          punisher.sendMessage("You have unbanned $username.")
                          logger.log(PUNISHMENT, "Staff member {} has unbanned {}.", punisher.username, username)
                      }, gameThread.executor)
    }

    /**
     * Mutes [username] until a specific [Instant].
     */
    fun mute(punisher: Player, username: String, until: Instant) {
        val formatted = if (isPermanent(until))
            "permanently muted $username"
        else
            "muted $username until ${FORMATTER.format(until)}"

        val onComplete = {
            punisher.sendMessage("You have $formatted.")
            world.getPlayer(username).ifPresent { it.sendMessage("You have been muted.") }
            logger.log(PUNISHMENT, "Staff member {} has {}.", punisher.username, formatted)
        }

        loadData(username) {
            unmuteInstant = until
        }.addListener(onComplete, gameThread.executor)
    }

    /**
     * Mutes [username] for a duration of [days], [hours], and [minutes]. Leaving each value at `0`
     * will result in a permanent mute.
     */
    fun mute(punisher: Player, username: String, days: Long = 0, hours: Long = 0, minutes: Long = 0) {
        val until = if (days == 0L && hours == 0L && minutes == 0L) futureDate() else now()
            .plus(days, ChronoUnit.DAYS)
            .plus(hours, ChronoUnit.HOURS)
            .plus(minutes, ChronoUnit.MINUTES)
        mute(punisher, username, until)
    }

    /**
     * Permanently mutes [username].
     */
    fun permMute(punisher: Player, username: String) {
        mute(punisher, username)
    }

    /**
     * Unmutes [username] by setting their mute expiration to the past.
     */
    fun unmute(punisher: Player, username: String) {
        loadData(username) {
            unmuteInstant = pastDate()
        }.addListener({
                          punisher.sendMessage("You have unmuted $username.")
                          world.getPlayer(username).ifPresent { it.sendMessage("You have been unmuted.") }
                          logger.log(PUNISHMENT, "Staff member {} has unmuted {}.", punisher.username, username)
                      }, gameThread.executor)
    }

    /**
     * Will send [plr] details on how long they're muted (if applicable).
     */
    fun notifyIfMuted(plr: Player): Boolean {
        if (plr.isMuted) {
            if (isPermanent(plr.unmuteInstant)) {
                plr.sendMessage("You are permanently muted.")
            } else {
                val until = FORMATTER.format(plr.unmuteInstant)
                plr.sendMessage("You are muted. You will be unmuted on $until.")
            }
            return true
        }
        return false
    }

    /**
     * Returns the current time as an [Instant].
     */
    private fun now() = Instant.now()

    /**
     *  Returns an [Instant] one second in the past, used to mark “expired” punishments.
     */
    private fun pastDate() = now().minusSeconds(1)

    /**
     * Returns an [Instant] one hundred years in the future, used for permanent bans/mutes.
     */
    private fun futureDate() = now().plus(100 * 365, ChronoUnit.DAYS)

    /**
     * Checks whether a punishment expiring at [instant] should be considered permanent.
     *
     * A ban/mute expiring more than five years in the future counts as permanent.
     */
    fun isPermanent(instant: Instant) = now().plus(5 * 365, ChronoUnit.DAYS).isBefore(instant)
}