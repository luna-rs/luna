import api.attr.Attr
import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface
import io.luna.net.msg.out.AssignmentMessageWriter
import io.luna.net.msg.out.SkillUpdateMessageWriter
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// TODO error coming from this plugin
/**
 * The "first_login" attribute.
 */
var Player.firstLogin by Attr<Boolean>("first_login")

/**
 * Formats dates into the specified pattern.
 */
val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, uuuu")!!

/**
 * Inventory starter items.
 */
val inventoryStarter = listOf(
        Item(995, 10000), // Coins
        Item(556, 250), // Air runes
        Item(555, 250), // Water runes
        Item(554, 250), // Fire runes
        Item(557, 250), // Earth runes
        Item(558, 500), // Mind runes
        Item(841)) // Shortbow

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
        Item(1153), // Iron full helm
        Item(1115), // Iron platebody
        Item(1067), // Iron platelegs
        Item(1323), // Iron scimitar
        Item(1191), // Iron kiteshield
        Item(1731), // Amulet of power
        Item(4121), // Iron boots
        Item(1063), // Leather vambraces
        Item(2570), // Ring of life
        Item(1019), // Black cape
        Item(882, 750)) // Bronze arrows

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")

    plr.inventory.addAll(inventoryStarter)
    plr.equipment.addAll(equipmentStarter)
    plr.interfaces.open(DesignPlayerInterface())
}

/**
 * Final initialization of the player before gameplay.
 */
fun init(plr: Player) {
    plr.tabs.resetAll()

    plr.interactions.show(INTERACTION_FOLLOW)
    plr.interactions.show(INTERACTION_TRADE)

    // Temporary, until "init" for containers is moved here. That will happen
    // when login synchronization is being fixed.
    plr.equipment.loadBonuses()

    plr.inventory.refreshPrimary(plr)
    plr.equipment.refreshPrimary(plr)

    plr.queue(UpdateRunEnergyMessageWriter())
    plr.queue(AssignmentMessageWriter(true))

    plr.skills.forEach { plr.queue(SkillUpdateMessageWriter(it.id)) }

    plr.sendMessage("Welcome to Luna.")
    plr.sendMessage("You currently have " + plr.rights.formattedName + " privileges.")
}

/**
 * If the player is muted, send the mute details.
 */
fun checkMute(plr: Player) {
    if (plr.isMuted) {
        when (plr.unmuteDate) {
            "never" -> plr.sendMessage("You are permanently muted. It can only be overturned by an Admin.")
            else -> {
                val lift = LocalDate.parse(plr.unmuteDate)
                plr.sendMessage("You are muted. You will be unmuted on ${dateFormatter.format(lift)}.")
            }
        }
    }
}

/**
 * Listens for login events, initializes [Player]s.
 */
on(LoginEvent::class) {
    val plr = plr
    init(plr)
    checkMute(plr)
    if (plr.firstLogin) {
        firstLogin(plr)
        plr.firstLogin = false
    }
}
