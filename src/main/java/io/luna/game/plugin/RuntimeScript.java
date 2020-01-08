package io.luna.game.plugin;

import io.github.classgraph.ClassInfo;
import kotlin.script.templates.standard.ScriptTemplateWithArgs;

import java.util.Objects;

/**
 * A model that holds runtime data about a single Kotlin script file (.kts) contained within a {@link Plugin}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RuntimeScript {

    /**
     * The runtime information about the script.
     */
    private final ClassInfo info;

    /**
     * The script template. Essentially a runtime instance of the script itself.
     */
    private ScriptTemplateWithArgs script;

    /**
     * Creates a new {@link RuntimeScript}.
     *
     * @param info The runtime information about the script.
     * @param script The script template.
     */
    public RuntimeScript(ClassInfo info, ScriptTemplateWithArgs script) {
      this.info = info;
      this.script = script;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RuntimeScript) {
            RuntimeScript other = (RuntimeScript) obj;
            return info.getName().equals(other.info.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(info.getName());
    }

    /**
     * @return The runtime information about the script.
     */
    public ClassInfo getInfo() {
        return info;
    }

    /**
     * @return The script template. Essentially a runtime instance of the script itself.
     */
    public ScriptTemplateWithArgs getScript() {
        return script;
    }
}