package io.luna.game.model.mob.bot;

/**
 * Represents a script that can be used to control actions of bots. Script processing is done on the game thread before
 * player updating and regular player pre-processing. Therefore, bot script code should run efficiently and <strong>
 * should not</strong> contain any blocking code.
 * <p>
 * Fallback scripts should be used like parent scripts; for instance, a bot is mining and a drop party notice occurs.
 * The bot switches to a drop party script with the mining script as its fallback. Once the drop party ends, the
 * drop party script is terminated and the fallback mining script continues to run once again.
 *
 * @author lare96
 */
public abstract class BotScript {

    /**
     * The bot instance.
     */
    protected final Bot bot;

    /**
     * The bot client instance.
     */
    protected final BotClient client;

    /**
     * The bot message handler instance.
     */
    protected final BotMessageHandler botActions;

    /**
     * How many times {@link #process()} has been called on this script.
     */
    private int executions;

    /**
     * Creates a new {@link BotScript}.
     *
     * @param bot The bot instance.
     */
    public BotScript(Bot bot) {
        this.bot = bot;
        client = bot.getBotClient();
        botActions = bot.getMessageHandler();
    }

    /**
     * Initializes this script by executing the necessary listeners.
     */
    void init() {
        if(!start()) {
            bot.terminateScript();
        }
    }

    /**
     * Called when this script starts, either as a result of {@link Bot#login()} or {@link Bot#setScript(BotScript)}.
     *
     * @return {@code false} if this script should not be started, and its fallback script ran instead.
     */
    public boolean start() {
        return true;
    }

    /**
     * Called when this script stops, either as a result of returning < 1 from {@link #process()} or
     * calling {@link Bot#setScript(BotScript)}.
     *
     * @return The fallback script that will be ran, possibly {@code null}.
     */
    public BotScript stop() {
        return null; // No default fallback script.
    }

    /**
     * Called whenever {@link Bot#process()} determines it's necessary to do so. This is based on the returned delay
     * from this method, {@link Bot#processBasicActions()}, and if a script is currently assigned to a bot.
     *
     * @return The next delay before this will be called again, in ticks.
     * @throws Exception If any errors occur.
     */
    public abstract int process() throws Exception;

    /**
     * Adds an execution to the counter whenever {@link #process()} is called.
     */
    void addExecution() {
        executions++;
    }

    /**
     * @return How many times {@link #process()} has been called on this script.
     */
    public int getExecutions() {
        return executions;
    }
}
