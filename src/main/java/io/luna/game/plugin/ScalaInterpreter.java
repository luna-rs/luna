package io.luna.game.plugin;

import ammonite.Main;
import ammonite.util.Res;
import scala.Option;
import scala.collection.immutable.Vector$;

import java.io.OutputStream;

import static io.luna.game.plugin.PluginBootstrap.DIR;

/**
 * A wrapper for {@link Main} that interprets Scala scripts.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ScalaInterpreter {

    /**
     * A builder for this class.
     */
    public static final class Builder {

        /**
         * The output and error output streams.
         */
        private OutputStream outStream = System.out;

        /**
         * If output should output be verbose.
         */
        private boolean verboseOutput;

        /**
         * The predef code.
         */
        private String predefCode = "";

        /**
         * Sets the new predef code.
         *
         * @param predefCode The predef code.
         * @return This builder.
         */
        public Builder predef(String predefCode) {
            this.predefCode = predefCode;
            return this;
        }

        /**
         * Sets the output and error output streams.
         *
         * @param outStream The output and error output streams.
         * @return This builder.
         */
        public Builder outStream(OutputStream outStream) {
            this.outStream = outStream;
            return this;
        }

        /**
         * Enables verbose output.
         *
         * @return This builder.
         */
        public Builder enableVerboseOutput() {
            verboseOutput = true;
            return this;
        }

        /**
         * Builds a new {@link ScalaInterpreter} instance.
         *
         * @return The instance.
         */
        public ScalaInterpreter build() {
            return new ScalaInterpreter(this);
        }
    }

    /**
     * The Scala interpreter.
     */
    private final Main interpreter;

    /**
     * Creates a new {@link ScalaInterpreter}.
     *
     * @param builder The builder.
     */
    private ScalaInterpreter(Builder builder) {
        interpreter = new Main(builder.predefCode,
                Main.apply$default$2(),
                false,
                Main.apply$default$4(),
                new os.Path(DIR.toAbsolutePath()),
                Option.empty(),
                Main.apply$default$7(),
                builder.outStream,
                builder.outStream,
                builder.verboseOutput,
                false,
                Main.apply$default$12(),
                Main.apply$default$13(),
                Main.apply$default$14(),
                Main.apply$default$15());
    }

    /**
     * Evaluates {@code script} using the backing interpreter.
     *
     * @param script The script to evalute.
     * @throws ScriptInterpretException If the evaluation fails.
     */
    public void eval(Script script) throws ScriptInterpretException {
        os.Path ammonitePath = new os.Path(script.getPath());
        Res<Object> result = interpreter.runScript(ammonitePath, Vector$.MODULE$.empty())._1;
        if (!result.isSuccess()) {
            throw new ScriptInterpretException(script, result);
        }
    }
}