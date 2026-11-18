package dev.xcosmicvapez.automine.client;

import dev.xcosmicvapez.automine.AutoMine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only AutoMine + casual auto-reply when whispered on a server.
 */
public class AutoMineClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(AutoMine.MOD_ID);

	private static KeyMapping toggleMineKey;
	private static KeyMapping toggleReplyKey;
	private static boolean miningEnabled;
	private static AutoMineConfig config;
	private static HumanizedMiner miner;
	private static AutoReply autoReply;

	@Override
	public void onInitializeClient() {
		config = AutoMineConfig.load();
		miner = new HumanizedMiner(config);
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

		ClientTickEvents.END_CLIENT_TICK.register(AutoMineClient::onClientTick);
		HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null || client.options.hideGui) {
				return;
			}
			Font font = client.font;
			int y = 6;
			if (miningEnabled) {
				drawLabel(graphics, font, "AutoMine ON", 6, y, 0xFF55FF55);
				y += 12;
			}
			if (autoReply.isEnabled()) {
				drawLabel(graphics, font, "AutoReply ON", 6, y, 0xFF55FFFF);
			}
		});

		LOGGER.info("AutoMine ready — H toggles mining, J toggles auto-reply");
	}

	private static void drawLabel(net.minecraft.client.gui.GuiGraphics graphics, Font font, String label, int x, int y, int color) {
		graphics.drawString(font, label, x + 1, y + 1, 0x40000000, false);
		graphics.drawString(font, label, x, y, color, false);
	}

	private static void onClientTick(Minecraft client) {
		while (toggleMineKey.consumeClick()) {
			miningEnabled = !miningEnabled;
			if (!miningEnabled) {
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

		autoReply.tick(client);

		if (miningEnabled) {
			miner.tick(client);
		}
	}

	public static boolean isMiningEnabled() {
		return miningEnabled;
	}
}
