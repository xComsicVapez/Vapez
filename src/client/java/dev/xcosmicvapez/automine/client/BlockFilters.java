package dev.xcosmicvapez.automine.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class BlockFilters {
	private final Set<String> whitelist;
	private final Set<String> blacklist;

	BlockFilters(AutoMineConfig config) {
		this.whitelist = toSet(config.blockWhitelist);
		this.blacklist = toSet(config.blockBlacklist);
	}

	boolean allows(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		String id = idOf(state.getBlock());
		if (blacklist.contains(id)) {
			return false;
		}
		if (whitelist.isEmpty()) {
			return true;
		}
		return whitelist.contains(id);
	}

	private static String idOf(Block block) {
		ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
		return key.toString().toLowerCase(Locale.ROOT);
	}

	private static Set<String> toSet(java.util.List<String> list) {
		Set<String> set = new HashSet<>();
		if (list == null) {
			return set;
		}
		for (String entry : list) {
			if (entry != null && !entry.isBlank()) {
				set.add(entry.trim().toLowerCase(Locale.ROOT));
			}
		}
		return set;
	}
}
