package api.bot.action

import api.bot.Suspendable.delay
import api.bot.script.BotScript.ActionSafety
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import io.luna.game.model.mob.overlay.OverlayType
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Selects fixed native NPC responses. The ordinary dialogue reflex usually advances speech.
 * Queue ownership starts with the expected NPC, and replacements require this call's own preceding choice.
 * Foreign speakers, options and windows abort without being clicked or closed. Rewards remain native.
 *
 * @author lare96
 */
object BotNpcDialogues {
    /**
     * Starts one conversation and waits for its actual gameplay result within a shared deadline.
     * Exact player response pages are allowed after the corresponding choice; arbitrary dialogue is never advanced.
     *
     * @param bot The bot using its existing interaction and widget handlers.
     * @param npc The exact NPC targeted by the initiating player action.
     * @param responses Exact option text in native conversation order.
     * @param timeout Complete interaction budget, including any approach performed by [start].
     * @param allowed Additional live activity permission checked before every option.
     * @param completed Real inventory or world-state result that completes the conversation.
     * @param playerSpeech Exact scripted player pages that can occur independently of the selected options.
     * @param advanceSpeech Whether to advance recognized speech here so the reflex cannot skip the opening page.
     * @param speakerId NPC head shown by the dialogue when a morph uses a different ID from the clicked NPC.
     * @param start Native Talk-to or item-on-NPC interaction.
     */
    suspend fun respond(bot: Bot, npc: Npc, responses: List<String>, timeout: Duration = 30.seconds,
                        allowed: () -> Boolean = { true }, completed: () -> Boolean,
                        playerSpeech: Set<String> = emptySet(), advanceSpeech: Boolean = false, speakerId: Int = npc.id,
                        start: suspend () -> Boolean): Boolean =
        respondToSpeaker(bot, speakerId, responses, timeout, { npc.state == EntityState.ACTIVE && allowed() },
            completed, playerSpeech, advanceSpeech, start)

    /**
     * Answers a native remote speaker, such as Brian speaking through the Mystic jewel, without inventing an NPC.
     * The initiating action must open a new queue whose first observed page is this exact speaker. Every later
     * page must belong to that queue; native replacement queues require this call's preceding selected response.
     *
     * @param speakerId Native NPC head ID used by the remote conversation.
     * @param allowed Live permission for the initiating item or activity, checked before every option.
     */
    suspend fun respondVirtual(bot: Bot, speakerId: Int, responses: List<String>, timeout: Duration = 30.seconds,
                               allowed: () -> Boolean = { true }, completed: () -> Boolean,
                               start: suspend () -> Boolean): Boolean =
        respondToSpeaker(bot, speakerId, responses, timeout, allowed, completed, emptySet(), false, start)

    /**
     * Shares exact queue, speaker and page ownership between physical and remote native conversations.
     */
    private suspend fun respondToSpeaker(bot: Bot, speakerId: Int, responses: List<String>, timeout: Duration,
                                         allowed: () -> Boolean, completed: () -> Boolean,
                                         playerSpeech: Set<String>, advanceSpeech: Boolean,
                                         start: suspend () -> Boolean): Boolean {
        require(responses.isNotEmpty())
        val previous = bot.dialogues
        var queue: DialogueQueue? = null
        var page: DialogueInterface? = null
        var selectedPage: OptionDialogue? = null
        var lastResponse: String? = null
        var next = 0
        val previousReflex = advanceSpeech && bot.reflex.isDisableDialogueReflex
        fun safe() = ActionSafety.NON_COMBAT.allows(bot) && allowed()
        fun speech(current: DialogueInterface): Boolean = when (current) {
            is NpcDialogue -> current.npcId == speakerId
            is PlayerDialogue -> current.lines.joinToString(" ").let { it == lastResponse || it in playerSpeech }
            else -> false
        }
        fun recognized(current: DialogueInterface): Boolean = when (current) {
            is OptionDialogue -> current === selectedPage || responses.getOrNull(next)?.let { it in current.options } == true
            else -> speech(current)
        }
        try {
            if (advanceSpeech) bot.reflex.isDisableDialogueReflex = true
            return withTimeoutOrNull(timeout) {
                if (completed()) return@withTimeoutOrNull true
                if (!safe() || !start()) return@withTimeoutOrNull false
                while (safe()) {
                    if (completed()) return@withTimeoutOrNull true
                    val current = bot.overlays.getOverlay(DialogueInterface::class.java)
                    val currentQueue = bot.dialogues
                    if (current != null && currentQueue != null && currentQueue !== previous) {
                        if (!currentQueue.owns(current)) return@withTimeoutOrNull false
                        if (queue == null) {
                            if (!speech(current)) return@withTimeoutOrNull false
                            queue = currentQueue
                        } else if (currentQueue !== queue) {
                            if (selectedPage == null || !speech(current)) return@withTimeoutOrNull false
                            queue = currentQueue
                            selectedPage = null
                        }
                        if (!recognized(current)) return@withTimeoutOrNull false
                        val changed = current !== page
                        page = current
                        if (current is OptionDialogue && current !== selectedPage) {
                            val response = responses.getOrNull(next) ?: return@withTimeoutOrNull false
                            if (!safe() || response !in current.options ||
                                !bot.actionHandler.widgets.clickDialogueOption(response)) return@withTimeoutOrNull false
                            selectedPage = current
                            lastResponse = response
                            next++
                        } else if (advanceSpeech && changed && speech(current)) {
                            if (!safe() || !bot.output.sendContinueDialogue()) return@withTimeoutOrNull false
                        }
                    } else if (queue != null && currentQueue != null && currentQueue !== queue) {
                        return@withTimeoutOrNull false
                    }
                    delay(100.milliseconds)
                }
                false
            } == true
        } finally {
            if (advanceSpeech) bot.reflex.isDisableDialogueReflex = previousReflex
            val current = bot.overlays.getOverlay(DialogueInterface::class.java)
            val ownedQueue = queue
            if (ownedQueue != null && bot.dialogues === ownedQueue && current != null &&
                ownedQueue.owns(current) && (current === page || recognized(current)) &&
                !bot.overlays.containsType(OverlayType.INPUT)) {
                bot.overlays.closeWindows(false)
            }
        }
    }
}
