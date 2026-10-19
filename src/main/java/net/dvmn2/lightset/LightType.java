package net.dvmn2.lightset;

import java.util.Locale;

/**
 * Тип источника света.
 */
public enum LightType {
    /**
     * Направленный прожектор: лучи в конусе, свет на поверхностях.
     */
    SPOT,
    /**
     * Точечная лампа: один световой блок.
     */
    LAMP,
    /**
     * Линия света между двумя точками.
     */
    STRIP,
    /**
     * Прямоугольная область (сетка световых блоков).
     */
    AREA;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LightType fromKey(String key) {
        if (key == null) {
            return null;
        }
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
