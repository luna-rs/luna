package io.luna.game.model.mob.dialogue;

import io.luna.net.msg.out.WidgetAnimationMessageWriter;
import io.luna.util.RandomUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An enumerated type whose elements represent dialogue facial expressions.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum Expression {

    /**
     * The skeptical expressions. Includes {@link #DEFAULT}.
     */
    SKEPTICAL(588, 589, 590, 591),

    /**
     * The angry expressions.
     */
    ANGRY(592, 593, 594, 595),

    /**
     * The worried expressions.
     */
    WORRIED(596, 597, 598, 599),

    /**
     * The sleepy expressions.
     */
    SLEEPY(600, 601, 602, 603),

    /**
     * The laughing expressions.
     */
    LAUGHING(605, 606, 607, 608),

    /**
     * The sad expressions.
     */
    SAD(610, 611, 612, 613),

    /**
     * The default expression.
     */
    DEFAULT(591),

    /**
     * The angry silent expression. The lips of the model do not move.
     */
    ANGRY_SILENT(604),

    /**
     * The angry laughing expression.
     */
    ANGRY_LAUGHING(609);

    /**
     * The expression identifiers.
     */
    private final int[] ids;

    /**
     * Creates a new {@link Expression}.
     *
     * @param ids The expression identifiers.
     */
    Expression(int... ids) {
        checkArgument(ids.length > 0, "At minimum one identifier is required.");
        this.ids = ids;
    }

    /**
     * Builds a {@link WidgetAnimationMessageWriter} from the underlying identifiers.
     *
     * @param widgetId The widget to build the message for.
     * @return The message.
     */
    public WidgetAnimationMessageWriter buildMsgWriter(int widgetId) {
        int animationId = RandomUtils.random(ids);
        return new WidgetAnimationMessageWriter(widgetId, animationId);
    }

    /**
     * @return The expression identifiers.
     */
    public final int[] getIds() {
        return ids;
    }
}