package io.luna.game.model.mob.block;

import io.luna.game.model.Position;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PrayerIcon;
import io.luna.game.model.mob.SkullIcon;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

/**
 * Immutable container holding all per-tick update-block values for a mob.
 * <p>
 * A mob's update state for a single game cycle is captured in an {@link UpdateBlockData} instance, which is then
 * consumed by the outgoing update protocol to decide which update blocks to encode. Each field corresponds to a
 * specific update block type (animation, graphics, hitsplats, etc).
 * </p>
 * <p>
 * All fields are nullable: a {@code null} value indicates that the mob did <em>not</em> perform that type of update
 * during this game cycle, and therefore no corresponding block should be encoded. This keeps update packets compact
 * by only sending changes.
 * </p>
 * <p>
 * Instances are constructed via the nested {@link Builder} to keep {@code UpdateBlockData} immutable once created.
 * </p>
 *
 * @author lare96
 */
public final class UpdateBlockData {

    /**
     * Builder used to construct a single immutable {@link UpdateBlockData} snapshot.
     * <p>
     * The builder pattern is used because in a typical tick only a small subset of blocks will actually be present.
     * Callers selectively set the blocks that changed and then call {@link #build()} to create a compact, read-only
     * snapshot for the encoder.
     * </p>
     * <p>
     * Many appearance-related fields can be cached between ticks and only updated when something changes
     * (e.g., equipment swap, hairstyle change). This avoids rebuilding expensive appearance data when it is unchanged.
     * </p>
     */
    public static final class Builder {

        /* Default builder properties matching UpdateBlockData. */
        private final Mob mob;
        private Animation animation;
        private Position face;
        private String speak;
        private Graphic graphic;
        private Integer interact;
        private Hit hit1;
        private Hit hit2;
        private Chat chat;
        private ExactMovement move;

        /**
         * Creates an empty builder with all fields initially {@code null} or defaulted.
         *
         * @param mob The owning mob.
         */
        public Builder(Mob mob) {
            this.mob = mob;
        }

        /**
         * Sets the animation block.
         *
         * @param value The animation to play, or {@code null} to clear it.
         * @return This builder instance for chaining.
         */
        public Builder animation(Animation value) {
            animation = value;
            mob.getFlags().flag(UpdateFlag.ANIMATION);
            return this;
        }

        /**
         * Sets the facing position block.
         *
         * @param value The absolute position to face, or {@code null} to clear it.
         * @return This builder instance for chaining.
         */
        public Builder face(Position value) {
            face = value;
            mob.getFlags().flag(UpdateFlag.FACE_POSITION);
            return this;
        }

        /**
         * Sets the forced speech (overhead text) block.
         *
         * @param value The text to display above the mob, or {@code null} to clear it.
         * @return This builder instance for chaining.
         */
        public Builder speak(String value) {
            speak = value;
            mob.getFlags().flag(UpdateFlag.SPEAK);
            return this;
        }

        /**
         * Sets the graphic block.
         *
         * @param value The graphic to play, or {@code null} to clear it.
         * @return This builder instance for chaining.
         */
        public Builder graphic(Graphic value) {
            graphic = value;
            mob.getFlags().flag(UpdateFlag.GRAPHIC);
            return this;
        }

        /**
         * Sets the interaction index block.
         *
         * @param value The index of the entity being interacted with, or {@code null}.
         * @return This builder instance for chaining.
         */
        public Builder interact(Integer value) {
            interact = value;
            mob.getFlags().flag(UpdateFlag.INTERACTION);
            return this;
        }

        /**
         * Sets the primary hitsplat block.
         *
         * @param value The primary hit, or {@code null} if there is no primary hit.
         * @return This builder instance for chaining.
         */
        public Builder hit1(Hit value) {
            hit1 = value;
            mob.getFlags().flag(UpdateFlag.PRIMARY_HIT);
            return this;
        }

        /**
         * Sets the secondary hitsplat block.
         *
         * @param value The secondary hit, or {@code null} if there is no secondary hit.
         * @return This builder instance for chaining.
         */
        public Builder hit2(Hit value) {
            hit2 = value;
            mob.getFlags().flag(UpdateFlag.SECONDARY_HIT);
            return this;
        }

        /**
         * Sets the normal chat block.
         *
         * @param value The chat payload, or {@code null} if there is no chat this tick.
         * @return This builder instance for chaining.
         */
        public Builder chat(Chat value) {
            chat = value;
            mob.getFlags().flag(UpdateFlag.CHAT);
            return this;
        }

        /**
         * Sets the exact movement block.
         *
         * @param value The forced movement descriptor, or {@code null} if not used.
         * @return This builder instance for chaining.
         */
        public Builder move(ExactMovement value) {
            move = value;
            mob.getFlags().flag(UpdateFlag.EXACT_MOVEMENT);
            return this;
        }

        /**
         * Builds an immutable {@link UpdateBlockData} instance with the accumulated values.
         * <p>
         * After calling this method, further changes to the builder do not affect the returned snapshot. Callers
         * should create a new builder or reuse the existing one carefully across ticks, updating only the fields that
         * have changed.
         * </p>
         *
         * @return A snapshot of the mob’s update state for this cycle.
         */
        public UpdateBlockData build() {
            if (mob instanceof Player) {
                Player player = mob.asPlr();
                return new UpdateBlockData(animation, face, speak, graphic, interact, hit1, hit2, mob.getTransformId(),
                        chat, move, player.getPrayerIcon(), player.getSkullIcon(), player.getAppearance().toArray(),
                        player.getModel(), player.getEquipment().toArray(), player.getUsernameHash(),
                        player.getSkills().getCombatLevel(), player.getSkills().getSkillLevel());
            } else {
                return new UpdateBlockData(animation, face, speak, graphic, interact, hit1, hit2, mob.getTransformId(),
                        null, null, null, null, null, null, null, 0L, 0, 0);
            }
        }

        /* Convenient getters if external code needs to inspect builder state. */

        /**
         * @return The current animation value in this builder, or {@code null}.
         */
        public Animation getAnimation() {
            return animation;
        }

        /**
         * @return The current facing position in this builder, or {@code null}.
         */
        public Position getFace() {
            return face;
        }

        /**
         * @return The current forced speech text in this builder, or {@code null}.
         */
        public String getSpeak() {
            return speak;
        }

        /**
         * @return The current graphic value in this builder, or {@code null}.
         */
        public Graphic getGraphic() {
            return graphic;
        }

        /**
         * @return The current interaction index in this builder, or {@code null}.
         */
        public Integer getInteract() {
            return interact;
        }

        /**
         * @return The current primary hit in this builder, or {@code null}.
         */
        public Hit getHit1() {
            return hit1;
        }

        /**
         * @return The current secondary hit in this builder, or {@code null}.
         */
        public Hit getHit2() {
            return hit2;
        }

        /**
         * @return The current chat payload in this builder, or {@code null}.
         */
        public Chat getChat() {
            return chat;
        }

        /**
         * @return The current exact movement value in this builder, or {@code null}.
         */
        public ExactMovement getMove() {
            return move;
        }
    }

    /* All fields below are immutable and match the builder fields. */

    /**
     * The animation update block for this tick, or {@code null} if none.
     */
    final Animation animation;

    /**
     * The facing position block for this tick, or {@code null} if none.
     */
    final Position face;

    /**
     * The forced speech text block for this tick, or {@code null} if none.
     */
    final String speak;

    /**
     * The graphic update block for this tick, or {@code null} if none.
     */
    final Graphic graphic;

    /**
     * The interaction target index block, or {@code null} if not interacting.
     */
    final Integer interact;

    /**
     * The primary hitsplat block, or {@code null} if none.
     */
    final Hit hit1;

    /**
     * The secondary hitsplat block, or {@code null} if none.
     */
    final Hit hit2;

    /**
     * The transformation ID block, or {@code null} if not transformed.
     */
    final int transform;

    /**
     * The normal chat block, or {@code null} if no chat this tick.
     */
    final Chat chat;

    /**
     * The exact movement block, or {@code null} if not used.
     */
    final ExactMovement move;

    /**
     * The prayer icon value within the appearance block, or {@code null}.
     */
    final PrayerIcon prayer;

    /**
     * The skull icon value within the appearance block, or {@code null}.
     */
    final SkullIcon skull;

    /**
     * The appearance array used by the appearance block, or {@code null}.
     */
    final int[] appearance;

    /**
     * The model animation definition used by the appearance block, or {@code null}.
     */
    final PlayerModelAnimation model;

    /**
     * The equipped items used by the appearance block, or {@code null}.
     */
    final Item[] equipment;

    /**
     * The packed username used by the appearance block.
     */
    final long username;

    /**
     * The combat level used by the appearance block.
     */
    final int combat;

    /**
     * The skill/total level used by the appearance block.
     */
    final int skill;

    /**
     * Creates a fully-initialized update block snapshot.
     * <p>
     * This constructor is intentionally private and only invoked by
     * {@link UpdateBlockData.Builder#build()} to ensure immutability and a single
     * construction path for {@code UpdateBlockData}.
     * </p>
     */
    private UpdateBlockData(Animation animation,
                            Position face,
                            String speak,
                            Graphic graphic,
                            Integer interact,
                            Hit hit1,
                            Hit hit2,
                            int transform,
                            Chat chat,
                            ExactMovement move,
                            PrayerIcon prayer,
                            SkullIcon skull,
                            int[] appearance,
                            PlayerModelAnimation model,
                            Item[] equipment,
                            long username,
                            int combat,
                            int skill) {
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
        this.prayer = prayer;
        this.skull = skull;
        this.appearance = appearance;
        this.model = model;
        this.equipment = equipment;
        this.username = username;
        this.combat = combat;
        this.skill = skill;
    }
}
