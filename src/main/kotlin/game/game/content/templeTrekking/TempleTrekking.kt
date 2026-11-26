package game.content.templeTrekking

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.petCats.PetCats
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varbit

/**
 * Temple Trekking: guiding a Burgh de Rott villager through Morytania to Paterdomus, the temple on the Salve.
 *
 * @author TheLining
 */
object TempleTrekking {

    const val YELLOW_TOKEN = 7774
    const val RED_TOKEN = 7775
    const val BLUE_TOKEN = 7776
    val TOKENS = setOf(YELLOW_TOKEN, RED_TOKEN, BLUE_TOKEN)

    /**
     * The route map, its Close Window text and the pawns it moves along the routes.
     */
    const val ROUTE_MAP = 341
    const val CLOSE_BUTTON = 18588
    const val PAWNS = 18618

    /**
     * The animation holding the pawns at Burgh de Rott.
     */
    const val PAWNS_AT_START = 3748

    /**
     * Varbits on the trek's varp: the villagers in town, the trek's points and the map's "!".
     */
    const val VILLAGERS_VARBIT = 1954
    const val POINTS_VARBIT = 1955
    const val MARKER_VARBIT = 1958

    /**
     * Burgh de Rott's banker, who exchanges the tokens.
     */
    const val CORNELIUS = 3569

    /**
     * Where a finished trek ends: on the Morytania bank, outside the temple.
     */
    val ARRIVAL = Position(3431, 3486)
    val ARRIVAL_VILLAGER = Position(3432, 3486)

    /**
     * The quest that opens the minigame.
     */
    val QUEST = Quest.IN_AID_OF_THE_MYREQUE

    /**
     * The points earned on the trek whose token the player holds.
     */
    var Player.trekTokenPoints by Attr.int().persist("temple_trek_token_points")

    /**
     * Whether the trek behind the player's token had any events.
     */
    var Player.trekTokenEvents by Attr.boolean().persist("temple_trek_token_events")

    /**
     * The trek under way, if any.
     */
    var Player.trek by Attr.nullableObj(Trek::class)

    /**
     * Shows the villagers in Burgh de Rott to a player who can take them.
     */
    fun login(plr: Player) {
        plr.sendVarbit(Varbit(VILLAGERS_VARBIT, if (QuestGates.passes(plr, QUEST)) 1 else 0))
        // The client keeps varps over a logout, and a trek never outlives one.
        plr.sendVarbit(Varbit(POINTS_VARBIT, 0))
        plr.sendVarbit(Varbit(MARKER_VARBIT, 0))
    }

    /**
     * Talks to a villager waiting in Burgh de Rott.
     */
    fun talk(plr: Player, villager: TrekVillager) {
        if (plr.trek != null) return
        val id = villager.npc
        val dialogue = plr.newDialogue()
            .player("Hello there. What are you doing here?")
            .npc(id, "I'm waiting here hoping that someone can guide me to the Paterdomus, the temple on the Salve.")
        questions(dialogue, villager).open()
    }

    private fun questions(dialogue: DialogueQueueBuilder, villager: TrekVillager): DialogueQueueBuilder {
        val id = villager.npc
        return dialogue.options(
            "Where is Paterdomus?", {
                it.newDialogue().player("Where is Paterdomus?")
                    .npc(id, "Oh, Paterdomus is that rather impressive looking temple on the Salve... I'm sure you " +
                        "must have passed it on your way into Morytania.")
                    .then { p -> questions(p.newDialogue(), villager).open() }.open()
            },
            "Why do you want to go to Paterdomus?", {
                it.newDialogue().player("Why do you want to go to Paterdomus?")
                    .npc(id, "I'm hoping to get back to civilisation and see what the lands of Misthalin look like.")
                    .then { p -> questions(p.newDialogue(), villager).open() }.open()
            },
            "Could you defend yourself if we met enemies...?", {
                it.newDialogue().player("Could you defend yourself if we met enemies on the route to Paterdomus?")
                    .npc(id, villager.tier.defence)
                    .then { p -> questions(p.newDialogue(), villager).open() }.open()
            },
            "I can take you to Paterdomus.", { offer(it, villager) },
            "Ok, thanks.", { it.newDialogue().player("Ok, thanks.").open() })
    }

    /**
     * The player offers to guide [villager]: a pet must be left at home and any token cashed in first.
     */
    private fun offer(plr: Player, villager: TrekVillager) {
        val id = villager.npc
        val dialogue = plr.newDialogue().player("I can take you to Paterdomus.")
        when {
            PetCats.follower(plr) != null ->
                dialogue.npc(id, "Hmmm, it seems that you have a follower. I think it may get lost on this " +
                    "journey if it's left to wander on its own. Let's leave when your follower is safe.").open()

            hasToken(plr) ->
                dialogue.npc(id, "You've already got a reward token from another trek. Hand it in at the bank " +
                    "before you take anyone else.").open()

            else ->
                dialogue.npc(id, "Oh really? That would be great! I'll give you a small reward once we reach our " +
                    "destination.")
                    .npc(id, "Ok, which route should we take?")
                    .then { plr.overlays.open(RouteMap(villager)) }
                    .open()
        }
    }

    /**
     * The route map, opened for a villager about to be guided and kept open to show the trek's progress.
     */
    class RouteMap(val villager: TrekVillager) : StandardInterface(ROUTE_MAP) {

        /**
         * Set while the map shows a trek under way.
         */
        var travelling = false
    }

    /**
     * Sets off with the villager whose route map is open, along [route].
     */
    fun chooseRoute(plr: Player, route: TrekRoute) {
        val map = plr.overlays.getOverlay(RouteMap::class.java) ?: return
        if (map.travelling || plr.trek != null || hasToken(plr) || plr.isLocked) return
        val trek = Trek(plr, map.villager, route)
        plr.trek = trek
        trek.sendPoints()
        TrekJourney.start(plr, trek)
    }

    /**
     * Closes the route map, unless a trek is being shown on it.
     */
    fun closeMap(plr: Player) {
        val map = plr.overlays.getOverlay(RouteMap::class.java) ?: return
        if (!map.travelling) plr.overlays.closeWindows()
    }

    /**
     * Whether [plr] holds a reward token anywhere.
     */
    fun hasToken(plr: Player) = TOKENS.any { plr.inventory.contains(it) || plr.bank.contains(it) }

    /**
     * Ends a successful trek at Paterdomus: the villager hands over the route's token and goes on its way.
     */
    fun arrive(plr: Player, trek: Trek) {
        plr.trek = null
        plr.sendVarbit(Varbit(MARKER_VARBIT, 0))
        val villager = trek.villager
        val npc = world.addNpc(Npc(ctx, villager.npc, ARRIVAL_VILLAGER))
        npc.respawnTicks = -1
        npc.face(plr.position)
        plr.face(npc.position)
        plr.trekTokenPoints = trek.points
        plr.trekTokenEvents = trek.events > 0
        // The token is handed over at once, so closing the chat early can't lose it.
        if (!plr.inventory.add(Item(trek.route.token))) {
            world.addItem(trek.route.token, 1, plr.position, plr)
        }
        plr.newDialogue()
            .npc(villager.npc, "Many thanks for getting me safely to the temple. Here's your reward.")
            .then {
                it.sendMessage("${villager.displayName} gives you a reward token!")
                leave(npc)
            }
            .open()
        world.scheduleOnce(100) { if (npc.state == EntityState.ACTIVE) world.removeNpc(npc) }
    }

    /**
     * Walks a villager who has reached the temple off towards the bridge, then out of sight.
     */
    private fun leave(npc: Npc) {
        if (npc.state != EntityState.ACTIVE) return
        npc.navigator.navigate(Position(npc.position.x - 3, npc.position.y), true)
        world.scheduleOnce(5) { if (npc.state == EntityState.ACTIVE) world.removeNpc(npc) }
    }
}
