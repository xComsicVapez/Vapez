package dev.cosmicpixel.aliases.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carpet registers its /spawn after Essential Commands, so Brigadier merges both into one node that keeps
 * Essential Commands' everyone-allowed requirement. Carpet's subcommands (rates, mocking, test, ...) would
 * then be open to every player, so Carpet's /spawn is not registered at all.
 */
@Mixin(targets = "carpet.commands.SpawnCommand", remap = false)
public abstract class CarpetSpawnCommandMixin {
	@Inject(method = "register", at = @At("HEAD"), cancellable = true, require = 0)
	private static void commandAliases$skipCarpetSpawn(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext context, CallbackInfo ci) {
		ci.cancel();
	}
}
