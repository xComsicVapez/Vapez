package dev.xcosmicvapez.automine.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When someone whispers / privately messages you on a server, queue a delayed
 * casual reply so you don't look completely AFK.
 */
public final class AutoReply {
	// Vanilla-ish whisper formats + common plugin styles.
	private static final Pattern[] WHISPER_PATTERNS = {
			Pattern.compile("^(?<name>[\\w.]{1,32}) whispers(?: to you)?:\\s*(?<msg>.+)$", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^From (?<name>[\\w.]{1,32}):\\s*(?<msg>.+)$", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\[(?<name>[\\w.]{1,32}) -> me\\]\\s*(?<msg>.+)$", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^\\[(?<name>[\\w.]{1,32}) -> You\\]\\s*(?<msg>.+)$", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^(?<name>[\\w.]{1,32}) -> you:\\s*(?<msg>.+)$", Pattern.CASE_INSENSITIVE),
	};

	private final AutoMineConfig config;
	private boolean enabled;
	private PendingReply pending;
	private final Map<String, Long> lastReplyTickByPlayer = new ConcurrentHashMap<>();

	public AutoReply(AutoMineConfig config) {
		this.config = config;
		this.enabled = config.autoReplyEnabledByDefault;
	}

	public void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			onChat(message.getString(), sender != null ? sender.getName() : null);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onChat(message.getString(), null);
			}
		});
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean toggle() {
		enabled = !enabled;
		return enabled;
	}

	public void tick(Minecraft client) {
		if (!enabled || pending == null || client.player == null) {
			return;
		}
		if (client.player.tickCount < pending.sendAtTick) {
			return;
		}
		PendingReply toSend = pending;
		pending = null;

		String self = client.player.getGameProfile().getName();
		if (toSend.fromName.equalsIgnoreCase(self)) {
			return;
		}

		// Prefer a private reply when we know the name; otherwise say it in chat.
		String outbound = "/msg " + toSend.fromName + " " + toSend.text;
		sendChat(client, outbound);
		lastReplyTickByPlayer.put(toSend.fromName.toLowerCase(Locale.ROOT), (long) client.player.tickCount);
	}

	private void onChat(String raw, String senderName) {
		if (!enabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.getConnection() == null) {
			return;
		}
		// Singleplayer integrated / no remote server — skip.
		if (client.getCurrentServer() == null && client.isLocalServer()) {
			// Still allow on open-to-lan; only skip pure solo when no server entry.
			if (client.getSingleplayerServer() != null && !client.getSingleplayerServer().isPublished()) {
				return;
			}
		}

		String stripped = stripFormatting(raw).trim();
		if (stripped.isEmpty()) {
			return;
		}

		String from = senderName;
		String body = stripped;

		boolean whisper = false;
		for (Pattern pattern : WHISPER_PATTERNS) {
			Matcher matcher = pattern.matcher(stripped);
			if (matcher.matches()) {
				from = matcher.group("name");
				body = matcher.group("msg");
				whisper = true;
				break;
			}
		}

		if (config.whispersOnly && !whisper) {
			return;
		}
		if (from == null || from.isBlank()) {
			return;
		}
		if (from.equalsIgnoreCase(player.getGameProfile().getName())) {
			return;
		}

		long now = player.tickCount;
		Long last = lastReplyTickByPlayer.get(from.toLowerCase(Locale.ROOT));
		if (last != null && now - last < config.replyCooldownTicks) {
			return;
		}
		if (pending != null) {
			return;
		}

		ThreadLocalRandom rng = ThreadLocalRandom.current();
		int delay = rng.nextInt(config.replyDelayTicksMin, config.replyDelayTicksMax + 1);
		String reply = pickReply(rng);
		pending = new PendingReply(from, reply, (int) (now + delay));

		player.displayClientMessage(
				Component.literal("AutoReply → " + from + " in ~" + (delay / 20.0) + "s"),
				true
		);
	}

	private String pickReply(ThreadLocalRandom rng) {
		var list = config.replyMessages;
		if (list == null || list.isEmpty()) {
			return "hey sorry afk mining";
		}
		return list.get(rng.nextInt(list.size()));
	}

	private static void sendChat(Minecraft client, String message) {
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		if (message.startsWith("/")) {
			client.player.connection.sendCommand(message.substring(1));
		} else {
			client.player.connection.sendChat(message);
		}
	}

	private static String stripFormatting(String input) {
		StringBuilder out = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '§' && i + 1 < input.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	private record PendingReply(String fromName, String text, int sendAtTick) {
	}
}
