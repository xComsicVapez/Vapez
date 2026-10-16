package dev.cosmicpixel.spawnguard;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class SpawnGuard implements ModInitializer {
	// Footprint of the spawn build on lifesteal2, at any height.
	private static final int MIN_X = 80, MAX_X = 367, MIN_Z = -96, MAX_Z = 175;

	@Override
	public void onInitialize() {
		ProtectedAreas.register();
		Plots.register();
		SpawnRules.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Plots.commands(dispatcher));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) {
				return true;
			}
			if (source.is(DamageTypeTags.IS_FALL) && inSpawn(victim)) {
				return false;
			}
			// getEntity() is the owner for projectiles, so arrows and tridents count as PvP too.
			return !(source.getEntity() instanceof ServerPlayer attacker && attacker != victim
				&& (inSpawn(victim) || inSpawn(attacker)));
		});
	}

	private static boolean inSpawn(Entity entity) {
		return inSpawn(entity.level(), entity.getX(), entity.getZ());
	}

	static boolean inSpawn(Level level, double x, double z) {
		return inSpawn(level, x, z, 0);
	}

	static boolean inSpawn(Level level, double x, double z, int margin) {
		return level.dimension() == Level.OVERWORLD
			&& x >= MIN_X - margin && x < MAX_X + 1 + margin && z >= MIN_Z - margin && z < MAX_Z + 1 + margin;
	}
}
