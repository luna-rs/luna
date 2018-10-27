package io.luna.game.model.mob.dialogue;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;

import java.util.ArrayDeque;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A builder that uses chaining to enable the dynamic and concise construction of dialogues. Unless otherwise
 * necessary, this model should be accessed through {@link Player#newDialogue()}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class DialogueQueueBuilder {

    /**
     * The player.
     */
    private final Player player;

    /**
     * The queue of dialogues.
     */
    private final ArrayDeque<DialogueInterface> dialogues;

    /**
     * If this builder is locked.
     */
    private boolean locked;

    /**
     * Creates a new {@link DialogueQueueBuilder}.
     *
     * @param player The player.
     * @param initialSize The initial size of the internal queue.
     */
    public DialogueQueueBuilder(Player player, int initialSize) {
        this.player = player;
        dialogues = new ArrayDeque<>(initialSize);
    }

    /**
     * Shortcut to {@link PlayerDialogueInterface#PlayerDialogueInterface(Expression, String...)}.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder playerChat(Expression expression, String... text) {
        checkLocked();
        dialogues.add(new PlayerDialogueInterface(expression, text));
        return this;
    }

    /**
     * Shortcut to {@link PlayerDialogueInterface#PlayerDialogueInterface(Expression, String...)} with the
     * default expression value.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder playerChat(String... text) {
        return playerChat(Expression.DEFAULT, text);
    }

    /**
     * Shortcut to {@link NpcDialogueInterface#NpcDialogueInterface(int, Expression, String...)}.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder npcChat(int npcId, Expression expression, String... text) {
        checkLocked();
        dialogues.add(new NpcDialogueInterface(npcId, expression, text));
        return this;
    }

    /**
     * Shortcut to {@link NpcDialogueInterface#NpcDialogueInterface(int, Expression, String...)} with the
     * default expression value.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder npcChat(int npcId, String... text) {
        return npcChat(npcId, Expression.DEFAULT, text);
    }

    /**
     * Shortcut to {@link TextDialogueInterface#TextDialogueInterface(String...)}.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder chat(String... text) {
        checkLocked();
        dialogues.add(new TextDialogueInterface(text));
        return this;
    }

    /**
     * Shortcut to {@link OptionDialogueInterface#OptionDialogueInterface(String...)} with two options.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder options(String option0, Consumer<Player> action0,
                                        String option1, Consumer<Player> action1) {
        checkLocked();
        dialogues.add(new OptionDialogueInterface(option0, option1) {
            @Override
            public void firstOption(Player player) {
                action0.accept(player);
            }

            @Override
            public void secondOption(Player player) {
                action1.accept(player);
            }
        });
        return this;
    }

    /**
     * Shortcut to {@link OptionDialogueInterface#OptionDialogueInterface(String...)} with three options.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder options(String option0, Consumer<Player> action0,
                                        String option1, Consumer<Player> action1,
                                        String option2, Consumer<Player> action2) {
        checkLocked();
        dialogues.add(new OptionDialogueInterface(option0, option1, option2) {
            @Override
            public void firstOption(Player player) {
                action0.accept(player);
            }

            @Override
            public void secondOption(Player player) {
                action1.accept(player);
            }

            @Override
            public void thirdOption(Player player) {
                action2.accept(player);
            }
        });
        return this;
    }

    /**
     * Shortcut to {@link OptionDialogueInterface#OptionDialogueInterface(String...)} with four options.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder options(String option0, Consumer<Player> action0,
                                        String option1, Consumer<Player> action1,
                                        String option2, Consumer<Player> action2,
                                        String option3, Consumer<Player> action3) {
        checkLocked();
        dialogues.add(new OptionDialogueInterface(option0, option1, option2, option3) {
            @Override
            public void firstOption(Player player) {
                action0.accept(player);
            }

            @Override
            public void secondOption(Player player) {
                action1.accept(player);
            }

            @Override
            public void thirdOption(Player player) {
                action2.accept(player);
            }

            @Override
            public void fourthOption(Player player) {
                action3.accept(player);
            }
        });
        return this;
    }

    /**
     * Shortcut to {@link OptionDialogueInterface#OptionDialogueInterface(String...)} with all five options.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder options(String option0, Consumer<Player> action0,
                                        String option1, Consumer<Player> action1,
                                        String option2, Consumer<Player> action2,
                                        String option3, Consumer<Player> action3,
                                        String option4, Consumer<Player> action4) {
        checkLocked();
        dialogues.add(new OptionDialogueInterface(option0, option1, option2, option3, option4) {
            @Override
            public void firstOption(Player player) {
                action0.accept(player);
            }

            @Override
            public void secondOption(Player player) {
                action1.accept(player);
            }

            @Override
            public void thirdOption(Player player) {
                action2.accept(player);
            }

            @Override
            public void fourthOption(Player player) {
                action3.accept(player);
            }

            @Override
            public void fifthOption(Player player) {
                action4.accept(player);
            }
        });
        return this;
    }

    /**
     * Shortcut to {@link GiveItemDialogueInterface#GiveItemDialogueInterface(Item, String)}.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder giveItem(Item item, String displayText) {
        checkLocked();
        dialogues.add(new GiveItemDialogueInterface(item, displayText));
        return this;
    }

    /**
     * Shortcut to {@link GiveItemDialogueInterface#GiveItemDialogueInterface(Item)}.
     *
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder giveItem(Item item) {
        checkLocked();
        dialogues.add(new GiveItemDialogueInterface(item));
        return this;
    }

    /**
     * Attaches the argued consumer to the last appended dialogue.
     *
     * @param action The action to run.
     * @return This builder, for chaining.
     */
    public DialogueQueueBuilder openAction(Consumer<Player> action) {
        checkLocked();

        DialogueInterface lastDialogue = dialogues.peekLast();

        checkState(lastDialogue != null, "No past dialogue to attach action to.");
        lastDialogue.setOpenAction(action);
        return this;
    }

    /**
     * Throws an exception if this builder is locked.
     */
    private void checkLocked() {
        checkState(!locked, "Cannot append to dialogue builder once it has been locked.");
    }

    /**
     * Creates and advances a new dialogue queue. Sets this builder's internal queue as the Player's current
     * dialogue queue.
     */
    public void open() {
        if(dialogues.size() == 1) {
            // Optimization for single-entry dialogues.
            player.getInterfaces().open(dialogues.peekFirst());
        } else {
            DialogueQueue queue = new DialogueQueue(player, dialogues);
            queue.advance();

            player.setDialogues(queue);
        }

        // Lock so that this builder's reference to 'dialogues' is immutable.
        locked = true;
    }
}