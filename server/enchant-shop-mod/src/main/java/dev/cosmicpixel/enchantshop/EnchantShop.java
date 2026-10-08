package dev.cosmicpixel.enchantshop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Random;

public class EnchantShop implements ModInitializer {
	private static final Random RANDOM = new Random();

	private record Tier(int slot, String name, ChatFormatting color, Item icon, int cost, List<String> groups) {}

	// Groups are the folder names Enchant Plus uses for its rarity tiers; curses are never sold.
	private static final List<Tier> TIERS = List.of(
		new Tier(10, "Common", ChatFormatting.GREEN, Items.LAPIS_LAZULI, 5,
			List.of("0_common_enchants_item", "2_rare_enchants_item", "3_particle_enchants_item")),
		new Tier(12, "Epic", ChatFormatting.LIGHT_PURPLE, Items.AMETHYST_SHARD, 15,
			List.of("4_epic_enchants_item", "5_ultimate_enchants_item")),
		new Tier(14, "Legendary", ChatFormatting.GOLD, Items.GOLD_INGOT, 25,
			List.of("6_legendary_enchants_item", "7_mythical_enchants_item")),
		new Tier(16, "Exclusive", ChatFormatting.RED, Items.NETHER_STAR, 40,
			List.of("8_special_enchants_item", "9_exclusive_enchants_item")));

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			for (String name : List.of("enchants", "ce")) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopMenu(id, inventory),
						Component.literal("Custom Enchants")));
					return 1;
				}));
			}
		});
	}

	private static ItemStack icon(Tier tier) {
		ItemStack stack = new ItemStack(tier.icon());
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(tier.name() + " Enchant Book")
			.withStyle(tier.color(), ChatFormatting.BOLD).withStyle(style -> style.withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Cost: " + tier.cost() + " XP levels").withStyle(ChatFormatting.YELLOW),
			Component.literal("Gives a random " + tier.name().toLowerCase() + " enchantment book.").withStyle(ChatFormatting.GRAY),
			Component.literal("Apply it to gear in an anvil.").withStyle(ChatFormatting.GRAY),
			Component.literal("Click to buy").withStyle(ChatFormatting.GREEN))));
		return stack;
	}

	private static void buy(ServerPlayer player, Tier tier) {
		if (player.experienceLevel < tier.cost() && !player.isCreative()) {
			player.sendSystemMessage(Component.literal("You need " + tier.cost() + " XP levels for a "
				+ tier.name() + " book (you have " + player.experienceLevel + ").").withStyle(ChatFormatting.RED));
			return;
		}
		List<Holder.Reference<Enchantment>> pool = player.level().getServer().registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT).listElements()
			.filter(holder -> {
				String path = holder.key().identifier().toString();
				return tier.groups().stream().anyMatch(group -> path.startsWith("enchantplus:" + group + "/"));
			})
			.toList();
		if (pool.isEmpty()) {
			player.sendSystemMessage(Component.literal("No enchantments are available in this tier.").withStyle(ChatFormatting.RED));
			return;
		}
		Holder.Reference<Enchantment> enchantment = pool.get(RANDOM.nextInt(pool.size()));
		int level = 1 + RANDOM.nextInt(enchantment.value().getMaxLevel());
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(enchantment, level);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());

		if (!player.isCreative()) player.giveExperienceLevels(-tier.cost());
		if (!player.getInventory().add(book)) player.drop(book, false);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.sendSystemMessage(Component.literal("You got ").withStyle(ChatFormatting.GREEN)
			.append(Enchantment.getFullname(enchantment, level)).append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
	}

	private static class ShopMenu extends ChestMenu {
		ShopMenu(int id, Inventory inventory) {
			super(MenuType.GENERIC_9x3, id, inventory, filled(), 3);
		}

		private static SimpleContainer filled() {
			SimpleContainer container = new SimpleContainer(27);
			ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
			pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
			for (int i = 0; i < 27; i++) container.setItem(i, pane.copy());
			for (Tier tier : TIERS) container.setItem(tier.slot(), icon(tier));
			return container;
		}

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			if (player instanceof ServerPlayer serverPlayer) {
				for (Tier tier : TIERS) {
					if (tier.slot() == slot) buy(serverPlayer, tier);
				}
			}
			sendAllDataToRemote();
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
