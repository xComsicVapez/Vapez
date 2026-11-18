package dev.xcosmicvapez.automine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory-full handling, optional /sell /deposit, and tool durability checks.
 */
public final class PrisonHelpers {
	private final AutoMineConfig config;
	private int sellCooldown;
	private int depositCooldown;
	private String lastStatus = "";

	public PrisonHelpers(AutoMineConfig config) {
		this.config = config;
	}

	public void reset() {
		sellCooldown = 0;
		depositCooldown = 0;
		lastStatus = "";
	}

	public String lastStatus() {
		return lastStatus;
	}

	/**
	 * @return {@code true} if mining should continue this tick
	 */
	public boolean allowMining(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}

		if (sellCooldown > 0) {
			sellCooldown--;
		}
		if (depositCooldown > 0) {
			depositCooldown--;
		}

		if (config.pauseOnLowDurability && isToolTooDamaged(player)) {
			lastStatus = "Low durability";
			return false;
		}

		if (config.pauseWhenInventoryFull && isInventoryFull(player)) {
			lastStatus = "Inventory full";
			trySellOrDeposit(client, player);
			return false;
		}

		lastStatus = "";
		return true;
	}

	private void trySellOrDeposit(Minecraft client, LocalPlayer player) {
		if (config.sellWhenFull && config.sellCommand != null && !config.sellCommand.isBlank() && sellCooldown <= 0) {
			sendCommand(client, config.sellCommand);
			sellCooldown = Math.max(20, config.sellCooldownTicks);
			player.displayClientMessage(Component.literal("AutoMine: ran /" + stripSlash(config.sellCommand)), true);
		}
		if (config.depositCommand != null && !config.depositCommand.isBlank() && depositCooldown <= 0) {
			sendCommand(client, config.depositCommand);
			depositCooldown = Math.max(20, config.depositCooldownTicks);
		}
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
