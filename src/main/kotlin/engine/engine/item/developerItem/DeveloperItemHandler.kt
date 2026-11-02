package engine.item.developerItem

import api.bot.isRealBot
import api.bot.script.BotScript
import api.predef.*
import api.predef.ext.*
import engine.player.punishment.PunishmentHandler
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.overlay.TextInput
import java.util.WeakHashMap
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Luna's toy menus. Every callback checks the live actor and target before changing state.
 * Repeat reopens the last menu instead of repeating a destructive command.
 *
 * @author lare96z
 */
object DeveloperItemHandler {
    const val ITEM_ID = 4177
    private val previous = WeakHashMap<Player, (Player) -> Unit>()

    fun allowed(plr: Player): Boolean {
        if (plr.rights != PlayerRights.DEVELOPER) {
            destroy(plr)
            forget(plr)
            return false
        }
        return plr.state == EntityState.ACTIVE
    }

    /**
     * Removes every carried toy without creating a ground item.
     */
    internal fun destroy(plr: Player) {
        for (container in listOf(plr.inventory, plr.equipment)) {
            container.forIndexedItems { index, item ->
                if (item?.id == ITEM_ID) container[index] = null
            }
        }
    }

    fun available(plr: Player, target: Player): Boolean {
        if (!allowed(plr)) return false
        if (target.state != EntityState.ACTIVE || target.client.isPendingLogout) {
            plr.sendMessage("That target is no longer available.")
            return false
        }
        return true
    }

    fun forget(plr: Player) { previous.remove(plr) }

    internal fun menu(plr: Player, choices: List<Pair<String, (Player) -> Unit>>, page: Int = 0) {
        if (!allowed(plr)) return
        val pages = choices.chunked(3)
        val index = page.coerceIn(0, maxOf(0, pages.lastIndex))
        previous[plr] = { menu(it, choices, index) }
        val options = pages.getOrElse(index) { emptyList() }.map { (name, action) ->
            DialogueOption(name) { if (allowed(it)) action(it) }
        }.toMutableList()
        if (index > 0) options += DialogueOption("Previous") { menu(it, choices, index - 1) }
        if (index < pages.lastIndex) options += DialogueOption("Next") { menu(it, choices, index + 1) }
        plr.newDialogue().options(options).open()
    }

    internal fun report(plr: Player, title: String, lines: List<String>) {
        if (!allowed(plr)) return
        val view = QuestJournalInterface(title)
        lines.take(100).forEach { view.addLine(it) }
        plr.overlays.open(view)
    }

    internal fun position(plr: Player) {
        if (!allowed(plr)) return
        val p = plr.position
        plr.sendMessage("Pos ${p.x},${p.y},${p.z} | Chunk ${p.chunk.x},${p.chunk.y} | Region ${p.regionId}")
    }

    internal fun automation(plr: Player) {
        if (!allowed(plr)) return
        menu(plr, listOf(
            "Combat (melee, ranged, magic)" to { p -> start(p, p, DeveloperAutomation::combat) },
            "Skills" to { p -> skillsAutomation(p, p) },
            "Auto-walker" to { p -> navigation(p) },
            "Stop automation" to { p -> PlayerToBotInjector.stop(p) }))
    }

    private fun start(actor: Player, target: Player, factory: (Bot) -> BotScript) {
        if (!available(actor, target)) return
        try {
            if (target.isRealBot()) {
                target.scriptStack.pushHead(factory(target))
                actor.sendMessage("Injected script into ${target.username}.")
            } else if (target === actor) {
                PlayerToBotInjector.start(actor, factory)
            } else actor.sendMessage("Player automation must be started by the connected player.")
        } catch (e: IllegalArgumentException) {
            actor.sendMessage(e.message ?: "No eligible activity is available.")
        }
    }

    private fun skillsAutomation(actor: Player, target: Player) {
        menu(actor, DeveloperAutomation.skillIds.map { id ->
            Skill.getName(id) to { p: Player -> start(p, target) { DeveloperAutomation.skill(it, id) } }
        })
    }

    internal fun navigation(plr: Player) {
        navigationPrompt(plr, null)
    }

    private fun navigationPrompt(plr: Player, target: Player?) {
        if (!allowed(plr) || (target != null && !available(plr, target))) return
        previous[plr] = { navigationPrompt(it, target) }
        plr.sendMessage("Enter x y [height], or a zone/subzone name prefix. Height defaults to ${if (target == null) "your" else "the selected player's"} current height.")
        plr.overlays.open(object : TextInput() {
            override fun input(player: Player, value: String) { navigateInput(player, value, target) }
        })
    }

    internal fun navigateInput(plr: Player, value: String, target: Player? = null) {
        if (!allowed(plr) || (target != null && !available(plr, target))) return
        val result = DeveloperNavigation.resolve(value, (target ?: plr).position.z)
        if (result.matches.isNotEmpty()) {
            plr.sendMessage("Several destinations match. Select the intended place.")
            menu(plr, result.matches.map { place ->
                "${place.kind}: ${place.name.lowercase().replace('_', ' ')}" to { p: Player -> destinationChoice(p, place.position, target) }
            })
        } else if (result.position == null) {
            plr.sendMessage(result.message)
            navigationPrompt(plr, target)
        } else destinationChoice(plr, result.position, target)
    }

    private fun destinationChoice(plr: Player, destination: io.luna.game.model.Position, target: Player?) {
        if (target != null && !available(plr, target)) return
        menu(plr, listOf(
            "${if (target == null) "Walk to" else "Move ${target.username} to"} ${destination.x} ${destination.y} ${destination.z}" to { p ->
                if (target == null) {
                    PlayerToBotInjector.start(p) { DeveloperNavigation.walkScript(it, destination) }
                } else if (available(p, target)) {
                    target.move(destination)
                    p.sendMessage("Moved ${target.username} to ${destination.x} ${destination.y} ${destination.z}.")
                }
            }, "Change destination" to { p -> navigationPrompt(p, target) }))
    }

    internal fun repeat(plr: Player) {
        if (!allowed(plr)) return
        previous[plr]?.invoke(plr) ?: plr.sendMessage("No previous developer menu.")
    }

    fun player(actor: Player, target: Player) {
        if (!available(actor, target)) return
        val choices = mutableListOf<Pair<String, (Player) -> Unit>>(
            "Skills: ${target.username}" to { p -> skillMenu(p, target) },
            "Navigation" to { p -> navigationPrompt(p, target) },
            "Punishment" to { p -> punishment(p, target) },
            "Containers" to { p -> DeveloperInspection.containers(p, target) })
        if (target.isRealBot()) choices += "Kernel" to { p -> kernel(p, target) }
        menu(actor, choices)
    }

    private fun skillMenu(actor: Player, target: Player) {
        if (!available(actor, target)) return
        menu(actor, listOf(
            "View skills and total" to { p -> if (available(p, target)) p.overlays.open(DeveloperSkillsInterface(target)) },
            "Set level" to { p -> menu(p, Skill.IDS.map { id -> Skill.getName(id) to { viewer: Player ->
                number(viewer, "Level (1..99)") { v, level ->
                    if (available(v, target) && level in 1..99) {
                        target.skill(id).experience = SkillSet.experienceForLevel(level).toDouble()
                        target.skill(id).level = level
                        v.sendMessage("${target.username}: ${Skill.getName(id)} set to $level.")
                    } else v.sendMessage("Enter a level from 1 to 99.")
                }
            } }) },
            "Master" to { p -> if (available(p, target)) {
                target.skills.forEach { it.experience = SkillSet.MAXIMUM_EXPERIENCE; it.level = 99 }
                p.sendMessage("${target.username}: all skills set to 99.")
            } }))
    }

    internal fun number(plr: Player, prompt: String, action: (Player, Int) -> Unit) {
        if (!allowed(plr)) return
        plr.sendMessage(prompt)
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) { if (allowed(player)) action(player, value) }
        })
    }

    private fun punishment(actor: Player, target: Player) {
        if (!available(actor, target)) return
        menu(actor, listOf(
            "Mute" to { p -> duration(p, target, true) },
            "Kick" to { p -> if (available(p, target)) { target.forceLogout(); p.sendMessage("Kicked ${target.username}.") } },
            "Ban" to { p -> duration(p, target, false) },
            "IP ban" to { p -> if (available(p, target)) {
                if (target.isRealBot()) p.sendMessage("Autonomous bots have no remote client IP to ban.")
                else PunishmentHandler.ipBan(p, target.username).exceptionally { error ->
                    gameService.sync { p.sendMessage("IP ban failed: ${error.cause?.message ?: error.message}") }
                    null
                }
            } }))
    }

    private fun duration(actor: Player, target: Player, mute: Boolean) {
        menu(actor, listOf("Minutes" to { p -> number(p, "Duration in minutes (positive)") { v, minutes ->
            if (available(v, target) && minutes > 0) {
                val until = Instant.now().plus(minutes.toLong(), ChronoUnit.MINUTES)
                if (mute) PunishmentHandler.mute(v, target.username, until) else PunishmentHandler.ban(v, target.username, until)
            } else v.sendMessage("Enter a positive duration.")
        } }, "Permanent" to { p -> if (available(p, target)) {
            if (mute) PunishmentHandler.permMute(p, target.username) else PunishmentHandler.permBan(p, target.username)
        } }))
    }

    private fun kernel(actor: Player, target: Bot) {
        if (!available(actor, target) || !target.isRealBot()) return
        menu(actor, listOf(
            "Inject script" to { p -> menu(p, listOf(
                "Combat" to { v -> start(v, target, DeveloperAutomation::combat) },
                "Skill script" to { v -> skillsAutomation(v, target) })) },
            "View script queue" to { p -> if (available(p, target)) report(p, "${target.username}: scripts",
                target.scriptStack.scripts.mapIndexed { index, script -> "$index: ${script.javaClass.simpleName} (running=${script.isRunning()})" }) },
            "Stop current" to { p -> if (available(p, target)) { target.scriptStack.stopCurrent(); p.sendMessage("Current script stopped.") } },
            "Clear pending queue" to { p -> if (available(p, target)) { target.scriptStack.clear(); p.sendMessage("Pending queue cleared; running script retained.") } }))
    }
}
