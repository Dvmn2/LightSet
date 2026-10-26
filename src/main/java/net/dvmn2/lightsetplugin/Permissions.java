package net.dvmn2.lightsetplugin;

/**
 * Permission nodes. Every node is also declared in {@code plugin.yml}.
 */
public final class Permissions {

    /** List, inspect and teleport to light sources. */
    public static final String VIEW = "lightset.view";
    /** Switch, dim and animate light sources and groups. */
    public static final String CONTROL = "lightset.control";
    /** Create, remove and reconfigure light sources. */
    public static final String EDIT = "lightset.edit";
    /** Reload the configuration. */
    public static final String RELOAD = "lightset.reload";

    private Permissions() {
    }
}
