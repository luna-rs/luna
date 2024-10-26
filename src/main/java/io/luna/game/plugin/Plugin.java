package io.luna.game.plugin;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/**
 * Represents a loaded and active plugin. Plugins consist of an {@link InfoScript} along with a set of content
 * {@link Script} types, and are contained within their own package tree.
 * <p>
 * Nesting plugins will result in runtime errors. Each plugin should have its own package tree, with a {@code info.plugin.kts} file
 * in the top level package.
 * <p>
 * This class only contains runtime information about plugins and their scripts. To actually run the code within the scripts, see the
 * {@link io.luna.game.event} package.
 *
 * @author lare96
 */
public final class Plugin {

    /**
     * The top-level package of this plugin.
     */
    private final String packageName;

    /**
     * The {@code info.plugin.kts} script for this plugin.
     */
    private final InfoScript infoScript;

    /**
     * The content scripts for this plugin.
     */
    private final ImmutableList<Script> scripts;

    /**
     * Creates a new {@link Plugin}.
     *
     * @param packageName The top-level package of this plugin.
     * @param infoScript The {@code info.plugin.kts} script for this plugin.
     * @param scripts The content scripts for this plugin.
     */
    public Plugin(String packageName, InfoScript infoScript, ImmutableList<Script> scripts) {
        this.packageName = packageName;
        this.infoScript = infoScript;
        this.scripts = scripts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plugin)) return false;
        Plugin plugin = (Plugin) o;
        return Objects.equals(packageName, plugin.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packageName);
    }

    /**
     * @return The top-level package of this plugin.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * @return The name of this plugin.
     */
    public String getName() {
        return infoScript.getData().getName();
    }

    /**
     * @return The {@code info.plugin.kts} script for this plugin.
     */
    public Script getInfoScript() {
        return infoScript;
    }

    /**
     * @return The content scripts for this plugin.
     */
    public ImmutableList<Script> getScripts() {
        return scripts;
    }
}
