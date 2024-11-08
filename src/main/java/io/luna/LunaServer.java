package io.luna;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.luna.game.cache.Cache;
import io.luna.game.cache.codec.ItemDefinitionDecoder;
import io.luna.game.cache.codec.MapDecoder;
import io.luna.game.cache.codec.NpcDefinitionDecoder;
import io.luna.game.cache.codec.ObjectDefinitionDecoder;
import io.luna.game.cache.codec.VarBitDefinitionDecoder;
import io.luna.game.cache.codec.VarpDefinitionDecoder;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.GameMessageRepository;
import io.luna.util.AsyncExecutor;
import io.luna.util.ThreadUtils;
import io.luna.util.benchmark.BenchmarkManager;
import io.luna.util.parser.impl.EquipmentDefinitionFileParser;
import io.luna.util.parser.impl.MessageRepositoryFileParser;
import io.luna.util.parser.impl.MusicDefinitionFileParser;
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A model that handles Server initialization logic.
 *
 * @author lare96
 */
public final class LunaServer {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The context.
     */
    private final LunaContext context;

    /**
     * A channel handler that will filter channels.
     */
    private final LunaChannelFilter channelFilter = new LunaChannelFilter();

    /**
     * A message repository.
     */
    private final GameMessageRepository messageRepository = new GameMessageRepository();

    /**
     * The benchmark manager.
     */
    private final BenchmarkManager benchmarkManager = new BenchmarkManager();

    /**
     * A package-private constructor.
     */
    LunaServer(LunaContext context) {
        this.context = context;
    }

    /**
     * Runs the individual tasks that start Luna.
     *
     * @throws Exception If an error occurs.
     */
    public void init() throws Exception {
        Stopwatch launchTimer = Stopwatch.createStarted();

        initCache();
        initLaunchTasks();
        initPlugins();
        initServices();

        initNetwork();

        long elapsedTime = launchTimer.elapsed(TimeUnit.SECONDS);
        logger.info("Luna is now online on port {} (took {}s).", box(Luna.settings().game().port()), box(elapsedTime));
    }

    /**
     * Initializes the cache resource and the cache decoders.
     */
    private void initCache() throws Exception {
        Cache cache = context.getCache();
        cache.open();
        cache.runDecoders(context, new ObjectDefinitionDecoder(),
                new ItemDefinitionDecoder(),
                new NpcDefinitionDecoder(),
                new VarBitDefinitionDecoder(),
                new VarpDefinitionDecoder(),
                new MapDecoder());
    }

    /**
     * Initializes the network server using Netty.
     */
    private void initNetwork() {
        ResourceLeakDetector.setLevel(Luna.settings().game().resourceLeakDetection());

        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();
        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new LunaChannelInitializer(context, channelFilter, messageRepository));
        bootstrap.bind(Luna.settings().game().port()).syncUninterruptibly();
    }

    /**
     * Initializes all {@link Service}s. This will start the game loop and create login/logout workers.
     */
    private void initServices() throws InterruptedException {
        // Benchmark service started separately since it may not have to run.
        benchmarkManager.getService().startAsync();

        var gameService = context.getGame();
        var loginService = context.getWorld().getLoginService();
        var logoutService = context.getWorld().getLogoutService();
        var allServices = new ServiceManager(List.of(gameService, loginService, logoutService));
        allServices.startAsync().awaitHealthy();
        logger.info("All services are now running.");

        // Wait for last minute Kotlin tasks before we let players login.
        gameService.getSynchronizer().await();
    }

    /**
     * Initializes the {@link PluginBootstrap}.
     *
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    private void initPlugins() throws ReflectiveOperationException {
        PluginBootstrap bootstrap = new PluginBootstrap(context);
        bootstrap.start();

        int pluginCount = context.getPlugins().getPluginCount();
        int scriptCount = context.getPlugins().getScriptCount();
        logger.info("{} Kotlin plugins containing {} scripts have been loaded.", box(pluginCount), box(scriptCount));
    }

    /**
     * Initializes misc. startup tasks.
     **/
    private void initLaunchTasks() {
        AsyncExecutor executor = new AsyncExecutor(ThreadUtils.cpuCount(), "BackgroundLoaderThread");
        executor.execute(new MessageRepositoryFileParser(messageRepository));
        executor.execute(new EquipmentDefinitionFileParser());
        executor.execute(new MusicDefinitionFileParser());
        executor.execute(new NpcCombatDefinitionFileParser());

        try {
            int count = executor.getTaskCount();
            if (count > 0) {
                logger.info("Waiting for {} Java launch task(s) to complete...", box(count));
                executor.await(true);
                context.getCache().waitForDecoders();
            }
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    /**
     * @return A channel handler that will filter channels.
     */
    public LunaChannelFilter getChannelFilter() {
        return channelFilter;
    }

    /**
     * @return A message repository.
     */
    public GameMessageRepository getMessageRepository() {
        return messageRepository;
    }

    /**
     * @return The benchmark manager.
     */
    public BenchmarkManager getBenchmarkManager() {
        return benchmarkManager;
    }
}
