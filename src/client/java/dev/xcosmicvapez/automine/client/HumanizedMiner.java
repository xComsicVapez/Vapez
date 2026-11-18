package dev.xcosmicvapez.automine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Breaks the crosshair block with human-ish pacing: micro look drift,
 * short random pauses, and a brief lag after each block pops.
 */
public final class HumanizedMiner {
	private final AutoMineConfig config;

	private int pauseTicksRemaining;
	private int afterBreakCooldown;
	private BlockPos lastTarget;
	private boolean wasDestroying;

	public HumanizedMiner(AutoMineConfig config) {
		this.config = config;
	}

	public void reset() {
		pauseTicksRemaining = 0;
		afterBreakCooldown = 0;
		lastTarget = null;
		wasDestroying = false;
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null) {
			return;
		}

		ThreadLocalRandom rng = ThreadLocalRandom.current();

		if (pauseTicksRemaining > 0) {
			pauseTicksRemaining--;
			applyIdleLook(player, rng);
			return;
		}

		if (afterBreakCooldown > 0) {
			afterBreakCooldown--;
			applyIdleLook(player, rng);
			return;
		}

		// Occasional "adjust grip / stretch" pause so timing isn't metronomic.
		if (rng.nextDouble() < config.pauseChancePerSecond / 20.0) {
			pauseTicksRemaining = rng.nextInt(config.pauseTicksMin, config.pauseTicksMax + 1);
			return;
		}

		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			wasDestroying = false;
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		if (state.isAir()) {
			wasDestroying = false;
			return;
		}

		if (lastTarget != null && !lastTarget.equals(pos) && wasDestroying) {
			// Switched blocks — small human hesitation.
			afterBreakCooldown = rng.nextInt(1, 5);
			lastTarget = pos;
			wasDestroying = false;
			return;
		}

		applyMiningJitter(player, rng);

		boolean continued = client.gameMode.continueDestroyBlock(pos, blockHit.getDirection());
		if (continued) {
			client.particleEngine.crack(pos, blockHit.getDirection());
			// Don't swing every tick — people don't; ~every 4–7 ticks feels natural.
			if (client.player.tickCount % rng.nextInt(4, 8) == 0) {
				client.player.swing(InteractionHand.MAIN_HAND);
			}
			wasDestroying = true;
			lastTarget = pos;
		} else if (wasDestroying) {
			// Block just finished breaking.
			wasDestroying = false;
			afterBreakCooldown = rng.nextInt(config.afterBreakDelayMin, config.afterBreakDelayMax + 1);
			nudgeLookAfterBreak(player, rng);
		}
	}

	private void applyMiningJitter(LocalPlayer player, ThreadLocalRandom rng) {
		float max = config.lookJitterDegrees;
		if (max <= 0f) {
			return;
		}
		float yawJitter = (rng.nextFloat() * 2f - 1f) * max;
		float pitchJitter = (rng.nextFloat() * 2f - 1f) * max * 0.6f;
		player.setYRot(player.getYRot() + yawJitter);
		player.setXRot(clampPitch(player.getXRot() + pitchJitter));
	}

	private void applyIdleLook(LocalPlayer player, ThreadLocalRandom rng) {
		if (rng.nextInt(8) != 0) {
			return;
		}
		float yaw = (rng.nextFloat() * 2f - 1f) * 0.8f;
		float pitch = (rng.nextFloat() * 2f - 1f) * 0.4f;
		player.setYRot(player.getYRot() + yaw);
		player.setXRot(clampPitch(player.getXRot() + pitch));
	}

	private void nudgeLookAfterBreak(LocalPlayer player, ThreadLocalRandom rng) {
		player.setYRot(player.getYRot() + (rng.nextFloat() * 2f - 1f) * 1.2f);
		player.setXRot(clampPitch(player.getXRot() + (rng.nextFloat() * 2f - 1f) * 0.7f));
	}

	private static float clampPitch(float pitch) {
		return Math.max(-90f, Math.min(90f, pitch));
	}
}
