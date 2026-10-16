package dev.cosmicpixel.spawnguard.mixin;

import dev.cosmicpixel.spawnguard.Plots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which blocks a renter placed in their spawn plot; only those may be broken there. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void spawnGuard$recordPlacement(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() instanceof ServerPlayer player) {
			Plots.recordPlaced(player, context.getLevel(), context.getClickedPos(), state);
		}
	}
}
