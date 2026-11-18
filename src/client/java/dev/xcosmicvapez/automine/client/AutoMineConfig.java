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
import java.util.List;

/**
 * Simple JSON config under {@code config/automine.json}.
 */
public final class AutoMineConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("automine.json");

	public boolean autoReplyEnabledByDefault = true;
	/** Min/max ticks to wait before sending a reply (20 ticks = 1s). */
	public int replyDelayTicksMin = 30;
	public int replyDelayTicksMax = 90;
	/** Don't reply to the same person more often than this (ticks). */
	public int replyCooldownTicks = 200;
	/** Only auto-reply to private whispers / tells. */
	public boolean whispersOnly = true;
	/** Reply lines picked at random so it doesn't read like a bot. */
	public List<String> replyMessages = defaultReplies();

	/** Chance per second (approx) to take a short break while mining (0–1). */
	public double pauseChancePerSecond = 0.04;
	public int pauseTicksMin = 12;
	public int pauseTicksMax = 45;
	/** Extra delay after a block breaks before starting the next one. */
	public int afterBreakDelayMin = 2;
	public int afterBreakDelayMax = 8;
	/** Max look jitter in degrees while mining. */
	public float lookJitterDegrees = 0.35f;

	private static List<String> defaultReplies() {
		List<String> list = new ArrayList<>();
		list.add("hey sorry afk mining real quick");
		list.add("yo gimme a sec mining");
		list.add("hey what's up, one sec");
		list.add("afk strip mining, brb");
		list.add("hey! back in a bit just grinding");
		return list;
	}

	public static AutoMineConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				AutoMineConfig loaded = GSON.fromJson(reader, AutoMineConfig.class);
				if (loaded != null) {
					if (loaded.replyMessages == null || loaded.replyMessages.isEmpty()) {
						loaded.replyMessages = defaultReplies();
					}
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
