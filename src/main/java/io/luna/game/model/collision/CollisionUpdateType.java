package io.luna.game.model.collision;

/**
 * An enum which represents the type of a {@link CollisionUpdate}.
 *
 * @author Major
 */
public enum CollisionUpdateType {

	/**
	 * Indicates that a {@link CollisionUpdate} will be adding new flags to collision matrices.
	 */
	ADDING,

	/**
	 * Indicates that a {@link CollisionUpdate} will be clearing existing flags from collision matrices.
	 */
	REMOVING

}