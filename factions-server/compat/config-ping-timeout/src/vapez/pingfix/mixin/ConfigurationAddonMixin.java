package vapez.pingfix.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API pauses every login until the client answers a configuration-phase ping
 * (or registers its channels). ViaBackwards turns that ping into a play ping for
 * 1.17-1.20.1 clients, which Velocity swallows while it is still switching servers,
 * so those players hang forever. If no answer arrives in time, carry on as if a
 * vanilla client had answered; onPong ignores the call when an answer already came.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.networking.server.ServerConfigurationNetworkAddon", remap = false)
public abstract class ConfigurationAddonMixin {
	private static final int FABRIC_PING_ID = 0xFAB71C;
	private static final long TIMEOUT_SECONDS = 3;

	@Shadow
	@Final
	private MinecraftServer server;

	@Shadow
	public abstract void onPong(int parameter);

	@Inject(method = "startConfiguration", at = @At("RETURN"))
	private void vapez$answerMissingPong(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		Executor mainThread = server;
		CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.execute(() -> mainThread.execute(() -> onPong(FABRIC_PING_ID)));
	}
}
