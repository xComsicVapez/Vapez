package dev.cosmicpixel.economylink.mixin;

import com.echoesofaegis.auctionhouse.db.AuctionDatabase;
import com.echoesofaegis.auctionhouse.economy.EconomyService;
import com.echoesofaegis.auctionhouse.economy.EconomyServices;
import dev.cosmicpixel.economylink.AuctionWallet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EconomyServices.class, remap = false)
public abstract class AuctionEconomyMixin {
	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private static void economyLink$useSharedWallet(AuctionDatabase database, CallbackInfoReturnable<EconomyService> cir) {
		cir.setReturnValue(new AuctionWallet());
	}
}
