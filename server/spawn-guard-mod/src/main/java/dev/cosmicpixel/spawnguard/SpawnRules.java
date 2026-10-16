package dev.cosmicpixel.spawnguard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Protection for the spawn build, replacing vanilla spawn-protection (which also stopped players from opening the
 * ender chests). In spawn, players who are not ops cannot break or place blocks or touch decorations, but can use
 * containers, crafting stations, doors and buttons. Inside a plot they rent they may build (see {@link Plots}).
 * Everyone but ops gets Mining Fatigue III in spawn, except inside their own plot.
 */
public final class SpawnRules {
	// Things that could destroy or move the spawn build even from inside a rented plot.
	private static final Set<Item> BANNED = Set.of(Items.TNT, Items.TNT_MINECART, Items.END_CRYSTAL, Items.RESPAWN_ANCHOR,
		Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.LAVA_BUCKET, Items.PISTON, Items.STICKY_PISTON, Items.DISPENSER,
		Items.WITHER_SKELETON_SKULL);
	private static final int FATIGUE_TICKS = 60, FATIGUE_LEVEL = 2;
	private static final Set<UUID> FATIGUED = new HashSet<>();
	private static int ticks;

	private SpawnRules() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp) || exempt(sp) || !SpawnGuard.inSpawn(level, pos.getX(), pos.getZ())) {
				return true;
			}
			if (Plots.mayBreak(sp, level, pos, state)) return true;
			deny(sp, Plots.ownsPlotAt(sp, level, pos)
				? "You can only break blocks you placed in your plot." : "Spawn is protected.");
			return false;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> Plots.recordBroken(level, pos, state));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || exempt(sp)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockPos placeAt = pos.relative(hit.getDirection());
			if (!SpawnGuard.inSpawn(level, pos.getX(), pos.getZ()) && !SpawnGuard.inSpawn(level, placeAt.getX(), placeAt.getZ())) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockState state = level.getBlockState(pos);
			if (BANNED.contains(stack.getItem()) || stack.getItem() instanceof SpawnEggItem) {
				deny(sp, "That item is not allowed in spawn.");
				return InteractionResult.FAIL;
			}
			Plots.Plot plot = Plots.at(level, pos);
			boolean own = Plots.mayBuild(sp, level, pos) && Plots.mayBuild(sp, level, placeAt);
			if (own) {
				// Tools could still change spawn blocks (strip, till, path, wax), so only placing is allowed on them.
				if (Plots.isPlacedBlock(level, pos, state) || stack.isEmpty() || places(stack) || publicUse(state, level, pos)) {
					return InteractionResult.PASS;
				}
				deny(sp, "You can only change blocks you placed in your plot.");
				return InteractionResult.FAIL;
			}
			if (plot != null && plot.active(System.currentTimeMillis() / 1000)) {
				deny(sp, "This plot is rented by " + plot.renterName + ".");
				return InteractionResult.FAIL;
			}
			boolean wouldPlace = player.isSecondaryUseActive() && !stack.isEmpty();
			if (!wouldPlace && publicUse(state, level, pos)) return InteractionResult.PASS;
			deny(sp, "Spawn is protected.");
			return InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || exempt(sp)) return InteractionResult.PASS;
			Item item = player.getItemInHand(hand).getItem();
			if (!(item instanceof BucketItem) && !BANNED.contains(item)) return InteractionResult.PASS;
			// Buckets aim at whatever the player looks at, so judge by where the player stands (with a margin).
			if (!SpawnGuard.inSpawn(level, player.getX(), player.getZ(), 6)) return InteractionResult.PASS;
			if (item != Items.LAVA_BUCKET && !BANNED.contains(item) && Plots.standsInOwnPlot(sp)) return InteractionResult.PASS;
			deny(sp, "You can't use that in spawn.");
			return InteractionResult.FAIL;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> decoration(player, level, entity));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> decoration(player, level, entity));
		ServerTickEvents.END_SERVER_TICK.register(SpawnRules::fatigue);
	}

	private static InteractionResult decoration(Player player, Level level, Entity entity) {
		if (!(player instanceof ServerPlayer sp) || exempt(sp)
			|| !(entity instanceof HangingEntity || entity instanceof ArmorStand || entity instanceof VehicleEntity)
			|| !SpawnGuard.inSpawn(level, entity.getX(), entity.getZ())) {
			return InteractionResult.PASS;
		}
		if (Plots.mayBuild(sp, level, entity.blockPosition())) return InteractionResult.PASS;
		deny(sp, "Spawn is protected.");
		return InteractionResult.FAIL;
	}

	private static boolean places(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof BlockItem || item instanceof HangingEntityItem || item instanceof ArmorStandItem
			|| item instanceof BucketItem;
	}

	/** Blocks anyone may use in spawn: containers, crafting stations, ender chests, doors, buttons. */
	private static boolean publicUse(BlockState state, Level level, BlockPos pos) {
		Block block = state.getBlock();
		if (block instanceof LecternBlock) return false;
		return block instanceof EnderChestBlock || block instanceof LeverBlock || block instanceof BellBlock
			|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.BUTTONS) || state.is(BlockTags.BEDS)
			|| state.getMenuProvider(level, pos) != null;
	}

	static boolean exempt(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static void deny(ServerPlayer player, String why) {
		player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.RED));
	}

	private static void fatigue(MinecraftServer server) {
		if (++ticks % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean wanted = !exempt(player) && SpawnGuard.inSpawn(player.level(), player.getX(), player.getZ())
				&& !Plots.standsInOwnPlot(player);
			if (wanted) {
				player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, FATIGUE_TICKS, FATIGUE_LEVEL, true, false, true));
				FATIGUED.add(player.getUUID());
			} else if (FATIGUED.remove(player.getUUID())) {
				MobEffectInstance current = player.getEffect(MobEffects.MINING_FATIGUE);
				if (current != null && current.getAmplifier() == FATIGUE_LEVEL && current.getDuration() <= FATIGUE_TICKS) {
					player.removeEffect(MobEffects.MINING_FATIGUE);
				}
			}
		}
	}
}
