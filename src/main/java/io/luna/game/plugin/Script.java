package io.luna.game.plugin;

import io.github.classgraph.ClassInfo;
import io.luna.LunaContext;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;

/**
 * Represents a regular script contained within a {@link Plugin}.
 *
 * @author lare96
 */
public class Script {

    /**
     * The context.
     */
    private final LunaContext context;

    /**
     * The package that this script belongs to.
     */
    private final String packageName;

    /**
     * Metadata related to the scripts runtime class.
     */
    private final ClassInfo info;

    /**
     * The script definition instance.
     */
    private final ScriptTemplateWithArgs definition;

    /**
     * Creates a new {@link Script}.
     *
     * @param context The context.
     * @param packageName The package that this script belongs to.
     * @param info Info related to the scripts runtime class.
     * @param definition The script definition instance.
     */
    public Script(LunaContext context, String packageName, ClassInfo info, ScriptTemplateWithArgs definition) {
        this.context = context;
        this.packageName = packageName;
        this.info = info;
        this.definition = definition;
    }

    /**
     * @return The {@link Plugin} that this script belongs to.
     */
    public Plugin computePlugin() {
        return context.getPlugins().getPluginMap().get(packageName);
    }

    /**
     * @return The package that this script belongs to.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return Info related to the scripts runtime class.
     */
    public ClassInfo getInfo() {
        return info;
    }

    /**
     * @return The script definition instance.
     */
    public ScriptTemplateWithArgs getDefinition() {
        return definition;
    }
}
