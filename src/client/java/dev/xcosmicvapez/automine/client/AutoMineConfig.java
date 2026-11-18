package dev.xcosmicvapez.automine.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JSON config at {@code config/automine.json} (rewritten on first launch / missing fields use defaults).
 */
public final class AutoMineConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("automine.json");

	// --- AutoReply ---
	public boolean autoReplyEnabledByDefault = true;
	public int replyDelayTicksMin = 40;
	public int replyDelayTicksMax = 120;
	public int replyCooldownTicks = 300;
	public boolean whispersOnly = true;
	public List<String> replyMessages = defaultReplies();
	/** Exact names (case-insensitive) never get auto-replies. */
	public List<String> ignorePlayers = defaultIgnorePlayers();
	/** If the sender name contains any of these, skip (staff tags, etc.). */
	public List<String> ignoreNameContains = defaultIgnoreNameContains();

	// --- Human feel ---
	public double pauseChancePerSecond = 0.035;
	public int pauseTicksMin = 10;
	public int pauseTicksMax = 40;
	public int afterBreakDelayMin = 1;
	public int afterBreakDelayMax = 4;
	public float lookJitterDegrees = 0.25f;
	/** How fast the camera turns toward the next circle target (deg/tick). */
	public float lookSpeedDegrees = 18f;

	// --- Circle mining (good for high-efficiency picks) ---
	public boolean circleMine = true;
	/** How far around you to hunt blocks (blocks). Vanilla reach ~4.5. */
	public double circleRadius = 4.2;
	public float circlePitchMin = -55f;
	public float circlePitchMax = 55f;
	/** Angular step when sweeping the ring. Smaller = denser. */
	public float circleStepDegrees = 12f;
	/** After clearing nearby blocks, tap forward this many ticks. */
	public int forwardAssistTicks = 6;

	// --- Block filters (empty whitelist = anything not blacklisted) ---
	public List<String> blockWhitelist = defaultWhitelist();
	public List<String> blockBlacklist = defaultBlacklist();

	// --- Inventory / prison economy ---
	public boolean pauseWhenInventoryFull = true;
	public boolean sellWhenFull = true;
	/** e.g. "sell all" or "sell" — sent as a slash command. Empty disables. */
	public String sellCommand = "sell all";
	public int sellCooldownTicks = 100;
	public String depositCommand = "";
	public int depositCooldownTicks = 100;

	// --- Tool guard ---
	public boolean pauseOnLowDurability = true;
	/** Stop mining when remaining durability is at or below this. */
	public int minToolDurability = 15;

	// --- Session limits (0 = unlimited) ---
	public int maxSessionTicks = 12000;
	public int maxBlocksPerSession = 4000;

	private static List<String> defaultReplies() {
		return new ArrayList<>(Arrays.asList(
				"hey sorry grinding the mine rn",
				"yo one sec mining for rank",
				"hey what's good, afk mine for a bit",
				"gimme a min in the pits",
				"sup, selling soon brb",
				"hey! busy mining will msg back"
		));
	}

	private static List<String> defaultIgnorePlayers() {
		return new ArrayList<>();
	}

	private static List<String> defaultIgnoreNameContains() {
		return new ArrayList<>(Arrays.asList("admin", "mod", "staff", "owner", "helper", "manager"));
	}

	/** Common prison mine blocks — edit for your server's mine. Empty list = all non-blacklisted. */
	private static List<String> defaultWhitelist() {
		return new ArrayList<>(Arrays.asList(
				"minecraft:stone",
				"minecraft:cobblestone",
				"minecraft:cobbled_deepslate",
				"minecraft:deepslate",
				"minecraft:andesite",
				"minecraft:diorite",
				"minecraft:granite",
				"minecraft:netherrack",
				"minecraft:coal_ore",
				"minecraft:deepslate_coal_ore",
				"minecraft:iron_ore",
				"minecraft:deepslate_iron_ore",
				"minecraft:copper_ore",
				"minecraft:deepslate_copper_ore",
				"minecraft:gold_ore",
				"minecraft:deepslate_gold_ore",
				"minecraft:redstone_ore",
				"minecraft:deepslate_redstone_ore",
				"minecraft:lapis_ore",
				"minecraft:deepslate_lapis_ore",
				"minecraft:diamond_ore",
				"minecraft:deepslate_diamond_ore",
				"minecraft:emerald_ore",
				"minecraft:deepslate_emerald_ore",
				"minecraft:nether_quartz_ore",
				"minecraft:nether_gold_ore",
				"minecraft:ancient_debris"
		));
	}

	private static List<String> defaultBlacklist() {
		return new ArrayList<>(Arrays.asList(
				"minecraft:bedrock",
				"minecraft:barrier",
				"minecraft:command_block",
				"minecraft:chain_command_block",
				"minecraft:repeating_command_block",
				"minecraft:structure_block",
				"minecraft:structure_void",
				"minecraft:spawner",
				"minecraft:end_portal_frame",
				"minecraft:obsidian",
				"minecraft:crying_obsidian",
				"minecraft:chest",
				"minecraft:trapped_chest",
				"minecraft:ender_chest",
				"minecraft:shulker_box"
		));
	}

	public static AutoMineConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				AutoMineConfig loaded = GSON.fromJson(reader, AutoMineConfig.class);
				if (loaded != null) {
					loaded.normalize();
					return loaded;
				}
			} catch (IOException e) {
				AutoMineClient.LOGGER.warn("Failed to read automine.json, using defaults", e);
			}
		}
		AutoMineConfig fresh = new AutoMineConfig();
		fresh.save();
		return fresh;
	}

	private void normalize() {
		if (replyMessages == null || replyMessages.isEmpty()) {
			replyMessages = defaultReplies();
		}
		if (ignorePlayers == null) {
			ignorePlayers = defaultIgnorePlayers();
		}
		if (ignoreNameContains == null) {
			ignoreNameContains = defaultIgnoreNameContains();
		}
		if (blockWhitelist == null) {
			blockWhitelist = defaultWhitelist();
		}
		if (blockBlacklist == null) {
			blockBlacklist = defaultBlacklist();
		}
		if (sellCommand == null) {
			sellCommand = "sell all";
		}
		if (depositCommand == null) {
			depositCommand = "";
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			AutoMineClient.LOGGER.warn("Failed to write automine.json", e);
		}
	}
}
