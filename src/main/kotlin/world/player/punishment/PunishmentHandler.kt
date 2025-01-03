package world.player.punishment

import api.predef.*
import com.google.common.collect.Sets
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.luna.game.model.mob.Player
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.ParameterizedMessage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A global model that handles player punishments.
 *
 * @author lare96 
 */
object PunishmentHandler {

    /**
     * The date-time formatter.
     */
    val FORMATTER = SimpleDateFormat("MMM d, yyyy @ hh:mm aaa")

    /**
     * The path to the IP ban database.
     */
    val IP_BANS: Path = Path.of("data", "net", "blacklisted_addresses.txt")

    /**
     * The punishment logging level.
     */
    private val PUNISHMENT = Level.forName("PUNISHMENT", 700)

    /**
     * The logger that will log all punishments to a file.
     */
    private val fileLogger = LogManager.getLogger("PunishmentFileLogger")

    /**
     * The logger for general output.
     */
    private val logger = LogManager.getLogger()

    /**
     * A concurrent set of IP banned users.
     */
    private val ipBans = Sets.newConcurrentHashSet<String>()

    /**
     * IP bans [target], and returns the result of saving it to the database.
     */
    fun ipBan(target: Player): ListenableFuture<Void> {
        // Add target to in-memory database and disconnect them.
        if (ipBans.add(target.currentIp)) {
            fileLogger.log(PUNISHMENT, "{} has been IP banned.", target.username)
            target.logout()

            // Add target to local file database.
            return game.submit {
                try {
                    Files.writeString(IP_BANS,
                                      (target.currentIp + '\n'),
                                      StandardOpenOption.CREATE,
                                      StandardOpenOption.WRITE,
                                      StandardOpenOption.APPEND)
                } catch (e: IOException) {
                    fileLogger.error("Could not write IP ban entry to file.")
                    throw e
                }
            }
        }
        return Futures.immediateFailedFuture(IllegalStateException("This player has already been IP banned."))
    }

    /**
     * Bans [target], will be lifted after [until] has passed.
     */
    fun ban(target: Player, until: Instant) {
        val username = target.username
        target.unbanInstant = until
        target.logout()
        when {
            isPermanent(until) -> fileLogger.log(PUNISHMENT, "{} has been permanently banned.", username)
            else -> fileLogger.log(PUNISHMENT, "{} has been banned until {}.", username, FORMATTER.format(until))
        }
    }

    /**
     * Bans [target], will be lifted after [days], [hours], and [minutes] have passed.
     */
    fun ban(target: Player, days: Long = 0, hours: Long = 0, minutes: Long = 30) {
        ban(target, now().plus(minutes, ChronoUnit.MINUTES)
            .plus(hours, ChronoUnit.HOURS)
            .plus(days, ChronoUnit.DAYS))
    }

    /**
     * Permanently bans [target].
     */
    fun permBan(target: Player) {
        ban(target, futureDate())
    }

    /**
     * Unbans the player with [username] by modifying their saved data.
     */
    fun unban(username: String) {
        val loadFuture = world.persistenceService.transform(username) {
            it.unbanInstant = Instant.now().minus(1, ChronoUnit.DAYS)
        }
        loadFuture.addListener(Runnable {
            try {
                loadFuture.get()
                fileLogger.info("{} has been unbanned.", username)
            } catch (e: Exception) {
                logger.error(ParameterizedMessage("Issue while trying to unban {}.", username), e)
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Mutes [target], will be lifted after [until] has passed.
     */
    fun mute(target: Player, until: Instant) {
        target.unmuteInstant = until
        when {
            isPermanent(until) -> target.sendMessage("You have been permanently muted.")
            else -> target.sendMessage("You have been muted until ${FORMATTER.format(until)}.")
        }
    }

    /**
     * Mutes [target], will be lifted after [days], [hours], and [minutes] have passed.
     */
    fun mute(target: Player, days: Long = 0, hours: Long = 0, minutes: Long = 30) {
        mute(target, now().plus(days, ChronoUnit.DAYS).plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES))
    }

    /**
     * Permanently mutes [target].
     */
    fun permMute(target: Player) {
        mute(target, futureDate())
    }

    /**
     * Unmutes [target].
     */
    fun unmute(target: Player) {
        target.unmuteInstant = pastDate()
    }

    /**
     * Returns the current [Instant].
     */
    private fun now() = Instant.now()

    /**
     * Always returns a date in the near past.
     */
    private fun pastDate() = now().minusSeconds(1)

    /**
     * Always returns a date in the far future.
     */
    private fun futureDate() = now().plus(100, ChronoUnit.YEARS)

    /**
     * Determines if [instant] is far in the future enough to be a permanent ban.
     */
    fun isPermanent(instant: Instant) = now().plus(1, ChronoUnit.YEARS).isBefore(instant)
}