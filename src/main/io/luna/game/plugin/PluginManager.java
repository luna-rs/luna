package io.luna.game.plugin;

import io.luna.LunaContext;
import io.netty.util.internal.StringUtil;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import plugin.PluginBootstrap;

/**
 * A class that manages all of the {@link Plugin}s and their respective
 * {@link PluginPipeline}s. Has a function to submit a new {@code Plugin} and
 * another to post events to existing {@code Plugin}s.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager {

	/**
	 * A {@link Map} containing the event types and the designated pipelines.
	 */
	private final Map<Class<?>, PluginPipeline<?>> plugins = new HashMap<>();

	/**
	 * The context for this {@code PluginManager}.
	 */
	private final LunaContext context;

	/**
	 * Creates a new {@link PluginManager}.
	 *
	 * @param context The context for this {@code PluginManager}.
	 */
	public PluginManager(LunaContext context) {
		this.context = context;
	}

	/**
	 * Submits a {@link Plugin} represented as {@code clazz} to the backing
	 * plugin map. This should only ever be called by the
	 * {@link PluginBootstrap}.
	 * 
	 * @param clazz The class to submit as a {@code Plugin}.
	 * @throws Exception If the class cannot be instantiated.
	 */
	public void submit(Class<?> clazz) throws Exception {
		ParameterizedType superType = (ParameterizedType) clazz.getGenericSuperclass();
		Class<?> typeEvent = (Class<?>) superType.getActualTypeArguments()[0];

		Plugin<?> plugin = (Plugin<?>) clazz.newInstance();

		plugin.plugins = this;
		plugin.service = context.getService();
		plugin.world = context.getWorld();

		plugins.putIfAbsent(typeEvent, new PluginPipeline<>()).add(plugin);
	}

	/**
	 * Posts an event represented as {@code evt} to all {@link Plugin}s that
	 * listen for its underlying type. If no {@code PluginPipeline}s are found
	 * for the event, an {@link NoSuchElementException} is thrown.
	 * 
	 * @param evt The event to post.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void post(Object evt) {
		PluginPipeline pipeline = plugins.get(evt.getClass());

		if (pipeline == null) {
			throw new NoSuchElementException("No pipeline for event: " + StringUtil.simpleClassName(evt));
		}
		pipeline.traverse(evt);
	}
}
