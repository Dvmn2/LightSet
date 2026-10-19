package net.dvmn2.lightset;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * RU/EN локализация. Язык задаётся в config.yml (language): "ru" / "en" или
 * "auto" — по клиентской локали игрока (для не-игроков — английский).
 */
public final class Lang {

    public enum Key {
        USAGE,
        INVALID_NAME, NAME_EXISTS, NOT_FOUND, INVALID_TYPE, INVALID_EFFECT, INVALID_TOGGLE,
        NEED_ENTITY, SPOT_ONLY, LINE_ONLY, WORLD_MISSING, OTHER_WORLD,
        CREATED, REMOVED,
        LIST_HEADER, LIST_EMPTY, LIST_ENTRY,
        INFO, INFO_SPOT, INFO_POS2,
        STATE_ON, STATE_OFF, STATE_CHANGED,
        BRIGHTNESS_SET, AIMED, ANGLE_SET, RANGE_SET, RAYS_SET, BEAM_SET, EFFECT_SET,
        MOVED, POS2_SET, TELEPORTED,
        GROUP_SET, GROUP_CLEARED, GROUP_APPLIED, GROUP_EMPTY,
        RELOADED
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    static {
        put(Key.USAGE,
                "§6LightSet §7— команды:\n"
                        + "§e/lightset create <имя> <spot|lamp|strip|area>\n"
                        + "§e/lightset remove|info|tp <имя>, /lightset list\n"
                        + "§e/lightset on|off|toggle <имя>\n"
                        + "§e/lightset brightness <имя> <0-15>\n"
                        + "§e/lightset aim <имя> <yaw> <pitch>, aimhere <имя>, lookat <имя> <x y z>, rotate <имя> <dYaw> <dPitch>\n"
                        + "§e/lightset move <имя> [x y z], pos2 <имя> [x y z]\n"
                        + "§e/lightset angle|range|rays <имя> <значение>\n"
                        + "§e/lightset beam <имя> <on|off>\n"
                        + "§e/lightset effect <имя> <none|flicker|pulse|blink>\n"
                        + "§e/lightset group set <имя> <группа>, group clear <имя>\n"
                        + "§e/lightset group on|off|toggle <группа>, group brightness <группа> <0-15>\n"
                        + "§e/lightset reload",
                "§6LightSet §7— commands:\n"
                        + "§e/lightset create <name> <spot|lamp|strip|area>\n"
                        + "§e/lightset remove|info|tp <name>, /lightset list\n"
                        + "§e/lightset on|off|toggle <name>\n"
                        + "§e/lightset brightness <name> <0-15>\n"
                        + "§e/lightset aim <name> <yaw> <pitch>, aimhere <name>, lookat <name> <x y z>, rotate <name> <dYaw> <dPitch>\n"
                        + "§e/lightset move <name> [x y z], pos2 <name> [x y z]\n"
                        + "§e/lightset angle|range|rays <name> <value>\n"
                        + "§e/lightset beam <name> <on|off>\n"
                        + "§e/lightset effect <name> <none|flicker|pulse|blink>\n"
                        + "§e/lightset group set <name> <group>, group clear <name>\n"
                        + "§e/lightset group on|off|toggle <group>, group brightness <group> <0-15>\n"
                        + "§e/lightset reload");

        put(Key.INVALID_NAME,
                "§cИмя: латинские буквы, цифры, _ и - (до 32 символов).",
                "§cName: Latin letters, digits, _ and - (up to 32 characters).");
        put(Key.NAME_EXISTS, "§cСвет с именем %s уже существует.", "§cA light named %s already exists.");
        put(Key.NOT_FOUND, "§cСвет %s не найден.", "§cLight %s not found.");
        put(Key.INVALID_TYPE, "§cТип должен быть spot, lamp, strip или area.", "§cType must be spot, lamp, strip or area.");
        put(Key.INVALID_EFFECT, "§cЭффект: none, flicker, pulse или blink.", "§cEffect: none, flicker, pulse or blink.");
        put(Key.INVALID_TOGGLE, "§cЗначение должно быть on или off.", "§cValue must be on or off.");
        put(Key.NEED_ENTITY, "§cЭту команду должен выполнять игрок (или сущность).", "§cThis command must be run by a player (or entity).");
        put(Key.SPOT_ONLY, "§cЭта настройка есть только у прожекторов (spot).", "§cThis setting exists only for spotlights (spot).");
        put(Key.LINE_ONLY, "§cВторая точка есть только у типов strip и area.", "§cThe second point exists only for strip and area types.");
        put(Key.WORLD_MISSING, "§cМир %s не загружен.", "§cWorld %s is not loaded.");

        put(Key.OTHER_WORLD,
                "§cСвет %s находится в мире %s — переносить между мирами нельзя, создайте его заново.",
                "§cLight %s is in world %s — it cannot be moved between worlds, create it again.");

        put(Key.CREATED, "§aСвет %s (%s) создан.", "§aLight %s (%s) created.");
        put(Key.REMOVED, "§aСвет %s удалён.", "§aLight %s removed.");

        put(Key.LIST_HEADER, "§6Источников света: %d", "§6Light sources: %d");
        put(Key.LIST_EMPTY, "§7Источников света нет.", "§7There are no light sources.");
        put(Key.LIST_ENTRY, "§e%s §7[%s] %s §7яркость %d, %s %d %d %d", "§e%s §7[%s] %s §7brightness %d, %s %d %d %d");

        put(Key.INFO,
                "§6=== %s ===\n§7Тип: §f%s §7Состояние: %s\n§7Мир: §f%s §7Позиция: §f%.1f %.1f %.1f\n"
                        + "§7Яркость: §f%d §7Эффект: §f%s §7Группа: §f%s §7Световых блоков: §f%d",
                "§6=== %s ===\n§7Type: §f%s §7State: %s\n§7World: §f%s §7Position: §f%.1f %.1f %.1f\n"
                        + "§7Brightness: §f%d §7Effect: §f%s §7Group: §f%s §7Light blocks: §f%d");
        put(Key.INFO_SPOT,
                "§7Yaw/Pitch: §f%.1f / %.1f §7Угол: §f%.1f §7Дальность: §f%d §7Лучей: §f%d §7Луч-частицы: %s",
                "§7Yaw/Pitch: §f%.1f / %.1f §7Angle: §f%.1f §7Range: §f%d §7Rays: §f%d §7Particle beam: %s");
        put(Key.INFO_POS2, "§7Вторая точка: §f%.1f %.1f %.1f", "§7Second point: §f%.1f %.1f %.1f");

        put(Key.STATE_ON, "§aвкл", "§aon");
        put(Key.STATE_OFF, "§cвыкл", "§coff");
        put(Key.STATE_CHANGED, "§aСвет %s: %s§a.", "§aLight %s: %s§a.");

        put(Key.BRIGHTNESS_SET, "§aЯркость %s: %d.", "§aBrightness of %s: %d.");
        put(Key.AIMED, "§aСвет %s направлен: yaw %.1f, pitch %.1f.", "§aLight %s aimed: yaw %.1f, pitch %.1f.");
        put(Key.ANGLE_SET, "§aУгол конуса %s: %.1f°.", "§aCone angle of %s: %.1f°.");
        put(Key.RANGE_SET, "§aДальность %s: %d.", "§aRange of %s: %d.");
        put(Key.RAYS_SET, "§aЛучей у %s: %d.", "§aRays of %s: %d.");
        put(Key.BEAM_SET, "§aЛуч-частицы %s: %s§a.", "§aParticle beam of %s: %s§a.");
        put(Key.EFFECT_SET, "§aЭффект %s: %s.", "§aEffect of %s: %s.");

        put(Key.MOVED, "§aСвет %s перемещён.", "§aLight %s moved.");
        put(Key.POS2_SET, "§aВторая точка %s задана.", "§aSecond point of %s set.");
        put(Key.TELEPORTED, "§aТелепорт к %s.", "§aTeleported to %s.");

        put(Key.GROUP_SET, "§aСвет %s добавлен в группу %s.", "§aLight %s added to group %s.");
        put(Key.GROUP_CLEARED, "§aСвет %s убран из группы.", "§aLight %s removed from its group.");
        put(Key.GROUP_APPLIED, "§aГруппа %s: затронуто источников — %d.", "§aGroup %s: %d sources affected.");
        put(Key.GROUP_EMPTY, "§cВ группе %s нет источников.", "§cGroup %s has no sources.");

        put(Key.RELOADED, "§aКонфигурация LightSet перезагружена.", "§aLightSet configuration reloaded.");
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        configuredLanguage = (language == null || language.isBlank())
                ? "auto"
                : language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String template = table.getOrDefault(key, RU.get(key));
        return args.length == 0 ? template : String.format(Locale.US, template, args);
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> autoResolve(sender);
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            return "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        }
        return EN;
    }
}
