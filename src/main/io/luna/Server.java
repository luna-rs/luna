package io.luna;

import io.luna.game.GameService;
import io.luna.net.LunaChannelHandlers;
import io.luna.net.LunaNetworkConstants;
import io.luna.task.AsyncTaskService;
import io.luna.util.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import plugin.PluginBootstrap;

/**
 * Initializes the individual modules to launch {@link Luna}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Server {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    /**
     * The {@link AsyncTaskService} that will execute startup tasks.
     */
    private final AsyncTaskService service = AsyncTaskService.newService();

    /**
     * The {@link LunaContext} that this server will be managed with.
     */
    private final LunaContext context = new LunaContext();

    /**
     * A package-private constructor to discourage external instantiation
     * outside of the {@code io.luna} package.
     */
    Server() {}

    /**
     * Creates {@link Luna} by initializing all of the individual modules.
     * 
     * @throws Exception
     *             If any exceptions are thrown during initialization.
     */
    public void create() throws Exception {
        LOGGER.info("Luna is being initialized...");

        initAsyncTasks();
        initGame();
        service.awaitTerminated(); // Await completion of background tasks.
        context.getService().awaitRunning(); // Await completion of logic
                                             // service.
        bind();

        LOGGER.info("Luna is now online on port " + LunaNetworkConstants.PORT + ".");
    }

    /**
     * Initializes the Netty implementation. Will block indefinitely until the
     * {@link ServerBootstrap} is bound.
     * 
     * @throws Exception
     *             If any exceptions are thrown while binding.
     */
    private void bind() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup loopGroup = new NioEventLoopGroup();

        ResourceLeakDetector.setLevel(LunaNetworkConstants.RESOURCE_LEAK_DETECTION);

        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(LunaChannelHandlers.CHANNEL_INITIALIZER);
        bootstrap.bind(LunaNetworkConstants.PORT).syncUninterruptibly();

        if (!LunaNetworkConstants.PREFERRED_PORTS.contains(LunaNetworkConstants.PORT)) {
            String prefix = "The preferred ports for Runescape servers are ";
            LOGGER.info(StringUtils.joinWithAnd(prefix, ".", LunaNetworkConstants.PREFERRED_PORTS));
        }
    }

    /**
     * Initializes the {@link GameService} asynchronously, does not wait for it
     * to enter a {@code RUNNING} state.
     * 
     * @throws Exception
     *             If any exceptions are thrown during initialization of the
     *             {@code GameService}.
     */
    private void initGame() throws Exception {
        context.getService().startAsync();
    }

    /**
     * Executes all startup tasks asynchronously in the background using
     * {@link AsyncTaskService}.
     * 
     * @throws Exception
     *             If any exceptions are thrown while executing startup tasks.
     */
    private void initAsyncTasks() throws Exception {
        service.add(new PluginBootstrap(LogManager.getLogger(PluginBootstrap.class), context));
        service.startAsync();
    }
}
