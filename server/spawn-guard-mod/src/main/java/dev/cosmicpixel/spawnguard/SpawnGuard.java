package dev.cosmicpixel.spawnguard;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;

public class SpawnGuard implements ModInitializer {
	// Footprint of the spawn build on lifesteal2, at any height.
	private static final int MIN_X = 80, MAX_X = 367, MIN_Z = -96, MAX_Z = 175;

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player
				&& source.is(DamageTypeTags.IS_FALL)
				&& player.level().dimension() == Level.OVERWORLD
				&& inSpawn(player.getX(), player.getZ())));
	}

	private static boolean inSpawn(double x, double z) {
		return x >= MIN_X && x < MAX_X + 1 && z >= MIN_Z && z < MAX_Z + 1;
	}
}
