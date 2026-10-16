package dev.cosmicpixel.cosmicstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Extra hearts won from web store purchases. They sit on top of the lifesteal mod's hearts as a separate max-health
 * modifier, so they can go past the lifesteal heart cap and the lifesteal mod never changes them. They are reset
 * when the store sees the player get eliminated.
 */
public final class BonusHearts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = CosmicStore.DIR.resolve("bonus-hearts.json");
	private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("cosmicstore", "bonus_hearts");
	private static final Map<UUID, Integer> hearts = new HashMap<>();

	private BonusHearts() {}

	static void register() {
		load();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> apply(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> apply(player));
	}

	static synchronized int of(UUID player) {
		return hearts.getOrDefault(player, 0);
	}

	static void apply(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) return;
		int bonus = of(player.getUUID());
		attribute.removeModifier(MODIFIER);
		if (bonus > 0) {
			attribute.addPermanentModifier(new AttributeModifier(MODIFIER, bonus * 2.0, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	/** hearts > 0 adds that many (up to the cap); 0 resets to none. Prints "OK <total>" or "MAX <total>". */
	static int add(CommandContext<CommandSourceStack> ctx, int amount) {
		UUID player = CosmicStore.uuid(ctx);
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("ERROR bad uuid"));
			return 0;
		}
		JsonObject config = CosmicStore.config();
		int max = config.has("maxBonusHearts") ? config.get("maxBonusHearts").getAsInt() : 5;
		int before, after;
		synchronized (BonusHearts.class) {
			before = of(player);
			after = amount == 0 ? 0 : Math.min(max, before + amount);
			if (after == 0) hearts.remove(player); else hearts.put(player, after);
			if (!save()) {
				ctx.getSource().sendFailure(Component.literal("ERROR could not save"));
				return 0;
			}
		}
		ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			apply(online);
			if (after > before) {
				online.heal(2.0f * (after - before));
				online.sendSystemMessage(Component.literal("You gained " + (after - before) + " bonus heart"
					+ (after - before > 1 ? "s" : "") + " from the web store! (" + after + " bonus in total)").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		String result = (amount > 0 && after == before ? "MAX " : "OK ") + after;
		CosmicStore.LOGGER.info("bonus hearts {} {} -> {}", player, amount, result);
		ctx.getSource().sendSuccess(() -> Component.literal(result), false);
		return 1;
	}

	static int get(CommandContext<CommandSourceStack> ctx) {
		UUID player = CosmicStore.uuid(ctx);
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("ERROR bad uuid"));
			return 0;
		}
		int bonus = of(player);
		ctx.getSource().sendSuccess(() -> Component.literal("BONUS " + bonus), false);
		return 1;
	}

	private static synchronized void load() {
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			root.entrySet().forEach(e -> hearts.put(UUID.fromString(e.getKey()), e.getValue().getAsInt()));
		} catch (Exception e) {
			CosmicStore.LOGGER.error("Could not read {}", FILE, e);
		}
	}

	private static synchronized boolean save() {
		JsonObject root = new JsonObject();
		hearts.forEach((uuid, n) -> root.addProperty(uuid.toString(), n));
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("bonus-hearts.json.tmp");
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (IOException e) {
			CosmicStore.LOGGER.error("Could not save {}", FILE, e);
			return false;
		}
	}
}
