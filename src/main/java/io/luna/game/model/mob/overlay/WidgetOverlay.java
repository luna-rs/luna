package io.luna.game.model.mob.overlay;

import io.luna.game.model.def.WidgetDefinition;

/**
 * A base {@link AbstractOverlay} implementation for overlays backed by a widget definition.
 * <p>
 * {@code WidgetOverlay} represents any interface that has a corresponding entry in
 * {@link WidgetDefinition}, identified by a numeric widget id. This includes both
 * standard interfaces and walkable overlays.
 *
 * @author lare96
 */
abstract class WidgetOverlay extends AbstractOverlay {

    /**
     * The client widget identifier associated with this overlay.
     */
    final int id;

    /**
     * The cached widget definition for this overlay.
     */
    private final WidgetDefinition widgetDef;

    /**
     * Creates a new {@link WidgetOverlay} for the specified widget id and overlay type.
     *
     * @param id The client widget identifier.
     * @param walkable Whether this overlay should use {@link OverlayType#WIDGET_WALKABLE} (if {@code true})
     * or {@link OverlayType#WIDGET_STANDARD} (if {@code false}).
     */
    public WidgetOverlay(int id, boolean walkable) {
        super(walkable ? OverlayType.WIDGET_WALKABLE : OverlayType.WIDGET_STANDARD);
        this.id = id;
        this.widgetDef = WidgetDefinition.ALL.retrieve(id);
    }

    /**
     * Returns the widget identifier associated with this overlay.
     *
     * @return The widget id.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the {@link WidgetDefinition} describing this overlay.
     * <p>
     * The definition is retrieved from {@link WidgetDefinition#ALL} at construction time.
     *
     * @return The widget definition, or {@code null} if not found.
     */
    public WidgetDefinition getWidgetDef() {
        return widgetDef;
    }
}
