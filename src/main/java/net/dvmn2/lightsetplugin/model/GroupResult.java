package net.dvmn2.lightsetplugin.model;

/**
 * Outcome of changing the group of a light source.
 */
public enum GroupResult {
    /** The group was changed. */
    SUCCESS,
    /** The group name does not match the allowed format. */
    INVALID_NAME
}
