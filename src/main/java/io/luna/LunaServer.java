package io.luna;

import com.google.common.base.Stopwatch;
import fj.P2;
import io.luna.game.GameService;
import io.luna.game.event.impl.ServerLaunchEvent;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.plugin.PluginManager;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.MessageRepository;
import io.luna.util.AsyncExecutor;
import io.luna.util.parser.impl.EquipmentDefinitionParser;
import io.luna.util.parser.impl.ItemDefinitionParser;
import io.luna.util.parser.impl.MessageRepositoryParser;
import io.luna.util.parser.impl.NpcCombatDefinitionParser;
import io.luna.util.parser.impl.NpcDefinitionParser;
import io.luna.util.parser.impl.ObjectDefinitionParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.luna.util.ThreadUtils.getCpuAmount;
import static io.luna.util.ThreadUtils.nameThreadFactory;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A model that handles Server initialization logic.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaServer {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * An executor that will load launch tasks.
     */
    private final AsyncExecutor executor =
            AsyncExecutor.newSingletonExecutor(getCpuAmount(), nameThreadFactory("LunaInitializationThread"));

    /**
     * A luna context instance.
     */
    private final LunaContext context = new LunaContext();

    /**
     * A message repository.
     */
    private final MessageRepository messageRepository = new MessageRepository();

    /**
     * A package-private constructor.
     */
    LunaServer() {
    }

    /**
     * Runs the individual tasks that start Luna.
     *
     * @throws ExecutionException If asynchronous tasks cannot be computed.
     * @throws ScriptException    If evaluation for a plugin script fails.
     * @throws IOException        If any I/O errors occur.
     */
    public void init() throws ExecutionException, ScriptException, IOException {
        Stopwatch launchTimer = Stopwatch.createStarted();

        initLaunchTasks();
        initPlugins();
        initGame();

        initNetwork();

        PluginManager plugins = context.getPlugins();
        plugins.post(ServerLaunchEvent.INSTANCE); // Post an event for launch.

        long elapsedTime = launchTimer.elapsed(TimeUnit.SECONDS);
        LOGGER.info("Luna is now online on port {} (took {}s).", box(LunaConstants.PORT), box(elapsedTime));
    }

    /**
     * Initializes the network server using Netty.
     */
    private void initNetwork() {
        ResourceLeakDetector.setLevel(LunaConstants.RESOURCE_LEAK_DETECTION);

        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();

        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new LunaChannelInitializer(context, messageRepository));
        bootstrap.bind(LunaConstants.PORT).syncUninterruptibly();
    }

    /**
     * Initializes the {@link GameService}.
     */
    private void initGame() {
        GameService service = context.getService();
        service.startAsync().awaitRunning();
        LOGGER.info("The game thread is now running.");
    }

    /**
     * Initializes the {@link PluginBootstrap}.
     *
     * @throws ExecutionException If asynchronous tasks cannot be computed.
     * @throws ScriptException    If evaluation for a plugin script fails.
     * @throws IOException        If any I/O errors occur.
     */
    private void initPlugins() throws IOException, ExecutionException, ScriptException {
        PluginBootstrap bootstrap = new PluginBootstrap(context);
        P2<Integer, Integer> pluginCount = bootstrap.init(LunaConstants.PLUGIN_GUI);

        String fractionString = pluginCount._1() + "/" + pluginCount._2();
        LOGGER.info("[{}] Scala plugins have been loaded into memory.", fractionString);
    }

    /**
     * Initializes misc. startup tasks.
     *
     * @throws ExecutionException If asynchronous tasks cannot be computed.
     */
    private void initLaunchTasks() throws ExecutionException {
        executor.execute(new MessageRepositoryParser(messageRepository));
        executor.execute(new EquipmentDefinitionParser());
        executor.execute(new ItemDefinitionParser());
        executor.execute(new NpcCombatDefinitionParser());
        executor.execute(new NpcDefinitionParser());
        executor.execute(new ObjectDefinitionParser());

        int count = executor.size();
        LOGGER.info("Waiting for {} launch task(s) to complete...", box(count));
        executor.await();
    }
}
