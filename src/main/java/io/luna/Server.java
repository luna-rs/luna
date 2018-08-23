package io.luna;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.luna.game.GameService;
import io.luna.game.event.impl.ServerLaunchEvent;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.plugin.PluginManager;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.MessageRepository;
import io.luna.util.BlockingTaskManager;
import io.luna.util.ThreadUtils;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A model that handles initialization logic.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Server {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A thread pool that will run startup tasks.
     */
    private final ListeningExecutorService launchPool;

    /**
     * A service manager.
     */
    private final BlockingTaskManager tasks;

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
    Server() {
        ExecutorService delegateService = ThreadUtils.newThreadPool("LunaInitializationThread");

        launchPool = MoreExecutors.listeningDecorator(delegateService);
        tasks = new BlockingTaskManager(launchPool);
    }

    /**
     * Runs the individual tasks that start Luna.
     */
    public void init() throws InterruptedException, ExecutionException, ScriptException, IOException {
        initLaunchTasks();
        initPlugins();
        initGame();

        launchPool.shutdown();
        launchPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        initNetwork();

        /* Post an event signalling that the server has launched. */
        PluginManager plugins = context.getPlugins();
        plugins.post(ServerLaunchEvent.INSTANCE);
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
        LOGGER.info("Luna is now listening for connections on port {}!", box(LunaConstants.PORT));
    }

    /**
     * Initializes the game service.
     */
    private void initGame() {
        GameService service = context.getService();
        service.startAsync().awaitRunning();
        LOGGER.info("The main game loop is now running.");
    }

    /**
     * Initializes the plugin bootstrap.
     */
    private void initPlugins() throws InterruptedException, IOException, ExecutionException, ScriptException {
        PluginBootstrap bootstrap = new PluginBootstrap(context, launchPool);
        int pluginCount = bootstrap.init();

        LOGGER.info("{} Scala plugins have been loaded into memory.", box(pluginCount));
    }

    /**
     * Initializes misc. startup tasks.
     */
    private void initLaunchTasks() throws InterruptedException {
        tasks.submit(new MessageRepositoryParser(messageRepository));
        tasks.submit(new EquipmentDefinitionParser());
        tasks.submit(new ItemDefinitionParser());
        tasks.submit(new NpcCombatDefinitionParser());
        tasks.submit(new NpcDefinitionParser());
        tasks.submit(new ObjectDefinitionParser());
        tasks.await();
        LOGGER.info("All launch tasks have completed successfully.");
    }
}
