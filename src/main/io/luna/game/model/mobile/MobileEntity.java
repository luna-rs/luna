package io.luna.game.model.mobile;

import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.attr.AttributeMap;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Something that exists in the Runescape world and is able to move around.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public abstract class MobileEntity extends Entity {

    /**
     * An {@link AttributeMap} instance assigned to this {@code MobileEntity}.
     */
    protected final AttributeMap attr = new AttributeMap();

    /**
     * The index of this mob in its list.
     */
    private int index = -1;

    /**
     * Creates a new {@link MobileEntity}.
     *
     * @param context The context to be managed in.
     * @param position The position of this {@code MobileEntity}.
     */
    public MobileEntity(LunaContext context, Position position) {
        super(context, position);
    }

    /**
     * Creates a new {@link MobileEntity} at the default position.
     *
     * @param context The context to be managed in.
     */
    public MobileEntity(LunaContext context) {
        super(context);
    }

    /**
     * @return The index of this {@link MobileEntity} in its list.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Sets the value for {@link #index}, cannot be below {@code 1} unless the
     * value is {@code -1}.
     */
    public void setIndex(int index) {
        if (index != -1) {
            checkArgument(index >= 1, "index < 1");
        }
        this.index = index;
    }

    /**
     * @return The {@link AttributeMap} instance assigned to this {@code MobileEntity}.
     */
    public AttributeMap getAttr() {
        return attr;
    }
}
