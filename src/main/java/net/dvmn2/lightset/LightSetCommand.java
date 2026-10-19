package net.dvmn2.lightset;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Команды плагина (право {@code lightset.admin}):
 * <pre>
 * /lightset create <имя> <spot|lamp|strip|area>
 * /lightset remove|info|tp <имя>        /lightset list
 * /lightset on|off|toggle <имя>
 * /lightset brightness <имя> <0-15>
 * /lightset aim <имя> <yaw> <pitch> | aimhere <имя> | lookat <имя> <x y z> | rotate <имя> <dYaw> <dPitch>
 * /lightset move <имя> [x y z]          /lightset pos2 <имя> [x y z]
 * /lightset angle|range|rays <имя> <значение>
 * /lightset beam <имя> <on|off>
 * /lightset effect <имя> <none|flicker|pulse|blink>
 * /lightset group set <имя> <группа> | clear <имя> | on|off|toggle <группа> | brightness <группа> <0-15>
 * /lightset reload
 * </pre>
 */
public final class LightSetCommand {

    private static final String PERMISSION = "lightset.admin";
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_\\-]{1,32}$");
    private static final String[] TYPES = {"spot", "lamp", "strip", "area"};
    private static final String[] EFFECTS = {"none", "flicker", "pulse", "blink"};
    private static final String[] TOGGLE = {"on", "off"};

    private final LightManager manager;

    public LightSetCommand(LightManager manager) {
        this.manager = manager;
    }

    public LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("lightset")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> {
                    send(ctx, Lang.Key.USAGE);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggest(b, TYPES))
                                        .executes(this::runCreate))))
                .then(simple("remove", this::runRemove))
                .then(simple("info", this::runInfo))
                .then(simple("tp", this::runTp))
                .then(Commands.literal("list").executes(this::runList))
                .then(simple("on", ctx -> runState(ctx, Boolean.TRUE)))
                .then(simple("off", ctx -> runState(ctx, Boolean.FALSE)))
                .then(simple("toggle", ctx -> runState(ctx, null)))
                .then(Commands.literal("brightness")
                        .then(nameArg()
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                        .executes(this::runBrightness))))
                .then(Commands.literal("aim")
                        .then(nameArg()
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                                                .executes(this::runAim)))))
                .then(simple("aimhere", this::runAimHere))
                .then(Commands.literal("lookat")
                        .then(nameArg()
                                .then(Commands.argument("pos", ArgumentTypes.finePosition(true))
                                        .executes(this::runLookAt))))
                .then(Commands.literal("rotate")
                        .then(nameArg()
                                .then(Commands.argument("dyaw", FloatArgumentType.floatArg())
                                        .then(Commands.argument("dpitch", FloatArgumentType.floatArg())
                                                .executes(this::runRotate)))))
                .then(Commands.literal("move")
                        .then(nameArg()
                                .executes(this::runMoveHere)
                                .then(Commands.argument("pos", ArgumentTypes.finePosition(true))
                                        .executes(this::runMove))))
                .then(Commands.literal("pos2")
                        .then(nameArg()
                                .executes(this::runPos2Here)
                                .then(Commands.argument("pos", ArgumentTypes.finePosition(true))
                                        .executes(this::runPos2))))
                .then(Commands.literal("angle")
                        .then(nameArg()
                                .then(Commands.argument("value", FloatArgumentType.floatArg(1f, 179f))
                                        .executes(this::runAngle))))
                .then(Commands.literal("range")
                        .then(nameArg()
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(this::runRange))))
                .then(Commands.literal("rays")
                        .then(nameArg()
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(this::runRays))))
                .then(Commands.literal("beam")
                        .then(nameArg()
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggest(b, TOGGLE))
                                        .executes(this::runBeam))))
                .then(Commands.literal("effect")
                        .then(nameArg()
                                .then(Commands.argument("effect", StringArgumentType.word())
                                        .suggests((ctx, b) -> suggest(b, EFFECTS))
                                        .executes(this::runEffect))))
                .then(Commands.literal("group")
                        .then(Commands.literal("set")
                                .then(nameArg()
                                        .then(groupArg().executes(this::runGroupSet))))
                        .then(Commands.literal("clear")
                                .then(nameArg().executes(this::runGroupClear)))
                        .then(Commands.literal("on")
                                .then(groupArg().executes(ctx -> runGroupState(ctx, Boolean.TRUE))))
                        .then(Commands.literal("off")
                                .then(groupArg().executes(ctx -> runGroupState(ctx, Boolean.FALSE))))
                        .then(Commands.literal("toggle")
                                .then(groupArg().executes(ctx -> runGroupState(ctx, null))))
                        .then(Commands.literal("brightness")
                                .then(groupArg()
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                                .executes(this::runGroupBrightness)))))
                .then(Commands.literal("reload").executes(this::runReload))
                .build();
    }

    // ------------------------------------------------------------------ построение узлов

    private LiteralArgumentBuilder<CommandSourceStack> simple(
            String literal, Command<CommandSourceStack> executor) {
        return Commands.literal(literal).then(nameArg().executes(executor));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> suggest(b, manager.names()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> groupArg() {
        return Commands.argument("group", StringArgumentType.word())
                .suggests((ctx, b) -> suggest(b, manager.groupNames()));
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, String... options) {
        return suggest(builder, List.of(options));
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<String> options) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    // ------------------------------------------------------------------ вспомогательное

    private static void send(CommandContext<CommandSourceStack> ctx, Lang.Key key, Object... args) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Lang.get(key, sender, args));
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private static String stateText(CommandContext<CommandSourceStack> ctx, boolean on) {
        return Lang.get(on ? Lang.Key.STATE_ON : Lang.Key.STATE_OFF, sender(ctx));
    }

    private LightSource find(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        LightSource s = manager.get(name);
        if (s == null) {
            send(ctx, Lang.Key.NOT_FOUND, name);
        }
        return s;
    }

    /**
     * Источник должен быть прожектором (spot).
     */
    private LightSource findSpot(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s != null && s.type() != LightType.SPOT) {
            send(ctx, Lang.Key.SPOT_ONLY);
            return null;
        }
        return s;
    }

    private static Boolean parseToggle(String value) {
        if ("on".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("off".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static FinePosition resolvePos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("pos", FinePositionResolver.class).resolve(ctx.getSource());
    }

    // ------------------------------------------------------------------ исполнители

    private int runCreate(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!NAME.matcher(name).matches()) {
            send(ctx, Lang.Key.INVALID_NAME);
            return 0;
        }
        if (manager.get(name) != null) {
            send(ctx, Lang.Key.NAME_EXISTS, name);
            return 0;
        }
        LightType type = LightType.fromKey(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            send(ctx, Lang.Key.INVALID_TYPE);
            return 0;
        }
        Location loc = ctx.getSource().getLocation();
        manager.create(name, type, loc);
        send(ctx, Lang.Key.CREATED, name, type.key());
        return Command.SINGLE_SUCCESS;
    }

    private int runRemove(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        manager.remove(s);
        send(ctx, Lang.Key.REMOVED, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        if (manager.all().isEmpty()) {
            send(ctx, Lang.Key.LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        send(ctx, Lang.Key.LIST_HEADER, manager.all().size());
        for (LightSource s : manager.all()) {
            send(ctx, Lang.Key.LIST_ENTRY, s.name, s.type().key(), stateText(ctx, s.enabled()),
                    s.brightness(), s.world(), (int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        send(ctx, Lang.Key.INFO, s.name, s.type().key(), stateText(ctx, s.enabled()),
                s.world(), s.x(), s.y(), s.z(),
                s.brightness(), s.effect().key(), s.group() == null ? "-" : s.group(), s.blockCount());
        if (s.type() == LightType.SPOT) {
            send(ctx, Lang.Key.INFO_SPOT, s.yaw(), s.pitch(), s.angle(), s.range(), s.rays(),
                    stateText(ctx, s.beam()));
        }
        if (s.hasPos2()) {
            send(ctx, Lang.Key.INFO_POS2, s.x2(), s.y2(), s.z2());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runTp(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        Entity executor = ctx.getSource().getExecutor();
        if (executor == null) {
            send(ctx, Lang.Key.NEED_ENTITY);
            return 0;
        }
        World world = Bukkit.getWorld(s.world());
        if (world == null) {
            send(ctx, Lang.Key.WORLD_MISSING, s.world());
            return 0;
        }
        executor.teleportAsync(new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
        send(ctx, Lang.Key.TELEPORTED, s.name);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * @param mode TRUE — включить, FALSE — выключить, null — переключить.
     */
    private int runState(CommandContext<CommandSourceStack> ctx, Boolean mode) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        boolean on = mode == null ? !s.enabled() : mode;
        manager.setEnabled(s, on);
        send(ctx, Lang.Key.STATE_CHANGED, s.name, stateText(ctx, on));
        return Command.SINGLE_SUCCESS;
    }

    private int runBrightness(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        manager.setBrightness(s, value);
        send(ctx, Lang.Key.BRIGHTNESS_SET, s.name, value);
        return Command.SINGLE_SUCCESS;
    }

    private int runAim(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        manager.setDirection(s, FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"));
        send(ctx, Lang.Key.AIMED, s.name, s.yaw(), s.pitch());
        return Command.SINGLE_SUCCESS;
    }

    private int runAimHere(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        if (!(ctx.getSource().getExecutor() instanceof LivingEntity living)) {
            send(ctx, Lang.Key.NEED_ENTITY);
            return 0;
        }
        Location eye = living.getEyeLocation();
        manager.setDirection(s, eye.getYaw(), eye.getPitch());
        send(ctx, Lang.Key.AIMED, s.name, s.yaw(), s.pitch());
        return Command.SINGLE_SUCCESS;
    }

    private int runLookAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        FinePosition p = resolvePos(ctx);
        manager.aimAt(s, p.x(), p.y(), p.z());
        send(ctx, Lang.Key.AIMED, s.name, s.yaw(), s.pitch());
        return Command.SINGLE_SUCCESS;
    }

    private int runRotate(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        manager.setDirection(s,
                s.yaw() + FloatArgumentType.getFloat(ctx, "dyaw"),
                s.pitch() + FloatArgumentType.getFloat(ctx, "dpitch"));
        send(ctx, Lang.Key.AIMED, s.name, s.yaw(), s.pitch());
        return Command.SINGLE_SUCCESS;
    }

    private int runMoveHere(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        Location loc = ctx.getSource().getLocation();
        if (!loc.getWorld().getName().equals(s.world())) {
            send(ctx, Lang.Key.OTHER_WORLD, s.name, s.world());
            return 0;
        }
        manager.move(s, Math.floor(loc.getX()) + 0.5, Math.floor(loc.getY()) + 0.5, Math.floor(loc.getZ()) + 0.5);
        send(ctx, Lang.Key.MOVED, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private int runMove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        FinePosition p = resolvePos(ctx);
        manager.move(s, p.x(), p.y(), p.z());
        send(ctx, Lang.Key.MOVED, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private LightSource findLine(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s != null && s.type() != LightType.STRIP && s.type() != LightType.AREA) {
            send(ctx, Lang.Key.LINE_ONLY);
            return null;
        }
        return s;
    }

    private int runPos2Here(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findLine(ctx);
        if (s == null) {
            return 0;
        }
        Location loc = ctx.getSource().getLocation();
        manager.setPos2(s, Math.floor(loc.getX()) + 0.5, Math.floor(loc.getY()) + 0.5, Math.floor(loc.getZ()) + 0.5);
        send(ctx, Lang.Key.POS2_SET, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private int runPos2(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LightSource s = findLine(ctx);
        if (s == null) {
            return 0;
        }
        FinePosition p = resolvePos(ctx);
        manager.setPos2(s, p.x(), p.y(), p.z());
        send(ctx, Lang.Key.POS2_SET, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private int runAngle(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        manager.setAngle(s, FloatArgumentType.getFloat(ctx, "value"));
        send(ctx, Lang.Key.ANGLE_SET, s.name, s.angle());
        return Command.SINGLE_SUCCESS;
    }

    private int runRange(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        manager.setRange(s, IntegerArgumentType.getInteger(ctx, "value"));
        send(ctx, Lang.Key.RANGE_SET, s.name, s.range());
        return Command.SINGLE_SUCCESS;
    }

    private int runRays(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        manager.setRays(s, IntegerArgumentType.getInteger(ctx, "value"));
        send(ctx, Lang.Key.RAYS_SET, s.name, s.rays());
        return Command.SINGLE_SUCCESS;
    }

    private int runBeam(CommandContext<CommandSourceStack> ctx) {
        LightSource s = findSpot(ctx);
        if (s == null) {
            return 0;
        }
        Boolean on = parseToggle(StringArgumentType.getString(ctx, "state"));
        if (on == null) {
            send(ctx, Lang.Key.INVALID_TOGGLE);
            return 0;
        }
        manager.setBeam(s, on);
        send(ctx, Lang.Key.BEAM_SET, s.name, stateText(ctx, on));
        return Command.SINGLE_SUCCESS;
    }

    private int runEffect(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        Effect effect = Effect.fromKey(StringArgumentType.getString(ctx, "effect"));
        if (effect == null) {
            send(ctx, Lang.Key.INVALID_EFFECT);
            return 0;
        }
        manager.setEffect(s, effect);
        send(ctx, Lang.Key.EFFECT_SET, s.name, effect.key());
        return Command.SINGLE_SUCCESS;
    }

    private int runGroupSet(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        String group = StringArgumentType.getString(ctx, "group");
        if (!NAME.matcher(group).matches()) {
            send(ctx, Lang.Key.INVALID_NAME);
            return 0;
        }
        manager.setGroup(s, group);
        send(ctx, Lang.Key.GROUP_SET, s.name, group);
        return Command.SINGLE_SUCCESS;
    }

    private int runGroupClear(CommandContext<CommandSourceStack> ctx) {
        LightSource s = find(ctx);
        if (s == null) {
            return 0;
        }
        manager.setGroup(s, null);
        send(ctx, Lang.Key.GROUP_CLEARED, s.name);
        return Command.SINGLE_SUCCESS;
    }

    private List<LightSource> findGroup(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        List<LightSource> members = manager.groupMembers(group);
        if (members.isEmpty()) {
            send(ctx, Lang.Key.GROUP_EMPTY, group);
        }
        return members;
    }

    /**
     * @param mode TRUE — включить, FALSE — выключить, null — переключить каждый источник.
     */
    private int runGroupState(CommandContext<CommandSourceStack> ctx, Boolean mode) {
        List<LightSource> members = findGroup(ctx);
        if (members.isEmpty()) {
            return 0;
        }
        for (LightSource s : members) {
            manager.setEnabled(s, mode == null ? !s.enabled() : mode);
        }
        send(ctx, Lang.Key.GROUP_APPLIED, StringArgumentType.getString(ctx, "group"), members.size());
        return Command.SINGLE_SUCCESS;
    }

    private int runGroupBrightness(CommandContext<CommandSourceStack> ctx) {
        List<LightSource> members = findGroup(ctx);
        if (members.isEmpty()) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        for (LightSource s : members) {
            manager.setBrightness(s, value);
        }
        send(ctx, Lang.Key.GROUP_APPLIED, StringArgumentType.getString(ctx, "group"), members.size());
        return Command.SINGLE_SUCCESS;
    }

    private int runReload(CommandContext<CommandSourceStack> ctx) {
        manager.reload();
        send(ctx, Lang.Key.RELOADED);
        return Command.SINGLE_SUCCESS;
    }
}
