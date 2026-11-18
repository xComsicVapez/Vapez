package dev.xcosmicvapez.automine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Complex-like prison economy helpers: /sell all (10s CD), optional /rankupmax,
 * optional /mine on start, inventory/tool guards.
 */
public final class PrisonHelpers {
	private final AutoMineConfig config;
	private int sellCooldown;
	private int depositCooldown;
	private int periodicSellCounter;
	private int pendingRankupTicks = -1;
	private String lastStatus = "";

	public PrisonHelpers(AutoMineConfig config) {
		this.config = config;
	}

	public void reset() {
		sellCooldown = 0;
		depositCooldown = 0;
		periodicSellCounter = 0;
		pendingRankupTicks = -1;
		lastStatus = "";
	}

	public String lastStatus() {
		return lastStatus;
	}

	/** Optional one-shot mine teleport when AutoMine is enabled. */
	public void onMiningStarted(Minecraft client) {
		if (!config.teleportToMineOnStart) {
			return;
		}
		if (config.mineTeleportCommand == null || config.mineTeleportCommand.isBlank()) {
			return;
		}
		sendCommand(client, config.mineTeleportCommand);
		if (client.player != null) {
			client.player.displayClientMessage(
					Component.literal("AutoMine: /" + stripSlash(config.mineTeleportCommand)),
					true
			);
		}
	}

	/**
	 * @return {@code true} if mining should continue this tick
	 */
	public boolean allowMining(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}

		tickCooldowns();
		tickPendingRankup(client);
		tickPeriodicSell(client, player);

		if (config.pauseOnLowDurability && isToolTooDamaged(player)) {
			lastStatus = "Low durability";
			return false;
		}

		if (config.pauseWhenInventoryFull && isInventoryFull(player)) {
			lastStatus = sellCooldown > 0
					? "Inv full · sell CD " + (sellCooldown / 20) + "s"
					: "Inventory full";
			if (config.sellWhenFull) {
				doSell(client, player, true);
			}
			tryDeposit(client);
			return false;
		}

		lastStatus = sellCooldown > 0 ? "Sell CD " + (sellCooldown / 20) + "s" : "";
		return true;
	}

	/** Manual sell keybind. */
	public void forceSell(Minecraft client) {
		if (client.player == null) {
			return;
		}
		doSell(client, client.player, true);
	}

	private void tickCooldowns() {
		if (sellCooldown > 0) {
			sellCooldown--;
		}
		if (depositCooldown > 0) {
			depositCooldown--;
		}
	}

	private void tickPeriodicSell(Minecraft client, LocalPlayer player) {
		if (config.periodicSellTicks <= 0) {
			return;
		}
		periodicSellCounter++;
		if (periodicSellCounter >= config.periodicSellTicks) {
			periodicSellCounter = 0;
			doSell(client, player, false);
		}
	}

	private void tickPendingRankup(Minecraft client) {
		if (pendingRankupTicks < 0) {
			return;
		}
		if (pendingRankupTicks > 0) {
			pendingRankupTicks--;
			return;
		}
		pendingRankupTicks = -1;
		if (config.rankupAfterSell && config.rankupCommand != null && !config.rankupCommand.isBlank()) {
			sendCommand(client, config.rankupCommand);
			if (client.player != null) {
				client.player.displayClientMessage(
						Component.literal("AutoMine: /" + stripSlash(config.rankupCommand)),
						true
				);
			}
		}
	}

	private void doSell(Minecraft client, LocalPlayer player, boolean announce) {
		if (config.sellCommand == null || config.sellCommand.isBlank() || sellCooldown > 0) {
			return;
		}
		sendCommand(client, config.sellCommand);
		sellCooldown = Math.max(20, config.sellCooldownTicks);
		periodicSellCounter = 0;
		if (announce && player != null) {
			player.displayClientMessage(Component.literal("AutoMine: /" + stripSlash(config.sellCommand)), true);
		}
		if (config.rankupAfterSell && config.rankupCommand != null && !config.rankupCommand.isBlank()) {
			pendingRankupTicks = Math.max(1, config.rankupDelayTicks);
		}
	}

	private void tryDeposit(Minecraft client) {
		if (config.depositCommand == null || config.depositCommand.isBlank() || depositCooldown > 0) {
			return;
		}
		sendCommand(client, config.depositCommand);
		depositCooldown = Math.max(20, config.depositCooldownTicks);
	}

	static boolean isInventoryFull(LocalPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	boolean isToolTooDamaged(LocalPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return false;
		}
		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return remaining <= Math.max(1, config.minToolDurability);
	}

	static void sendCommand(Minecraft client, String command) {
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		client.player.connection.sendCommand(stripSlash(command));
	}

	private static String stripSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}
}
