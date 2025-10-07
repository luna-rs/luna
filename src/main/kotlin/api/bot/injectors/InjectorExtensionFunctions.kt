package api.bot.injectors

import io.luna.game.model.mob.bot.speech.BotSpeechPool

/**
 * A helper function that clears all tags within the pool after [run] completes.
 */
fun <T : Enum<T>> BotSpeechPool<T>.withTags(run: BotSpeechPool<T>.() -> Unit) {
    try {
        run(this)
    } finally {
        clearTags()
    }
}