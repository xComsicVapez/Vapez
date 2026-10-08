package dev.cosmicpixel.aliases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Bukkit-style shortcuts for Essential Commands' homes, which only understands
 * /home set|tp|delete|list. Each alias re-runs the real command as the player,
 * so Essential Commands still applies its own permissions and home limit.
 */
public class CommandAliases implements ModInitializer {
	private static final String DEFAULT_HOME = "home";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		named(dispatcher, "sethome", "home set ");
		named(dispatcher, "delhome", "home delete ");
		dispatcher.register(Commands.literal("homes")
			.executes(ctx -> run(ctx, "home list")));
		// Merges into Essential Commands' /home node; its set/tp/delete/list literals still take priority.
		dispatcher.register(Commands.literal("home")
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> run(ctx, "home tp " + StringArgumentType.getString(ctx, "name")))));
	}

	private static void named(CommandDispatcher<CommandSourceStack> dispatcher, String alias, String target) {
		dispatcher.register(Commands.literal(alias)
			.executes(ctx -> run(ctx, target + DEFAULT_HOME))
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> run(ctx, target + StringArgumentType.getString(ctx, "name")))));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, String command) {
		CommandSourceStack source = ctx.getSource();
		source.getServer().getCommands().performPrefixedCommand(source, command);
		return 1;
	}
}
