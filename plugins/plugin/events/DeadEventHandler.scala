package plugin.events

import org.apache.logging.log4j.LogManager
import com.google.common.eventbus.Subscribe
import io.netty.util.internal.StringUtil.simpleClassName
import com.google.common.eventbus.DeadEvent

/**
 * Used by the PluginManager to handle 'dead' events.
 */
final class DeadEventHandler {
  
  private val Logger = LogManager.getLogger(this.getClass)
  
  @Subscribe
  def handleDeadEvent(evt: DeadEvent) = Logger.info("No subscribers found for (" + simpleClassName(evt.getEvent()) + ").")
}