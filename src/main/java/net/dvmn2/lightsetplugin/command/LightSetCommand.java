package net.dvmn2.lightsetplugin.command;

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
import net.dvmn2.lightsetplugin.Lang;
import net.dvmn2.lightsetplugin.LightManager;
import net.dvmn2.lightsetplugin.LightSetPlugin;
import net.dvmn2.lightsetplugin.Permissions;
import net.dvmn2.lightsetplugin.model.CreateResult;
import net.dvmn2.lightsetplugin.model.Effect;
import net.dvmn2.lightsetplugin.model.GroupResult;
import net.dvmn2.lightsetplugin.model.LightSource;
import net.dvmn2.lightsetplugin.model.LightType;
import net.dvmn2.lightsetplugin.model.MoveResult;
import net.dvmn2.lightsetplugin.model.Vec3;
import net.dvmn2.lightsetplugin.util.Format;
import net.dvmn2.lightsetplugin.util.Positions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code /lightset} command tree.
 *
 * <pre>
 * /lightset create &lt;name&gt; &lt;spot|lamp|strip|area&gt;                  lightset.edit
 * /lightset remove &lt;name&gt;                                              lightset.edit
 * /lightset info|tp &lt;name&gt;, /lightset list                             lightset.view
 * /lightset on|off|toggle &lt;name&gt;                                       lightset.control
 * /lightset brightness &lt;name&gt; &lt;0-15&gt;                                   lightset.control
 * /lightset effect &lt;name&gt; &lt;none|flicker|pulse|blink&gt;                  lightset.control
 * /lightset aim &lt;name&gt; &lt;yaw&gt; &lt;pitch&gt;                                  lightset.edit
 * /lightset aimhere &lt;name&gt;                                              lightset.edit
 * /lightset lookat &lt;name&gt; &lt;x y z&gt;                                      lightset.edit
 * /lightset rotate &lt;name&gt; &lt;dYaw&gt; &lt;dPitch&gt;                             lightset.edit
 * /lightset move|pos2 &lt;name&gt; [x y z]                                    lightset.edit
 * /lightset angle|range|rays &lt;name&gt; &lt;value&gt;                            lightset.edit
 * /lightset beam &lt;name&gt; &lt;on|off&gt;                                       lightset.edit
 * /lightset group set &lt;name&gt; &lt;group&gt;, group clear &lt;name&gt;               lightset.edit
 * /lightset group on|off|toggle &lt;group&gt;                                 lightset.control
 * /lightset group brightness &lt;group&gt; &lt;0-15&gt;                             lightset.control
 * /lightset reload                                                    lightset.reload
 * </pre>
 *
 * <p>Runs on the main thread.
 */
public final class LightSetCommand {

    private static final String ARG_NAME = "name";
    private static final String ARG_TYPE = "type";
    private static final String ARG_VALUE = "value";
    private static final String ARG_YAW = "yaw";
    private static final String ARG_PITCH = "pitch";
    private static final String ARG_DELTA_YAW = "dyaw";
    private static final String ARG_DELTA_PITCH = "dpitch";
    private static final String ARG_POS = "pos";
    private static final String ARG_STATE = "state";
    private static final String ARG_EFFECT = "effect";
    private static final String ARG_GROUP = "group";

    private static final int MAX_BRIGHTNESS = 15;
    private static final float MAX_PITCH_DEGREES = 90f;
    private static final float MIN_ANGLE_DEGREES = 1f;
    private static final float MAX_ANGLE_DEGREES = 179f;
    private static final List<String> TYPE_KEYS = Arrays.stream(LightType.values()).map(LightType::getKey).toList();
    private static final List<String> EFFECT_KEYS = Arrays.stream(Effect.values()).map(Effect::getKey).toList();
    private static final List<String> TOGGLE_KEYS = List.of("on", "off");

    /** How an on/off command changes the state. */
    private enum Mode { ON, OFF, TOGGLE }

    private final LightSetPlugin plugin;
    private final LightManager manager;

    /**
     * Creates the command.
     *
     * @param plugin  plugin, used to reload the configuration
     * @param manager light source manager
     */
    public LightSetCommand(LightSetPlugin plugin, LightManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Builds the command tree. */
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("lightset")
                .requires(LightSetCommand::hasAnyPermission)
                .executes(ctx -> {
                    send(ctx, Lang.Key.USAGE);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("create", Permissions.EDIT)
                        .then(Commands.argument(ARG_NAME, StringArgumentType.word())
                                .then(Commands.argument(ARG_TYPE, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, TYPE_KEYS))
                                        .executes(this::runCreate))))
                .then(named("remove", Permissions.EDIT, this::runRemove))
                .then(named("info", Permissions.VIEW, this::runInfo))
                .then(named("tp", Permissions.VIEW, this::runTeleport))
                .then(literal("list", Permissions.VIEW).executes(this::runList))
                .then(named("on", Permissions.CONTROL, ctx -> runState(ctx, Mode.ON)))
                .then(named("off", Permissions.CONTROL, ctx -> runState(ctx, Mode.OFF)))
                .then(named("toggle", Permissions.CONTROL, ctx -> runState(ctx, Mode.TOGGLE)))
                .then(literal("brightness", Permissions.CONTROL)
                        .then(nameArg()
                                .then(Commands.argument(ARG_VALUE, IntegerArgumentType.integer(0, MAX_BRIGHTNESS))
                                        .executes(this::runBrightness))))
                .then(literal("effect", Permissions.CONTROL)
                        .then(nameArg()
                                .then(Commands.argument(ARG_EFFECT, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, EFFECT_KEYS))
                                        .executes(this::runEffect))))
                .then(aimNode())
                .then(named("aimhere", Permissions.EDIT, this::runAimHere))
                .then(lookAtNode())
                .then(rotateNode())
                .then(positionNode("move", this::runMoveHere, this::runMove))
                .then(positionNode("pos2", this::runSecondPointHere, this::runSecondPoint))
                .then(literal("angle", Permissions.EDIT)
                        .then(nameArg()
                                .then(Commands.argument(ARG_VALUE,
                                                FloatArgumentType.floatArg(MIN_ANGLE_DEGREES, MAX_ANGLE_DEGREES))
                                        .executes(this::runAngle))))
                .then(literal("range", Permissions.EDIT)
                        .then(nameArg()
                                .then(Commands.argument(ARG_VALUE, IntegerArgumentType.integer(1))
                                        .executes(this::runRange))))
                .then(literal("rays", Permissions.EDIT)
                        .then(nameArg()
                                .then(Commands.argument(ARG_VALUE, IntegerArgumentType.integer(1))
                                        .executes(this::runRays))))
                .then(literal("beam", Permissions.EDIT)
                        .then(nameArg()
                                .then(Commands.argument(ARG_STATE, StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggest(builder, TOGGLE_KEYS))
                                        .executes(this::runBeam))))
                .then(groupNode())
                .then(literal("reload", Permissions.RELOAD).executes(this::runReload))
                .build();
    }

    // ---------- Node builders ----------

    private LiteralArgumentBuilder<CommandSourceStack> aimNode() {
        return literal("aim", Permissions.EDIT)
                .then(nameArg()
                        .then(Commands.argument(ARG_YAW, FloatArgumentType.floatArg())
                                .then(Commands.argument(ARG_PITCH,
                                                FloatArgumentType.floatArg(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES))
                                        .executes(this::runAim))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> lookAtNode() {
        return literal("lookat", Permissions.EDIT)
                .then(nameArg()
                        .then(Commands.argument(ARG_POS, ArgumentTypes.finePosition(true))
                                .executes(this::runLookAt)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> rotateNode() {
        return literal("rotate", Permissions.EDIT)
                .then(nameArg()
                        .then(Commands.argument(ARG_DELTA_YAW, FloatArgumentType.floatArg())
                                .then(Commands.argument(ARG_DELTA_PITCH, FloatArgumentType.floatArg())
                                        .executes(this::runRotate))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> positionNode(
            String name, Command<CommandSourceStack> here, Command<CommandSourceStack> at) {
        return literal(name, Permissions.EDIT)
                .then(nameArg()
                        .executes(here)
                        .then(Commands.argument(ARG_POS, ArgumentTypes.finePosition(true))
                                .executes(at)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> groupNode() {
        return Commands.literal("group")
                .then(literal("set", Permissions.EDIT)
                        .then(nameArg()
                                .then(groupArg().executes(this::runGroupSet))))
                .then(literal("clear", Permissions.EDIT)
                        .then(nameArg().executes(this::runGroupClear)))
                .then(literal("on", Permissions.CONTROL)
                        .then(groupArg().executes(ctx -> runGroupState(ctx, Mode.ON))))
                .then(literal("off", Permissions.CONTROL)
                        .then(groupArg().executes(ctx -> runGroupState(ctx, Mode.OFF))))
                .then(literal("toggle", Permissions.CONTROL)
                        .then(groupArg().executes(ctx -> runGroupState(ctx, Mode.TOGGLE))))
                .then(literal("brightness", Permissions.CONTROL)
                        .then(groupArg()
                                .then(Commands.argument(ARG_VALUE, IntegerArgumentType.integer(0, MAX_BRIGHTNESS))
                                        .executes(this::runGroupBrightness))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name, String permission) {
        return Commands.literal(name).requires(src -> src.getSender().hasPermission(permission));
    }

    private LiteralArgumentBuilder<CommandSourceStack> named(
            String name, String permission, Command<CommandSourceStack> executor) {
        return literal(name, permission).then(nameArg().executes(executor));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument(ARG_NAME, StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, manager.getNames()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> groupArg() {
        return Commands.argument(ARG_GROUP, StringArgumentType.word())
                .suggests((ctx, builder) -> suggest(builder, manager.getGroupNames()));
    }

    private static boolean hasAnyPermission(CommandSourceStack src) {
        CommandSender sender = src.getSender();
        return sender.hasPermission(Permissions.VIEW) || sender.hasPermission(Permissions.CONTROL)
                || sender.hasPermission(Permissions.EDIT) || sender.hasPermission(Permissions.RELOAD);
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

    // ---------- Messages and lookups ----------

    private static void send(CommandContext<CommandSourceStack> ctx, Lang.Key key, TagResolver... resolvers) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Lang.get(key, sender, resolvers));
    }

    private static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    private static TagResolver number(String name, double value) {
        return Placeholder.unparsed(name, Format.decimal(value));
    }

    private static TagResolver integer(String name, long value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    private static TagResolver state(CommandContext<CommandSourceStack> ctx, String name, boolean on) {
        CommandSender sender = ctx.getSource().getSender();
        return Placeholder.component(name, Lang.get(on ? Lang.Key.STATE_ON : Lang.Key.STATE_OFF, sender));
    }

    private @Nullable LightSource find(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, ARG_NAME);
        LightSource source = manager.get(name);
        if (source == null) {
            send(ctx, Lang.Key.NOT_FOUND, text("name", name));
        }
        return source;
    }

    private @Nullable LightSource findSpot(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source != null && source.getType() != LightType.SPOT) {
            send(ctx, Lang.Key.SPOT_ONLY);
            return null;
        }
        return source;
    }

    private @Nullable LightSource findLine(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source != null && source.getType() != LightType.STRIP && source.getType() != LightType.AREA) {
            send(ctx, Lang.Key.LINE_ONLY);
            return null;
        }
        return source;
    }

    private @Nullable FinePosition resolvePosition(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument(ARG_POS, FinePositionResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            send(ctx, Lang.Key.INVALID_POSITION);
            return null;
        }
    }

    private static boolean resolveMode(Mode mode, boolean current) {
        return switch (mode) {
            case ON -> true;
            case OFF -> false;
            case TOGGLE -> !current;
        };
    }

    // ---------- Create, remove, inspect ----------

    private int runCreate(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, ARG_NAME);
        LightType type = LightType.fromKey(StringArgumentType.getString(ctx, ARG_TYPE));
        if (type == null) {
            send(ctx, Lang.Key.INVALID_TYPE);
            return 0;
        }
        CreateResult result = manager.create(name, type, ctx.getSource().getLocation());
        switch (result) {
            case SUCCESS -> send(ctx, Lang.Key.CREATED, text("name", name), text("type", type.getKey()));
            case INVALID_NAME -> send(ctx, Lang.Key.INVALID_NAME);
            case NAME_EXISTS -> send(ctx, Lang.Key.NAME_EXISTS, text("name", name));
        }
        return result == CreateResult.SUCCESS ? Command.SINGLE_SUCCESS : 0;
    }

    private int runRemove(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        manager.remove(source);
        send(ctx, Lang.Key.REMOVED, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        if (manager.getAll().isEmpty()) {
            send(ctx, Lang.Key.LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        send(ctx, Lang.Key.LIST_HEADER, integer("count", manager.getAll().size()));
        for (LightSource source : manager.getAll()) {
            Vec3 pos = source.getPosition();
            send(ctx, Lang.Key.LIST_ENTRY,
                    text("name", source.getName()),
                    text("type", source.getType().getKey()),
                    state(ctx, "state", source.isEnabled()),
                    integer("brightness", source.getBrightness()),
                    text("world", source.getWorld()),
                    integer("x", Positions.floor(pos.x())),
                    integer("y", Positions.floor(pos.y())),
                    integer("z", Positions.floor(pos.z())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        Vec3 pos = source.getPosition();
        String group = source.getGroup();
        send(ctx, Lang.Key.INFO,
                text("name", source.getName()),
                text("type", source.getType().getKey()),
                state(ctx, "state", source.isEnabled()),
                text("world", source.getWorld()),
                number("x", pos.x()),
                number("y", pos.y()),
                number("z", pos.z()),
                integer("brightness", source.getBrightness()),
                text("effect", source.getEffect().getKey()),
                text("group", group == null ? "-" : group),
                integer("blocks", source.getBlockCount()));
        if (source.getType() == LightType.SPOT) {
            send(ctx, Lang.Key.INFO_SPOT,
                    number("yaw", source.getYaw()),
                    number("pitch", source.getPitch()),
                    number("angle", source.getAngleDegrees()),
                    integer("range", source.getRangeBlocks()),
                    integer("rays", source.getRays()),
                    state(ctx, "beam", source.isBeamEnabled()));
        }
        Vec3 second = source.getSecondPoint();
        if (second != null) {
            send(ctx, Lang.Key.INFO_POS2, number("x", second.x()), number("y", second.y()), number("z", second.z()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runTeleport(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        Entity executor = ctx.getSource().getExecutor();
        if (executor == null) {
            send(ctx, Lang.Key.NEED_ENTITY);
            return 0;
        }
        World world = Bukkit.getWorld(source.getWorld());
        if (world == null) {
            send(ctx, Lang.Key.WORLD_MISSING, text("world", source.getWorld()));
            return 0;
        }
        Vec3 pos = source.getPosition();
        executor.teleportAsync(new Location(world, pos.x(), pos.y(), pos.z(), source.getYaw(), source.getPitch()));
        send(ctx, Lang.Key.TELEPORTED, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Switching and brightness ----------

    private int runState(CommandContext<CommandSourceStack> ctx, Mode mode) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        boolean on = resolveMode(mode, source.isEnabled());
        manager.setEnabled(source, on);
        send(ctx, Lang.Key.STATE_CHANGED, text("name", source.getName()), state(ctx, "state", on));
        return Command.SINGLE_SUCCESS;
    }

    private int runBrightness(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, ARG_VALUE);
        manager.setBrightness(source, value);
        send(ctx, Lang.Key.BRIGHTNESS_SET, text("name", source.getName()), integer("value", value));
        return Command.SINGLE_SUCCESS;
    }

    private int runEffect(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        Effect effect = Effect.fromKey(StringArgumentType.getString(ctx, ARG_EFFECT));
        if (effect == null) {
            send(ctx, Lang.Key.INVALID_EFFECT);
            return 0;
        }
        manager.setEffect(source, effect);
        send(ctx, Lang.Key.EFFECT_SET, text("name", source.getName()), text("value", effect.getKey()));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Aiming ----------

    private int runAim(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        manager.setDirection(source,
                FloatArgumentType.getFloat(ctx, ARG_YAW), FloatArgumentType.getFloat(ctx, ARG_PITCH));
        sendAimed(ctx, source);
        return Command.SINGLE_SUCCESS;
    }

    private int runAimHere(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        if (!(ctx.getSource().getExecutor() instanceof LivingEntity living)) {
            send(ctx, Lang.Key.NEED_ENTITY);
            return 0;
        }
        Location eye = living.getEyeLocation();
        manager.setDirection(source, eye.getYaw(), eye.getPitch());
        sendAimed(ctx, source);
        return Command.SINGLE_SUCCESS;
    }

    private int runLookAt(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        FinePosition target = resolvePosition(ctx);
        if (target == null) {
            return 0;
        }
        manager.aimAt(source, target.x(), target.y(), target.z());
        sendAimed(ctx, source);
        return Command.SINGLE_SUCCESS;
    }

    private int runRotate(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        manager.setDirection(source,
                source.getYaw() + FloatArgumentType.getFloat(ctx, ARG_DELTA_YAW),
                source.getPitch() + FloatArgumentType.getFloat(ctx, ARG_DELTA_PITCH));
        sendAimed(ctx, source);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendAimed(CommandContext<CommandSourceStack> ctx, LightSource source) {
        send(ctx, Lang.Key.AIMED, text("name", source.getName()),
                number("yaw", source.getYaw()), number("pitch", source.getPitch()));
    }

    // ---------- Position ----------

    private int runMoveHere(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        MoveResult result = manager.moveTo(source, ctx.getSource().getLocation());
        switch (result) {
            case SUCCESS -> send(ctx, Lang.Key.MOVED, text("name", source.getName()));
            case OTHER_WORLD -> send(ctx, Lang.Key.OTHER_WORLD,
                    text("name", source.getName()), text("world", source.getWorld()));
        }
        return result == MoveResult.SUCCESS ? Command.SINGLE_SUCCESS : 0;
    }

    private int runMove(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        FinePosition target = resolvePosition(ctx);
        if (target == null) {
            return 0;
        }
        manager.move(source, target.x(), target.y(), target.z());
        send(ctx, Lang.Key.MOVED, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int runSecondPointHere(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findLine(ctx);
        if (source == null) {
            return 0;
        }
        manager.setSecondPointTo(source, ctx.getSource().getLocation());
        send(ctx, Lang.Key.POS2_SET, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int runSecondPoint(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findLine(ctx);
        if (source == null) {
            return 0;
        }
        FinePosition target = resolvePosition(ctx);
        if (target == null) {
            return 0;
        }
        manager.setSecondPoint(source, target.x(), target.y(), target.z());
        send(ctx, Lang.Key.POS2_SET, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Spotlight settings ----------

    private int runAngle(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        manager.setAngle(source, FloatArgumentType.getFloat(ctx, ARG_VALUE));
        send(ctx, Lang.Key.ANGLE_SET, text("name", source.getName()), number("value", source.getAngleDegrees()));
        return Command.SINGLE_SUCCESS;
    }

    private int runRange(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        manager.setRange(source, IntegerArgumentType.getInteger(ctx, ARG_VALUE));
        send(ctx, Lang.Key.RANGE_SET, text("name", source.getName()), integer("value", source.getRangeBlocks()));
        return Command.SINGLE_SUCCESS;
    }

    private int runRays(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        manager.setRays(source, IntegerArgumentType.getInteger(ctx, ARG_VALUE));
        send(ctx, Lang.Key.RAYS_SET, text("name", source.getName()), integer("value", source.getRays()));
        return Command.SINGLE_SUCCESS;
    }

    private int runBeam(CommandContext<CommandSourceStack> ctx) {
        LightSource source = findSpot(ctx);
        if (source == null) {
            return 0;
        }
        String value = StringArgumentType.getString(ctx, ARG_STATE);
        boolean on;
        if ("on".equalsIgnoreCase(value)) {
            on = true;
        } else if ("off".equalsIgnoreCase(value)) {
            on = false;
        } else {
            send(ctx, Lang.Key.INVALID_TOGGLE);
            return 0;
        }
        manager.setBeam(source, on);
        send(ctx, Lang.Key.BEAM_SET, text("name", source.getName()), state(ctx, "state", on));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Groups ----------

    private int runGroupSet(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        String group = StringArgumentType.getString(ctx, ARG_GROUP);
        GroupResult result = manager.setGroup(source, group);
        switch (result) {
            case SUCCESS -> send(ctx, Lang.Key.GROUP_SET, text("name", source.getName()), text("group", group));
            case INVALID_NAME -> send(ctx, Lang.Key.INVALID_NAME);
        }
        return result == GroupResult.SUCCESS ? Command.SINGLE_SUCCESS : 0;
    }

    private int runGroupClear(CommandContext<CommandSourceStack> ctx) {
        LightSource source = find(ctx);
        if (source == null) {
            return 0;
        }
        manager.setGroup(source, null);
        send(ctx, Lang.Key.GROUP_CLEARED, text("name", source.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int runGroupState(CommandContext<CommandSourceStack> ctx, Mode mode) {
        List<LightSource> members = findGroup(ctx);
        for (LightSource source : members) {
            manager.setEnabled(source, resolveMode(mode, source.isEnabled()));
        }
        sendGroupApplied(ctx, members.size());
        return members.size();
    }

    private int runGroupBrightness(CommandContext<CommandSourceStack> ctx) {
        List<LightSource> members = findGroup(ctx);
        int value = IntegerArgumentType.getInteger(ctx, ARG_VALUE);
        for (LightSource source : members) {
            manager.setBrightness(source, value);
        }
        sendGroupApplied(ctx, members.size());
        return members.size();
    }

    /** Returns the members of the group argument; reports an empty group to the sender. */
    private List<LightSource> findGroup(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, ARG_GROUP);
        List<LightSource> members = manager.getGroupMembers(group);
        if (members.isEmpty()) {
            send(ctx, Lang.Key.GROUP_EMPTY, text("group", group));
        }
        return members;
    }

    private static void sendGroupApplied(CommandContext<CommandSourceStack> ctx, int count) {
        if (count == 0) {
            return;
        }
        send(ctx, Lang.Key.GROUP_APPLIED,
                text("group", StringArgumentType.getString(ctx, ARG_GROUP)), integer("count", count));
    }

    // ---------- Reload ----------

    private int runReload(CommandContext<CommandSourceStack> ctx) {
        plugin.reloadAll();
        send(ctx, Lang.Key.RELOADED);
        return Command.SINGLE_SUCCESS;
    }
}
