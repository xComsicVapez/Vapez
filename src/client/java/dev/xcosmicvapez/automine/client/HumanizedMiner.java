package dev.xcosmicvapez.automine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Humanized miner with optional circular look sweep — aims around you instead of
 * drilling a single column (works well with high-efficiency picks).
 */
public final class HumanizedMiner {
	private final AutoMineConfig config;
	private final BlockFilters filters;
	private final MiningSession session;

	private int pauseTicksRemaining;
	private int afterBreakCooldown;
	private int forwardAssistRemaining;
	private BlockPos lastTarget;
	private boolean wasDestroying;
	private float sweepYaw;
	private boolean sweepInitialized;
	private String status = "";

	public HumanizedMiner(AutoMineConfig config, MiningSession session) {
		this.config = config;
		this.filters = new BlockFilters(config);
		this.session = session;
	}

	public void reset() {
		pauseTicksRemaining = 0;
		afterBreakCooldown = 0;
		forwardAssistRemaining = 0;
		lastTarget = null;
		wasDestroying = false;
		sweepInitialized = false;
		status = "";
		releaseForward(Minecraft.getInstance());
	}

	public String status() {
		return status;
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null) {
			releaseForward(client);
			return;
		}

		ThreadLocalRandom rng = ThreadLocalRandom.current();

		if (forwardAssistRemaining > 0) {
			client.options.keyUp.setDown(true);
			forwardAssistRemaining--;
			if (forwardAssistRemaining <= 0) {
				releaseForward(client);
			}
		}

		if (pauseTicksRemaining > 0) {
			pauseTicksRemaining--;
			status = "Pausing";
			applyIdleLook(player, rng);
			return;
		}

		if (afterBreakCooldown > 0) {
			afterBreakCooldown--;
			status = "Re-aiming";
			return;
		}

		if (rng.nextDouble() < config.pauseChancePerSecond / 20.0) {
			pauseTicksRemaining = rng.nextInt(config.pauseTicksMin, config.pauseTicksMax + 1);
			status = "Pausing";
			return;
		}

		if (!sweepInitialized) {
			sweepYaw = player.getYRot();
			sweepInitialized = true;
		}

		BlockPos target = config.circleMine
				? pickCircleTarget(client, player)
				: pickCrosshairTarget(client);

		if (target == null) {
			status = "No blocks";
			wasDestroying = false;
			if (config.circleMine && config.forwardAssistTicks > 0 && forwardAssistRemaining <= 0) {
				forwardAssistRemaining = config.forwardAssistTicks;
			}
			return;
		}

		lookToward(player, target, config.lookSpeedDegrees);

		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			status = "Turning";
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		if (!filters.allows(state) || !pos.equals(target) && !isCloseEnough(player, pos, target)) {
			// Keep turning toward intended target; don't mine wrong block.
			status = "Turning";
			return;
		}

		if (lastTarget != null && !lastTarget.equals(pos) && wasDestroying) {
			afterBreakCooldown = rng.nextInt(1, 4);
			lastTarget = pos;
			wasDestroying = false;
			return;
		}

		applyMiningJitter(player, rng);
		Direction face = blockHit.getDirection();

		if (!wasDestroying || lastTarget == null || !lastTarget.equals(pos)) {
			client.gameMode.startDestroyBlock(pos, face);
			player.swing(InteractionHand.MAIN_HAND);
			lastTarget = pos;
			if (client.level.getBlockState(pos).isAir()) {
				// Efficiency / haste insta-break
				onBroken(rng);
			} else {
				wasDestroying = true;
				status = config.circleMine ? "Circle mining" : "Mining";
			}
			return;
		}

		boolean continued = client.gameMode.continueDestroyBlock(pos, face);
		if (continued && !client.level.getBlockState(pos).isAir()) {
			client.particleEngine.crack(pos, face);
			if (player.tickCount % rng.nextInt(3, 7) == 0) {
				player.swing(InteractionHand.MAIN_HAND);
			}
			status = config.circleMine ? "Circle mining" : "Mining";
		} else {
			onBroken(rng);
		}
	}

	private void onBroken(ThreadLocalRandom rng) {
		wasDestroying = false;
		session.onBlockBroken();
		afterBreakCooldown = rng.nextInt(config.afterBreakDelayMin, config.afterBreakDelayMax + 1);
		sweepYaw += config.circleStepDegrees;
		status = "Broken";
	}

	private BlockPos pickCrosshairTarget(Minecraft client) {
		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockState state = client.level.getBlockState(blockHit.getBlockPos());
		return filters.allows(state) ? blockHit.getBlockPos() : null;
	}

	/**
	 * Pick the next mineable block around the player in yaw-circle order,
	 * preferring blocks near the current sweep angle.
	 */
	private BlockPos pickCircleTarget(Minecraft client, LocalPlayer player) {
		Level level = client.level;
		Vec3 eye = player.getEyePosition(1f);
		BlockPos origin = BlockPos.containing(eye);
		double radius = Math.max(1.5, config.circleRadius);
		int r = (int) Math.ceil(radius);

		List<Scored> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			BlockPos immutable = pos.immutable();
			if (eye.distanceToSqr(Vec3.atCenterOf(immutable)) > radius * radius) {
				continue;
			}
			BlockState state = level.getBlockState(immutable);
			if (!filters.allows(state)) {
				continue;
			}
			if (state.getDestroySpeed(level, immutable) < 0) {
				continue;
			}

			Vec3 center = Vec3.atCenterOf(immutable);
			Vec3 diff = center.subtract(eye);
			double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
			float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, horiz));
			if (pitch < config.circlePitchMin || pitch > config.circlePitchMax) {
				continue;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
			float delta = Math.abs(Mth.wrapDegrees(yaw - sweepYaw));
			candidates.add(new Scored(immutable, delta, eye.distanceToSqr(center)));
		}

		if (candidates.isEmpty()) {
			sweepYaw += config.circleStepDegrees;
			return null;
		}

		candidates.sort(Comparator
				.comparingDouble((Scored s) -> s.angleDelta)
				.thenComparingDouble(s -> s.distSq));
		return candidates.get(0).pos;
	}

	private static boolean isCloseEnough(LocalPlayer player, BlockPos looking, BlockPos intended) {
		return looking.distSqr(intended) <= 2.25;
	}

	private void lookToward(LocalPlayer player, BlockPos pos, float speed) {
		Vec3 eye = player.getEyePosition(1f);
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 diff = center.subtract(eye);
		double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		float targetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
		float targetPitch = (float) Math.toDegrees(-Math.atan2(diff.y, horiz));

		float yaw = player.getYRot();
		float pitch = player.getXRot();
		float yawDiff = Mth.wrapDegrees(targetYaw - yaw);
		float pitchDiff = targetPitch - pitch;

		float yawStep = Mth.clamp(yawDiff, -speed, speed);
		float pitchStep = Mth.clamp(pitchDiff, -speed, speed);
		player.setYRot(yaw + yawStep);
		player.setXRot(clampPitch(pitch + pitchStep));
	}

	private void applyMiningJitter(LocalPlayer player, ThreadLocalRandom rng) {
		float max = config.lookJitterDegrees;
		if (max <= 0f) {
			return;
		}
		player.setYRot(player.getYRot() + (rng.nextFloat() * 2f - 1f) * max);
		player.setXRot(clampPitch(player.getXRot() + (rng.nextFloat() * 2f - 1f) * max * 0.5f));
	}

	private void applyIdleLook(LocalPlayer player, ThreadLocalRandom rng) {
		if (rng.nextInt(8) != 0) {
			return;
		}
		player.setYRot(player.getYRot() + (rng.nextFloat() * 2f - 1f) * 0.8f);
		player.setXRot(clampPitch(player.getXRot() + (rng.nextFloat() * 2f - 1f) * 0.4f));
	}

	private static void releaseForward(Minecraft client) {
		if (client.options != null) {
			client.options.keyUp.setDown(false);
		}
	}

	private static float clampPitch(float pitch) {
		return Mth.clamp(pitch, -90f, 90f);
	}

	private record Scored(BlockPos pos, float angleDelta, double distSq) {
	}
}
