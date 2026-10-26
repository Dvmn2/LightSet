package net.dvmn2.lightsetplugin.storage.dto;

import org.jetbrains.annotations.Nullable;

/**
 * Stored form of one light source inside {@code lights.json}. Public fields, no logic.
 */
public final class LightDto {

    public String name;
    public String type = "spot";
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public int brightness = 15;
    public float angle = 40f;
    public int range = 250;
    public int rays = 120;
    public boolean enabled = true;
    public String effect = "none";
    public @Nullable String group;
    public boolean beam = true;
    public @Nullable Double x2;
    public @Nullable Double y2;
    public @Nullable Double z2;
}
