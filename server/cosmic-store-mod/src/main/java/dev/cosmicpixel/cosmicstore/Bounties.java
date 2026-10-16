package dev.cosmicpixel.cosmicstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import savage.commoneconomy.EconomyManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DonutSMP-style bounties paid from the /bal wallet: /bounty <player> <amount> adds to a player's bounty and whoever
 * kills them collects all of it. /bounty lists the biggest ones, /bounty <player> shows one.
 */
public final class Bounties {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = CosmicStore.DIR.resolve("bounties.json");
	private static final BigDecimal MIN = new BigDecimal("100.00");
	private static final BigDecimal MAX = new BigDecimal("1000000.00");
	private static final Map<UUID, Bounty> bounties = new LinkedHashMap<>();

	private record Bounty(String name, BigDecimal amount) {}

	private Bounties() {}

	static void register() {
		load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("bounty")
				.executes(Bounties::top)
				.then(Commands.argument("player", EntityArgument.player())
					.executes(Bounties::show)
					.then(Commands.argument("amount", DoubleArgumentType.doubleArg(MIN.doubleValue(), MAX.doubleValue()))
						.executes(Bounties::place)))));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer killer && killer != victim) {
				claim(victim, killer);
			}
		});
	}

	private static String money(BigDecimal amount) {
		return "$" + String.format("%,.2f", amount).replace(".00", "");
	}

	private static int top(CommandContext<CommandSourceStack> ctx) {
		List<Map.Entry<UUID, Bounty>> list;
		synchronized (Bounties.class) {
			list = new ArrayList<>(bounties.entrySet());
		}
		if (list.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No bounties right now. Place one: /bounty <player> <amount>")
				.withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		list.sort(Comparator.comparing((Map.Entry<UUID, Bounty> e) -> e.getValue().amount()).reversed());
		var text = Component.literal("Top bounties").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (int i = 0; i < Math.min(10, list.size()); i++) {
			Bounty b = list.get(i).getValue();
			text.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(b.name()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" " + money(b.amount())).withStyle(ChatFormatting.GREEN));
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		Bounty b;
		synchronized (Bounties.class) {
			b = bounties.get(target.getUUID());
		}
		String name = target.getPlainTextName();
		ctx.getSource().sendSuccess(() -> b == null
			? Component.literal(name + " has no bounty.").withStyle(ChatFormatting.GRAY)
			: Component.literal(name + " has a bounty of ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(money(b.amount())).withStyle(ChatFormatting.GREEN)), false);
		return 1;
	}

	private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer payer = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		if (target == payer) {
			ctx.getSource().sendFailure(Component.literal("You can't put a bounty on yourself."));
			return 0;
		}
		BigDecimal amount = BigDecimal.valueOf(DoubleArgumentType.getDouble(ctx, "amount")).setScale(2, RoundingMode.HALF_UP);
		MinecraftServer server = ctx.getSource().getServer();
		UUID payerId = payer.getUUID(), targetId = target.getUUID();
		String targetName = target.getPlainTextName(), payerName = payer.getPlainTextName();
		EconomyManager.getInstance().removeBalance(payerId, amount).whenComplete((taken, error) -> server.execute(() -> {
			ServerPlayer online = server.getPlayerList().getPlayer(payerId);
			if (error != null || !Boolean.TRUE.equals(taken)) {
				if (online != null) online.sendSystemMessage(Component.literal("You don't have " + money(amount) + ".").withStyle(ChatFormatting.RED));
				return;
			}
			BigDecimal total;
			synchronized (Bounties.class) {
				Bounty old = bounties.get(targetId);
				total = (old == null ? BigDecimal.ZERO : old.amount()).add(amount);
				bounties.put(targetId, new Bounty(targetName, total));
				save();
			}
			CosmicStore.LOGGER.info("bounty {} +{} on {} -> {}", payerName, amount, targetName, total);
			server.getPlayerList().broadcastSystemMessage(Component.literal("[Bounty] ").withStyle(ChatFormatting.RED)
				.append(Component.literal(payerName + " added " + money(amount) + " to " + targetName + "'s bounty. Total: ")
					.withStyle(ChatFormatting.GOLD))
				.append(Component.literal(money(total)).withStyle(ChatFormatting.GREEN)), false);
		}));
		return 1;
	}

	private static void claim(ServerPlayer victim, ServerPlayer killer) {
		Bounty b;
		synchronized (Bounties.class) {
			b = bounties.remove(victim.getUUID());
			if (b == null) return;
			save();
		}
		MinecraftServer server = killer.level().getServer();
		UUID killerId = killer.getUUID();
		String killerName = killer.getPlainTextName(), victimName = victim.getPlainTextName();
		EconomyManager.getInstance().addBalance(killerId, b.amount()).whenComplete((paid, error) -> server.execute(() -> {
			if (error != null || !Boolean.TRUE.equals(paid)) {
				CosmicStore.LOGGER.error("bounty payout of {} to {} failed; restoring the bounty on {}", b.amount(), killerName, victimName, error);
				synchronized (Bounties.class) {
					bounties.merge(victim.getUUID(), b, (now, back) -> new Bounty(now.name(), now.amount().add(back.amount())));
					save();
				}
				return;
			}
			CosmicStore.LOGGER.info("bounty on {} ({}) claimed by {}", victimName, b.amount(), killerName);
			server.getPlayerList().broadcastSystemMessage(Component.literal("[Bounty] ").withStyle(ChatFormatting.RED)
				.append(Component.literal(killerName + " killed " + victimName + " and collected a ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(money(b.amount())).withStyle(ChatFormatting.GREEN))
				.append(Component.literal(" bounty!").withStyle(ChatFormatting.GOLD)), false);
		}));
	}

	private static synchronized void load() {
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			root.entrySet().forEach(e -> {
				JsonObject o = e.getValue().getAsJsonObject();
				bounties.put(UUID.fromString(e.getKey()), new Bounty(o.get("name").getAsString(), o.get("amount").getAsBigDecimal()));
			});
		} catch (Exception e) {
			CosmicStore.LOGGER.error("Could not read {}", FILE, e);
		}
	}

	private static synchronized void save() {
		JsonObject root = new JsonObject();
		bounties.forEach((uuid, b) -> {
			JsonObject o = new JsonObject();
			o.addProperty("name", b.name());
			o.addProperty("amount", b.amount());
			root.add(uuid.toString(), o);
		});
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("bounties.json.tmp");
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			CosmicStore.LOGGER.error("Could not save {}", FILE, e);
		}
	}
}
