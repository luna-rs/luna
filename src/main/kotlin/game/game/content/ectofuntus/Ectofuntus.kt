package game.content.ectofuntus

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Manual Ectofuntus training using the native split grinder and slime pools. Each worship returns both
 * containers, grants four times burial XP and earns five tokens, with sixty tokens awaiting collection.
 * Grinder contents and earned tokens are personal saved stock. Ghosts Ahoy is not an access prerequisite.
 * The amulet requirement is temporarily waived until quest support is available.
 *
 * @author lare96
 */
object Ectofuntus {
    const val POT = 1931
    const val BUCKET = 1925
    const val SLIME = 4286
    const val TOKEN = 4278
    const val GHOSTSPEAK = 552
    val GHOSTSPEAK_IDS = setOf(552, 4250)
    const val FOUNTAIN = 5282
    const val LOADER = 11162
    const val GRINDER = 11163
    const val BIN = 11164
    const val TOKEN_CAP = 60
    const val TOKEN_REWARD = 5
    val POOL_IDS = setOf(5461, 5462, 5465, 5466, 5467, 5468, 5470, 5471, 5472)
    val DISCIPLE_IDS = setOf(1686)
    val FOUNTAIN_POSITION = Position(3658, 3518)
    val LOADER_POSITION = Position(3660, 3525, 1)
    val GRINDER_POSITION = Position(3659, 3525, 1)
    val BIN_POSITION = Position(3658, 3525, 1)
    val POOL = Position(3680, 9888)
    val DISCIPLE_POSITION = Position(3655, 3516)
    val requiresGhostspeak: Boolean get() = false

    private var Player.grinderBone by Attr.int { -1 }.persist("ectofuntus_grinder_bone")
    private var Player.grinderGround by Attr.boolean().persist("ectofuntus_grinder_ground")
    private var Player.ectoTokens by Attr.int().persist("ectofuntus_tokens")

    fun loadedBone(player: Player): EctofuntusBone? = EctofuntusBone.forBone(player.grinderBone)
    fun isGround(player: Player): Boolean = loadedBone(player) != null && player.grinderGround
    fun pendingTokens(player: Player): Int = player.ectoTokens.coerceIn(0, TOKEN_CAP)

    /**
     * Activates all persistent fields on login, including logins outside the temple.
     */
    fun restore(player: Player) {
        if (loadedBone(player) == null) {
            player.grinderBone = -1
            player.grinderGround = false
        }
        player.ectoTokens = pendingTokens(player)
    }

    fun load(player: Player, target: GameObject, index: Int, bone: EctofuntusBone) =
        submit(player, target, EctofuntusAction.Step.LOAD, index, bone)

    fun grind(player: Player, target: GameObject) = submit(player, target, EctofuntusAction.Step.GRIND)
    fun collect(player: Player, target: GameObject) = submit(player, target, EctofuntusAction.Step.COLLECT)
    fun fill(player: Player, target: GameObject, index: Int) =
        submit(player, target, EctofuntusAction.Step.FILL, index)
    fun worship(player: Player, target: GameObject) = submit(player, target, EctofuntusAction.Step.WORSHIP)

    private fun submit(player: Player, target: GameObject, step: EctofuntusAction.Step,
                       index: Int = -1, bone: EctofuntusBone? = null) {
        if (!player.isLocked) player.submitAction(EctofuntusAction(player, target, step, index, bone))
    }

    /**
     * Checks the actual mapped machinery, its rotated footprint and the live player's proximity.
     */
    internal fun reachable(player: Player, target: GameObject): Boolean {
        if (player.state != EntityState.ACTIVE || !player.isAlive || target.world != player.world || target.isHidden ||
            target.state != EntityState.ACTIVE || player.position.z != target.position.z) return false
        val mapped = when (target.id) {
            FOUNTAIN -> target.position == FOUNTAIN_POSITION
            LOADER -> target.position == LOADER_POSITION
            GRINDER -> target.position == GRINDER_POSITION
            BIN -> target.position == BIN_POSITION
            in POOL_IDS -> target.position.z == 0 && target.position.x in 3676..3684 &&
                    target.position.y in 9881..9895
            else -> false
        }
        if (!mapped) return false
        val rotated = target.direction.id % 2 != 0
        val width = if (rotated) target.sizeY() else target.sizeX()
        val height = if (rotated) target.sizeX() else target.sizeY()
        return player.position.x in target.position.x - 1..target.position.x + width &&
                player.position.y in target.position.y - 1..target.position.y + height
    }

    internal fun ready(player: Player, target: GameObject, step: EctofuntusAction.Step,
                       index: Int, bone: EctofuntusBone?, notify: Boolean): Boolean {
        if (!reachable(player, target)) return false
        val inventory = player.inventory
        val error = when (step) {
            EctofuntusAction.Step.LOAD -> when {
                target.id != LOADER -> return false
                loadedBone(player) != null -> "The grinder already contains bones."
                bone == null || inventory.computeIdForIndex(index) != bone.boneId -> return false
                else -> null
            }
            EctofuntusAction.Step.GRIND -> when {
                target.id != GRINDER -> return false
                loadedBone(player) == null -> "There are no bones in the grinder."
                isGround(player) -> "The bones are already ground. Empty the bin with a pot."
                else -> null
            }
            EctofuntusAction.Step.COLLECT -> when {
                target.id != BIN -> return false
                loadedBone(player) == null -> "The bin is empty."
                !isGround(player) -> "Turn the grinder's handle first."
                POT !in inventory -> "You need an empty pot to collect the bonemeal."
                else -> null
            }
            EctofuntusAction.Step.FILL -> when {
                target.id !in POOL_IDS || inventory.computeIdForIndex(index) != BUCKET -> return false
                else -> null
            }
            EctofuntusAction.Step.WORSHIP -> when {
                target.id != FOUNTAIN -> return false
                pendingTokens(player) > TOKEN_CAP - TOKEN_REWARD ->
                    "Collect your ecto-tokens from a ghost disciple before worshipping again."
                SLIME !in inventory || meal(player) == null ->
                    "You need a pot of bonemeal and a bucket of slime to worship."
                else -> null
            }
        }
        if (error != null && notify) player.sendMessage(error)
        return error == null
    }

    private fun meal(player: Player): EctofuntusBone? =
        player.inventory.mapNotNull { it?.let { item -> EctofuntusBone.forMeal(item.id) } }.firstOrNull()

    /**
     * Completes one manual step on the game thread after revalidating its state and supplies.
     */
    internal fun finish(player: Player, target: GameObject, step: EctofuntusAction.Step,
                        index: Int, bone: EctofuntusBone?) {
        if (!ready(player, target, step, index, bone, false)) return
        when (step) {
            EctofuntusAction.Step.LOAD -> {
                if (!player.inventory.remove(index, Item(bone!!.boneId))) return
                player.grinderBone = bone.boneId
                player.grinderGround = false
                player.sendMessage("You load the bones into the grinder.")
            }
            EctofuntusAction.Step.GRIND -> {
                player.grinderGround = true
                player.sendMessage("You grind the bones into bonemeal.")
            }
            EctofuntusAction.Step.COLLECT -> {
                if (!player.inventory.replace(POT, loadedBone(player)!!.bonemealId)) return
                player.grinderBone = -1
                player.grinderGround = false
                player.sendMessage("You fill the pot with bonemeal.")
            }
            EctofuntusAction.Step.FILL -> {
                player.inventory[index] = Item(SLIME)
                player.sendMessage("You fill the bucket with slime.")
            }
            EctofuntusAction.Step.WORSHIP -> {
                val recipe = meal(player) ?: return
                val mealIndex = player.inventory.computeIndexForId(recipe.bonemealId)
                val slimeIndex = player.inventory.computeIndexForId(SLIME)
                player.inventory.startBulkUpdate()
                try {
                    player.inventory[mealIndex] = Item(POT)
                    player.inventory[slimeIndex] = Item(BUCKET)
                    player.ectoTokens = pendingTokens(player) + TOKEN_REWARD
                    player.prayer.addExperience(recipe.baseXp * 4.0)
                } finally {
                    player.inventory.finishBulkUpdate()
                }
                player.sendMessage("You worship the Ectofuntus.")
            }
        }
    }

    fun status(player: Player, target: GameObject) {
        if (!reachable(player, target) || player.isLocked) return
        player.sendMessage(when {
            loadedBone(player) == null -> "The grinder is empty. Load some bones into the hopper."
            isGround(player) -> "The bones are ground. Collect the bonemeal from the bin with an empty pot."
            else -> "There are bones in the grinder. Turn the handle to grind them."
        })
    }

    /**
     * Claims earned tokens through the native disciple's Talk-to option, retaining credit if the inventory is full.
     */
    fun claim(player: Player, disciple: Npc) {
        if (player.isLocked || player.state != EntityState.ACTIVE || !player.isAlive ||
            disciple.state != EntityState.ACTIVE || !disciple.isAlive || disciple.world != player.world ||
            disciple.id !in DISCIPLE_IDS ||
            disciple.position.z !in 0..1 || disciple.position.x !in 3650..3667 ||
            disciple.position.y !in 3510..3529 || !player.position.isWithinDistance(disciple.position, 2)) return
        if (requiresGhostspeak && player.equipment[Equipment.AMULET]?.id !in GHOSTSPEAK_IDS) {
            player.newDialogue().npc(disciple.id, "Woooo wooo wooooo.")
                .text("You cannot understand the ghost.").open()
            return
        }
        val tokens = pendingTokens(player)
        val message = when {
            tokens == 0 -> "Worship the Ectofuntus with bonemeal and slime to earn ecto-tokens."
            !player.inventory.add(Item(TOKEN, tokens)) -> "Make room in your inventory for your ecto-tokens."
            else -> {
                player.ectoTokens = 0
                "Thank you for worshipping. Here are your $tokens ecto-tokens."
            }
        }
        player.newDialogue().npc(disciple.id, message).open()
    }

    fun empty(player: Player, index: Int, id: Int, container: Int) {
        if (!player.isLocked && player.state == EntityState.ACTIVE && player.isAlive &&
            player.inventory.computeIdForIndex(index) == id) {
            player.inventory[index] = Item(container)
            player.sendMessage("You empty the container.")
        }
    }
}
