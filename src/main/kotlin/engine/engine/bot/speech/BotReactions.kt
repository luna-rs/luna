package engine.bot.speech

import api.attr.Attr
import api.bot.action.BotCombatThreat
import io.luna.game.model.mob.bot.Bot
import api.predef.*
import engine.bot.speech.BotPkingSpeechPool.PkingSpeech
import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import engine.controllers.Controllers.inWilderness
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.def.ItemNicknameDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionalTrigger
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.util.StringUtils
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Coordinates event-driven emotional and speech reactions for bots. Bot reactions are small behavior hooks
 * that let bots respond to notable gameplay events.
 *
 * This object also owns the temporary reaction context used by [BotReactionSpeechPool]. Before a reaction line is taken,
 * the relevant context value is stored on the bot through an attribute, allowing speech placeholders such as `<name>`,
 * `<skill>`, `<level>`, and `<drop>` to resolve to event-specific values.
 *
 * @author lare96
 */
object BotReactions {

    /**
     * The most recent player name that impressed this bot.
     *
     * This value is used by [ReactionSpeech.IMPRESSED] lines to resolve the `<name>` placeholder.
     */
    val Bot.lastImpressedBy by Attr.string { "null" }

    /**
     * The most recent skill advancement made by this bot.
     *
     * The first component stores the lowercase skill name and the second component stores the newly reached level. These
     * values are used by [ReactionSpeech.LEVEL_UP] lines to resolve the `<skill>` and `<level>` placeholders.
     */
    var Bot.lastSkillAdvanced by Attr.obj { "null" to 0 }

    /**
     * The most recent player name this bot witnessed gaining a level.
     *
     * This value is used by [ReactionSpeech.OTHER_LEVEL_UP] lines to resolve the `<name>` placeholder.
     */
    var Bot.lastWitnessedSkillAdvance by Attr.string { "null" }

    /**
     * The most recent rare or valuable drop received by this bot.
     *
     * This value is used by [ReactionSpeech.RARE_DROP] lines to resolve the `<drop>` placeholder.
     */
    var Bot.lastRareDrop by Attr.obj { "null" }

    /**
     * The most recent player or bot that successfully scammed this bot.
     *
     * This value is used by [ReactionSpeech.SCAMMED] lines to resolve the `<name>` placeholder.
     */
    var Bot.lastScammedBy by Attr.obj { "null" }

    /**
     * When the scare from the last threat that attacked this bot wears off. See [reactToThreat].
     */
    private var Bot.threatScareEnds by Attr.obj { Instant.EPOCH }

    /**
     * Makes nearby bots react after a player dies.
     *
     * If both the killer and victim are bots and the death happened in the Wilderness, the killer and victim may also queue
     * PK-specific end-of-fight speech before nearby witnesses react. Witnessing bots then roll against their social
     * personality trait and may queue an [ReactionSpeech.OTHER_DIED] line with a short randomized delay.
     *
     * @param source The mob that caused the death, or `null` if there is no known source.
     * @param died The player who died.
     */
    fun reactToOtherDied(source: Mob?, died: Player) {
        if (source is Bot && died is Bot && died.inWilderness()) {
            val delay = 1..3
            if(rand(source.personality.social)) {
                val sourceText =
                    BotPkingSpeechPool.take(source, if (rand(source.personality.kindness)) PkingSpeech.POSITIVE_END else
                        PkingSpeech.NEGATIVE_END)
                source.speechStack.pushHead(BotSpeech(sourceText, rand(delay)))
            }
            if (rand(died.personality.kindness)) {
                val diedText = BotPkingSpeechPool.take(died, PkingSpeech.POSITIVE_END)
                died.speechStack.pushHead(BotSpeech(diedText, rand(delay)))
            }
        }

        // Ignore if the source or victim, if currently in combat with a player, or if the social check fails.
        world.locator.findViewablePlayers(died) { it != died && it.combat.target !is Player && it != source &&
                it is Bot && rand(it.personality.social) }
            .forEach {
                val bot = it as Bot
                val text = BotReactionSpeechPool.take(bot, ReactionSpeech.OTHER_DIED)
                val delay = rand(4, 12)
                bot.speechStack.pushHead(BotSpeech(text, delay))
            }
    }

    /**
     * Makes a bot react to its own death.
     *
     * Death reduces happiness, with lower-confidence bots having a chance to lose additional happiness. It also scares
     * the bot for 10 minutes, the less confident the more. The bot then queues a [ReactionSpeech.DIED] line after a
     * randomized delay.
     *
     * @param died The bot that died.
     * @param killer The mob that killed this bot.
     */
    fun reactToDied(died: Bot, killer: Mob) {
        var happinessLoss = 0.25
        if (rand(1.0 - died.personality.confidence)) {
            happinessLoss += 0.25
        }
        died.emotions.add(EmotionalTrigger(EmotionType.HAPPY, -happinessLoss))
        died.emotions.add(EmotionalTrigger(EmotionType.SCARED, 0.25 * (1.0 - died.personality.confidence),
                                           minutesFromNow(10)))
        if (killer is Player) {
            died.preferences.adjustFeelingsToward(killer.username, -0.25)
        }

        val text = BotReactionSpeechPool.take(died, ReactionSpeech.DIED)
        val delay = rand(8, 20)
        died.speechStack.pushHead(BotSpeech(text, delay))
    }

    /**
     * Makes a bot happier and less greedy for ten minutes after a completed trade increases stock it needed.
     *
     * The confirmation handler checks actual ownership gains before invoking this reaction. The adjustment is
     * applied once per qualifying trade, regardless of how many wanted item types were received.
     *
     * @param bot The bot that acquired needed stock.
     */
    fun reactToWantedTrade(bot: Bot) {
        val expire = minutesFromNow(10)
        bot.emotions.add(EmotionalTrigger(EmotionType.HAPPY, 0.10, expire))
        bot.emotions.add(EmotionalTrigger(EmotionType.GREEDY, -0.10, expire))
    }

    /**
     * Makes a bot react to a kill. Killing a player increases happiness by 0.25 and reduces greed by 0.10 for one hour.
     * Killing a threatening NPC increases happiness by 0.10 and reduces greed by 0.10 for ten minutes.
     * Difficulty uses full-health capabilities so the dead victim and supplies spent winning cannot change the reward.
     *
     * @param killer The bot that made the kill.
     * @param victim The mob it killed.
     */
    fun reactToKill(killer: Bot, victim: Mob) {
        if (victim is Player || killer.actionHandler.combat.isThreat(victim, BotCombatThreat.Context.ACHIEVEMENT)) {
            val playerKill = victim is Player
            val expire = minutesFromNow(if (playerKill) 60 else 10)
            killer.emotions.add(EmotionalTrigger(EmotionType.HAPPY, if (playerKill) 0.25 else 0.10, expire))
            killer.emotions.add(EmotionalTrigger(EmotionType.GREEDY, -0.10, expire))
        }
    }

    /**
     * Makes a bot react to getting away from a fight. Getting away alive under 20% health was a narrow escape, which
     * scares it a little for 5 minutes.
     *
     * @param bot The bot that got away.
     */
    fun reactToEscape(bot: Bot) {
        if (bot.isAlive && bot.healthPercent < 20) {
            bot.emotions.add(EmotionalTrigger(EmotionType.SCARED, 0.15, minutesFromNow(5)))
        }
    }

    /**
     * Makes a bot that isn't confident react to being attacked by a threat, which scares it a little for 2 minutes.
     * Another threat in that time doesn't add to it.
     *
     * @param bot The bot being attacked.
     * @param attacker The mob attacking it.
     */
    fun reactToThreat(bot: Bot, attacker: Mob) {
        val now = Instant.now()
        if (!bot.personality.isConfident && now.isAfter(bot.threatScareEnds) &&
            bot.actionHandler.combat.isThreat(attacker)) {
            bot.threatScareEnds = now.plus(2, ChronoUnit.MINUTES)
            bot.emotions.add(EmotionalTrigger(EmotionType.SCARED, 0.10, bot.threatScareEnds))
        }
    }

    /**
     * Works out when an emotional trigger that lasts [minutes] expires.
     *
     * @param minutes How long the trigger lasts.
     * @return The time it expires.
     */
    private fun minutesFromNow(minutes: Long): Instant = Instant.now().plus(minutes, ChronoUnit.MINUTES)

    /**
     * Makes a bot react after gaining a level.
     *
     * Higher levels can produce a stronger happiness gain, and social bots may queue a [ReactionSpeech.LEVEL_UP] line.
     *
     * @param bot The bot that gained a level.
     * @param skill The skill that advanced.
     * @param newLevel The newly reached level.
     */
    fun reactToLevelUp(bot: Bot, skill: Skill, newLevel: Int) {
        bot.lastSkillAdvanced = skill.name.lowercase() to newLevel

        val minMaxHappiness = newLevel / 100.0
        val amount = rand(minMaxHappiness * 0.50, minMaxHappiness)
        bot.emotions.add(EmotionalTrigger(EmotionType.HAPPY, amount))

        if (rand(bot.personality.social)) {
            val text = BotReactionSpeechPool.take(bot, ReactionSpeech.LEVEL_UP)
            val delay = rand(2, 6)
            bot.speechStack.pushHead(BotSpeech(text, delay))
        }
    }

    /**
     * Registers the reaction hook for witnessing another player's skill advancement.
     *
     * When an actual level increase is observed, happy and social bots may react with an [ReactionSpeech.OTHER_LEVEL_UP]
     * line. Social bots also have a chance to refer to the player by a shortened name, stripping trailing digits for
     * more natural player-like speech.
     */
    fun reactToOtherLevelUp() {
        inject(SkillChangeEvent::class).filter { msg.isLevelUp }.then {
            if (rand(bot.personality.social) && bot.emotions.isFeeling(EmotionType.HAPPY)) {
                val delay = rand(4, 12)
                bot.lastWitnessedSkillAdvance =
                    if (rand(bot.personality.social)) StringUtils.stripTrailingDigits(msg.plr.username)
                    else msg.plr.username

                val text = BotReactionSpeechPool.take(bot, ReactionSpeech.OTHER_LEVEL_UP)
                bot.speechStack.pushHead(BotSpeech(text, delay))
            }
        }
    }

    /**
     * Makes a bot react to an impressive nearby player.
     *
     * Impressive players are evaluated using visible achievements, wealth, equipment, combat strength, and show-off items.
     * Happy and kind bots may compliment the player, and the interaction can improve how both bots feel toward each other.
     *
     * @param bot The bot evaluating nearby impressive players.
     */
    fun reactToImpressivePlayer(bot: Bot) {
        if (rand(bot.personality.kindness) && bot.emotions.isFeeling(EmotionType.HAPPY)) {

            // If the person being complimented is a bot, improve happiness and reduce "scared"
            // also a small chance to follow the bot being complimented if the bot is idle
            // reaction should only go through if impression score > 100
            // item contained within "showoff", impression score +50 (Doesnt stack)
            // level 126, impression score +25
            // if a full set of something is equipped (full rune, full guthans, full etc.) then impression score +25
            // if full value of equipment (except for arrows) exceeds > 500_000 economy value, impression score +50
            // adjustfeelingtoward(complimenter, 0.25)
            // adjustfeelingtoward(complimented, 0.25)

            // TODO@0.5.0 Opposite way? Unkind and unhappy bots will randomly follow "bums" based on a bum score and
            //  say things like "you bum" "noob lol" "LOL ur poor" "LOL newbie" "LOOOOOOL look at this bum"
            //  bum score as follows
            //  level < 50 +25
            //  total equipment value < 10,000 +25
            //  wearing "nooby" items  (bronze, iron, wood shield, shortbow, basic staff with no orb) +50
            //  no gloves, boots, or amulet equipped +25
            //  IGNORE BOT/PLAYER IF TOTAL PLAY TIME IS LESS THAN ONE DAY
            // adjustfeelingtoward(insulter, -0.25)
            // adjustfeelingtoward(insulted, -0.25)
        }
    }

    /**
     * Makes a bot react after receiving a rare or valuable drop.
     *
     * Rare drops make the receiving bot happier and slightly less greedy. The drop name is stored before speech is
     * selected, allowing [ReactionSpeech.RARE_DROP] lines to resolve the `<drop>` placeholder. Social bots may use an
     * item nickname instead of the full item name.
     *
     * Nearby unhappy bots become slightly greedier after seeing the drop, simulating jealousy and desire for valuable
     * loot.
     *
     * @param bot The bot that received the rare or valuable drop.
     * @param item The rare or valuable item that was received.
     */
    fun reactToRareDrop(bot: Bot, item: Item) {
        bot.emotions.add(EmotionalTrigger(EmotionType.GREEDY, -0.25))
        bot.emotions.add(EmotionalTrigger(EmotionType.HAPPY, 0.25))
        bot.lastRareDrop =
            if (rand(bot.personality.social)) ItemNicknameDefinition.getNickname(item.id)
            else item.itemDef.name.lowercase()

        if (rand(bot.personality.social)) {
            val text = BotReactionSpeechPool.take(bot, ReactionSpeech.RARE_DROP)
            val delay = rand(4, 12)
            bot.speechStack.pushHead(BotSpeech(text, delay))
        }

        world.locator.findViewablePlayers(bot) {
            it != bot && it is Bot && it.emotions.isFeeling(EmotionType.HAPPY, true)
        }.forEach { it.asBot().emotions.add(EmotionalTrigger(EmotionType.GREEDY, 0.10)) }
    }

    /**
     * Makes a bot react after being scammed.
     *
     * Scam reactions use the bot's stored scam context to resolve the scammer name, then allow the victim bot to queue
     * an [ReactionSpeech.SCAMMED] line. This gives scam outcomes visible social feedback and helps nearby bot behavior
     * feel more reactive.
     *
     * @param bot The bot that was scammed.
     */
    fun reactToScammed(bot: Bot) {
        // TODO@0.5.0 Impl, in ScamBotScript
        // adjustfeelingtoward(scammer, -0.50)
    }

    fun reactToOtherScammed(bot: Bot) {
        // TODO@0.5.0 OTHER_SCAMMED, bots reacting to another bot getting scammed.
        // adjustfeelingtoward(scammer, -0.50)
        // gain feelings toward person scammed
    }
}
