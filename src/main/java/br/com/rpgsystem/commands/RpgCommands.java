package br.com.rpgsystem.commands;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.RpgClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixRepository;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.affixes.AffixRarity;
import br.com.rpgsystem.loot.LootSortBy;
import br.com.rpgsystem.loot.LootToolHelper;
import br.com.rpgsystem.loot.RpgLootEntry;
import br.com.rpgsystem.loot.RpgLootTable;
import br.com.rpgsystem.loot.RpgLootTableSavedData;
import br.com.rpgsystem.loot.SortDirection;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.progression.ProgressionMath;
import br.com.rpgsystem.mining.MineradorEffects;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class RpgCommands {
	private RpgCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
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
						.then(Commands.literal("list").executes(RpgCommands::listAffixes))
						.then(Commands.literal("roll")
								.then(Commands.literal("rarity")
										.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RpgCommands::suggestRarities)
												.executes(RpgCommands::rollAffixByRarity)))
								.then(Commands.argument("affix", StringArgumentType.word()).suggests(RpgCommands::suggestAffixes)
										.executes(RpgCommands::rollUntilTarget))
								.executes(RpgCommands::rollAffix))
						.then(Commands.literal("apply")
								.then(Commands.argument("affix", StringArgumentType.word())
										.suggests(RpgCommands::suggestAffixes)
										.executes(RpgCommands::applyAffix)))
						.then(Commands.literal("remove").executes(RpgCommands::removeAffix)))
				.then(tableCommands(registryAccess)));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tableCommands(
			CommandBuildContext registryAccess) {
		return Commands.literal("table")
				.then(Commands.literal("create")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.then(Commands.argument("min", IntegerArgumentType.integer(0))
										.then(Commands.argument("max", IntegerArgumentType.integer(0))
												.executes(RpgCommands::createTableWithAmount)))
								.executes(RpgCommands::createTable)))
				.then(Commands.literal("delete")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.executes(RpgCommands::deleteTable)))
				.then(Commands.literal("insert")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("item", ItemArgument.item(registryAccess))
										.then(Commands.argument("peso", DoubleArgumentType.doubleArg(0.0001))
												.executes(RpgCommands::insertItem)))))
				.then(Commands.literal("inserthand")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("peso", DoubleArgumentType.doubleArg(0.0001))
										.executes(RpgCommands::insertHandItem))))
				.then(Commands.literal("deleteitem")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("id", IntegerArgumentType.integer(0))
										.executes(RpgCommands::deleteItem))))
				.then(Commands.literal("weight")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("id", IntegerArgumentType.integer(0))
										.then(Commands.argument("peso", DoubleArgumentType.doubleArg(0.0001))
												.executes(RpgCommands::setWeight)))))
				.then(Commands.literal("amount")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("min", IntegerArgumentType.integer(0))
										.then(Commands.argument("max", IntegerArgumentType.integer(0))
												.executes(RpgCommands::setAmount)))))
				.then(Commands.literal("list")
						.executes(RpgCommands::listAllTables)
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.then(Commands.argument("criterio", StringArgumentType.word())
										.suggests(RpgCommands::suggestSortCriteria)
										.then(Commands.argument("direcao", StringArgumentType.word())
												.suggests(RpgCommands::suggestSortDirection)
												.executes(RpgCommands::listTableWithOrder))
										.executes(RpgCommands::listTableWithCriteria))
								.executes(RpgCommands::listTableDefault)))
				.then(Commands.literal("tool")
						.then(Commands.argument("nome_tabela", StringArgumentType.word())
								.suggests(RpgCommands::suggestTables)
								.executes(RpgCommands::giveLootTool)));
	}

	private static int createTable(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		MinecraftServer server = context.getSource().getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		if (data.hasTable(name)) {
			context.getSource().sendFailure(Component.literal("A tabela de looting '" + name + "' já existe!"));
			return 0;
		}
		RpgLootTable table = data.createTable(name);
		data.syncToFile(server, table);
		context.getSource().sendSuccess(() -> Component.literal("Tabela de looting '" + name
				+ "' criada (qtd padrão: 1-3). Use /rpg table amount para ajustar ou /rpg table insert para adicionar itens."), true);
		return 1;
	}

	private static int createTableWithAmount(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		int min = IntegerArgumentType.getInteger(context, "min");
		int max = IntegerArgumentType.getInteger(context, "max");
		if (max < min) {
			context.getSource().sendFailure(Component.literal(
					"O valor máximo (" + max + ") não pode ser menor que o mínimo (" + min + ")."));
			return 0;
		}
		MinecraftServer server = context.getSource().getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		if (data.hasTable(name)) {
			context.getSource().sendFailure(Component.literal("A tabela de looting '" + name + "' já existe!"));
			return 0;
		}
		RpgLootTable table = data.createTable(name, min, max);
		data.syncToFile(server, table);
		context.getSource().sendSuccess(() -> Component.literal("Tabela de looting '" + name
				+ "' criada com sucesso (qtd: " + min + " a " + max + " itens)! Use /rpg table insert para adicionar itens."), true);
		return 1;
	}

	private static int deleteTable(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		RpgLootTableSavedData data = RpgLootTableSavedData.get(context.getSource().getServer());
		if (!data.removeTable(name)) {
			context.getSource().sendFailure(Component.literal("A tabela de looting '" + name + "' não existe."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Tabela de looting '" + name + "' removida."), true);
		return 1;
	}

	private static int insertItem(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String name = StringArgumentType.getString(context, "nome_tabela");
		double peso = DoubleArgumentType.getDouble(context, "peso");
		ItemInput input = ItemArgument.getItem(context, "item");
		ItemStack stack = input.createItemStack(1);
		if (stack.isEmpty()) {
			context.getSource().sendFailure(Component.literal("O item informado é inválido ou vazio."));
			return 0;
		}

		MinecraftServer server = context.getSource().getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name + "' não encontrada."));
			return 0;
		}

		RpgLootTable table = tableOpt.get();

		// Verifica duplicata por item + NBT
		Optional<RpgLootEntry> existing = table.findMatchingEntry(stack);
		if (existing.isPresent()) {
			RpgLootEntry dup = existing.get();
			table.setWeight(dup.id(), peso);
			data.setDirty();
			data.syncToFile(server, table);
			context.getSource().sendSuccess(() -> Component.literal("Item idêntico já existe (ID " + dup.id() + " - "
					+ dup.displayName() + "). Peso atualizado para " + formatWeight(peso)
					+ " (Nova chance: " + String.format(Locale.ROOT, "%.2f%%", table.getChancePercentage(dup.id())) + ")."), true);
			return 1;
		}

		RpgLootEntry entry = table.addItem(stack, peso);
		data.setDirty();
		data.syncToFile(server, table);

		context.getSource().sendSuccess(() -> Component.literal("Item inserido na tabela '" + table.name()
				+ "': ID " + entry.id() + " - " + entry.displayName() + " (Peso: " + formatWeight(peso)
				+ ", Chance: " + String.format(Locale.ROOT, "%.2f%%", table.getChancePercentage(entry.id())) + ")."), true);
		return 1;
	}

	private static int insertHandItem(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "nome_tabela");
		double peso = DoubleArgumentType.getDouble(context, "peso");
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Você precisa segurar um item na mão principal."));
			return 0;
		}

		MinecraftServer server = player.level().getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name + "' não encontrada."));
			return 0;
		}

		RpgLootTable table = tableOpt.get();

		// Verifica duplicata por item + NBT
		Optional<RpgLootEntry> existing = table.findMatchingEntry(stack);
		if (existing.isPresent()) {
			RpgLootEntry dup = existing.get();
			table.setWeight(dup.id(), peso);
			data.setDirty();
			data.syncToFile(server, table);
			context.getSource().sendSuccess(() -> Component.literal("Item idêntico já existe (ID " + dup.id() + " - "
					+ dup.displayName() + "). Peso atualizado para " + formatWeight(peso)
					+ " (Nova chance: " + String.format(Locale.ROOT, "%.2f%%", table.getChancePercentage(dup.id())) + ")."), true);
			return 1;
		}

		RpgLootEntry entry = table.addItem(stack.copy(), peso);
		data.setDirty();
		data.syncToFile(server, table);

		context.getSource().sendSuccess(() -> Component.literal("Item da mão (" + entry.displayName()
				+ ") inserido na tabela '" + table.name() + "' com ID " + entry.id() + " (Peso: " + formatWeight(peso)
				+ ", Chance: " + String.format(Locale.ROOT, "%.2f%%", table.getChancePercentage(entry.id())) + ")."), true);
		return 1;
	}

	private static int deleteItem(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		int id = IntegerArgumentType.getInteger(context, "id");
		RpgLootTableSavedData data = RpgLootTableSavedData.get(context.getSource().getServer());
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name + "' não encontrada."));
			return 0;
		}

		RpgLootTable table = tableOpt.get();
		boolean removed = table.removeItem(id);
		if (!removed) {
			context.getSource().sendFailure(Component.literal("Item com ID " + id + " não encontrado na tabela '" + table.name() + "'."));
			return 0;
		}

		data.setDirty();
		data.syncToFile(context.getSource().getServer(), table);
		context.getSource().sendSuccess(() -> Component.literal("Item ID " + id + " removido da tabela '" + table.name()
				+ "'. O ID " + id + " está livre para o próximo item adicionado."), true);
		return 1;
	}

	private static int setWeight(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		int id = IntegerArgumentType.getInteger(context, "id");
		double peso = DoubleArgumentType.getDouble(context, "peso");
		RpgLootTableSavedData data = RpgLootTableSavedData.get(context.getSource().getServer());
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name + "' não encontrada."));
			return 0;
		}

		RpgLootTable table = tableOpt.get();
		boolean updated = table.setWeight(id, peso);
		if (!updated) {
			context.getSource().sendFailure(Component.literal("Item com ID " + id + " não encontrado na tabela '" + table.name() + "'."));
			return 0;
		}

		data.setDirty();
		data.syncToFile(context.getSource().getServer(), table);
		context.getSource().sendSuccess(() -> Component.literal("Peso do item ID " + id + " na tabela '" + table.name()
				+ "' alterado para " + formatWeight(peso) + " (Nova chance: "
				+ String.format(Locale.ROOT, "%.2f%%", table.getChancePercentage(id)) + ")."), true);
		return 1;
	}

	private static int setAmount(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		int min = IntegerArgumentType.getInteger(context, "min");
		int max = IntegerArgumentType.getInteger(context, "max");

		if (max < min) {
			context.getSource().sendFailure(Component.literal("O valor máximo (" + max + ") não pode ser menor que o mínimo (" + min + ")."));
			return 0;
		}

		RpgLootTableSavedData data = RpgLootTableSavedData.get(context.getSource().getServer());
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name + "' não encontrada."));
			return 0;
		}

		RpgLootTable table = tableOpt.get();
		table.setAmount(min, max);
		data.setDirty();
		data.syncToFile(context.getSource().getServer(), table);

		context.getSource().sendSuccess(() -> Component.literal("Quantidade de itens da tabela '" + table.name()
				+ "' definida para: " + min + " a " + max + "."), true);
		return 1;
	}

	private static int listAllTables(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		Set<String> names = data.allTableNames();

		// Limpa o chat antes de exibir
		try {
			RpgNetworking.sendClearChat(source.getPlayerOrException());
		} catch (Exception ignored) {}

		source.sendSuccess(() -> Component.literal("§6=== Tabelas de Loot (" + names.size() + ") ==="), false);

		if (names.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§cNenhuma tabela criada. Use /rpg table create <nome> <min> <max>"), false);
		} else {
			List<String> sorted = new ArrayList<>(names);
			Collections.sort(sorted);
			for (String tname : sorted) {
				RpgLootTable t = data.getTable(tname).orElse(null);
				if (t == null) continue;
				MutableComponent line = Component.literal(
						"§e" + t.name() + " §7(" + t.size() + " iten(s), qtd: " + t.minAmount() + "–" + t.maxAmount() + ")")
						.withStyle(s -> s
								.withClickEvent(new ClickEvent.RunCommand("/rpg table list " + t.name()))
								.withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClique para ver os itens de '§f" + t.name() + "§e'"))));
				source.sendSuccess(() -> line, false);
			}
		}
		source.sendSuccess(() -> Component.literal("§6=============================="), false);
		return names.size();
	}

	private static int listTableDefault(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		return listTable(context.getSource(), name, LootSortBy.ID, SortDirection.ASCENDING);
	}

	private static int listTableWithCriteria(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		String crit = StringArgumentType.getString(context, "criterio");
		return listTable(context.getSource(), name, LootSortBy.fromString(crit), SortDirection.ASCENDING);
	}

	private static int listTableWithOrder(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "nome_tabela");
		String crit = StringArgumentType.getString(context, "criterio");
		String dir = StringArgumentType.getString(context, "direcao");
		return listTable(context.getSource(), name, LootSortBy.fromString(crit), SortDirection.fromString(dir));
	}

	private static int listTable(CommandSourceStack source, String tableName, LootSortBy sortBy, SortDirection direction) {
		MinecraftServer server = source.getServer();
		RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
		Optional<RpgLootTable> tableOpt = data.getTable(tableName);
		if (tableOpt.isEmpty()) {
			source.sendFailure(Component.literal("Tabela de looting '" + tableName + "' não encontrada."));
			return 0;
		}

		// Limpa o chat antes de exibir a listagem atualizada
		try {
			RpgNetworking.sendClearChat(source.getPlayerOrException());
		} catch (Exception ignored) {}

		RpgLootTable table = tableOpt.get();
		String canonicalName = table.name();

		source.sendSuccess(() -> Component.literal("§6========== [ Tabela de Loot: §e" + canonicalName + " §6] =========="), false);
		source.sendSuccess(() -> Component.literal("§7Configuração: Qtd no baú = §f" + table.minAmount() + " a " + table.maxAmount()
				+ " itens§7 | Total cadastrado: §f" + table.size() + " item(ns)§7 | Peso total: §f" + formatWeight(table.totalWeight())), false);

		if (table.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§cEsta tabela não possui nenhum item cadastrado."), false);
			source.sendSuccess(() -> Component.literal("§7Use §e/rpg table insert " + canonicalName + " <item> <peso> §7ou segure um item e use §e/rpg table inserthand " + canonicalName + " <peso>§7."), false);
		} else {
			source.sendSuccess(() -> Component.literal("§8----------------------------------------------------"), false);
			source.sendSuccess(() -> Component.literal("§eID      §8| §eNome do Item                     §8| §eChance (% / Peso)"), false);
			source.sendSuccess(() -> Component.literal("§8----------------------------------------------------"), false);

			List<RpgLootEntry> entries = table.getSortedEntries(sortBy, direction);
			for (RpgLootEntry entry : entries) {
				int id = entry.id();
				String idCol = String.format(Locale.ROOT, "#%-5d", id);
				String nameCol = entry.displayName();
				double chance = table.getChancePercentage(id);
				String chanceCol = String.format(Locale.ROOT, "%6.2f%%", chance);
				String weightStr = "(peso: " + formatWeight(entry.weight()) + ")";

				MutableComponent line = Component.literal("§b" + idCol + " §8| §f" + nameCol + " §8| §a" + chanceCol + " §7" + weightStr);
				line.withStyle(style -> style
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("§bID: #" + id + "\n§fItem: " + entry.itemNameOnly()
								+ "\n§aChance: " + chanceCol + "\n§7Peso: " + formatWeight(entry.weight()) + "\n\n§eClique para sugerir remoção deste item")))
						.withClickEvent(new ClickEvent.SuggestCommand("/rpg table deleteitem " + canonicalName + " " + id)));
				source.sendSuccess(() -> line, false);
			}

			source.sendSuccess(() -> Component.literal("§8----------------------------------------------------"), false);
		}

		// Linha 1 do rodapé: Ordem de exibição: [ID] [A-Z] [Chance]
		MutableComponent sortLine = Component.literal("§7Ordem de exibição: ");
		appendSortOption(sortLine, canonicalName, "ID", "id", LootSortBy.ID, sortBy, direction);
		sortLine.append(Component.literal(" "));
		appendSortOption(sortLine, canonicalName, "A-Z", "name", LootSortBy.NAME, sortBy, direction);
		sortLine.append(Component.literal(" "));
		appendSortOption(sortLine, canonicalName, "Chance", "chance", LootSortBy.CHANCE, sortBy, direction);
		source.sendSuccess(() -> sortLine, false);

		// Linha 2 do rodapé: Exibir: [Crescente] [Decrescente]
		MutableComponent dirLine = Component.literal("§7Exibir: ");
		appendDirectionOption(dirLine, canonicalName, "Crescente", "crescente", SortDirection.ASCENDING, sortBy, direction);
		dirLine.append(Component.literal(" "));
		appendDirectionOption(dirLine, canonicalName, "Decrescente", "decrescente", SortDirection.DESCENDING, sortBy, direction);
		source.sendSuccess(() -> dirLine, false);

		source.sendSuccess(() -> Component.literal("§6===================================================="), false);
		return table.size();
	}

	private static void appendSortOption(MutableComponent parent, String tableName, String label, String commandArg,
			LootSortBy option, LootSortBy currentSort, SortDirection currentDir) {
		boolean active = option == currentSort;
		if (active) {
			parent.append(Component.literal("§a§l[" + label + "]§r")
					.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
							Component.literal("§aAtualmente ordenado por " + label)))));
		} else {
			String cmd = "/rpg table list " + tableName + " " + commandArg + " " + currentDir.commandKey();
			parent.append(Component.literal("§e[" + label + "]§r")
					.withStyle(style -> style
							.withUnderlined(true)
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClique para alternar ordenação para: " + label)))
							.withClickEvent(new ClickEvent.RunCommand(cmd))));
		}
	}

	private static void appendDirectionOption(MutableComponent parent, String tableName, String label, String commandArg,
			SortDirection option, LootSortBy currentSort, SortDirection currentDir) {
		boolean active = option == currentDir;
		if (active) {
			parent.append(Component.literal("§a§l[" + label + "]§r")
					.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
							Component.literal("§aExibindo atualmente em ordem " + label)))));
		} else {
			String cmd = "/rpg table list " + tableName + " " + currentSort.commandKey() + " " + commandArg;
			parent.append(Component.literal("§e[" + label + "]§r")
					.withStyle(style -> style
							.withUnderlined(true)
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClique para exibir em ordem " + label)))
							.withClickEvent(new ClickEvent.RunCommand(cmd))));
		}
	}

	private static int giveLootTool(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "nome_tabela");
		RpgLootTableSavedData data = RpgLootTableSavedData.get(player.level().getServer());
		Optional<RpgLootTable> tableOpt = data.getTable(name);
		if (tableOpt.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Tabela de looting '" + name
					+ "' não encontrada. Crie-a primeiro com /rpg table create " + name));
			return 0;
		}

		RpgLootTable table = tableOpt.get();
		ItemStack tool = LootToolHelper.createTool(table.name());
		if (!player.getInventory().add(tool)) {
			player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), tool);
		}

		context.getSource().sendSuccess(() -> Component.literal("Você recebeu a Ferramenta de Loot para a tabela '"
				+ table.name() + "'. Clique com botão direito em qualquer baú ou barril para testar!"), false);
		return 1;
	}

	private static String formatWeight(double weight) {
		if (weight == (long) weight) {
			return String.format(Locale.ROOT, "%d", (long) weight);
		}
		return String.format(Locale.ROOT, "%.2f", weight);
	}

	private static CompletableFuture<Suggestions> suggestTables(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		MinecraftServer server = context.getSource().getServer();
		if (server != null) {
			RpgLootTableSavedData data = RpgLootTableSavedData.get(server);
			for (String name : data.allTableNames()) {
				if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
					builder.suggest(name);
				}
			}
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestSortCriteria(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (String opt : List.of("id", "name", "chance")) {
			if (opt.startsWith(prefix)) {
				builder.suggest(opt);
			}
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestSortDirection(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (String opt : List.of("crescente", "decrescente")) {
			if (opt.startsWith(prefix)) {
				builder.suggest(opt);
			}
		}
		return builder.buildFuture();
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
		MineradorEffects.refreshMiningSpeed(player);
		context.getSource().sendSuccess(() -> Component.literal("Sua classe agora é " + rpgClass.name() + "."), false);
		RpgNetworking.sendSnapshot(player, false);
		return 1;
	}

	private static int resetClass(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		RpgSavedData.get(player.level().getServer()).update(player.getUUID(), RpgSavedData.Profile::withoutClass);
		MineradorEffects.refreshMiningSpeed(player);
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
			MineradorEffects.refreshMiningSpeed(target);
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

	private static int listAffixes(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getMainHandItem();
		List<AffixDefinition> candidates = AffixService.candidates(stack);
		if (stack.isEmpty() || candidates.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Não há afixos compatíveis com o item na mão principal."));
			return 0;
		}
		Map<AffixDefinition, Double> chances = AffixService.individualChances(candidates, AffixService.affixLuck(player));
		context.getSource().sendSuccess(() -> Component.literal("Afixos candidatos para " + stack.getHoverName().getString() + ":"), false);
		for (AffixRarity rarity : AffixRarity.values()) {
			context.getSource().sendSuccess(() -> Component.literal("  " + rarity.displayName() + ":"), false);
			candidates.stream().filter(d -> d.rarity() == rarity).forEach(d ->
				context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "    - %s: %.4f%%", d.name(), chances.get(d) * 100.0)), false));
		}
		return candidates.size();
	}

	private static int rollAffixByRarity(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		AffixRarity rarity = parseRarity(context, StringArgumentType.getString(context, "rarity"));
		if (rarity == null) return 0;
		ItemStack stack = player.getMainHandItem();
		AffixDefinition definition = AffixService.rollWithinRarity(AffixService.candidates(stack), rarity, player.getRandom()).orElse(null);
		if (definition == null || !AffixService.apply(stack, definition)) {
			context.getSource().sendFailure(Component.literal("Não há afixos dessa raridade compatíveis com o item na mão.")); return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Afixo rolado: " + definition.name() + " (" + rarity.displayName() + ")."), false);
		return 1;
	}

	private static int rollUntilTarget(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Você precisa segurar um item na mão principal."));
			return 0;
		}
		String target = StringArgumentType.getString(context, "affix");
		List<AffixDefinition> candidates = AffixService.candidates(stack);
		AffixDefinition wanted = AffixRepository.byPath(target).orElse(null);
		if (wanted == null || !candidates.contains(wanted)) {
			context.getSource().sendFailure(Component.literal("Esse afixo não existe no pack ou não é compatível com o item."));
			return 0;
		}
		int rolls = AffixService.rollUntil(candidates, wanted, AffixService.affixLuck(player), player.getRandom()).orElse(0);
		if (rolls <= 0) {
			context.getSource().sendFailure(Component.literal("Não foi possível obter esse afixo com as chances atuais."));
			return 0;
		}
		AffixService.apply(stack, wanted);
		context.getSource().sendSuccess(() -> Component.literal("Afixo aplicado: " + wanted.name() + " após " + rolls + " rolagem(ns)."), false);
		return 1;
	}

	private static AffixRarity parseRarity(CommandContext<CommandSourceStack> context, String value) {
		for (AffixRarity rarity : AffixRarity.values()) if (rarity.getSerializedName().equalsIgnoreCase(value)) return rarity;
		context.getSource().sendFailure(Component.literal("Raridade desconhecida: " + value)); return null;
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

	private static CompletableFuture<Suggestions> suggestRarities(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (AffixRarity rarity : AffixRarity.values()) if (rarity.getSerializedName().startsWith(prefix)) builder.suggest(rarity.getSerializedName());
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
