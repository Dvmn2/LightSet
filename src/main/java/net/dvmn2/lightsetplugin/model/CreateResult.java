package net.dvmn2.lightsetplugin.model;

/**
 * Outcome of creating a light source.
 */
public enum CreateResult {
    /** The source was created. */
    SUCCESS,
    /** The name does not match the allowed format. */
    INVALID_NAME,
    /** A source with this name already exists. */
    NAME_EXISTS
}
