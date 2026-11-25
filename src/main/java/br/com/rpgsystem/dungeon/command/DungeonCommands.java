package br.com.rpgsystem.dungeon.command;

import br.com.rpgsystem.dungeon.data.DungeonDefinition;
import br.com.rpgsystem.dungeon.data.DungeonRepository;
import br.com.rpgsystem.dungeon.data.WaveData;
import br.com.rpgsystem.dungeon.data.MobSpawnData;
import br.com.rpgsystem.dungeon.data.ChestSpawnData;
import br.com.rpgsystem.dungeon.editor.DungeonEditorSession;
import br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager;
import br.com.rpgsystem.dungeon.network.DungeonNetworking;
import br.com.rpgsystem.dungeon.world.DungeonWorldManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DungeonCommands {
	private DungeonCommands() {}

	public static LiteralArgumentBuilder<CommandSourceStack> register(CommandBuildContext registryAccess) {
		return Commands.literal("dungeon")
				// /rpg dungeon create <nome_dungeon> [nívelMÍN] [nívelMÁX] [peso]
				.then(Commands.literal("create")
						.then(Commands.argument("nome_dungeon", StringArgumentType.word())
								.executes(ctx -> createDungeon(ctx, 1, 1, 1))
								.then(Commands.argument("nívelMÍN", IntegerArgumentType.integer(1, 100))
										.executes(ctx -> createDungeon(ctx,
												IntegerArgumentType.getInteger(ctx, "nívelMÍN"),
												IntegerArgumentType.getInteger(ctx, "nívelMÍN"), 1))
										.then(Commands.argument("nívelMÁX", IntegerArgumentType.integer(1, 100))
												.executes(ctx -> createDungeon(ctx,
														IntegerArgumentType.getInteger(ctx, "nívelMÍN"),
														IntegerArgumentType.getInteger(ctx, "nívelMÁX"), 1))
												.then(Commands.argument("peso", IntegerArgumentType.integer(1, 1000))
														.executes(ctx -> createDungeon(ctx,
																IntegerArgumentType.getInteger(ctx, "nívelMÍN"),
																IntegerArgumentType.getInteger(ctx, "nívelMÁX"),
																IntegerArgumentType.getInteger(ctx, "peso"))))))))

				// /rpg dungeon editor <nome_dungeon>
				.then(Commands.literal("editor")
						.then(Commands.argument("nome_dungeon", StringArgumentType.word())
								.suggests(DungeonCommands::suggestDungeons)
								.executes(DungeonCommands::openEditor)))

				// /rpg dungeon config
				.then(Commands.literal("config").executes(DungeonCommands::openStructureConfig))

				// /rpg dungeon editor_save confirm/cancel
				.then(Commands.literal("editor_save")
						.then(Commands.literal("confirm").executes(DungeonCommands::confirmEditorSave))
						.then(Commands.literal("cancel").executes(DungeonCommands::cancelEditorSave)))

				// /rpg dungeon spawn elective/here [nome_dungeon]
				.then(Commands.literal("spawn")
						.then(Commands.literal("elective")
								.executes(ctx -> spawnElective(ctx, null, null))
								.then(Commands.argument("nome_dungeon", StringArgumentType.word())
										.suggests(DungeonCommands::suggestActiveDungeons)
										.executes(ctx -> spawnElective(ctx, StringArgumentType.getString(ctx, "nome_dungeon"), null))
										.then(Commands.argument("nivel", IntegerArgumentType.integer(1))
												.executes(ctx -> spawnElective(ctx, StringArgumentType.getString(ctx, "nome_dungeon"), IntegerArgumentType.getInteger(ctx, "nivel"))))))
						.then(Commands.literal("here")
								.executes(ctx -> spawnHere(ctx, null, null))
								.then(Commands.argument("nome_dungeon", StringArgumentType.word())
										.suggests(DungeonCommands::suggestActiveDungeons)
										.executes(ctx -> spawnHere(ctx, StringArgumentType.getString(ctx, "nome_dungeon"), null))
										.then(Commands.argument("nivel", IntegerArgumentType.integer(1))
												.executes(ctx -> spawnHere(ctx, StringArgumentType.getString(ctx, "nome_dungeon"), IntegerArgumentType.getInteger(ctx, "nivel")))))))

				// /rpg dungeon activate <nome_dungeon>
				.then(Commands.literal("activate")
						.then(Commands.argument("nome_dungeon", StringArgumentType.word())
								.suggests(DungeonCommands::suggestDungeons)
								.executes(DungeonCommands::activateDungeon)))

				// /rpg dungeon deactivate <nome_dungeon>
				.then(Commands.literal("deactivate")
						.then(Commands.argument("nome_dungeon", StringArgumentType.word())
								.suggests(DungeonCommands::suggestDungeons)
								.executes(DungeonCommands::deactivateDungeon)))

				// /rpg dungeon delete <nome_dungeon> (apenas locais)
				.then(Commands.literal("delete")
						.then(Commands.argument("nome_dungeon", StringArgumentType.word())
								.suggests(DungeonCommands::suggestLocalDungeons)
								.executes(DungeonCommands::deleteDungeon)))

				// Suporte retrocompatível para /rpg dungeon <nome_dungeon> activate/deactivate
				.then(Commands.argument("nome_dungeon", StringArgumentType.word())
						.suggests(DungeonCommands::suggestDungeons)
						.then(Commands.literal("activate").executes(DungeonCommands::activateDungeon))
						.then(Commands.literal("deactivate").executes(DungeonCommands::deactivateDungeon)));
	}

	private static int createDungeon(CommandContext<CommandSourceStack> ctx, int minLevel, int maxLevel, int weight) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "nome_dungeon");

		if (maxLevel < minLevel) {
			source.sendFailure(Component.literal("§cO nívelMÁX deve ser maior ou igual ao nívelMÍN!"));
			return 0;
		}

		if (DungeonRepository.exists(name)) {
			source.sendFailure(Component.literal("§cJá existe uma dungeon com o nome '" + name + "'!"));
			return 0;
		}

		DungeonRepository.create(name, minLevel, maxLevel, weight, source.getServer());

		source.sendSuccess(() -> Component.literal("§aDungeon '§e" + name + "§a' criada com sucesso! (Nível: " + minLevel + "-" + maxLevel + ", Peso: " + weight + ")"), false);

		// Opção: "Você quer editar essa dungeon agora? [EDITAR]"
		MutableComponent prompt = Component.literal("§fVocê quer editar essa dungeon agora? ");
		MutableComponent editBtn = Component.literal("§a§n[EDITAR]")
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand("/rpg dungeon editor " + name))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Clique para abrir o editor desta dungeon"))));

		source.sendSuccess(() -> prompt.append(editBtn), false);
		return 1;
	}

	private static int openEditor(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player;
		try {
			player = ctx.getSource().getPlayerOrException();
		} catch (Exception e) {
			ctx.getSource().sendFailure(Component.literal("§cApenas jogadores podem abrir o editor de dungeons."));
			return 0;
		}

		String name = StringArgumentType.getString(ctx, "nome_dungeon");
		Optional<DungeonDefinition> opt = DungeonRepository.get(name);
		if (opt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("§cDungeon '" + name + "' não encontrada."));
			return 0;
		}

		DungeonEditorSessionManager.startSession(player, opt.get());
		return 1;
	}

	private static int openStructureConfig(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player;
		try {
			player = ctx.getSource().getPlayerOrException();
		} catch (Exception e) {
			return 0;
		}

		if (!DungeonEditorSessionManager.isInEditorSession(player.getUUID())) {
			ctx.getSource().sendFailure(Component.literal("§cVocê precisa estar em uma sessão de edição de dungeon."));
			return 0;
		}

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session != null && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			ServerLevel level = (ServerLevel) player.level();
			for (BlockPos pos : session.getPlacedMarkers().keySet()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				session.getPlacedBlocks().remove(pos);
			}
			session.getPlacedMarkers().clear();
			session.getUndoRedo().clear();
			session.setState(DungeonEditorSession.SessionState.FREE_BUILD);
			player.getInventory().clearContent();
		}

		DungeonNetworking.openStructureConfigScreen(player);
		return 1;
	}

	private static int confirmEditorSave(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player;
		try {
			player = ctx.getSource().getPlayerOrException();
		} catch (Exception e) {
			return 0;
		}

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return 0;

		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return 0;

		// Converte os markers colocados no mundo para dados definitivos na dungeon
		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			int idx = session.getEditingWaveIndex();
			if (session.getPlayerSpawnPos() != null) {
				def.getDungeonStructure().setPlayerSpawnPos(session.getPlayerSpawnPos());
				def.getDungeonStructure().setPlayerSpawnYaw(session.getPlayerSpawnYaw());
			}

			WaveData wave = null;
			if (idx >= 0 && idx < def.getDungeonStructure().getWaves().size()) {
				wave = def.getDungeonStructure().getWaves().get(idx);
			}
			if (wave != null) {
				wave.getMobs().clear();
				int currentWaveOrder = wave.getOrder();
				def.getDungeonStructure().getChests().removeIf(c -> c.getWaveOrder() == currentWaveOrder);

				for (Map.Entry<BlockPos, Object> entry : session.getPlacedMarkers().entrySet()) {
					BlockPos markerWorldPos = entry.getKey();
					Object obj = entry.getValue();
					if (obj instanceof MobSpawnData mob) {
						mob.setRelativePos(markerWorldPos);
						wave.addMob(mob);
					} else if (obj instanceof ChestSpawnData chest) {
						chest.setRelativePos(markerWorldPos);
						chest.setWaveOrder(currentWaveOrder);
						def.getDungeonStructure().getChests().add(chest);
					}
				}
			}
		}

		// Remove marcadores de onda e overlays da visão do jogador, mantendo os blocos de portal no mundo
		ServerLevel level = (ServerLevel) player.level();
		List<BlockPos> toRemove = new ArrayList<>();
		for (BlockPos pos : session.getPlacedMarkers().keySet()) {
			var state = level.getBlockState(pos);
			if (state.getBlock() instanceof br.com.rpgsystem.dungeon.block.WhitePortalBlock
					|| state.getBlock() instanceof br.com.rpgsystem.dungeon.block.BluePortalBlock) {
				continue; // Preserva portais no mundo
			}
			toRemove.add(pos);
		}
		for (BlockPos pos : toRemove) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			session.getPlacedBlocks().remove(pos);
			session.getPlacedMarkers().remove(pos);
		}
		session.getActiveOverlayBlocks().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
		session.getActiveOverlayBlocks().clear();
		session.getPlacedMarkers().clear();
		session.getUndoRedo().clear();
		session.setTempStructureConfig(null);

		DungeonRepository.saveWorldDungeons(player.level().getServer());

		// Retira as ferramentas e restaura os itens do jogador
		player.getInventory().clearContent();
		session.setState(DungeonEditorSession.SessionState.FREE_BUILD);
		player.sendSystemMessage(Component.literal("§aAlterações salvas com sucesso!"));

		// Abre a tela de configuração de estrutura
		DungeonNetworking.openStructureConfigScreen(player);
		return 1;
	}

	private static int cancelEditorSave(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player;
		try {
			player = ctx.getSource().getPlayerOrException();
		} catch (Exception e) {
			return 0;
		}

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return 0;

		ServerLevel level = (ServerLevel) player.level();

		// Se estava editando portal, desfaz as alterações de portais feitas nesta sessão
		if (session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			while (session.getUndoRedo().canUndo()) {
				var action = session.getUndoRedo().popUndo();
				if (action != null) {
					for (BlockPos p : action.positions()) {
						var restore = action.previousBlockStates() != null && action.previousBlockStates().containsKey(p)
								? action.previousBlockStates().get(p)
								: action.previousState();
						level.setBlock(p, restore, 3);
						session.getPlacedBlocks().remove(p);
						session.getPlacedMarkers().remove(p);
					}
				}
			}
		} else {
			// Se estava editando onda, limpa os blocos marcadores colocados
			for (BlockPos pos : session.getPlacedMarkers().keySet()) {
				var state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof br.com.rpgsystem.dungeon.block.WhitePortalBlock
						|| state.getBlock() instanceof br.com.rpgsystem.dungeon.block.BluePortalBlock)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					session.getPlacedBlocks().remove(pos);
				}
			}
			session.getPlacedMarkers().clear();
		}

		session.getActiveOverlayBlocks().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
		session.getActiveOverlayBlocks().clear();
		session.getUndoRedo().clear();
		session.setTempStructureConfig(null);

		player.getInventory().clearContent();
		session.setState(DungeonEditorSession.SessionState.FREE_BUILD);
		player.sendSystemMessage(Component.literal("§cEdição cancelada e alterações descartadas."));

		DungeonNetworking.openStructureConfigScreen(player);
		return 1;
	}

	private static int activateDungeon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "nome_dungeon");
		Optional<DungeonDefinition> opt = DungeonRepository.get(name);
		if (opt.isEmpty()) {
			source.sendFailure(Component.literal("§cDungeon '" + name + "' não encontrada."));
			return 0;
		}

		DungeonDefinition def = opt.get();
		List<String> errors = def.validateForActivation();
		if (!errors.isEmpty()) {
			source.sendFailure(Component.literal("§cNão foi possível ativar a dungeon '" + name + "':"));
			for (String err : errors) {
				source.sendFailure(Component.literal("§c - " + err));
			}
			return 0;
		}

		DungeonRepository.activate(name, source.getServer());
		source.sendSuccess(() -> Component.literal("§aDungeon '§e" + name + "§a' ativada com sucesso! Ela poderá aparecer na geração e spawn do mundo."), true);
		return 1;
	}

	private static int deactivateDungeon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "nome_dungeon");
		if (!DungeonRepository.exists(name)) {
			source.sendFailure(Component.literal("§cDungeon '" + name + "' não encontrada."));
			return 0;
		}

		DungeonRepository.deactivate(name, source.getServer());
		source.sendSuccess(() -> Component.literal("§eDungeon '" + name + "' foi desativada."), true);
		return 1;
	}

	private static int spawnElective(CommandContext<CommandSourceStack> ctx, String dungeonName, Integer forcedLevel) {
		CommandSourceStack source = ctx.getSource();
		DungeonDefinition target = null;
		if (dungeonName != null) {
			target = DungeonRepository.get(dungeonName).orElse(null);
			if (target == null) {
				source.sendFailure(Component.literal("§cDungeon '" + dungeonName + "' não encontrada."));
				return 0;
			}
		} else {
			List<DungeonDefinition> active = DungeonRepository.all().stream().filter(DungeonDefinition::isActive).toList();
			if (active.isEmpty()) {
				source.sendFailure(Component.literal("§cNenhuma dungeon ativa disponível para spawnar."));
				return 0;
			}
			target = active.get(source.getServer().overworld().getRandom().nextInt(active.size()));
		}

		int min = target.getMinLevel();
		int max = Math.max(min, target.getMaxLevel());
		int level = (forcedLevel != null && forcedLevel > 0)
				? forcedLevel
				: (min == max ? min : min + source.getServer().overworld().getRandom().nextInt(max - min + 1));

		boolean success = DungeonWorldManager.spawnDungeonElective(source.getServer(), target, level);
		if (success) {
			DungeonDefinition finalTarget = target;
			source.sendSuccess(() -> Component.literal("§aDungeon '" + finalTarget.getName() + "' spawnada com sucesso (eletiva, nível " + level + ")!"), true);
			return 1;
		} else {
			source.sendFailure(Component.literal("§cNão foi possível encontrar uma coordenada compatível para spawnar a dungeon."));
			return 0;
		}
	}

	private static int spawnHere(CommandContext<CommandSourceStack> ctx, String dungeonName, Integer forcedLevel) {
		ServerPlayer player;
		try {
			player = ctx.getSource().getPlayerOrException();
		} catch (Exception e) {
			ctx.getSource().sendFailure(Component.literal("§cApenas jogadores podem usar 'spawn here'."));
			return 0;
		}

		DungeonDefinition target = null;
		if (dungeonName != null) {
			target = DungeonRepository.get(dungeonName).orElse(null);
			if (target == null) {
				ctx.getSource().sendFailure(Component.literal("§cDungeon '" + dungeonName + "' não encontrada."));
				return 0;
			}
		} else {
			List<DungeonDefinition> active = DungeonRepository.all().stream().filter(DungeonDefinition::isActive).toList();
			if (active.isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("§cNenhuma dungeon ativa disponível para spawnar."));
				return 0;
			}
			target = active.get(player.level().getRandom().nextInt(active.size()));
		}

		int min = target.getMinLevel();
		int max = Math.max(min, target.getMaxLevel());
		int level = (forcedLevel != null && forcedLevel > 0)
				? forcedLevel
				: (min == max ? min : min + player.level().getRandom().nextInt(max - min + 1));

		boolean success = DungeonWorldManager.spawnDungeonHere(player, target, level);
		if (success) {
			DungeonDefinition finalTarget = target;
			ctx.getSource().sendSuccess(() -> Component.literal("§aDungeon '" + finalTarget.getName() + "' spawnada em frente ao jogador (nível " + level + ")!"), true);
			return 1;
		} else {
			ctx.getSource().sendFailure(Component.literal("§cNão foi possível spawnar a dungeon em frente ao jogador."));
			return 0;
		}
	}

	private static int deleteDungeon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "nome_dungeon");
		Optional<DungeonDefinition> opt = DungeonRepository.get(name);
		if (opt.isEmpty()) {
			source.sendFailure(Component.literal("§cDungeon '" + name + "' não encontrada."));
			return 0;
		}

		DungeonDefinition def = opt.get();
		if (def.isOfficial() || "MOD".equalsIgnoreCase(def.getSource())) {
			source.sendFailure(Component.literal("§cNão é permitido deletar dungeons oficiais ou do mod!"));
			return 0;
		}

		boolean deleted = DungeonRepository.delete(name, source.getServer());
		if (deleted) {
			source.sendSuccess(() -> Component.literal("§aDungeon local '§e" + name + "§a' deletada com sucesso!"), true);
			return 1;
		} else {
			source.sendFailure(Component.literal("§cErro ao deletar a dungeon '" + name + "'."));
			return 0;
		}
	}

	private static CompletableFuture<Suggestions> suggestLocalDungeons(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (DungeonDefinition def : DungeonRepository.all()) {
			if (!def.isOfficial() && !"MOD".equalsIgnoreCase(def.getSource())) {
				if (def.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
					builder.suggest(def.getName());
				}
			}
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestDungeons(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (DungeonDefinition def : DungeonRepository.all()) {
			if (def.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(def.getName());
			}
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestActiveDungeons(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (DungeonDefinition def : DungeonRepository.all()) {
			if (def.isActive() && def.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(def.getName());
			}
		}
		return builder.buildFuture();
	}
}
