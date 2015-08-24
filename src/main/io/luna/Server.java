package io.luna;

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
 * Prepares the Netty networking implementation and the game logic service loop,
 * while simultaneously executing startup tasks in the background.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Server {

    /**
     * The logger that will print important information.
     */
    private final Logger logger = LogManager.getLogger(Server.class);

    /**
     * The asynchronous task service that will execute our startup tasks in the
     * background.
     */
    private final AsyncTaskService service = AsyncTaskService.newService();

    /**
     * A default access level constructor to discourage external instantiation
     * outside of the {@code io.luna} package.
     */
    Server() {}

    /**
     * Creates the Luna server by initializing all of the individual modules.
     * 
     * @throws Exception
     *             If any errors occur while creating any of the individual
     *             modules.
     */
    public void create() throws Exception {
        logger.info("Luna is being initialized...");

        initStartupTasks();
        initGame();
        service.awaitTerminated(); // Await completion of background tasks.
        Luna.getService().awaitRunning(); // Await completion of logic service.
        bind();

        logger.info("Luna is now online on port " + LunaNetworkConstants.PORT + ".");
    }

    /**
     * Initializes the Netty implementation. Will block indefinitely until the
     * bootstrap is bound.
     * 
     * @throws Exception
     *             If any errors occur while creating the Netty module.
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
            logger.info(StringUtils.joinWithAnd(prefix, ".", LunaNetworkConstants.PREFERRED_PORTS));
        }
    }

    /**
     * Initializes the game logic service asynchronously, does not wait for it
     * to enter a {@code RUNNING} state.
     * 
     * @throws Exception
     *             If any errors occur while creating the GameService module.
     */
    private void initGame() throws Exception {
        Luna.getService().startAsync();
    }

    /**
     * Executes all startup tasks in the background using a
     * {@link io.luna.task.AsyncTaskService}.
     * 
     * @throws Exception
     *             If any errors occur while executing startup tasks.
     */
    private void initStartupTasks() throws Exception {
        service.add(new PluginBootstrap(LogManager.getLogger(PluginBootstrap.class)));
        service.startAsync();
    }
}
