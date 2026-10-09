package api.bot.script

import api.predef.*
import engine.bot.coordinator.skill.ThievingScriptFactoryTest
import engine.bot.gear.BotItemTracker
import io.luna.game.action.Action
import io.luna.game.action.ActionQueue
import io.luna.game.event.EventListener
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.Bank
import io.luna.game.model.item.Inventory
import io.luna.game.model.item.Item
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import org.mockito.Mockito.*
import kotlinx.coroutines.Job

/** Mock world/bot fixture with real inventory, bank, and gameplay action queue; no server or network is started. */
object InventoryProductionFixtures {
    /** Direct lifecycle-hook tests need an active job for BotScript.stop to perform normal cancellation. */
    fun <T : BotScript> active(script: T): T {
        val progress = AbstractBotScript::class.java.getDeclaredField("progress")
        progress.isAccessible = true
        progress.set(script, Job())
        return script
    }

    fun initialize() {
        ThievingScriptFactoryTest.initializeScriptFixtures()
        for (id in listOf(5292, 5063, 8880, 8866, 8899, 8938)) {
            if (WidgetDefinition.ALL.get(id).isEmpty) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    fun bot(): Bot {
        val fixtureWorld = world
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.attributes()).thenReturn(AttributeMap())
        `when`(bot.world).thenReturn(fixtureWorld)
        `when`(bot.health).thenReturn(99)
        for (id in listOf(SKILL_CRAFTING, SKILL_HERBLORE)) {
            `when`(bot.skill(id).staticLevel).thenReturn(99)
            `when`(bot.skill(id).level).thenReturn(99)
        }
        val inventory = Inventory(bot).also { it.setListeners(BotItemTracker(bot)) }
        `when`(bot.inventory).thenReturn(inventory)
        val bank = Bank(bot).also { it.setListeners(BotItemTracker(bot)) }
        `when`(bot.bank).thenReturn(bank)
        `when`(bot.actions).thenReturn(ActionQueue(bot))
        return bot
    }

    fun bank(bot: Bot, vararg items: Item) {
        items.forEach { check(bot.bank.add(it)) }
    }

    fun inventory(bot: Bot, vararg items: Item) {
        items.forEach { check(bot.inventory.add(it)) }
    }

    fun execute(bot: Bot, action: Action<*>) {
        bot.actions.submit(action)
        check(action.run()) { "Fixture expects a single completed gameplay operation." }
    }

    /** Loads the actual compiled registration script and executes its server-launch callback. */
    @Suppress("UNCHECKED_CAST")
    fun register(name: String) {
        val first = scriptListeners.size
        Class.forName("game.bot.scripts.Register$name").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        val added = scriptListeners.drop(first)
        check(added.size == 1)
        (added.single() as EventListener<ServerLaunchEvent>).apply(mock(ServerLaunchEvent::class.java))
    }
}
