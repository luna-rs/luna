package io.luna;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.game.GameService;
import io.luna.game.event.impl.ServerLaunchEvent;
import io.luna.game.model.Chance;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.plugin.PluginManager;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.MessageRepository;
import io.luna.util.Rational;
import io.luna.util.parser.impl.MessageRepositoryParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.luna.util.ClassUtils.loadClass;
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
     * A luna context instance.
     */
    private final LunaContext context = new LunaContext();

    /**
     * A message repository.
     */
    private final MessageRepository repository = new MessageRepository();

    /**
     * A package-private constructor.
     */
    Server() {
        ExecutorService delegateService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            new ThreadFactoryBuilder().setNameFormat("LunaInitializationThread").build());

        launchPool = MoreExecutors.listeningDecorator(delegateService);
    }

    /**
     * Runs the individual tasks that start Luna.
     */
    public void init() throws Exception {
        LOGGER.info("Luna is being initialized...");

        initLaunchTasks();
        initPlugins();
        initGame();

        //launchPool.shutdown();
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
        bootstrap.childHandler(new LunaChannelInitializer(context, repository));
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
        PluginBootstrap bootstrap = new PluginBootstrap(context);
        bootstrap.load(launchPool);
    }

    /**
     * Initializes misc. startup tasks.
     */
    private void initLaunchTasks() throws Exception {
        launchPool.execute(new MessageRepositoryParser(repository));
        launchPool.execute(() -> loadClass(ItemDefinition.class));
        launchPool.execute(() -> loadClass(EquipmentDefinition.class));
        launchPool.execute(() -> loadClass(NpcCombatDefinition.class));
        launchPool.execute(() -> loadClass(NpcDefinition.class));
    }
}
