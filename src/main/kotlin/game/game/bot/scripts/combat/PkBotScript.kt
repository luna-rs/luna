package game.bot.scripts.combat

import api.bot.Suspendable.delay
import api.bot.action.BotCombatActionHandler
import api.bot.action.PkCombatObservations
import api.bot.action.PkLoadoutPreparation
import api.bot.action.PkClanPolicy
import api.bot.script.BotScript
import api.bot.social.BotSocialInteractions
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.levelAt
import game.obj.wildernessObelisk.Obelisk
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Wilderness PK bot behaviour script.
 *
 * This script sends a bot into the Wilderness, moves it through configured PK hotspots, searches for valid player
 * targets, and reacts to combat encounters. Once the configured [duration] expires, the bot attempts to flee the
 * Wilderness and finish safely.
 *
 * Movement reuses verified anchors and existing travel helpers. Recreational SKILL sessions stay in low Edgeville;
 * their geographic restriction belongs to this session rather than the account's ordinary combat activities.
 *
 * @param bot The bot controlled by this script.
 * @param duration The maximum amount of time this script should actively PK before fleeing.
 * @param soloClanOuting One outing's confidence-based permission to hunt without another online clanmate.
 * @author lare96
 */
class PkBotScript(bot: Bot, val duration: Duration,
                  val soloClanOuting: Boolean = PkClanPolicy.chooseSolo(bot)) : BotScript(bot) {

    /**
     * Restores the outing's remaining duration, solo decision and current destination without rerolling identity.
     */
    constructor(bot: Bot, data: PkData) : this(bot, data.duration, data.soloClanOuting) {
        patrolDestination = data.patrolDestination?.takeIf { it.z == 0 && levelAt(it) > 0 && allowsHuntingAt(it) }
    }

    override val actionSafety = ActionSafety.COMBAT

    /**
     * Shared native eligibility checks, also used by the combat child's equipment-switch loop.
     */
    private val combatPolicy = BotCombatActionHandler(bot, handler)
    private val observations = PkCombatObservations(bot)
    private val preparation = PkLoadoutPreparation(bot, handler)
    private val clan = PkClanPolicy(bot)

    /**
     * Runtime preparation state. Reloaded sessions rebuild their kit; normal combat pauses keep the current pack.
     * Restocking suppresses owned pursuits and clears native auto-attacks while escape or banking is in progress.
     */
    private var preparationState = PreparationState.NOT_PREPARED
        set(value) {
            field = value
            if (value == PreparationState.RESTOCKING || value == PreparationState.LEAVING) bot.combat.target = null
        }

    /**
     * Keeps resupply separate from a final departure, so an aborted trip cannot restart after prices change.
     *
     * @author lare96
     */
    private enum class PreparationState { NOT_PREPARED, READY, RESTOCKING, LEAVING }

    /**
     * Leaves escape and bank preparation with the parent instead of repeatedly queuing defensive combat children.
     */
    fun isRestocking(): Boolean = preparationState in setOf(PreparationState.RESTOCKING, PreparationState.LEAVING)


    /**
     * Shared PK bot constants.
     */
    companion object {

        /**
         * The surface region immediately north of Edgeville, covering native Wilderness levels 1 through 8.
         * Existing LOW_LEVEL patrol anchors in this region supply entry and wandering destinations.
         */
        private const val EDGEVILLE_PK_REGION = 12343

        /**
         * Low-level Wilderness anchor points used for entering, escaping, and returning toward safer Wilderness levels.
         *
         * These are spread across the lower Wilderness so bots do not always enter or escape through the exact same
         * tile.
         */
        val LOW_LEVEL_ANCHOR_POINTS = listOf(
            Position(2978, 3607, 0),
            Position(3030, 3611, 0),
            Position(3079, 3620, 0),
            Position(3100, 3633, 0),
            Position(3164, 3616, 0),
            Position(3198, 3618, 0),
            Position(3234, 3637, 0),
            Position(3274, 3612, 0),
            Position(3331, 3640, 0),
            Position(3367, 3641, 0),
            Position(3162, 3668, 0),
            Position(3251, 3662, 0),
            Position(3310, 3672, 0),
            Position(3060, 3662, 0),
            Position(3020, 3667, 0),
            Position(2987, 3672, 0),
            Position(2969, 3667, 0),
            Position(3102, 3671, 0),
            Position(3143, 3677, 0)
        )

        class PkData : BotScriptData() {
            var duration = Duration.ZERO
            var soloClanOuting = false
            var patrolDestination: Position? = null
            override fun load(data: JsonObject) {
                duration = data.get("duration").asLong.milliseconds
                soloClanOuting = data.get("soloClanOuting")?.asBoolean ?: false
                patrolDestination = data.getAsJsonObject("patrol")?.let {
                    Position(it.get("x").asInt, it.get("y").asInt, it.get("z").asInt)
                }
            }

            override fun save(data: JsonObject) {
                data.addProperty("duration", duration.inWholeMilliseconds)
                data.addProperty("soloClanOuting", soloClanOuting)
                patrolDestination?.let { position ->
                    data.add("patrol", JsonObject().apply {
                        addProperty("x", position.x)
                        addProperty("y", position.y)
                        addProperty("z", position.z)
                    })
                }
            }
        }
    }

    /**
     * Common Wilderness PK areas that bots can travel through while looking for targets.
     *
     * Each area contains a set of region ids used for rough location grouping and a list of anchor points used for
     * wandering, routing, and hotspot selection.
     *
     * @property regions The map region ids covered by this PK area.
     * @property anchors Known walkable anchor points inside or near this PK area.
     */
    enum class PkArea(val regions: Set<Int>, val anchors: List<Position>) {

        /**
         * Lower Wilderness routes near the ditch and early Wilderness combat levels.
         */
        LOW_LEVEL(regions = setOf(11831, 12087, 12343, 12599, 12855, 13111),
                  anchors = listOf(Position(2962, 3558, 0),
                                   Position(2984, 3563, 0),
                                   Position(2998, 3567, 0),
                                   Position(3023, 3570, 0),
                                   Position(3039, 3561, 0),
                                   Position(3052, 3549, 0),
                                   Position(3067, 3542, 0),
                                   Position(3088, 3542, 0),
                                   Position(3096, 3547, 0),
                                   Position(3091, 3528, 0),
                                   Position(3106, 3529, 0),
                                   Position(3131, 3543, 0),
                                   Position(3163, 3533, 0),
                                   Position(3193, 3533, 0),
                                   Position(3209, 3548, 0),
                                   Position(3182, 3554, 0),
                                   Position(3194, 3571, 0),
                                   Position(3221, 3558, 0),
                                   Position(3234, 3542, 0),
                                   Position(3254, 3548, 0),
                                   Position(3274, 3554, 0),
                                   Position(3287, 3539, 0),
                                   Position(3300, 3554, 0),
                                   Position(3308, 3566, 0))),

        /**
         * Level-10 Chaos Temple area and the nearby low-to-mid Wilderness routes.
         */
        CHAOS_TEMPLE_LVL_10(regions = setOf(12856),
                            anchors = listOf(Position(3207, 3587, 0),
                                             Position(3210, 3610, 0),
                                             Position(3217, 3629, 0),
                                             Position(3234, 3638, 0),
                                             Position(3255, 3632, 0),
                                             Position(3238, 3620, 0),
                                             Position(3241, 3612, 0),
                                             Position(3238, 3600, 0),
                                             Position(3227, 3609, 0),
                                             Position(3229, 3587, 0))),

        /**
         * Graveyard of Shadows hotspot and surrounding level-20s Wilderness movement routes.
         */
        GRAVEYARD_OF_SHADOWS(regions = setOf(12601),
                             anchors = listOf(Position(3140, 3705, 0),
                                              Position(3151, 3697, 0),
                                              Position(3160, 3707, 0),
                                              Position(3176, 3695, 0),
                                              Position(3189, 3699, 0),
                                              Position(3193, 3676, 0),
                                              Position(3192, 3655, 0),
                                              Position(3182, 3651, 0),
                                              Position(3145, 3654, 0),
                                              Position(3144, 3668, 0),
                                              Position(3154, 3670, 0),
                                              Position(3163, 3670, 0),
                                              Position(3178, 3671, 0),
                                              Position(3172, 3678, 0),
                                              Position(3185, 3672, 0))),

        /**
         * Green dragon hotspot routes near the western level-20s Wilderness.
         */
        GREEN_DRAGONS(regions = setOf(12345, 12601),
                      anchors = listOf(Position(3078, 3707, 0),
                                       Position(3100, 3707, 0),
                                       Position(3102, 3698, 0),
                                       Position(3120, 3699, 0),
                                       Position(3118, 3685, 0),
                                       Position(3132, 3695, 0),
                                       Position(3143, 3701, 0),
                                       Position(3155, 3706, 0),
                                       Position(3153, 3699, 0),
                                       Position(3170, 3708, 0),
                                       Position(3176, 3696, 0),
                                       Position(3188, 3699, 0),
                                       Position(3082, 3681, 0))),

        /**
         * Bandit Camp and nearby west Wilderness routes.
         */
        BANDIT_CAMP(regions = setOf(12089),
                    anchors = listOf(Position(3016, 3657, 0),
                                     Position(3016, 3674, 0),
                                     Position(3017, 3702, 0),
                                     Position(3056, 3660, 0),
                                     Position(3068, 3666, 0),
                                     Position(3068, 3691, 0),
                                     Position(3039, 3698, 0),
                                     Position(3030, 3705, 0),
                                     Position(3050, 3708, 0),
                                     Position(3037, 3673, 0),
                                     Position(3038, 3653, 0))),

        /**
         * Forgotten Cemetery hotspot and surrounding level-30s Wilderness routes.
         */
        THE_FORGOTTEN_CEMETERY(regions = setOf(11834),
                               anchors = listOf(Position(2958, 3718, 0),
                                                Position(2973, 3723, 0),
                                                Position(2989, 3716, 0),
                                                Position(2999, 3720, 0),
                                                Position(3003, 3731, 0),
                                                Position(3003, 3744, 0),
                                                Position(3002, 3769, 0),
                                                Position(2966, 3772, 0),
                                                Position(2953, 3754, 0),
                                                Position(2966, 3750, 0),
                                                Position(2984, 3752, 0),
                                                Position(2976, 3735, 0),
                                                Position(2981, 3734, 0))),

        /**
         * Western level-20 ruins routes.
         */
        RUINS_LVL_20(regions = setOf(11833),
                     anchors = listOf(Position(2960, 3653, 0),
                                      Position(2969, 3656, 0),
                                      Position(2993, 3653, 0),
                                      Position(3002, 3664, 0),
                                      Position(3000, 3676, 0),
                                      Position(2993, 3695, 0),
                                      Position(2980, 3705, 0),
                                      Position(2967, 3695, 0),
                                      Position(2997, 3701, 0),
                                      Position(2981, 3670, 0))),

        /**
         * Central level-30 ruins routes near Web Chasm.
         */
        RUINS_LVL_30(regions = setOf(12602),
                     anchors = listOf(Position(3142, 3715, 0),
                                      Position(3156, 3725, 0),
                                      Position(3167, 3727, 0),
                                      Position(3179, 3734, 0),
                                      Position(3162, 3740, 0),
                                      Position(3146, 3751, 0),
                                      Position(3148, 3762, 0),
                                      Position(3154, 3767, 0),
                                      Position(3179, 3765, 0),
                                      Position(3188, 3754, 0),
                                      Position(3167, 3755, 0),
                                      Position(3192, 3743, 0),
                                      Position(3193, 3730, 0),
                                      Position(3193, 3719, 0),
                                      Position(3179, 3717, 0),
                                      Position(3162, 3715, 0),
                                      Position(3154, 3715, 0))),

        /**
         * Deep-west Chaos Temple routes near the level-40 Wilderness area.
         */
        CHAOS_TEMPLE_LVL_40(regions = setOf(11835),
                            anchors = listOf(Position(2955, 3785, 0),
                                             Position(2976, 3788, 0),
                                             Position(2988, 3796, 0),
                                             Position(2996, 3814, 0),
                                             Position(2999, 3821, 0),
                                             Position(3000, 3832, 0),
                                             Position(2984, 3832, 0),
                                             Position(2966, 3834, 0),
                                             Position(2951, 3832, 0),
                                             Position(2960, 3825, 0),
                                             Position(2979, 3821, 0),
                                             Position(2983, 3815, 0),
                                             Position(2962, 3807, 0),
                                             Position(2961, 3798, 0),
                                             Position(2951, 3802, 0),
                                             Position(2959, 3819, 0),
                                             Position(2952, 3820, 0))),

        /**
         * Lava Maze and surrounding deep Wilderness routes.
         */
        LAVA_MAZE(regions = setOf(12092, 12348, 12091, 12347),
                  anchors = listOf(Position(3021, 3875, 0),
                                   Position(3040, 3887, 0),
                                   Position(3030, 3894, 0),
                                   Position(3047, 3895, 0),
                                   Position(3059, 3889, 0),
                                   Position(3045, 3876, 0),
                                   Position(3067, 3894, 0),
                                   Position(3084, 3900, 0),
                                   Position(3082, 3886, 0),
                                   Position(3101, 3893, 0),
                                   Position(3103, 3900, 0),
                                   Position(3119, 3894, 0),
                                   Position(3128, 3889, 0),
                                   Position(3123, 3873, 0),
                                   Position(3126, 3862, 0),
                                   Position(3131, 3846, 0),
                                   Position(3130, 3829, 0),
                                   Position(3120, 3823, 0),
                                   Position(3118, 3815, 0),
                                   Position(3115, 3793, 0),
                                   Position(3100, 3785, 0),
                                   Position(3091, 3797, 0),
                                   Position(3094, 3806, 0),
                                   Position(3095, 3816, 0),
                                   Position(3079, 3804, 0),
                                   Position(3076, 3808, 0),
                                   Position(3075, 3787, 0),
                                   Position(3065, 3794, 0),
                                   Position(3064, 3800, 0),
                                   Position(3065, 3813, 0),
                                   Position(3046, 3820, 0),
                                   Position(3020, 3820, 0),
                                   Position(3025, 3804, 0),
                                   Position(3035, 3790, 0),
                                   Position(3039, 3800, 0),
                                   Position(3026, 3829, 0))),

        /**
         * Demonic Ruins routes in the north-east deep Wilderness.
         */
        DEMONIC_RUINS(regions = setOf(13116),
                      anchors = listOf(Position(3271, 3894, 0),
                                       Position(3283, 3897, 0),
                                       Position(3298, 3895, 0),
                                       Position(3310, 3887, 0),
                                       Position(3322, 3877, 0),
                                       Position(3278, 3867, 0),
                                       Position(3277, 3875, 0),
                                       Position(3276, 3879, 0),
                                       Position(3277, 3886, 0)));

        // TODO@1.0 Add deep wilderness locations once doors/gates are completed.

        /**
         * Immutable list of all configured PK areas.
         */
        companion object {
            val ALL = ImmutableList.copyOf(values())
        }
    }

    /**
     * Absolute script expiry time in milliseconds.
     *
     * Once this time is reached, the bot stops hunting and attempts to flee the Wilderness.
     */
    var expireAt: Long = System.currentTimeMillis() + duration.toLong(DurationUnit.MILLISECONDS)

    /**
     * Retains the current patrol goal through combat pauses and snapshots. Short loiter periods allow local searches.
     */
    internal var patrolDestination: Position? = null
        private set
    private var nextPatrolCycle = 0L

    /**
     * Restricts recreational PK trips, without changing this account's NPC training or other coordinators.
     */
    private val isSkillSession: Boolean get() = bot.preferences.pk?.purpose() == Purpose.SKILL

    /**
     * Checks the session's hunting boundary. SKILL sessions stay on the surface north of Edgeville;
     * their combat-level matchups still use the ordinary two-sided Wilderness range.
     */
    internal fun allowsHuntingAt(position: Position): Boolean = !isSkillSession ||
        (position.z == 0 && position.regionId == EDGEVILLE_PK_REGION && levelAt(position) > 0)

    /**
     * Keeps both ends of an owned pursuit inside the hunting area, including after equipment suspensions.
     * Kind GENERIC fights also require a skull or a continuing defensive reason.
     * Uncertain non-kind GENERIC bots only engage a current or recently received attacker.
     * Current death loss must remain affordable after stock, skull, prayer or equipment changes.
     * Defensive combat started by another coordinator does not inherit this session boundary.
     */
    internal fun allowsPursuit(target: Player): Boolean {
        if (isRestocking() || System.currentTimeMillis() >= expireAt) return false
        if (!allowsHuntingAt(bot.position) || !allowsHuntingAt(target.position)) return false
        if (!combatPolicy.pkRiskBudget().withinBudget) return false
        if (clan.enabled) return clan.wearingCape() &&
            (!clan.apparentAlly(target) || observations.canDefendClanAgainst(target) || combatPolicy.canRetaliateAgainst(target)) &&
            (clanCanHunt() || combatPolicy.canRetaliateAgainst(target))
        if (!isGenericSession()) return true
        if (isKindGenericSession()) return target.skullIcon != SkullIcon.NONE ||
            observations.canProtectAgainst(target) || combatPolicy.canRetaliateAgainst(target)
        return !bot.personality.isUncertain || combatPolicy.canRetaliateAgainst(target)
    }

    /**
     * Enables local observations for GENERIC PK sessions without changing the permanent purpose or style identity.
     */
    internal fun isGenericSession(): Boolean = bot.preferences.pk?.purpose() == Purpose.GENERIC &&
        !bot.preferences.isSkiller

    /**
     * Uses the established kindness threshold; saved PK purpose remains separate from the personality template.
     */
    internal fun isKindGenericSession(): Boolean = isGenericSession() && bot.personality.isKind

    /**
     * Clanmates may be anywhere online. A solo decision belongs to this outing and never changes saved allegiance.
     */
    internal fun clanCanHunt(): Boolean = !clan.enabled || soloClanOuting || clan.onlineMembers(activePkOnly = false).isNotEmpty()

    /**
     * Enables fresh attack evidence for kind protection and cape-based clan infighting.
     */
    internal fun observesEncounters(): Boolean = isGenericSession() || clan.enabled

    /**
     * Leaves active patrol/escape paths intact and stops incidental NPC auto-attacks. Only an idle PK parent
     * nudges away, within its hunting boundary; another coordinator's NPC training retains normal combat ownership.
     */
    fun avoidNpcCombat(npc: Npc) {
        if (bot.combat.target is Player) return
        if (bot.combat.target is Npc) bot.combat.target = null
        combatPolicy.stepAwayFrom(npc) { allowsHuntingAt(it) && levelAt(it) > 0 }
    }

    /**
     * Applies purpose-specific initiation. Kind GENERIC bots may challenge idle skulled players or protect a
     * witnessed victim, including against an unskulled aggressor. Unknown ongoing fights are left alone.
     * Non-kind uncertain bots only retaliate. Other non-kind bots can initiate, with vulnerability preference
     * applied separately unless they are stupidly confident. Clan sessions challenge other capes and witnessed
     * friendly initiators, requiring their own cape and an online clanmate unless this is a solo outing.
     */
    internal fun wantsToAttack(target: Player): Boolean = when (bot.preferences.pk?.purpose()) {
        Purpose.SKILL -> BotCombatActionHandler.isPker(target)
        Purpose.VALUE -> true
        Purpose.CLAN -> clan.enabled && clan.wearingCape() && clanCanHunt() &&
            levelAt(bot.position) >= 30 && levelAt(target.position) >= 30 &&
            (!clan.apparentAlly(target) || observations.canDefendClanAgainst(target))
        Purpose.GENERIC -> if (isKindGenericSession()) observations.canProtectAgainst(target) ||
            (target.skullIcon != SkullIcon.NONE && !target.combat.inCombat() && target.combat.target == null)
            else !bot.personality.isUncertain && !bot.preferences.isSkiller
        else -> false
    }

    /**
     * Records only attack starts visible during this session; a later damage impact supplies no new evidence.
     */
    internal fun observeAttack(attacker: Player, victim: Player) {
        if (isKindGenericSession() || clan.enabled) observations.attackStarted(attacker, victim, clan::apparentAlly)
    }

    /**
     * Maintains consecutive visible movement samples while this session or a combat child is active.
     */
    internal fun observeMovement(players: Collection<Player>) {
        if (observesEncounters()) observations.observe(players)
    }

    /**
     * Preserves ordinary patrol ordering for reckless challengers. Other non-kind GENERIC bots prefer visible
     * vulnerability, while kind bots prioritize a witnessed victim's aggressor over ordinary skull challenges.
     * This ranks an already legal candidate list; it never grants permission to interfere in single combat.
     */
    internal fun orderTargets(targets: List<Player>): List<Player> = when {
        clan.enabled -> {
            val assisting = clan.onlineMembers().filter { clan.apparentAlly(it) }
                .mapNotNull { it.combat.target as? Player }.toSet()
            targets.sortedByDescending { if (observations.canDefendClanAgainst(it)) 2 else if (it in assisting) 1 else 0 }
        }
        isKindGenericSession() -> targets.sortedByDescending { observations.canProtectAgainst(it) }
        bot.preferences.pk?.purpose() == Purpose.VALUE -> targets.sortedBy { BotCombatActionHandler.isPker(it) }
        isGenericSession() && !bot.personality.isStupidlyConfident && !bot.personality.isUncertain ->
            targets.sortedByDescending { combatPolicy.pkTargetVulnerability(it, observations.isFleeing(it)) }
        else -> targets
    }

    /**
     * Prioritizes a known aggressor over ordinary patrol travel, without interrupting another active fight.
     * Native single-combat rules may prevent intervention even when the bot witnessed an attack.
     */
    internal fun protectObservedPlayer(): Boolean {
        if ((!isKindGenericSession() && !clan.enabled) || bot.scriptStack.current() !== this || bot.combat.inCombat() ||
            System.currentTimeMillis() >= expireAt) return false
        val attacker = world.locator.findViewablePlayers(bot).firstOrNull {
            (if (clan.enabled) observations.canDefendClanAgainst(it) else observations.canProtectAgainst(it)) &&
                allowsPursuit(it) && combatPolicy.canAttackWildernessPlayer(it)
        } ?: return false
        bot.scriptStack.pushHead(CombatBotScript(bot, attacker, pkSession = this))
        return true
    }

    /**
     * SKILL stays in low Edgeville. VALUE covers resource hunters and obelisk arrivals. CLAN patrols deep surface
     * Wilderness; GENERIC retains the full existing area selection. Travel is delegated to the shared walker.
     */
    internal fun patrolAnchors(): List<Position> {
        val groups = when (bot.preferences.pk?.purpose()) {
            Purpose.SKILL -> listOf(PkArea.LOW_LEVEL.anchors.filter { allowsHuntingAt(it) })
            Purpose.VALUE -> listOf(PkArea.GREEN_DRAGONS.anchors, PkArea.CHAOS_TEMPLE_LVL_10.anchors,
                PkArea.CHAOS_TEMPLE_LVL_40.anchors, Obelisk.ALL.map { it.anchor },
                listOfNotNull(SubZone.KBD_LAIR.outside(bot)))
            Purpose.CLAN -> listOf(PkArea.CHAOS_TEMPLE_LVL_40.anchors, PkArea.LAVA_MAZE.anchors,
                PkArea.DEMONIC_RUINS.anchors)
            else -> PkArea.ALL.map { it.anchors }
        }
        return groups.map { group -> group.filter { it.z == 0 && levelAt(it) > 0 } }
            .filter { it.isNotEmpty() }.random()
    }

    /**
     * Ordinary clan outings share the lowest-username-hash active member's current deep patrol goal.
     * Solo outings choose independently, retaining clan allegiance and local assistance rules.
     */
    internal fun selectPatrolDestination(): Position {
        if (clan.enabled && !soloClanOuting) {
            val leader = (clan.onlineMembers().filter { clan.apparentAlly(it) } + bot).minBy { it.usernameHash }
            if (leader !== bot) leader.scriptStack.findActive(PkBotScript::class.java).orElse(null)
                ?.patrolDestination?.takeIf { it.z == 0 && levelAt(it) >= 30 }?.let { return it }
        }
        return patrolAnchors().random()
    }

    override suspend fun init(resumed: Boolean): Boolean {
        if (!bot.preferences.canChooseActivity(io.luna.game.model.mob.bot.brain.BotActivity.PKING)) return true
        return false
    }

    override fun paused() {
        BotSocialInteractions.cancel(bot)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        BotSocialInteractions.cancel(bot)
        if (!isPaused()) observations.clear()
    }

    override suspend fun run(): Boolean {
        if (BotSocialInteractions.participate(bot)) {
            delay(1.seconds)
            return false
        }
        if (!bot.preferences.canChooseActivity(io.luna.game.model.mob.bot.brain.BotActivity.PKING)) return true
        if (!clan.ensureCape()) preparationState = PreparationState.LEAVING
        if (!clanCanHunt()) preparationState = PreparationState.LEAVING
        if (System.currentTimeMillis() > expireAt) {
            preparationState = PreparationState.LEAVING
        }
        if (preparationState == PreparationState.LEAVING) {
            return handler.combat.fleeWilderness()
        }
        if (preparationState != PreparationState.READY || !preparation.ready()) {
            preparationState = PreparationState.RESTOCKING
            if (bot.inWilderness()) {
                handler.combat.fleeWilderness()
                return false
            }
            if (!ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!preparation.prepare()) {
                preparationState = PreparationState.LEAVING
                return if (bot.inWilderness()) handler.combat.fleeWilderness() else true
            }
            preparationState = PreparationState.READY
            return false
        }
        val risk = combatPolicy.pkRiskBudget()
        if (!risk.withinBudget) {
            preparationState = PreparationState.LEAVING
            bot.log("PK death loss ${risk.deathLoss} exceeds allowance ${risk.maximumLoss}. Ending the trip safely.")
            return handler.combat.fleeWilderness()
        }

        if (checkCombat()) {
            bot.log("checkCombat handled current state.")
            delay(1.seconds, 3.seconds)
            return false
        }

        if (!enterWild()) {
            bot.log("enterWild returned false.")
            delay(1.seconds, 3.seconds)
            return false
        }

        if (searchAndAttack()) return false
        travelToNewArea()

        if (!searchAndAttack()) {
            bot.log("No target found. Delaying before retry.")
            delay(1.seconds, 3.seconds)
            return false
        }

        bot.log("Target found. Combat script should now be active.")
        // The paused parent must resume after its combat child; selecting a fight does not complete the session.
        return false
    }

    override fun snapshot(): PkData {
        val remaining = expireAt - System.currentTimeMillis()
        val data = PkData()
        data.duration = remaining.coerceAtLeast(0).milliseconds
        data.soloClanOuting = soloClanOuting
        data.patrolDestination = patrolDestination
        return data
    }

    /**
     * Handles the bot's current combat state.
     *
     * If the bot is attacked outside the Wilderness, it immediately teleports home. Wilderness NPC combat retains
     * existing travel or takes an idle local retreat step. Wilderness player combat pushes a dedicated
     * [CombatBotScript] so the bot can fight back using combat-specific behaviour.
     *
     * @return `true` if combat state was handled this tick, otherwise `false`.
     */
    suspend fun checkCombat(): Boolean {
        val attacker = bot.combat.lastCombatWith

        if (bot.combat.inCombat() && attacker != null) {
            bot.log(
                "In combat. attacker=${attacker.javaClass.simpleName}, " +
                        "inWilderness=${bot.inWilderness()}, attacker=$attacker"
            )

            if (!bot.inWilderness()) {
                bot.log("Bot is in combat outside wilderness. Sending home command.")
                output.sendCommand("home")
                return true
            } else if (attacker is Npc) {
                avoidNpcCombat(attacker)
                return allowsHuntingAt(bot.position)
            } else if (attacker is Player) {
                bot.log("Player attacker detected. Pushing CombatBotScript for $attacker")
                bot.scriptStack.pushHead(CombatBotScript(bot, attacker, pkSession = this))
                return true
            }
        }

        return false
    }

    /**
     * Searches nearby viewable players and queues a combat child for the first eligible target.
     *
     * Candidates must pass the shared Wilderness, visibility, combat-level, controller and single-combat checks.
     * If multiple candidates are found, the bot may sort them by distance or health. The child owns equipment
     * readiness, reaction timing and the first attack; this patrol never attacks directly.
     *
     * @return `true` if a combat child was queued, otherwise `false`.
     */
    fun searchAndAttack(): Boolean {

        /**
         * Checks whether [other] is a valid target candidate for this bot.
         */
        fun check(other: Player): Boolean {
            return allowsPursuit(other) && wantsToAttack(other) &&
                combatPolicy.canAttackWildernessPlayer(other)
        }

        if (bot.combat.inCombat()) {
            bot.log("Skipping target search because bot is already in combat.")
            return false
        }

        if (!bot.inWilderness()) {
            bot.log("Skipping target search because bot is not in wilderness.")
            return false
        }

        val targets = ArrayList<Player>()
        for (other in world.locator.findViewablePlayers(bot)) {
            if (other != bot && check(other)) {
                targets += other
            }
        }

        bot.log("Found ${targets.size} candidate targets in view.")

        if (targets.size > 1) {
            if (rand(1 of 3)) {
                bot.log("Sorting candidate targets by distance.")
                targets.sortWith(LocatableDistanceComparator(bot))
            } else if (rand(1 of 4)) {
                bot.log("Sorting candidate targets by health percent.")
                targets.sortBy { it.healthPercent }
            } else {
                bot.log("Leaving candidate targets unsorted.")
            }
        }

        for (other in orderTargets(targets)) {
            if (check(other)) {
                bot.log("Selected target $other. Pushing CombatBotScript.")
                // The child owns the first attack, after its reaction delay and carried-supply checks.
                bot.scriptStack.pushHead(CombatBotScript(bot, other, pkSession = this))
                return true
            }
        }

        bot.log("No valid target selected after filtering.")
        return false
    }

    /**
     * Keeps an unfinished patrol goal, then loiters for 8–20 cycles after arrival before selecting another.
     * Active walking/navigation is never replaced. Nearby opponents are searched before this travel begins.
     */
    internal suspend fun travelToNewArea() {
        if (!bot.combat.inCombat() &&
            bot.inWilderness() &&
            !bot.navigator.isActive &&
            bot.walking.isEmpty && bot.cycles >= nextPatrolCycle
        ) {
            val anchor = patrolDestination ?: selectPatrolDestination().also { patrolDestination = it }
            bot.log("Travelling to PK patrol anchor=$anchor")
            val reached = handler.webWalkTo(anchor, bank = BankAccess.NONE)
            if (reached && bot.position.z == anchor.z && bot.position.computeLongestDistance(anchor) <= 2) {
                patrolDestination = null
                nextPatrolCycle = bot.cycles + rand(8, 20)
            } else {
                patrolDestination = null
                nextPatrolCycle = bot.cycles + 8
            }
        }
    }

    /**
     * Ensures the bot is inside the Wilderness before searching for targets.
     *
     * A SKILL session starting elsewhere in the Wilderness also returns to low Edgeville before hunting.
     * The existing walking helper owns travel; reaching a destination must satisfy the live hunting boundary.
     *
     * @return `true` if the bot is in the Wilderness, otherwise `false`.
     */
    internal suspend fun enterWild(): Boolean {
        if (!combatPolicy.pkRiskBudget().withinBudget || !clan.wearingCape() || !clanCanHunt()) return false
        if (!bot.inWilderness() || !allowsHuntingAt(bot.position)) {
            val anchor = if (isSkillSession) patrolAnchors().random() else LOW_LEVEL_ANCHOR_POINTS.random()

            bot.log("Bot is outside wilderness. Walking to low level anchor $anchor")
            handler.webWalkTo(anchor, bank = BankAccess.NONE)

            if (!bot.inWilderness() || !allowsHuntingAt(bot.position) || !clan.wearingCape() || !clanCanHunt() ||
                !combatPolicy.pkRiskBudget().withinBudget) {
                bot.log("Reached anchor but bot is still not in wilderness. position=${bot.position}")
                return false
            }

            bot.log("Bot entered wilderness successfully at position=${bot.position}")
        }
        return true
    }
}
