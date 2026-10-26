package net.dvmn2.lightsetplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Root object of {@code lights.json}. Public fields, no logic.
 *
 * <pre>
 * {
 *   "version": 1,
 *   "lights": [ { "name": "stage1", "type": "spot", "world": "world", ... } ]
 * }
 * </pre>
 */
public final class LightsFileDto {

    public int version;
    public List<LightDto> lights = new ArrayList<>();
}
