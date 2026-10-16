package dev.cosmicpixel.economylink.mixin;

import dev.cosmicpixel.economylink.Wallet;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ClassicGUIShop keeps its own balances file; route every balance operation to the shared wallet instead. */
@Mixin(targets = "com.zycu.guishop.EconomyStore", remap = false)
public abstract class ShopEconomyMixin {
	@Inject(method = "balance", at = @At("HEAD"), cancellable = true)
	private void economyLink$balance(UUID player, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(Wallet.balance(player).join().doubleValue());
	}

	@Inject(method = "withdraw", at = @At("HEAD"), cancellable = true)
	private void economyLink$withdraw(UUID player, double amount, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Wallet.validAmount(amount) && Wallet.withdraw(player, Wallet.money(amount)).join());
	}

	@Inject(method = "deposit", at = @At("HEAD"), cancellable = true)
	private void economyLink$deposit(UUID player, double amount, CallbackInfo ci) {
		if (Wallet.validAmount(amount)) {
			Wallet.deposit(player, Wallet.money(amount)).join();
		}
		ci.cancel();
	}

	@Inject(method = "setBalance", at = @At("HEAD"), cancellable = true)
	private void economyLink$setBalance(UUID player, double amount, CallbackInfo ci) {
		if (Double.isFinite(amount) && amount >= 0) {
			Wallet.set(player, Wallet.money(amount)).join();
		}
		ci.cancel();
	}

	@Inject(method = "transfer", at = @At("HEAD"), cancellable = true)
	private void economyLink$transfer(UUID from, UUID to, double amount, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Wallet.validAmount(amount) && !from.equals(to)
			&& Wallet.transfer(from, to, Wallet.money(amount)).join());
	}
}
