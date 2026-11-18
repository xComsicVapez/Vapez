package dev.xcosmicvapez.automine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Tracks session length / blocks broken and auto-stops when limits hit.
 */
public final class MiningSession {
	private final AutoMineConfig config;
	private int ticksActive;
	private int blocksBroken;
	private String stopReason = "";

	public MiningSession(AutoMineConfig config) {
		this.config = config;
	}

	public void reset() {
		ticksActive = 0;
		blocksBroken = 0;
		stopReason = "";
	}

	public void onBlockBroken() {
		blocksBroken++;
	}

	/**
	 * @return {@code false} when the session should end
	 */
	public boolean tick(Minecraft client) {
		ticksActive++;
		if (config.maxSessionTicks > 0 && ticksActive >= config.maxSessionTicks) {
			stopReason = "Session time limit";
			notify(client, stopReason);
			return false;
		}
		if (config.maxBlocksPerSession > 0 && blocksBroken >= config.maxBlocksPerSession) {
			stopReason = "Block limit (" + blocksBroken + ")";
			notify(client, stopReason);
			return false;
		}
		return true;
	}

	public int ticksActive() {
		return ticksActive;
	}

	public int blocksBroken() {
		return blocksBroken;
	}

	public String stopReason() {
		return stopReason;
	}

	public String hudLine() {
		int secs = ticksActive / 20;
		return "Session " + secs + "s · " + blocksBroken + " blocks";
	}

	private static void notify(Minecraft client, String reason) {
		LocalPlayer player = client.player;
		if (player != null) {
			player.displayClientMessage(Component.literal("AutoMine stopped: " + reason), false);
		}
	}
}
