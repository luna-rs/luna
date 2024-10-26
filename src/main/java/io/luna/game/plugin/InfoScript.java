package io.luna.game.plugin;

import io.github.classgraph.ClassInfo;
import io.luna.LunaContext;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;

/**
 * A {@link Script} implementation representing an {@code info.plugin.kts} file within a {@link Plugin}. It holds
 * metadata and must exist in the top-level package of every plugin.
 *
 * @author lare96
 */
public final class InfoScript extends Script {

    /**
     * The metadata contained within the {@code info.plugin.kts} file.
     */
    private final InfoScriptData data;

    /**
     * Creates a new {@link Script}.
     *
     * @param context The context.
     * @param packageName The package that this script belongs to.
     * @param info Info related to the scripts runtime class.
     * @param definition The script definition instance.
     * @param data The metadata contained within the {@code info.plugin.kts} file.
     */
    public InfoScript(LunaContext context, String packageName, ClassInfo info, ScriptTemplateWithArgs definition, InfoScriptData data) {
        super(context, packageName, info, definition);
        this.data = data;
    }

    /**
     * @return The metadata contained within the {@code info.plugin.kts} file.
     */
    public InfoScriptData getData() {
        return data;
    }
}
