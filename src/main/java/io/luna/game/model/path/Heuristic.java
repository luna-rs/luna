package io.luna.game.model.path;


import io.luna.game.model.Position;

/**
 * A heuristic used by the A* algorithm.
 *
 * @author Major
 */
public abstract class Heuristic {

	/**
	 * Estimates the value for this heuristic.
	 *
	 * @param current The current {@link Position}.
	 * @param target The target position.
	 * @return The heuristic value.
	 */
	public abstract int estimate(Position current, Position target);

}