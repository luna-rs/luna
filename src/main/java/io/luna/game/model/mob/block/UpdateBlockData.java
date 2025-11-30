package io.luna.game.model.mob.block;

import io.luna.game.model.Position;

/**
 * Immutable container holding all per-tick update-block values for a mob. A mob's update block for the cycle is
 * constructed via the nested {@link Builder}, and then encoded by the outgoing update protocol.
 * <p>
 * All fields are nullable. A {@code null} value indicates that the mob did not perform that type of update during
 * this game cycle and therefore no corresponding block should be encoded.
 * </p>
 *
 * @author lare96
 */
public final class UpdateBlockData {

    /**
     * Builder for constructing a single {@link UpdateBlockData} snapshot.
     * <p>
     * The builder pattern is used because only a small subset of fields are typically present per tick, and the
     * update block needs to remain immutable after construction.
     * </p>
     */
    public static final class Builder {

        /**
         * Animation update (e.g., playing an emote or weapon animation).
         */
        private Animation animation;

        /**
         * The absolute position this mob should face.
         */
        private Position face;

        /**
         * Forced speech text (client-side overhead text).
         */
        private String speak;

        /**
         * Graphic update (e.g., spell cast or effect animation).
         */
        private Graphic graphic;

        /**
         * Interaction target index (the mob or object being interacted with).
         */
        private Integer interact;

        /**
         * Primary hitsplat (damage, block, poison, etc.).
         */
        private Hit hit1;

        /**
         * Secondary hitsplat, if any.
         */
        private Hit hit2;

        /**
         * Mob transformation ID (used for disguises, morphing NPCs, etc.).
         */
        private Integer transform;

        /**
         * Normal chat message (bubble chat).
         */
        private Chat chat;

        /**
         * Exact movement update (used by forced/teleport movement blocks).
         */
        private ExactMovement move;

        /**
         * Sets the animation block.
         */
        public Builder animation(Animation value) {
            animation = value;
            return this;
        }

        /**
         * Sets the facing direction block.
         */
        public Builder face(Position value) {
            face = value;
            return this;
        }

        /**
         * Sets the forced speech (overhead text).
         */
        public Builder speak(String value) {
            speak = value;
            return this;
        }

        /**
         * Sets the graphic block.
         */
        public Builder graphic(Graphic value) {
            graphic = value;
            return this;
        }

        /**
         * Sets the interaction index (entity being interacted with).
         */
        public Builder interact(Integer value) {
            interact = value;
            return this;
        }

        /**
         * Sets the primary hitsplat block.
         */
        public Builder hit1(Hit value) {
            hit1 = value;
            return this;
        }

        /**
         * Sets the secondary hitsplat block.
         */
        public Builder hit2(Hit value) {
            hit2 = value;
            return this;
        }

        /**
         * Sets the mob transformation block.
         */
        public Builder transform(Integer value) {
            transform = value;
            return this;
        }

        /**
         * Sets the chat block.
         */
        public Builder chat(Chat value) {
            chat = value;
            return this;
        }

        /**
         * Sets the exact movement block.
         */
        public Builder move(ExactMovement value) {
            move = value;
            return this;
        }

        /**
         * Builds an immutable {@link UpdateBlockData} instance with the accumulated values.
         *
         * @return A snapshot of the mob’s update state for this cycle.
         */
        public UpdateBlockData build() {
            return new UpdateBlockData(animation, face, speak, graphic, interact, hit1, hit2, transform, chat, move);
        }

        /* Convenient getters if external code needs to inspect builder state. */
        public Animation getAnimation() {
            return animation;
        }

        public Position getFace() {
            return face;
        }

        public String getSpeak() {
            return speak;
        }

        public Graphic getGraphic() {
            return graphic;
        }

        public Integer getInteract() {
            return interact;
        }

        public Hit getHit1() {
            return hit1;
        }

        public Hit getHit2() {
            return hit2;
        }

        public Integer getTransform() {
            return transform;
        }

        public Chat getChat() {
            return chat;
        }

        public ExactMovement getMove() {
            return move;
        }
    }

    /* All fields below are immutable and match the builder fields. */
    final Animation animation;
    final Position face;
    final String speak;
    final Graphic graphic;
    final Integer interact;
    final Hit hit1;
    final Hit hit2;
    final Integer transform;
    final Chat chat;
    final ExactMovement move;

    /**
     * Creates a fully-initialized update block snapshot. This constructor is intentionally private and only invoked
     * by {@link UpdateBlockData.Builder#build()}.
     */
    private UpdateBlockData(Animation animation, Position face, String speak, Graphic graphic, Integer interact,
                            Hit hit1, Hit hit2, Integer transform, Chat chat, ExactMovement move) {
        this.animation = animation;
        this.face = face;
        this.speak = speak;
        this.graphic = graphic;
        this.interact = interact;
        this.hit1 = hit1;
        this.hit2 = hit2;
        this.transform = transform;
        this.chat = chat;
        this.move = move;
    }
}
