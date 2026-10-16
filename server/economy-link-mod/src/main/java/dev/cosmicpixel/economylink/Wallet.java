package dev.cosmicpixel.economylink;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import savage.commoneconomy.EconomyManager;

/** The one wallet every economy mod on the server uses: the Savs Common Economy balance. */
public final class Wallet {
	private Wallet() {
	}

	private static EconomyManager eco() {
		return EconomyManager.getInstance();
	}

	public static BigDecimal money(double amount) {
		return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
	}

	public static boolean validAmount(double amount) {
		return Double.isFinite(amount) && amount > 0;
	}

	public static CompletableFuture<Void> ensureAccount(UUID player, String name) {
		if (name == null || name.isBlank()) {
			return CompletableFuture.completedFuture(null);
		}
		return eco().getOrCreateAccount(player, name).thenApply(account -> null);
	}

	public static CompletableFuture<BigDecimal> balance(UUID player) {
		return eco().getBalance(player).thenApply(balance -> balance == null ? BigDecimal.ZERO : balance);
	}

	public static CompletableFuture<Boolean> withdraw(UUID player, BigDecimal amount) {
		return eco().removeBalance(player, amount).thenApply(Boolean.TRUE::equals);
	}

	public static CompletableFuture<Boolean> deposit(UUID player, BigDecimal amount) {
		return eco().addBalance(player, amount).thenApply(Boolean.TRUE::equals);
	}

	public static CompletableFuture<Void> set(UUID player, BigDecimal amount) {
		return eco().setBalance(player, amount);
	}

	public static CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount) {
		return withdraw(from, amount).thenCompose(taken -> {
			if (!taken) {
				return CompletableFuture.completedFuture(false);
			}
			return deposit(to, amount).thenCompose(given -> given
				? CompletableFuture.completedFuture(true)
				: deposit(from, amount).thenApply(refunded -> false));
		});
	}
}
