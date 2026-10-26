package net.dvmn2.lightsetplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN localisation of every message shown to players, in MiniMessage format.
 *
 * <p>Thread safe: the tables are filled once in the static initializer and the language is a
 * {@code volatile} value. Values are inserted only through {@link TagResolver}s.
 */
public final class Lang {

    /** Message keys. Every key has both a Russian and an English text. */
    public enum Key {
        USAGE,
        INVALID_NAME,
        NAME_EXISTS,
        NOT_FOUND,
        INVALID_TYPE,
        INVALID_EFFECT,
        INVALID_TOGGLE,
        INVALID_POSITION,
        NEED_ENTITY,
        SPOT_ONLY,
        LINE_ONLY,
        WORLD_MISSING,
        OTHER_WORLD,
        CREATED,
        REMOVED,
        LIST_HEADER,
        LIST_EMPTY,
        LIST_ENTRY,
        INFO,
        INFO_SPOT,
        INFO_POS2,
        STATE_ON,
        STATE_OFF,
        STATE_CHANGED,
        BRIGHTNESS_SET,
        AIMED,
        ANGLE_SET,
        RANGE_SET,
        RAYS_SET,
        BEAM_SET,
        EFFECT_SET,
        MOVED,
        POS2_SET,
        TELEPORTED,
        GROUP_SET,
        GROUP_CLEARED,
        GROUP_APPLIED,
        GROUP_EMPTY,
        RELOADED
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.USAGE,
                "<gold>LightSet <gray>— команды:\n"
                        + "<yellow>/lightset create \\<имя> \\<spot|lamp|strip|area>\n"
                        + "<yellow>/lightset remove|info|tp \\<имя>, /lightset list\n"
                        + "<yellow>/lightset on|off|toggle \\<имя>\n"
                        + "<yellow>/lightset brightness \\<имя> \\<0-15>\n"
                        + "<yellow>/lightset aim \\<имя> \\<yaw> \\<pitch>, aimhere \\<имя>, lookat \\<имя> \\<x y z>,"
                        + " rotate \\<имя> \\<dYaw> \\<dPitch>\n"
                        + "<yellow>/lightset move \\<имя> [x y z], pos2 \\<имя> [x y z]\n"
                        + "<yellow>/lightset angle|range|rays \\<имя> \\<значение>\n"
                        + "<yellow>/lightset beam \\<имя> \\<on|off>\n"
                        + "<yellow>/lightset effect \\<имя> \\<none|flicker|pulse|blink>\n"
                        + "<yellow>/lightset group set \\<имя> \\<группа>, group clear \\<имя>\n"
                        + "<yellow>/lightset group on|off|toggle \\<группа>, group brightness \\<группа> \\<0-15>\n"
                        + "<yellow>/lightset reload",
                "<gold>LightSet <gray>— commands:\n"
                        + "<yellow>/lightset create \\<name> \\<spot|lamp|strip|area>\n"
                        + "<yellow>/lightset remove|info|tp \\<name>, /lightset list\n"
                        + "<yellow>/lightset on|off|toggle \\<name>\n"
                        + "<yellow>/lightset brightness \\<name> \\<0-15>\n"
                        + "<yellow>/lightset aim \\<name> \\<yaw> \\<pitch>, aimhere \\<name>,"
                        + " lookat \\<name> \\<x y z>, rotate \\<name> \\<dYaw> \\<dPitch>\n"
                        + "<yellow>/lightset move \\<name> [x y z], pos2 \\<name> [x y z]\n"
                        + "<yellow>/lightset angle|range|rays \\<name> \\<value>\n"
                        + "<yellow>/lightset beam \\<name> \\<on|off>\n"
                        + "<yellow>/lightset effect \\<name> \\<none|flicker|pulse|blink>\n"
                        + "<yellow>/lightset group set \\<name> \\<group>, group clear \\<name>\n"
                        + "<yellow>/lightset group on|off|toggle \\<group>, group brightness \\<group> \\<0-15>\n"
                        + "<yellow>/lightset reload");

        put(Key.INVALID_NAME,
                "<red>Имя: латинские буквы, цифры, _ и - (до 32 символов).",
                "<red>Name: Latin letters, digits, _ and - (up to 32 characters).");
        put(Key.NAME_EXISTS,
                "<red>Свет с именем <name> уже существует.",
                "<red>A light named <name> already exists.");
        put(Key.NOT_FOUND,
                "<red>Свет <name> не найден.",
                "<red>Light <name> not found.");
        put(Key.INVALID_TYPE,
                "<red>Тип должен быть spot, lamp, strip или area.",
                "<red>Type must be spot, lamp, strip or area.");
        put(Key.INVALID_EFFECT,
                "<red>Эффект: none, flicker, pulse или blink.",
                "<red>Effect: none, flicker, pulse or blink.");
        put(Key.INVALID_TOGGLE,
                "<red>Значение должно быть on или off.",
                "<red>Value must be on or off.");
        put(Key.INVALID_POSITION,
                "<red>Не удалось разобрать позицию.",
                "<red>Could not parse the position.");
        put(Key.NEED_ENTITY,
                "<red>Эту команду должен выполнять игрок (или сущность).",
                "<red>This command must be run by a player (or entity).");
        put(Key.SPOT_ONLY,
                "<red>Эта настройка есть только у прожекторов (spot).",
                "<red>This setting exists only for spotlights (spot).");
        put(Key.LINE_ONLY,
                "<red>Вторая точка есть только у типов strip и area.",
                "<red>The second point exists only for strip and area types.");
        put(Key.WORLD_MISSING,
                "<red>Мир <world> не загружен.",
                "<red>World <world> is not loaded.");
        put(Key.OTHER_WORLD,
                "<red>Свет <name> находится в мире <world> — переносить между мирами нельзя, "
                        + "создайте его заново.",
                "<red>Light <name> is in world <world> — it cannot be moved between worlds, "
                        + "create it again.");

        put(Key.CREATED,
                "<green>Свет <name> (<type>) создан.",
                "<green>Light <name> (<type>) created.");
        put(Key.REMOVED,
                "<green>Свет <name> удалён.",
                "<green>Light <name> removed.");

        put(Key.LIST_HEADER,
                "<gold>Источников света: <count>",
                "<gold>Light sources: <count>");
        put(Key.LIST_EMPTY,
                "<gray>Источников света нет.",
                "<gray>There are no light sources.");
        put(Key.LIST_ENTRY,
                "<yellow><name> <gray>[<type>] <state> <gray>яркость <brightness>, <world> <x> <y> <z>",
                "<yellow><name> <gray>[<type>] <state> <gray>brightness <brightness>, <world> <x> <y> <z>");

        put(Key.INFO,
                "<gold>=== <name> ===\n"
                        + "<gray>Тип: <white><type> <gray>Состояние: <state>\n"
                        + "<gray>Мир: <white><world> <gray>Позиция: <white><x> <y> <z>\n"
                        + "<gray>Яркость: <white><brightness> <gray>Эффект: <white><effect> "
                        + "<gray>Группа: <white><group> <gray>Световых блоков: <white><blocks>",
                "<gold>=== <name> ===\n"
                        + "<gray>Type: <white><type> <gray>State: <state>\n"
                        + "<gray>World: <white><world> <gray>Position: <white><x> <y> <z>\n"
                        + "<gray>Brightness: <white><brightness> <gray>Effect: <white><effect> "
                        + "<gray>Group: <white><group> <gray>Light blocks: <white><blocks>");
        put(Key.INFO_SPOT,
                "<gray>Yaw/Pitch: <white><yaw> / <pitch> <gray>Угол: <white><angle> <gray>Дальность: <white><range> "
                        + "<gray>Лучей: <white><rays> <gray>Луч-частицы: <beam>",
                "<gray>Yaw/Pitch: <white><yaw> / <pitch> <gray>Angle: <white><angle> <gray>Range: <white><range> "
                        + "<gray>Rays: <white><rays> <gray>Particle beam: <beam>");
        put(Key.INFO_POS2,
                "<gray>Вторая точка: <white><x> <y> <z>",
                "<gray>Second point: <white><x> <y> <z>");

        put(Key.STATE_ON, "<green>вкл", "<green>on");
        put(Key.STATE_OFF, "<red>выкл", "<red>off");
        put(Key.STATE_CHANGED,
                "<green>Свет <name>: <state>",
                "<green>Light <name>: <state>");

        put(Key.BRIGHTNESS_SET,
                "<green>Яркость <name>: <value>.",
                "<green>Brightness of <name>: <value>.");
        put(Key.AIMED,
                "<green>Свет <name> направлен: yaw <yaw>, pitch <pitch>.",
                "<green>Light <name> aimed: yaw <yaw>, pitch <pitch>.");
        put(Key.ANGLE_SET,
                "<green>Угол конуса <name>: <value>°.",
                "<green>Cone angle of <name>: <value>°.");
        put(Key.RANGE_SET,
                "<green>Дальность <name>: <value>.",
                "<green>Range of <name>: <value>.");
        put(Key.RAYS_SET,
                "<green>Лучей у <name>: <value>.",
                "<green>Rays of <name>: <value>.");
        put(Key.BEAM_SET,
                "<green>Луч-частицы <name>: <state>",
                "<green>Particle beam of <name>: <state>");
        put(Key.EFFECT_SET,
                "<green>Эффект <name>: <value>.",
                "<green>Effect of <name>: <value>.");

        put(Key.MOVED,
                "<green>Свет <name> перемещён.",
                "<green>Light <name> moved.");
        put(Key.POS2_SET,
                "<green>Вторая точка <name> задана.",
                "<green>Second point of <name> set.");
        put(Key.TELEPORTED,
                "<green>Телепорт к <name>.",
                "<green>Teleported to <name>.");

        put(Key.GROUP_SET,
                "<green>Свет <name> добавлен в группу <group>.",
                "<green>Light <name> added to group <group>.");
        put(Key.GROUP_CLEARED,
                "<green>Свет <name> убран из группы.",
                "<green>Light <name> removed from its group.");
        put(Key.GROUP_APPLIED,
                "<green>Группа <group>: затронуто источников — <count>.",
                "<green>Group <group>: <count> sources affected.");
        put(Key.GROUP_EMPTY,
                "<red>В группе <group> нет источников.",
                "<red>Group <group> has no sources.");

        put(Key.RELOADED,
                "<green>Конфигурация LightSet перезагружена.",
                "<green>LightSet configuration reloaded.");
        verify();
    }

    private Lang() {
    }

    /**
     * Sets the language mode.
     *
     * @param value {@code ru}, {@code en} or {@code auto}; anything else is treated as {@code auto}
     */
    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Builds a message for a recipient.
     *
     * @param key       message key
     * @param sender    recipient, used to pick the language in {@code auto} mode
     * @param resolvers values for the placeholders of the message
     * @return the message
     */
    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
