package io.luna;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.game.GameService;
import io.luna.game.event.impl.ServerLaunchEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.NpcCombatDefinition;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.plugin.PluginManager;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.LunaNetworkConstants;
import io.luna.net.msg.MessageRepository;
import io.luna.util.FutureUtils;
import io.luna.util.StringUtils;
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
 * Initializes the individual modules to launch {@link Luna}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Server {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The {@link ExecutorService} that will execute startup tasks.
     */
    private final ListeningExecutorService launchService;

    /**
     * The {@link LunaContext} that this {@code Server} will be managed with.
     */
    private final LunaContext context = new LunaContext();

    /**
     * The repository containing data for incoming messages.
     */
    private final MessageRepository messageRepository = new MessageRepository();

    /**
     * A package-private constructor to discourage external instantiation. The {@code launchService} instance is also
     * initialized here.
     */
    Server() {
        ExecutorService delegateService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
            new ThreadFactoryBuilder().setNameFormat("LunaInitializationThread").build());

        launchService = MoreExecutors.listeningDecorator(delegateService);
    }

    /**
     * Creates {@link Luna} by initializing all of the individual modules.
     *
     * @throws Exception If any exceptions are thrown during initialization.
     */
    public void init() throws Exception {
        LOGGER.info("Luna is being initialized...");

        initAsyncTasks();
        initPlugins();
        initGame();

        launchService.shutdown();
        launchService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        bind();
        LOGGER.info("Luna is now online on port {}!", box(LunaNetworkConstants.PORT));

        PluginManager plugins = context.getPlugins();
        plugins.post(ServerLaunchEvent.INSTANCE);
    }

    /**
     * Initializes the Netty implementation. Will block indefinitely until the {@link ServerBootstrap} is bound.
     *
     * @throws Exception If any exceptions are thrown while binding.
     */
    private void bind() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();

        ResourceLeakDetector.setLevel(LunaNetworkConstants.RESOURCE_LEAK_DETECTION);

        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new LunaChannelInitializer(context, messageRepository));
        bootstrap.bind(LunaNetworkConstants.PORT).syncUninterruptibly();

        ImmutableSet<Integer> preferred = LunaNetworkConstants.PREFERRED_PORTS;
        if (!preferred.contains(LunaNetworkConstants.PORT)) {
            LOGGER.warn("The preferred ports for Runescape servers are {}.", StringUtils.COMMA_JOINER.join(preferred));
        }
    }

    /**
     * Initializes the {@link GameService} asynchronously.
     *
     * @throws Exception If any exceptions are thrown during initialization of the {@code GameService}.
     */
    private void initGame() throws Exception {
        GameService service = context.getService();
        service.startAsync().awaitRunning();
    }

    /**
     * Initializes the {@link PluginManager} asynchronously.
     *
     * @throws Exception If any exceptions are thrown while initializing the {@code PluginManager}.
     */
    private void initPlugins() throws Exception {
        PluginManager plugins = context.getPlugins();

        FutureUtils.addCallback(launchService.submit(new PluginBootstrap(context)),
            plugins.getPipelines()::replacePipelines);
    }

    /**
     * Initializes all miscellaneous startup tasks asynchronously.
     *
     * @throws Exception If any exceptions are thrown while initializing startup tasks.
     */
    private void initAsyncTasks() throws Exception {
        launchService.execute(new MessageRepositoryParser(messageRepository));
        launchService.execute(() -> loadClass(ItemDefinition.class));
        launchService.execute(() -> loadClass(EquipmentDefinition.class));
        launchService.execute(() -> loadClass(NpcCombatDefinition.class));
        launchService.execute(() -> loadClass(NpcDefinition.class));
    }
}
