package dev.cosmicpixel.economylink;

import com.echoesofaegis.auctionhouse.economy.BalanceEntry;
import com.echoesofaegis.auctionhouse.economy.EconomyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import savage.commoneconomy.EconomyManager;
import savage.commoneconomy.model.AccountData;

/** Echoes Auction House economy backed by the shared wallet. The auction house counts whole coins. */
public final class AuctionWallet implements EconomyService {
	@Override
	public CompletableFuture<Long> balance(UUID player, String name) {
		return Wallet.ensureAccount(player, name)
			.thenCompose(ignored -> Wallet.balance(player))
			.thenApply(balance -> balance.setScale(0, RoundingMode.FLOOR).longValue());
	}

	@Override
	public CompletableFuture<Boolean> withdraw(UUID player, String name, long amount) {
		if (amount <= 0) {
			return CompletableFuture.completedFuture(false);
		}
		return Wallet.ensureAccount(player, name).thenCompose(ignored -> Wallet.withdraw(player, BigDecimal.valueOf(amount)));
	}

	@Override
	public CompletableFuture<Void> deposit(UUID player, String name, long amount) {
		if (amount <= 0) {
			return CompletableFuture.completedFuture(null);
		}
		return Wallet.ensureAccount(player, name)
			.thenCompose(ignored -> Wallet.deposit(player, BigDecimal.valueOf(amount)))
			.thenApply(ignored -> null);
	}

	@Override
	public CompletableFuture<Boolean> transfer(UUID from, String fromName, UUID to, String toName, long amount) {
		if (amount <= 0 || from.equals(to)) {
			return CompletableFuture.completedFuture(false);
		}
		return Wallet.ensureAccount(from, fromName)
			.thenCompose(ignored -> Wallet.ensureAccount(to, toName))
			.thenCompose(ignored -> Wallet.transfer(from, to, BigDecimal.valueOf(amount)));
	}

	@Override
	public CompletableFuture<Void> setBalance(UUID player, String name, long amount) {
		return Wallet.ensureAccount(player, name).thenCompose(ignored -> Wallet.set(player, BigDecimal.valueOf(Math.max(0, amount))));
	}

	@Override
	public CompletableFuture<List<BalanceEntry>> topBalances(int limit) {
		EconomyManager eco = EconomyManager.getInstance();
		return eco.getTopAccounts(limit).thenCompose(accounts -> {
			List<CompletableFuture<BalanceEntry>> entries = new ArrayList<>();
			for (AccountData account : accounts) {
				entries.add(eco.getUUIDFromName(account.getName()).thenApply(uuid -> new BalanceEntry(
					uuid == null ? new UUID(0, 0) : uuid, account.getName(),
					account.getBalance().setScale(0, RoundingMode.FLOOR).longValue())));
			}
			return CompletableFuture.allOf(entries.toArray(CompletableFuture[]::new))
				.thenApply(ignored -> entries.stream().map(CompletableFuture::join).toList());
		});
	}

	@Override
	public String name() {
		return "Savs Common Economy (economy-link)";
	}
}
