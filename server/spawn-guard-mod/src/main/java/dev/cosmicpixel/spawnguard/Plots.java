package dev.cosmicpixel.spawnguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rentable plots inside spawn. A renter may place blocks in their plot and break only the blocks they placed, so
 * the spawn build itself can never be broken. Rentals are created by the web store through the op-only
 * "/plot admin rent" command; this class is the only writer of config/spawn-plots.json, which the store reads.
 */
public final class Plots {
	private static final Logger LOGGER = LoggerFactory.getLogger("spawn-guard");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spawn-plots.json");
	private static final Path STORE_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("cosmic-store.json");
	// Renters keep access to their own blocks for a day after the rental ends; then the blocks are removed.
	private static final long GRACE_SECONDS = 24 * 3600;

	static final class Plot {
		final int id;
		final String name;
		final int minX, minY, minZ, maxX, maxY, maxZ;
		UUID renter;
		String renterName;
		long until;
		BlockPos home;
		final Set<BlockPos> placed = new HashSet<>();

		Plot(int id, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.id = id;
			this.name = name;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		boolean contains(BlockPos pos) {
			return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
				&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
		}

		boolean contains(double x, double y, double z) {
			return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
		}

		boolean active(long now) {
			return renter != null && until > now;
		}

		boolean rentedBy(UUID player, long now) {
			return player.equals(renter) && until > now;
		}

		boolean inGrace(UUID player, long now) {
			return player.equals(renter) && until + GRACE_SECONDS > now;
		}
	}

	private static final List<Plot> PLOTS = new ArrayList<>();
	private static boolean dirty;
	private static int ticks;

	private Plots() {}

	private static long now() {
		return System.currentTimeMillis() / 1000;
	}

	// The eight diamond domes around the spawn hub, numbered clockwise from the north-west.
	private static List<Plot> defaults() {
		return List.of(
			new Plot(1, "North-West Dome", 179, 95, -95, 213, 150, -61),
			new Plot(2, "North-East Dome", 233, 95, -95, 267, 150, -61),
			new Plot(3, "East-North Dome", 319, 95, -9, 353, 150, 25),
			new Plot(4, "East-South Dome", 319, 95, 45, 353, 150, 79),
			new Plot(5, "South-East Dome", 233, 95, 131, 267, 150, 165),
			new Plot(6, "South-West Dome", 179, 95, 131, 213, 150, 165),
			new Plot(7, "West-South Dome", 93, 95, 45, 127, 150, 79),
			new Plot(8, "West-North Dome", 93, 95, -9, 127, 150, 25));
	}

	public static void register() {
		load();
		ServerTickEvents.END_SERVER_TICK.register(Plots::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> remind(handler.getPlayer()));
	}

	// ------------------------------------------------------------------ rules used by SpawnRules

	static Plot at(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) return null;
		for (Plot plot : PLOTS) {
			if (plot.contains(pos)) return plot;
		}
		return null;
	}

	static Plot at(Level level, double x, double y, double z) {
		if (level.dimension() != Level.OVERWORLD) return null;
		for (Plot plot : PLOTS) {
			if (plot.contains(x, y, z)) return plot;
		}
		return null;
	}

	static synchronized boolean mayBuild(ServerPlayer player, Level level, BlockPos pos) {
		Plot plot = at(level, pos);
		return plot != null && plot.rentedBy(player.getUUID(), now());
	}

	/** True when pos is in a plot the player still has access to (renting, or within the grace day). */
	static synchronized boolean ownsPlotAt(ServerPlayer player, Level level, BlockPos pos) {
		Plot plot = at(level, pos);
		return plot != null && plot.inGrace(player.getUUID(), now());
	}

	static synchronized boolean isPlacedBlock(Level level, BlockPos pos, BlockState state) {
		Plot plot = at(level, pos);
		if (plot == null) return false;
		if (plot.placed.contains(pos)) return true;
		BlockPos other = otherHalf(pos, state);
		return other != null && plot.placed.contains(other);
	}

	static synchronized boolean mayBreak(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
		Plot plot = at(level, pos);
		return plot != null && plot.inGrace(player.getUUID(), now()) && isPlacedBlock(level, pos, state);
	}

	static synchronized boolean standsInOwnPlot(ServerPlayer player) {
		Plot plot = at(player.level(), player.getX(), player.getY(), player.getZ());
		return plot != null && plot.rentedBy(player.getUUID(), now());
	}

	public static synchronized void recordPlaced(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
		Plot plot = at(level, pos);
		if (plot == null || !plot.rentedBy(player.getUUID(), now())) return;
		plot.placed.add(pos.immutable());
		BlockPos other = otherHalf(pos, state);
		if (other != null && plot.contains(other)) plot.placed.add(other.immutable());
		dirty = true;
	}

	static synchronized void recordBroken(Level level, BlockPos pos, BlockState state) {
		Plot plot = at(level, pos);
		if (plot == null) return;
		boolean changed = plot.placed.remove(pos);
		BlockPos other = otherHalf(pos, state);
		if (other != null) changed |= plot.placed.remove(other);
		dirty |= changed;
	}

	private static BlockPos otherHalf(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
		}
		return null;
	}

	// ------------------------------------------------------------------ expiry, reminders, persistence

	private static void tick(MinecraftServer server) {
		if (++ticks % 100 != 0) return;
		if (ticks % 1200 == 0) expire(server);
		if (dirty) save();
	}

	private static synchronized void expire(MinecraftServer server) {
		long now = now();
		ServerLevel level = server.overworld();
		for (Plot plot : PLOTS) {
			if (plot.renter == null || plot.until + GRACE_SECONDS > now) continue;
			Iterator<BlockPos> it = plot.placed.iterator();
			while (it.hasNext()) {
				BlockPos pos = it.next();
				if (!level.isLoaded(pos)) continue;
				level.destroyBlock(pos, true);
				it.remove();
			}
			if (plot.placed.isEmpty()) {
				LOGGER.info("Plot {} rental by {} ended; the plot is free again", plot.id, plot.renterName);
				plot.renter = null;
				plot.renterName = null;
				plot.until = 0;
				plot.home = null;
			}
			dirty = true;
		}
	}

	private static void remind(ServerPlayer player) {
		long now = now();
		Plot plot;
		synchronized (Plots.class) {
			plot = PLOTS.stream().filter(p -> p.inGrace(player.getUUID(), now)).findFirst().orElse(null);
		}
		if (plot == null) return;
		if (plot.until <= now) {
			player.sendSystemMessage(Component.literal("Your spawn plot rental has ended. Your blocks there will be removed in "
				+ duration(plot.until + GRACE_SECONDS - now) + " unless you renew it on the website.").withStyle(ChatFormatting.RED));
		} else if (plot.until - now < 24 * 3600) {
			player.sendSystemMessage(Component.literal("Your spawn plot rental ends in " + duration(plot.until - now)
				+ ". Renew it on the website to keep it.").withStyle(ChatFormatting.GOLD));
		}
	}

	private static synchronized void load() {
		PLOTS.clear();
		try {
			if (Files.exists(FILE)) {
				JsonArray list = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().getAsJsonArray("plots");
				for (JsonElement element : list) {
					JsonObject o = element.getAsJsonObject();
					JsonArray min = o.getAsJsonArray("min"), max = o.getAsJsonArray("max");
					Plot plot = new Plot(o.get("id").getAsInt(), o.get("name").getAsString(),
						min.get(0).getAsInt(), min.get(1).getAsInt(), min.get(2).getAsInt(),
						max.get(0).getAsInt(), max.get(1).getAsInt(), max.get(2).getAsInt());
					if (o.has("renter") && !o.get("renter").isJsonNull()) {
						plot.renter = UUID.fromString(o.get("renter").getAsString());
						plot.renterName = o.get("renterName").getAsString();
						plot.until = o.get("until").getAsLong();
					}
					if (o.has("home") && o.get("home").isJsonArray()) plot.home = pos(o.getAsJsonArray("home"));
					if (o.has("placed")) o.getAsJsonArray("placed").forEach(p -> plot.placed.add(pos(p.getAsJsonArray())));
					PLOTS.add(plot);
				}
			} else {
				PLOTS.addAll(defaults());
				dirty = true;
			}
			LOGGER.info("Loaded {} spawn plot(s)", PLOTS.size());
		} catch (Exception error) {
			// Refuse to run with half-read rentals; an empty list just means nobody can build in spawn.
			LOGGER.error("Could not read {}; plots are disabled until it is fixed", FILE, error);
			PLOTS.clear();
		}
		if (dirty) save();
	}

	private static BlockPos pos(JsonArray a) {
		return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	private static JsonArray json(BlockPos pos) {
		JsonArray a = new JsonArray();
		a.add(pos.getX());
		a.add(pos.getY());
		a.add(pos.getZ());
		return a;
	}

	static synchronized void save() {
		JsonArray list = new JsonArray();
		for (Plot plot : PLOTS) {
			JsonObject o = new JsonObject();
			o.addProperty("id", plot.id);
			o.addProperty("name", plot.name);
			o.add("min", json(new BlockPos(plot.minX, plot.minY, plot.minZ)));
			o.add("max", json(new BlockPos(plot.maxX, plot.maxY, plot.maxZ)));
			o.addProperty("renter", plot.renter == null ? null : plot.renter.toString());
			o.addProperty("renterName", plot.renterName);
			o.addProperty("until", plot.until);
			o.add("home", plot.home == null ? null : json(plot.home));
			JsonArray placed = new JsonArray();
			plot.placed.forEach(p -> placed.add(json(p)));
			o.add("placed", placed);
			list.add(o);
		}
		JsonObject root = new JsonObject();
		root.addProperty("graceSeconds", GRACE_SECONDS);
		root.add("plots", list);
		try {
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException error) {
			LOGGER.error("Could not save {}", FILE, error);
		}
	}

	// ------------------------------------------------------------------ commands

	static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("plot")
			.executes(Plots::info)
			.then(Commands.literal("home").executes(Plots::home))
			.then(Commands.literal("sethome").executes(Plots::setHome))
			.then(Commands.literal("admin")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("rent")
					.then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("uuid", StringArgumentType.word())
							.then(Commands.argument("name", StringArgumentType.word())
								.then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
									.executes(Plots::adminRent))))))
				.then(Commands.literal("evict")
					.then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(Plots::adminEvict)))
				.then(Commands.literal("clear")
					.then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(Plots::adminClear)))));
	}

	/** Ends a rental right now, without the grace day, and removes the renter's blocks. */
	private static int adminClear(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		int left;
		synchronized (Plots.class) {
			Plot plot = PLOTS.stream().filter(p -> p.id == id).findFirst().orElse(null);
			if (plot == null || plot.renter == null) {
				ctx.getSource().sendFailure(Component.literal("ERROR plot " + id + " is not rented"));
				return 0;
			}
			plot.until = Math.min(plot.until, now() - GRACE_SECONDS - 1);
			expire(ctx.getSource().getServer());
			left = plot.renter == null ? 0 : plot.placed.size();
			save();
		}
		ctx.getSource().sendSuccess(() -> Component.literal(left == 0 ? "OK cleared"
			: "OK " + left + " blocks are in unloaded chunks; they are removed when the plot frees up"), false);
		return 1;
	}

	private static String duration(long seconds) {
		long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
		return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
	}

	private static String storeUrl() {
		try {
			return JsonParser.parseString(Files.readString(STORE_CONFIG)).getAsJsonObject().get("url").getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		long now = now();
		MutableComponent text = Component.literal("Spawn plots (the 8 domes around spawn):").withStyle(ChatFormatting.GOLD);
		synchronized (Plots.class) {
			for (Plot plot : PLOTS) {
				text.append(Component.literal("\n #" + plot.id + " " + plot.name + ": ").withStyle(ChatFormatting.YELLOW));
				if (plot.active(now)) {
					boolean mine = player != null && plot.renter.equals(player.getUUID());
					text.append(Component.literal((mine ? "YOURS" : "rented by " + plot.renterName) + " for " + duration(plot.until - now))
						.withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.GRAY));
				} else if (plot.renter != null) {
					text.append(Component.literal("being cleared").withStyle(ChatFormatting.GRAY));
				} else {
					text.append(Component.literal("free").withStyle(ChatFormatting.GREEN));
				}
			}
		}
		String url = storeUrl();
		if (url != null) {
			text.append(Component.literal("\nRent or renew one at " + url + " (log in with /store)")
				.withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
					.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
		}
		text.append(Component.literal("\nIn your plot you can build and break your own blocks; /plot home takes you there.")
			.withStyle(ChatFormatting.GRAY));
		source.sendSuccess(() -> text, false);
		return 1;
	}

	private static Plot plotOf(ServerPlayer player) {
		long now = now();
		return PLOTS.stream().filter(p -> p.rentedBy(player.getUUID(), now)).findFirst().orElse(null);
	}

	private static int home(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return 0;
		BlockPos target;
		synchronized (Plots.class) {
			Plot plot = plotOf(player);
			if (plot == null) {
				ctx.getSource().sendFailure(Component.literal("You don't rent a spawn plot. See /plot."));
				return 0;
			}
			target = plot.home != null ? plot.home : safeSpot(ctx.getSource().getServer().overworld(), plot);
		}
		if (target == null) {
			ctx.getSource().sendFailure(Component.literal("Could not find a safe spot in your plot; stand in it and use /plot sethome."));
			return 0;
		}
		player.teleportTo(ctx.getSource().getServer().overworld(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
			Set.of(), player.getYRot(), player.getXRot(), true);
		return 1;
	}

	private static int setHome(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return 0;
		synchronized (Plots.class) {
			Plot plot = plotOf(player);
			if (plot == null || !plot.contains(player.getX(), player.getY(), player.getZ())) {
				ctx.getSource().sendFailure(Component.literal("Stand inside the plot you rent first."));
				return 0;
			}
			plot.home = player.blockPosition();
			dirty = true;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Plot home set.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** A spot with a solid floor and two free blocks above, as close to the middle of the plot as possible. */
	private static BlockPos safeSpot(ServerLevel level, Plot plot) {
		int cx = (plot.minX + plot.maxX) / 2, cz = (plot.minZ + plot.maxZ) / 2;
		for (int r = 0; r <= (plot.maxX - plot.minX) / 2; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int y = plot.maxY - 2; y > plot.minY; y--) {
						BlockPos feet = new BlockPos(cx + dx, y, cz + dz);
						if (level.getBlockState(feet.below()).blocksMotion() && level.getBlockState(feet).isAir()
							&& level.getBlockState(feet.above()).isAir() && hasRoof(level, feet, plot)) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	// Prefer spots inside the dome: something solid overhead within the plot.
	private static boolean hasRoof(ServerLevel level, BlockPos feet, Plot plot) {
		for (int y = feet.getY() + 2; y <= plot.maxY; y++) {
			if (level.getBlockState(new BlockPos(feet.getX(), y, feet.getZ())).blocksMotion()) return true;
		}
		return false;
	}

	private static int adminRent(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		int days = IntegerArgumentType.getInteger(ctx, "days");
		String name = StringArgumentType.getString(ctx, "name");
		UUID uuid;
		try {
			uuid = UUID.fromString(StringArgumentType.getString(ctx, "uuid"));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("ERROR bad uuid"));
			return 0;
		}
		long now = now();
		long until;
		synchronized (Plots.class) {
			Plot plot = PLOTS.stream().filter(p -> p.id == id).findFirst().orElse(null);
			if (plot == null) {
				ctx.getSource().sendFailure(Component.literal("ERROR no plot " + id));
				return 0;
			}
			Plot other = PLOTS.stream().filter(p -> p != plot && p.inGrace(uuid, now)).findFirst().orElse(null);
			if (other != null) {
				ctx.getSource().sendFailure(Component.literal("ERROR already renting plot " + other.id));
				return 0;
			}
			if (plot.renter != null && !plot.renter.equals(uuid)) {
				ctx.getSource().sendFailure(Component.literal("ERROR plot " + id + " is taken"));
				return 0;
			}
			plot.renter = uuid;
			plot.renterName = name;
			plot.until = Math.max(plot.until, now) + days * 86400L;
			until = plot.until;
			dirty = true;
			save();
		}
		ctx.getSource().sendSuccess(() -> Component.literal("OK " + until), false);
		return 1;
	}

	private static int adminEvict(CommandContext<CommandSourceStack> ctx) {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		synchronized (Plots.class) {
			Plot plot = PLOTS.stream().filter(p -> p.id == id).findFirst().orElse(null);
			if (plot == null || plot.renter == null) {
				ctx.getSource().sendFailure(Component.literal("ERROR plot " + id + " is not rented"));
				return 0;
			}
			plot.until = Math.min(plot.until, now());
			dirty = true;
			save();
		}
		ctx.getSource().sendSuccess(() -> Component.literal("OK evicted; blocks are removed after the grace day"), false);
		return 1;
	}
}
