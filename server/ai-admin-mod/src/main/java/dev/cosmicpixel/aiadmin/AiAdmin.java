package dev.cosmicpixel.aiadmin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class AiAdmin implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ai-admin");
	private static final Gson GSON = new Gson();
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("ai-admin.json");
	private static final Path QUEUE = FabricLoader.getInstance().getGameDir().resolve("ai-admin").resolve("requests.jsonl");

	@Override
	public void onInitialize() {
		writeDefaultConfig();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("ai")
				.requires(AiAdmin::isOwner)
				.then(Commands.argument("request", StringArgumentType.greedyString())
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayer();
						String request = StringArgumentType.getString(ctx, "request");
						if (!queue(player, request)) {
							ctx.getSource().sendFailure(Component.literal("[AI] Could not queue the request; check the server log."));
							return 0;
						}
						ctx.getSource().sendSuccess(() -> Component.literal("[AI] Working on it...").withStyle(ChatFormatting.GRAY), false);
						return 1;
					}))));
	}

	// Owners are re-read on every check so edits to the config apply without a restart.
	private static boolean isOwner(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null && loadOwners().contains(player.getUUID().toString());
	}

	private static Set<String> loadOwners() {
		Set<String> owners = new HashSet<>();
		try {
			JsonObject config = GSON.fromJson(Files.readString(CONFIG), JsonObject.class);
			JsonArray list = config.getAsJsonArray("owners");
			if (list != null) list.forEach(e -> owners.add(e.getAsString().toLowerCase()));
		} catch (Exception e) {
			LOGGER.warn("Could not read {}: {}", CONFIG, e.getMessage());
		}
		return owners;
	}

	private static boolean queue(ServerPlayer player, String request) {
		JsonObject entry = new JsonObject();
		entry.addProperty("uuid", player.getUUID().toString());
		entry.addProperty("name", player.getName().getString());
		entry.addProperty("request", request);
		entry.addProperty("time", System.currentTimeMillis());
		try {
			Files.createDirectories(QUEUE.getParent());
			Files.writeString(QUEUE, GSON.toJson(entry) + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not write {}", QUEUE, e);
			return false;
		}
	}

	private static void writeDefaultConfig() {
		if (Files.exists(CONFIG)) return;
		try {
			Files.writeString(CONFIG, "{\n  \"owners\": []\n}\n");
		} catch (IOException e) {
			LOGGER.error("Could not create {}", CONFIG, e);
		}
	}
}
