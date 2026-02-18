package io.luna;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.luna.game.GameService;
import io.luna.game.cache.Cache;
import io.luna.game.cache.codec.ItemDefinitionDecoder;
import io.luna.game.cache.codec.MapDecoder;
import io.luna.game.cache.codec.NpcDefinitionDecoder;
import io.luna.game.cache.codec.ObjectDefinitionDecoder;
import io.luna.game.cache.codec.VarBitDefinitionDecoder;
import io.luna.game.cache.codec.VarpDefinitionDecoder;
import io.luna.game.cache.codec.WidgetDefinitionDecoder;
import io.luna.game.model.World;
import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.GameMessageRepository;
import io.luna.util.ExecutorUtils;
import io.luna.util.parser.impl.EquipmentDefinitionFileParser;
import io.luna.util.parser.impl.MessageRepositoryFileParser;
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Startup orchestrator for the Luna server.
 * <p>
 * {@link LunaServer#init()} performs all initialization steps needed to accept logins:
 * <ol>
 *   <li>Scan the classpath for plugin/script metadata (ClassGraph)</li>
 *   <li>Open the 377 cache and launch cache decoders</li>
 *   <li>Run background “launch tasks” (parsers, bot name loading, repositories)</li>
 *   <li>Start core {@link Service}s (game loop + login/logout workers)</li>
 *   <li>Wait for the online lock (ensures game is ready to accept players)</li>
 *   <li>Bring the Netty network online and bind to the configured port</li>
 * </ol>
 * <p>
 * The classpath scan result is exposed via {@link #getClasspath()} while initialization is running (useful for
 * script loading / reflection-based registries).
 *
 * @author lare96
 */
public final class LunaServer {

    /**
     * Async logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Owning context (cache/world/services/plugins).
     */
    private final LunaContext context;

    /**
     * Channel filter used by the Netty pipeline.
     */
    private final LunaChannelFilter channelFilter = new LunaChannelFilter();

    /**
     * Repository of message codecs/handlers (protocol).
     */
    private final GameMessageRepository messageRepository = new GameMessageRepository();

    /**
     * Classpath scan result. Non-null only during {@link #init()}.
     */
    private volatile ScanResult classpath;

    /**
     * Package-private constructor. Instances are owned by {@link LunaContext}.
     */
    LunaServer(LunaContext context) {
        this.context = context;
    }

    /**
     * Runs initialization. When this returns successfully, the server is online and accepting connections.
     *
     * @throws Exception If a fatal startup failure occurs.
     */
    public void init() throws Exception {
        try (ScanResult result = new ClassGraph().enableClassInfo().disableJarScanning().scan()) {
            classpath = result;
            Stopwatch launchTimer = Stopwatch.createStarted();

            initCache();
            initLaunchTasks();
            initServices();

            // Wait for the above to finish before bringing the network online.
            GameService game = context.getGame();
            game.getOnlineLock().join();

            // We're ready to accept logins.
            initNetwork();

            long elapsedTime = launchTimer.elapsed(TimeUnit.SECONDS);
            logger.info("Luna is now online on port {} (took {}s).",
                    box(Luna.settings().game().port()), box(elapsedTime));
        } finally {
            classpath = null;
        }
    }

    /**
     * Opens the cache and schedules cache decoders.
     *
     * @throws Exception If the cache cannot be opened or decoders fail to start.
     */
    private void initCache() throws Exception {
        Cache cache = context.getCache();
        cache.open();
        cache.runDecoders(context,
                new ObjectDefinitionDecoder(),
                new WidgetDefinitionDecoder(),
                new ItemDefinitionDecoder(),
                new NpcDefinitionDecoder(),
                new VarBitDefinitionDecoder(),
                new VarpDefinitionDecoder(),
                new MapDecoder());
    }

    /**
     * Initializes the Netty server and binds to the configured port.
     * <p>
     * Resource leak detection level is configured via settings for debugging memory / ByteBuf leaks.
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
     * Starts core {@link Service}s (game loop and world login/logout services) and waits until healthy.
     *
     * @throws InterruptedException If startup is interrupted.
     */
    private void initServices() throws InterruptedException {
        World world = context.getWorld();

        var gameService = context.getGame();
        var loginService = world.getLoginService();
        var logoutService = world.getLogoutService();

        var allServices = new ServiceManager(List.of(gameService, loginService, logoutService));
        allServices.startAsync().awaitHealthy();

        logger.info("All services are now running.");
    }

    /**
     * Runs miscellaneous startup tasks in parallel and waits for completion.
     *
     * <p>This stage is intended for file parsing and other initialization that should finish before
     * players can safely interact with content.
     */
    private void initLaunchTasks() {
        List<Runnable> taskList = new ArrayList<>();
        taskList.add(new MessageRepositoryFileParser(messageRepository));
        taskList.add(new EquipmentDefinitionFileParser());
        taskList.add(new NpcCombatDefinitionFileParser());
        taskList.add(() -> context.getWorld().getBots().loadNames());

        ExecutorService pool = ExecutorUtils.threadPool("BackgroundLoaderThread");
        for (Runnable task : taskList) {
            pool.execute(task);
        }

        int count = taskList.size();
        logger.info("Waiting for {} Java launch task(s) to complete...", box(count));

        pool.shutdown();
        awaitTerminationUninterruptibly(pool);

        // Wait for cache decoders after launch tasks (keeps startup ordering deterministic).
        context.getCache().waitForDecoders();
    }

    /**
     * @return The channel filter used by Netty.
     */
    public LunaChannelFilter getChannelFilter() {
        return channelFilter;
    }

    /**
     * @return The message repository (protocol definitions).
     */
    public GameMessageRepository getMessageRepository() {
        return messageRepository;
    }

    /**
     * Returns the classpath scan result used during initialization.
     *
     * @return The scan result (non-null only during {@link #init()}).
     * @throws NullPointerException If called outside initialization.
     */
    public ScanResult getClasspath() {
        if (classpath == null) {
            throw new NullPointerException("Only accessible while the server is initializing!");
        }
        return classpath;
    }
}
