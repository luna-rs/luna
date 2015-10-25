package io.luna.game.task;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TaskManager {

	private static final Logger LOGGER = LogManager.getLogger(TaskManager.class);
	private final List<Task> tasks = new LinkedList<>();
	private final Queue<Task> readyTasks = new ArrayDeque<>();

	public void schedule(Task t) {
		if (t.isInstant()) {
			t.execute();
		}
		tasks.add(t);
	}

	public void handleTasks() {
		Iterator<Task> $it = tasks.iterator();
		while ($it.hasNext()) {
			Task it = $it.next();

			if (!it.isRunning()) {
				$it.remove();
				continue;
			}

			if (it.canExecute()) {
				readyTasks.add(it);
			}
		}

		for (;;) {
			Task it = readyTasks.poll();
			if (it == null) {
				break;
			}
			it.execute();
		}
	}
}
