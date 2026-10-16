package dev.cosmicpixel.spawnguard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Areas listed in config/spawn-guard-areas.json where only the listed owners and ops can break, place or use
 * blocks (doors, buttons, chests) or empty buckets. The file is re-read when it changes, so the AI bridge can add
 * an area while the server runs.
 *
 * {"areas": [{"name": "CosmicAI base", "dimension": "minecraft:overworld",
 *             "min": [x, z], "max": [x, z], "owners": ["CosmicAI"]}]}
 */
public final class ProtectedAreas {
	private static final Logger LOGGER = LoggerFactory.getLogger("spawn-guard");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spawn-guard-areas.json");
	// Buckets are aimed by the item, not at a clicked block, so they are blocked when the player is this close.
	private static final int BUCKET_RANGE = 6;

	private record Area(String name, String dimension, int minX, int minZ, int maxX, int maxZ, Set<String> owners) {
		boolean contains(String dim, double x, double z, int margin) {
			return dim.equals(dimension) && x >= minX - margin && x < maxX + 1 + margin
				&& z >= minZ - margin && z < maxZ + 1 + margin;
		}
	}

	private static List<Area> areas = List.of();
	private static long loadedModified = Long.MIN_VALUE;
	private static long lastCheck;

	private ProtectedAreas() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> allowed(player, level, pos));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			if (allowed(player, level, pos) && allowed(player, level, pos.relative(hit.getDirection()))) {
				return InteractionResult.PASS;
			}
			return InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player.getItemInHand(hand).getItem() instanceof BucketItem) || exempt(player)) {
				return InteractionResult.PASS;
			}
			Area area = find(level, player.getX(), player.getZ(), BUCKET_RANGE);
			if (area == null || area.owners().contains(player.getPlainTextName().toLowerCase(Locale.ROOT))) {
				return InteractionResult.PASS;
			}
			deny(player, area);
			return InteractionResult.FAIL;
		});
	}

	private static boolean allowed(Player player, Level level, BlockPos pos) {
		if (exempt(player)) {
			return true;
		}
		Area area = find(level, pos.getX(), pos.getZ(), 0);
		if (area == null || area.owners().contains(player.getPlainTextName().toLowerCase(Locale.ROOT))) {
			return true;
		}
		deny(player, area);
		return false;
	}

	private static boolean exempt(Player player) {
		return player.level().isClientSide() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static void deny(Player player, Area area) {
		player.sendOverlayMessage(Component.literal("This area is protected: " + area.name()).withStyle(ChatFormatting.RED));
	}

	private static Area find(Level level, double x, double z, int margin) {
		reloadIfChanged();
		String dim = level.dimension().identifier().toString();
		for (Area area : areas) {
			if (area.contains(dim, x, z, margin)) {
				return area;
			}
		}
		return null;
	}

	private static synchronized void reloadIfChanged() {
		long now = System.currentTimeMillis();
		if (now - lastCheck < 2000) {
			return;
		}
		lastCheck = now;
		try {
			long modified = Files.exists(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : -1;
			if (modified == loadedModified) {
				return;
			}
			loadedModified = modified;
			areas = modified < 0 ? List.of() : parse(Files.readString(FILE));
			LOGGER.info("Loaded {} protected area(s) from {}", areas.size(), FILE.getFileName());
		} catch (Exception error) {
			LOGGER.error("Could not read {}; keeping the previous areas", FILE, error);
		}
	}

	private static List<Area> parse(String text) {
		List<Area> result = new ArrayList<>();
		JsonArray list = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("areas");
		for (JsonElement element : list) {
			JsonObject o = element.getAsJsonObject();
			JsonArray min = o.getAsJsonArray("min"), max = o.getAsJsonArray("max");
			Set<String> owners = o.has("owners")
				? o.getAsJsonArray("owners").asList().stream()
					.map(owner -> owner.getAsString().toLowerCase(Locale.ROOT)).collect(Collectors.toSet())
				: Set.of();
			result.add(new Area(o.has("name") ? o.get("name").getAsString() : "protected area",
				o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
				Math.min(min.get(0).getAsInt(), max.get(0).getAsInt()), Math.min(min.get(1).getAsInt(), max.get(1).getAsInt()),
				Math.max(min.get(0).getAsInt(), max.get(0).getAsInt()), Math.max(min.get(1).getAsInt(), max.get(1).getAsInt()),
				owners));
		}
		return List.copyOf(result);
	}
}
