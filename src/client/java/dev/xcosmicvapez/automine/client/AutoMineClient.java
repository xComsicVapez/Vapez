package dev.xcosmicvapez.automine.client;

import dev.xcosmicvapez.automine.AutoMine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only AutoMine with a Complex-like prison command profile for allowlisted servers.
 */
public class AutoMineClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(AutoMine.MOD_ID);

	private static KeyMapping toggleMineKey;
	private static KeyMapping toggleReplyKey;
	private static KeyMapping toggleCircleKey;
	private static KeyMapping forceSellKey;
	private static boolean miningEnabled;
	private static AutoMineConfig config;
	private static HumanizedMiner miner;
	private static AutoReply autoReply;
	private static PrisonHelpers prisonHelpers;
	private static MiningSession session;

	@Override
	public void onInitializeClient() {
		config = AutoMineConfig.load();
		session = new MiningSession(config);
		miner = new HumanizedMiner(config, session);
		prisonHelpers = new PrisonHelpers(config);
		autoReply = new AutoReply(config);
		autoReply.register();

		toggleMineKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.automine.toggle",
				GLFW.GLFW_KEY_H,
				"category.automine"
		));
		toggleReplyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.automine.toggle_reply",
				GLFW.GLFW_KEY_J,
				"category.automine"
		));
		toggleCircleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.automine.toggle_circle",
				GLFW.GLFW_KEY_K,
				"category.automine"
		));
		forceSellKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.automine.force_sell",
				GLFW.GLFW_KEY_L,
				"category.automine"
		));

		ClientTickEvents.END_CLIENT_TICK.register(AutoMineClient::onClientTick);
		HudRenderCallback.EVENT.register((graphics, tickDelta) -> renderHud(graphics));

		LOGGER.info("AutoMine ready (profile={}) — H mine, J reply, K circle, L sell", config.prisonProfile);
	}

	private static void renderHud(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		Font font = client.font;
		int y = 6;
		if (miningEnabled) {
			drawLabel(graphics, font, "AutoMine ON · " + config.prisonProfile, 6, y, 0xFF55FF55);
			y += 11;
			drawLabel(graphics, font, config.circleMine ? "Mode: Circle" : "Mode: Crosshair", 6, y, 0xFFAAAAAA);
			y += 11;
			drawLabel(graphics, font, session.hudLine(), 6, y, 0xFFDDDDDD);
			y += 11;
			String minerStatus = miner.status();
			String helperStatus = prisonHelpers.lastStatus();
			String detail = !helperStatus.isEmpty() ? helperStatus : minerStatus;
			if (detail != null && !detail.isEmpty()) {
				drawLabel(graphics, font, detail, 6, y, 0xFFFFFF55);
				y += 11;
			}
		}
		if (autoReply.isEnabled()) {
			drawLabel(graphics, font, "AutoReply ON", 6, y, 0xFF55FFFF);
		}
	}

	private static void drawLabel(GuiGraphics graphics, Font font, String label, int x, int y, int color) {
		graphics.drawString(font, label, x + 1, y + 1, 0x40000000, false);
		graphics.drawString(font, label, x, y, color, false);
	}

	private static void onClientTick(Minecraft client) {
		while (toggleMineKey.consumeClick()) {
			miningEnabled = !miningEnabled;
			if (miningEnabled) {
				session.reset();
				miner.reset();
				prisonHelpers.reset();
				prisonHelpers.onMiningStarted(client);
			} else {
				miner.reset();
			}
			if (client.player != null) {
				client.player.displayClientMessage(
						Component.literal(miningEnabled ? "AutoMine enabled" : "AutoMine disabled"),
						true
				);
			}
		}

		while (toggleReplyKey.consumeClick()) {
			boolean on = autoReply.toggle();
			if (client.player != null) {
				client.player.displayClientMessage(
						Component.literal(on ? "AutoReply enabled" : "AutoReply disabled"),
						true
				);
			}
		}

		while (toggleCircleKey.consumeClick()) {
			config.circleMine = !config.circleMine;
			config.save();
			miner.reset();
			if (client.player != null) {
				client.player.displayClientMessage(
						Component.literal(config.circleMine ? "Circle mine ON" : "Circle mine OFF (crosshair)"),
						true
				);
			}
		}

		while (forceSellKey.consumeClick()) {
			prisonHelpers.forceSell(client);
		}

		autoReply.tick(client);

		if (!miningEnabled) {
			return;
		}

		if (!session.tick(client)) {
			miningEnabled = false;
			miner.reset();
			return;
		}

		if (!prisonHelpers.allowMining(client)) {
			return;
		}

		miner.tick(client);
	}

	public static boolean isMiningEnabled() {
		return miningEnabled;
	}

	public static AutoMineConfig config() {
		return config;
	}
}
