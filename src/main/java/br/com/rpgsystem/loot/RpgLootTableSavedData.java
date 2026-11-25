package br.com.rpgsystem.loot;

import br.com.rpgsystem.RpgMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Armazena e persiste todas as tabelas de looting criadas no mundo, com sincronização em JSON. */
public final class RpgLootTableSavedData extends SavedData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Codec<Map<String, RpgLootTable>> TABLES_CODEC =
			Codec.unboundedMap(Codec.STRING, RpgLootTable.CODEC);
	private static final Codec<RpgLootTableSavedData> CODEC =
			TABLES_CODEC.xmap(RpgLootTableSavedData::new, data -> data.tables);
	private static final SavedDataType<RpgLootTableSavedData> TYPE = new SavedDataType<>(
			RpgMod.id("loot_tables"), RpgLootTableSavedData::new, CODEC, null);

	private final Map<String, RpgLootTable> tables;
	private boolean filesLoaded = false;

	public RpgLootTableSavedData() {
		this(new HashMap<>());
	}

	public RpgLootTableSavedData(Map<String, RpgLootTable> tables) {
		this.tables = new HashMap<>(tables);
	}

	public static RpgLootTableSavedData get(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
		if (overworld == null) {
			throw new IllegalStateException("O mundo principal ainda não foi carregado.");
		}
		RpgLootTableSavedData data = overworld.getDataStorage().computeIfAbsent(TYPE);
		if (!data.filesLoaded) {
			data.loadWorldAndBundledFiles(server);
			data.filesLoaded = true;
		}
		return data;
	}

	public static Path getWorldLootDirectory(MinecraftServer server) {
		if (server == null) return null;
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("rpg_loot_tables");
		try {
			if (!Files.exists(dir)) Files.createDirectories(dir);
		} catch (IOException ignored) {}
		return dir;
	}

	public Optional<RpgLootTable> getTable(String name) {
		if (name == null) return Optional.empty();
		return Optional.ofNullable(tables.get(name.toLowerCase(Locale.ROOT)));
	}

	public boolean hasTable(String name) {
		if (name == null) return false;
		return tables.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public RpgLootTable createTable(String name) {
		return createTable(name, 1, 3);
	}

	public RpgLootTable createTable(String name, int minAmount, int maxAmount) {
		String key = name.toLowerCase(Locale.ROOT);
		RpgLootTable table = new RpgLootTable(name, minAmount, maxAmount);
		tables.put(key, table);
		setDirty();
		return table;
	}

	public boolean removeTable(String name) {
		return removeTable(null, name);
	}

	public boolean removeTable(MinecraftServer server, String name) {
		if (name == null) return false;
		String key = name.toLowerCase(Locale.ROOT);
		boolean removed = tables.remove(key) != null;
		if (removed) {
			setDirty();
			if (server != null) {
				deleteConfigFile(server, name);
			}
		}
		return removed;
	}

	public Set<String> allTableNames() {
		return Collections.unmodifiableSet(tables.keySet());
	}

	public Collection<RpgLootTable> allTables() {
		return Collections.unmodifiableCollection(tables.values());
	}

	/**
	 * Salva uma tabela em arquivo JSON no diretório do mundo (<world>/rpg_loot_tables/<nome>.json).
	 */
	public void syncToFile(MinecraftServer server, RpgLootTable table) {
		if (server == null || table == null) return;
		try {
			Path dir = getWorldLootDirectory(server);
			if (dir == null) return;
			Path file = dir.resolve(table.name() + ".json");
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
			DataResult<JsonElement> result = RpgLootTable.CODEC.encodeStart(ops, table);
			result.result().ifPresent(json -> {
				try {
					Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
				} catch (IOException e) {
					RpgMod.LOGGER.error("Falha ao salvar tabela de loot '{}' em {}", table.name(), file, e);
				}
			});
		} catch (Exception e) {
			RpgMod.LOGGER.error("Falha ao salvar arquivo de tabela de loot '{}'", table.name(), e);
		}
	}

	/**
	 * Deleta o arquivo JSON da tabela na pasta do mundo (<world>/rpg_loot_tables/).
	 */
	public void deleteConfigFile(MinecraftServer server, String tableName) {
		if (server == null) return;
		try {
			Path dir = getWorldLootDirectory(server);
			if (dir == null) return;
			Path file = dir.resolve(tableName + ".json");
			Files.deleteIfExists(file);
		} catch (IOException e) {
			RpgMod.LOGGER.error("Falha ao remover arquivo de tabela de loot '{}'", tableName, e);
		}
	}

	/**
	 * Carrega tabelas salvas na pasta do mundo (<world>/rpg_loot_tables/*.json)
	 * e também tabelas padrão embutidas no mod (src/main/resources/data/rpgsystem/loot_tables/*.json).
	 */
	public void loadWorldAndBundledFiles(MinecraftServer server) {
		if (server == null) return;
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

		// 1. Carrega arquivos da pasta exclusiva deste mundo (<world>/rpg_loot_tables/)
		try {
			Path worldLootDir = getWorldLootDirectory(server);
			if (worldLootDir != null && Files.isDirectory(worldLootDir)) {
				try (var stream = Files.walk(worldLootDir, 1)) {
					stream.filter(Files::isRegularFile)
							.filter(p -> p.getFileName().toString().endsWith(".json"))
							.forEach(p -> loadJsonFile(ops, p));
				}
			}
		} catch (Throwable t) {
			RpgMod.LOGGER.error("Erro ao carregar tabelas da pasta rpg_loot_tables do mundo", t);
		}

		// 2. Carrega recursos embutidos no mod (se existirem e ainda não estiverem carregados)
		try {
			FabricLoader.getInstance().getModContainer(RpgMod.MOD_ID).ifPresent(mod -> {
				mod.findPath("data/rpgsystem/loot_tables").ifPresent(dir -> {
					if (Files.isDirectory(dir)) {
						try (var stream = Files.walk(dir, 1)) {
							stream.filter(Files::isRegularFile)
									.filter(p -> p.getFileName().toString().endsWith(".json"))
									.forEach(p -> loadJsonFile(ops, p));
						} catch (IOException e) {
							RpgMod.LOGGER.error("Erro ao listar tabelas embutidas em {}", dir, e);
						}
					}
				});
			});
		} catch (Throwable t) {
			RpgMod.LOGGER.warn("Não foi possível carregar tabelas de loot embutidas pelo ModContainer", t);
		}
	}

	private void loadJsonFile(RegistryOps<JsonElement> ops, Path path) {
		try {
			String content = Files.readString(path, StandardCharsets.UTF_8);
			JsonElement json = JsonParser.parseString(content);
			DataResult<RpgLootTable> result = RpgLootTable.CODEC.parse(ops, json);
			result.result().ifPresent(loadedTable -> {
				String key = loadedTable.name().toLowerCase(Locale.ROOT);
				if (!tables.containsKey(key)) {
					tables.put(key, loadedTable);
					setDirty();
				}
			});
		} catch (Exception e) {
			RpgMod.LOGGER.error("Falha ao ler arquivo de tabela de loot em {}", path, e);
		}
	}
}
