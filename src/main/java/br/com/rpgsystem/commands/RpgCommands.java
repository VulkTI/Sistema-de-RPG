package br.com.rpgsystem.commands;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.RpgClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixRepository;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.progression.ProgressionMath;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class RpgCommands {
	private RpgCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("rpg")
				.then(Commands.literal("class")
						.then(Commands.literal("set")
								.then(Commands.argument("class", StringArgumentType.word())
										.suggests((context, builder) -> suggestClasses(builder))
										.executes(RpgCommands::setClass)))
						.then(Commands.literal("reset").executes(RpgCommands::resetClass)))
				.then(Commands.literal("menu").executes(context -> openMenu(context.getSource().getPlayerOrException())))
				.then(Commands.literal("xp").requires(RpgCommands::isModerator)
						.then(Commands.literal("player")
								.then(xpAction("add", false, ProgressionMath.Change.ADD))
								.then(xpAction("remove", false, ProgressionMath.Change.REMOVE))
								.then(xpAction("set", false, ProgressionMath.Change.SET)))
						.then(Commands.literal("class")
								.then(xpAction("add", true, ProgressionMath.Change.ADD))
								.then(xpAction("remove", true, ProgressionMath.Change.REMOVE))
								.then(xpAction("set", true, ProgressionMath.Change.SET))))
				.then(Commands.literal("reset").requires(RpgCommands::isModerator)
						.then(Commands.argument("player", EntityArgument.players()).executes(RpgCommands::resetPlayer)))
				.then(Commands.literal("affix")
						.then(Commands.literal("roll").executes(RpgCommands::rollAffix))
						.then(Commands.literal("apply")
								.then(Commands.argument("affix", StringArgumentType.word())
										.suggests(RpgCommands::suggestAffixes)
										.executes(RpgCommands::applyAffix)))
						.then(Commands.literal("remove").executes(RpgCommands::removeAffix))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> xpAction(
			String name, boolean classXp, ProgressionMath.Change change) {
		return Commands.literal(name)
				.then(xpTargets(classXp, change))
				.then(xpQuantities(classXp, change, false));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> xpTargets(
			boolean classXp, ProgressionMath.Change change) {
		return Commands.argument("target", EntityArgument.players()).then(xpQuantities(classXp, change, true));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> xpQuantities(
			boolean classXp, ProgressionMath.Change change, boolean hasTarget) {
		return Commands.argument("quantity", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
				.then(Commands.literal("points").executes(context -> applyXp(context, classXp, change, ProgressionMath.Unit.POINTS, hasTarget)))
				.then(Commands.literal("levels").executes(context -> applyXp(context, classXp, change, ProgressionMath.Unit.LEVELS, hasTarget)));
	}

	private static int setClass(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String requested = StringArgumentType.getString(context, "class");
		RpgClass rpgClass = ClassRegistry.byCommandName(requested).orElse(null);
		if (rpgClass == null) {
			context.getSource().sendFailure(Component.literal("Classe desconhecida: " + requested));
			return 0;
		}
		ServerPlayer player = context.getSource().getPlayerOrException();
		RpgSavedData.get(player.level().getServer()).update(player.getUUID(), profile -> profile.withClass(rpgClass.id().toString()));
		context.getSource().sendSuccess(() -> Component.literal("Sua classe agora é " + rpgClass.name() + "."), false);
		RpgNetworking.sendSnapshot(player, false);
		return 1;
	}

	private static int resetClass(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		RpgSavedData.get(player.level().getServer()).update(player.getUUID(), RpgSavedData.Profile::withoutClass);
		context.getSource().sendSuccess(() -> Component.literal("Sua classe foi removida."), false);
		RpgNetworking.sendSnapshot(player, false);
		return 1;
	}

	private static int openMenu(ServerPlayer player) {
		RpgNetworking.sendSnapshot(player, true);
		return 1;
	}

	private static int resetPlayer(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
		for (ServerPlayer target : targets) {
			RpgSavedData.get(target.level().getServer()).update(target.getUUID(), RpgSavedData.Profile::reset);
			RpgNetworking.sendSnapshot(target, true);
		}
		context.getSource().sendSuccess(() -> Component.literal("Dados RPG resetados para " + targets.size() + " jogador(es)."), true);
		return targets.size();
	}

	private static int applyXp(CommandContext<CommandSourceStack> context, boolean classXp,
			ProgressionMath.Change change, ProgressionMath.Unit unit, boolean hasTarget)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int quantity = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "quantity");
		Collection<ServerPlayer> targets = hasTarget
				? EntityArgument.getPlayers(context, "target")
				: List.of(context.getSource().getPlayerOrException());
		int changed = 0;
		for (ServerPlayer target : targets) {
			RpgSavedData data = RpgSavedData.get(target.level().getServer());
			RpgSavedData.Profile profile = data.profile(target.getUUID());
			if (classXp && profile.selectedClassId().isBlank()) {
				context.getSource().sendFailure(Component.literal(target.getScoreboardName() + " não tem uma classe."));
				continue;
			}
			if (classXp) {
				String classId = profile.selectedClassId();
				long newXp = ProgressionMath.change(profile.xpForClass(classId), quantity, change, unit);
				data.update(target.getUUID(), current -> current.withClassXp(classId, newXp));
			} else {
				long newXp = ProgressionMath.change(profile.playerXp(), quantity, change, unit);
				data.update(target.getUUID(), current -> current.withPlayerXp(newXp));
			}
			RpgNetworking.sendSnapshot(target, false);
			changed++;
		}
		int changedCount = changed;
		context.getSource().sendSuccess(() -> Component.literal("XP " + (classXp ? "da classe" : "do jogador")
				+ " atualizado para " + changedCount + " jogador(es)."), true);
		return changed;
	}

	private static int rollAffix(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getMainHandItem();
		AffixDefinition definition = AffixService.rollFor(stack, player).orElse(null);
		if (definition == null || !AffixService.apply(stack, definition)) {
			context.getSource().sendFailure(Component.literal(
					"Não há afixos compatíveis com o item na mão principal."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Afixo rolado: " + definition.name()
				+ " (" + definition.rarity().displayName() + ")."), false);
		return 1;
	}

	private static int applyAffix(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String requested = StringArgumentType.getString(context, "affix");
		AffixDefinition definition = AffixRepository.byPath(requested).orElse(null);
		if (definition == null) {
			context.getSource().sendFailure(Component.literal("Afixo desconhecido: " + requested));
			return 0;
		}
		if (!AffixService.apply(player.getMainHandItem(), definition)) {
			context.getSource().sendFailure(Component.literal("Esse afixo não é compatível com o item na mão."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Afixo aplicado: " + definition.name() + "."), false);
		return 1;
	}

	private static int removeAffix(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (!AffixService.remove(player.getMainHandItem())) {
			context.getSource().sendFailure(Component.literal("O item na mão não possui afixo."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Afixo removido."), false);
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestAffixes(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
		for (AffixDefinition definition : AffixRepository.all()) {
			String path = definition.id().getNamespace().equals("rpgsystem")
					? definition.id().getPath() : definition.id().toString();
			if (path.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) builder.suggest(path);
		}
		return builder.buildFuture();
	}

	private static boolean isModerator(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
	}

	private static CompletableFuture<Suggestions> suggestClasses(SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
		for (RpgClass rpgClass : ClassRegistry.all()) {
			if (rpgClass.commandName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
				builder.suggest(rpgClass.commandName());
			}
		}
		return builder.buildFuture();
	}
}
