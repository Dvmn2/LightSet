package net.dvmn2.lightsetplugin.model;

/**
 * Outcome of moving a light source to a location given as a world position.
 */
public enum MoveResult {
    /** The source was moved. */
    SUCCESS,
    /** The target location is in another world. */
    OTHER_WORLD
}
