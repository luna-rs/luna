package api.event

import io.luna.game.event.Event
import javax.script.ScriptException
import kotlin.reflect.KClass

/**
 * A [ScriptException] thrown when the user matches on the same key more than once.
 *
 * @author lare96
 */
class DuplicateMatchException(key: Any?, eventType: KClass<out Event>) :
        ScriptException("Key [$key] is already matched to a ${eventType.simpleName} listener.") // should be illegalstateexception