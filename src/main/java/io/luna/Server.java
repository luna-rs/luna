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
    public void init() throws Exception {
        LOGGER.info("Luna is being initialized...");

        initLaunchTasks();
        initPlugins();
        initGame();

        launchPool.shutdown();
        launchPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        initNetwork();
        LOGGER.info("Luna is now online on port {}!", box(LunaConstants.PORT));

        PluginManager plugins = context.getPlugins();
        plugins.post(ServerLaunchEvent.INSTANCE);
    }

    /**
     * Initializes the network server using Netty.
     */
    private void initNetwork() throws Exception {
        ResourceLeakDetector.setLevel(LunaConstants.RESOURCE_LEAK_DETECTION);

        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();

        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new LunaChannelInitializer(context, messageRepository));
        bootstrap.bind(LunaConstants.PORT).syncUninterruptibly();
    }

    /**
     * Initializes the game service.
     */
    private void initGame() throws Exception {
        GameService service = context.getService();
        service.startAsync().awaitRunning();
    }

    /**
     * Initializes the plugin bootstrap.
     */
    private void initPlugins() throws Exception {
        PluginBootstrap bootstrap = new PluginBootstrap(context, launchPool);
        int pluginCount = bootstrap.init();

        LOGGER.info("A total of {} Scala plugins have been loaded into memory.", box(pluginCount));
    }

    /**
     * Initializes misc. startup tasks.
     */
    private void initLaunchTasks() throws Exception {
        tasks.submit(new MessageRepositoryParser(messageRepository));
        tasks.submit(new EquipmentDefinitionParser());
        tasks.submit(new ItemDefinitionParser());
        tasks.submit(new NpcCombatDefinitionParser());
        tasks.submit(new NpcDefinitionParser());
        tasks.submit(new ObjectDefinitionParser());
        tasks.await();
    }
}
