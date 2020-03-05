package io.luna;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.net.LunaChannelFilter;
import io.luna.net.LunaChannelInitializer;
import io.luna.net.msg.GameMessageRepository;
import io.luna.util.AsyncExecutor;
import io.luna.util.ThreadUtils;
import io.luna.util.parser.impl.BlacklistFileParser;
import io.luna.util.parser.impl.EquipmentDefinitionFileParser;
import io.luna.util.parser.impl.ItemDefinitionFileParser;
import io.luna.util.parser.impl.MessageRepositoryFileParser;
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser;
import io.luna.util.parser.impl.NpcDefinitionFileParser;
import io.luna.util.parser.impl.ObjectDefinitionFileParser;
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
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaServer {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A Luna context instance.
     */
    private final LunaContext context = new LunaContext();

    /**
     * A channel handler that will filter channels.
     */
    private final LunaChannelFilter channelFilter = new LunaChannelFilter();

    /**
     * A message repository.
     */
    private final GameMessageRepository messageRepository = new GameMessageRepository();

    /**
     * A package-private constructor.
     */
    LunaServer() {
    }

    /**
     * Runs the individual tasks that start Luna.
     *
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    public void init() throws ReflectiveOperationException {
        Stopwatch launchTimer = Stopwatch.createStarted();

        initLaunchTasks();
        initPlugins();
        initServices();

        initNetwork();

        long elapsedTime = launchTimer.elapsed(TimeUnit.SECONDS);
        logger.info("Luna is now online on port {} (took {}s).", box(Luna.settings().port()), box(elapsedTime));
    }

    /**
     * Initializes the network server using Netty.
     */
    private void initNetwork() {

        ResourceLeakDetector.setLevel(Luna.settings().resourceLeakDetection());

        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();

        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new LunaChannelInitializer(context, channelFilter, messageRepository));
        bootstrap.bind(Luna.settings().port()).syncUninterruptibly();
    }

    /**
     * Initializes all {@link Service}s. This will start the game loop and create login/logout workers.
     */
    private void initServices() {
        var gameService = context.getGame();
        var loginService = context.getWorld().getLoginService();
        var logoutService = context.getWorld().getLogoutService();
        var allServices = new ServiceManager(List.of(gameService, loginService, logoutService));
        allServices.startAsync().awaitHealthy();
        logger.info("All services are now running.");
    }

    /**
     * Initializes the {@link PluginBootstrap}.
     *
     * @throws ReflectiveOperationException If an error occurs while instancing plugins.
     */
    private void initPlugins() throws ReflectiveOperationException {
        PluginBootstrap bootstrap = new PluginBootstrap(context);
        logger.info("{} Kotlin plugins have been loaded.", bootstrap.start());
    }

    /**
     * Initializes misc. startup tasks.
     **/
    private void initLaunchTasks() {
        AsyncExecutor executor = new AsyncExecutor(ThreadUtils.cpuCount(), "BackgroundLoaderThread");
        executor.execute(new MessageRepositoryFileParser(messageRepository));
        executor.execute(new EquipmentDefinitionFileParser());
        executor.execute(new ItemDefinitionFileParser());
        executor.execute(new NpcCombatDefinitionFileParser());
        executor.execute(new NpcDefinitionFileParser());
        executor.execute(new ObjectDefinitionFileParser());
        executor.execute(new BlacklistFileParser(channelFilter));

        try {
            int count = executor.size();
            if (count > 0) {
                logger.info("Waiting for {} launch task(s) to complete...", box(count));
                executor.await(true);
            }
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }
}
