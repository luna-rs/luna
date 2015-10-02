package plugin.events

import io.luna.LunaContext
import java.util.Objects.requireNonNull

/**
 * Synchronous game plugin events should extend.
 */
class GamePluginEvent {
  
  private var _context: LunaContext = null

  def context_= (context: LunaContext) = _context = context
 
  def context = _context
}