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
 * JSON config at {@code config/automine.json}.
 * Defaults follow a Complex-like prison command loop (sell all / rankupmax / mine)
 * so allowlisted prison servers with the same command names work out of the box.
 */
public final class AutoMineConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("automine.json");

	/**
	 * Named preset label only (documentation). Does not fetch anything remote.
	 * {@code complex-like} = public Complex-style command names & 10s sell cooldown.
	 */
	public String prisonProfile = "complex-like";

	// --- AutoReply ---
	public boolean autoReplyEnabledByDefault = false;
	public int replyDelayTicksMin = 40;
	public int replyDelayTicksMax = 120;
	public int replyCooldownTicks = 300;
	public boolean whispersOnly = true;
	public List<String> replyMessages = defaultReplies();
	public List<String> ignorePlayers = defaultIgnorePlayers();
	public List<String> ignoreNameContains = defaultIgnoreNameContains();

	// --- Human feel ---
	public double pauseChancePerSecond = 0.03;
	public int pauseTicksMin = 8;
	public int pauseTicksMax = 28;
	public int afterBreakDelayMin = 0;
	public int afterBreakDelayMax = 2;
	public float lookJitterDegrees = 0.2f;
	public float lookSpeedDegrees = 22f;

	// --- Circle mining ---
	public boolean circleMine = true;
	public double circleRadius = 4.2;
	public float circlePitchMin = -60f;
	public float circlePitchMax = 60f;
	public float circleStepDegrees = 10f;
	public int forwardAssistTicks = 5;

	// --- Block filters ---
	public List<String> blockWhitelist = defaultWhitelist();
	public List<String> blockBlacklist = defaultBlacklist();

	// --- Complex-like economy loop ---
	public boolean pauseWhenInventoryFull = true;
	public boolean sellWhenFull = true;
	/** Complex: {@code /sell all} sells inventory + offhand backpack. */
	public String sellCommand = "sell all";
	/**
	 * Complex applies a ~10s sell cooldown inside mines.
	 * 20 ticks = 1s → 200 ticks = 10s.
	 */
	public int sellCooldownTicks = 200;
	/**
	 * Also sell on a timer while mining (mirrors Auto Sell–style cadence).
	 * 0 disables. Default 200 = every 10s, aligned with Complex mine sell CD.
	 */
	public int periodicSellTicks = 200;
	public String depositCommand = "";
	public int depositCooldownTicks = 100;

	/** After a successful sell attempt, run rankup (Complex: {@code /rankupmax}). */
	public boolean rankupAfterSell = true;
	public String rankupCommand = "rankupmax";
	/** Small delay so sell money registers before rankup. */
	public int rankupDelayTicks = 25;

	/**
	 * When AutoMine turns on, optionally run this once (Complex: {@code /mine} or {@code /mine a}).
	 * Empty = skip.
	 */
	public String mineTeleportCommand = "mine";
	public boolean teleportToMineOnStart = false;

	// --- Tool guard ---
	public boolean pauseOnLowDurability = true;
	public int minToolDurability = 15;

	// --- Session limits (0 = unlimited) ---
	public int maxSessionTicks = 0;
	public int maxBlocksPerSession = 0;

	private static List<String> defaultReplies() {
		return new ArrayList<>(Arrays.asList(
				"hey sorry grinding the mine rn",
				"yo one sec mining for rank",
				"hey what's good, busy in mine",
				"gimme a min ranking up",
				"sup, selling soon brb",
				"hey! mining will msg back"
		));
	}

	private static List<String> defaultIgnorePlayers() {
		return new ArrayList<>();
	}

	private static List<String> defaultIgnoreNameContains() {
		return new ArrayList<>(Arrays.asList("admin", "mod", "staff", "owner", "helper", "manager"));
	}

	private static List<String> defaultWhitelist() {
		// Empty = mine anything not blacklisted (safest across custom prison mines).
		return new ArrayList<>();
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
				"minecraft:shulker_box",
				"minecraft:reinforced_deepslate"
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
		if (prisonProfile == null || prisonProfile.isBlank()) {
			prisonProfile = "complex-like";
		}
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
		if (rankupCommand == null) {
			rankupCommand = "rankupmax";
		}
		if (mineTeleportCommand == null) {
			mineTeleportCommand = "mine";
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
