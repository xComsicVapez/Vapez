package dev.cosmicpixel.cosmicstore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.commoneconomy.EconomyManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Server side of the web store (server/web_store.py). Players type /store for a one-time website login code. The
 * store talks to the server over RCON with the op-only "/cosmicstore" commands: charge and refund the Savs Common
 * Economy balance (the same money as /bal) and grant bonus hearts.
 */
public class CosmicStore implements ModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("cosmic-store");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("cosmic-store");
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("cosmic-store.json");
	private static final Path CODES = DIR.resolve("login-codes.json");
	private static final long CODE_SECONDS = 600;
	// No 0/O or 1/I so codes are easy to type from the chat.
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Map<String, JsonObject> codes = new HashMap<>();

	@Override
	public void onInitialize() {
		writeDefaultConfig();
		BonusHearts.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("store").executes(CosmicStore::loginCode));
			dispatcher.register(Commands.literal("cosmicstore")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
				.then(Commands.literal("charge").then(uuidArg().then(Commands.argument("amount", StringArgumentType.word())
					.executes(ctx -> money(ctx, true)))))
				.then(Commands.literal("refund").then(uuidArg().then(Commands.argument("amount", StringArgumentType.word())
					.executes(ctx -> money(ctx, false)))))
				.then(Commands.literal("balance").then(uuidArg().executes(CosmicStore::balance)))
				.then(Commands.literal("bonushearts")
					.then(Commands.literal("add").then(uuidArg().then(Commands.argument("hearts", IntegerArgumentType.integer(1, 10))
						.executes(ctx -> BonusHearts.add(ctx, IntegerArgumentType.getInteger(ctx, "hearts"))))))
					.then(Commands.literal("reset").then(uuidArg().executes(ctx -> BonusHearts.add(ctx, 0))))
					.then(Commands.literal("get").then(uuidArg().executes(BonusHearts::get)))));
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> uuidArg() {
		return Commands.argument("uuid", StringArgumentType.word());
	}

	static UUID uuid(CommandContext<CommandSourceStack> ctx) {
		try {
			return UUID.fromString(StringArgumentType.getString(ctx, "uuid"));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static JsonObject config() {
		try {
			return JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
		} catch (Exception e) {
			return new JsonObject();
		}
	}

	private static void writeDefaultConfig() {
		if (Files.exists(CONFIG)) return;
		JsonObject config = new JsonObject();
		config.addProperty("url", "http://localhost:8080");
		config.addProperty("maxBonusHearts", 5);
		try {
			Files.writeString(CONFIG, GSON.toJson(config));
		} catch (IOException e) {
			LOGGER.error("Could not write {}", CONFIG, e);
		}
	}

	// ------------------------------------------------------------------ website login

	private static int loginCode(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return 0;
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < 6; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		JsonObject entry = new JsonObject();
		entry.addProperty("uuid", player.getUUID().toString());
		entry.addProperty("name", player.getPlainTextName());
		entry.addProperty("expires", System.currentTimeMillis() / 1000 + CODE_SECONDS);
		synchronized (codes) {
			long now = System.currentTimeMillis() / 1000;
			Iterator<JsonObject> it = codes.values().iterator();
			while (it.hasNext()) {
				JsonObject old = it.next();
				if (old.get("expires").getAsLong() < now || old.get("uuid").getAsString().equals(entry.get("uuid").getAsString())) {
					it.remove();
				}
			}
			codes.put(code.toString(), entry);
			if (!saveCodes()) {
				ctx.getSource().sendFailure(Component.literal("The store is unavailable right now, try again later."));
				return 0;
			}
		}
		String url = config().has("url") ? config().get("url").getAsString() : null;
		MutableComponent text = Component.literal("Your web store login code: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(code.toString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal(" (valid 10 minutes, only works once)").withStyle(ChatFormatting.GRAY));
		if (url != null) {
			text.append(Component.literal("\nOpen " + url).withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static boolean saveCodes() {
		JsonObject root = new JsonObject();
		codes.forEach(root::add);
		try {
			Files.createDirectories(DIR);
			Path tmp = CODES.resolveSibling("login-codes.json.tmp");
			Files.writeString(tmp, GSON.toJson(root));
			Files.move(tmp, CODES, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not save login codes", e);
			return false;
		}
	}

	// ------------------------------------------------------------------ money (RCON only)

	private static int money(CommandContext<CommandSourceStack> ctx, boolean charge) {
		UUID player = uuid(ctx);
		BigDecimal amount;
		try {
			amount = new BigDecimal(StringArgumentType.getString(ctx, "amount")).setScale(2, RoundingMode.HALF_UP);
		} catch (NumberFormatException e) {
			amount = null;
		}
		if (player == null || amount == null || amount.signum() <= 0) {
			ctx.getSource().sendFailure(Component.literal("ERROR bad arguments"));
			return 0;
		}
		EconomyManager eco = EconomyManager.getInstance();
		try {
			// Savs does its storage work on its own IO thread, so waiting here cannot deadlock the server thread.
			Boolean done = (charge ? eco.removeBalance(player, amount) : eco.addBalance(player, amount)).get(10, TimeUnit.SECONDS);
			BigDecimal balance = eco.getBalance(player).get(10, TimeUnit.SECONDS);
			String result = (Boolean.TRUE.equals(done) ? "OK " : charge ? "NOFUNDS " : "ERROR ") + balance.toPlainString();
			LOGGER.info("store {} {} {} -> {}", charge ? "charge" : "refund", player, amount, result);
			ctx.getSource().sendSuccess(() -> Component.literal(result), false);
			return Boolean.TRUE.equals(done) ? 1 : 0;
		} catch (Exception e) {
			LOGGER.error("store {} failed for {}", charge ? "charge" : "refund", player, e);
			ctx.getSource().sendFailure(Component.literal("ERROR " + e.getClass().getSimpleName()));
			return 0;
		}
	}

	private static int balance(CommandContext<CommandSourceStack> ctx) {
		UUID player = uuid(ctx);
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("ERROR bad uuid"));
			return 0;
		}
		try {
			BigDecimal balance = EconomyManager.getInstance().getBalance(player).get(10, TimeUnit.SECONDS);
			ctx.getSource().sendSuccess(() -> Component.literal("BALANCE " + balance.toPlainString()), false);
			return 1;
		} catch (Exception e) {
			ctx.getSource().sendFailure(Component.literal("ERROR " + e.getClass().getSimpleName()));
			return 0;
		}
	}
}
