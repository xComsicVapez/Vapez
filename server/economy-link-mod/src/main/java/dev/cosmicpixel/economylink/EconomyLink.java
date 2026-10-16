package dev.cosmicpixel.economylink;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.math.BigDecimal;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import savage.commoneconomy.EconomyManager;

public final class EconomyLink implements ModInitializer {
	private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

	@Override
	public void onInitialize() {
		// %economy:balance% -> "$12.5K", %economy:balance_exact% -> "$12,480.00"
		Placeholders.registerServer(Identifier.fromNamespaceAndPath("economy", "balance"), (ctx, arg) -> {
			BigDecimal balance = balanceOf(ctx.serverPlayer());
			return balance == null ? PlaceholderResult.value("$...") : PlaceholderResult.value(compact(balance));
		});
		Placeholders.registerServer(Identifier.fromNamespaceAndPath("economy", "balance_exact"), (ctx, arg) -> {
			BigDecimal balance = balanceOf(ctx.serverPlayer());
			return balance == null ? PlaceholderResult.value("$...") : PlaceholderResult.value(String.format("$%,.2f", balance));
		});
	}

	private static BigDecimal balanceOf(net.minecraft.server.level.ServerPlayer player) {
		if (player == null) {
			return null;
		}
		EconomyManager eco = EconomyManager.getInstance();
		BigDecimal cached = eco.getCachedBalance(player.getUUID());
		if (cached == null) {
			// Placeholders are rendered on the server thread; load in the background and show it next refresh.
			eco.getBalance(player.getUUID());
		}
		return cached;
	}

	static String compact(BigDecimal amount) {
		double value = amount.doubleValue();
		int tier = 0;
		while (Math.abs(value) >= 1000 && tier < SUFFIXES.length - 1) {
			value /= 1000;
			tier++;
		}
		String number = tier == 0 ? String.format("%.0f", Math.floor(value)) : String.format("%.1f", Math.floor(value * 10) / 10);
		if (number.endsWith(".0")) {
			number = number.substring(0, number.length() - 2);
		}
		return "$" + number + SUFFIXES[tier];
	}
}
