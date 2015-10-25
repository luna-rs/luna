package io.luna.game.task;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;

public abstract class Task {

	private final boolean instant;
	private int delay;
	private boolean running;
	private int counter;
	private Optional<Object> key = Optional.empty();

	public Task(boolean instant, int delay) {
		checkArgument(delay > 0);
		this.instant = instant;
		this.delay = delay;
	}

	public Task(int delay) {
		this(false, delay);
	}

	protected abstract void execute();

	protected final boolean canExecute() {
		if (++counter >= delay && running) {
			counter = 0;
			return true;
		}
		return false;
	}

	public final void cancel() {
		if (running) {
			onCancel();
			running = false;
		}
	}

	protected void onLoop() {

	}

	protected void onSubmit() {

	}

	protected void onCancel() {

	}

	protected void onException(Throwable t) {

	}

	public Task attach(Object newKey) {
		key = Optional.ofNullable(newKey);
		return this;
	}

	public boolean isInstant() {
		return instant;
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public boolean isRunning() {
		return running;
	}

	public Optional<Object> getKey() {
		return key;
	}

	public Object takeKey() {
		return key.get();
	}
}
