package br.com.rpgsystem.dungeon.data;

import br.com.rpgsystem.RpgMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class DungeonRepository {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, DungeonDefinition> DUNGEONS = new LinkedHashMap<>();
	private static final Map<String, String> DUNGEON_FOLDERS = new HashMap<>();
	private static final Set<String> DISABLED_OFFICIAL_DUNGEONS = new HashSet<>();
	private static boolean initialized = false;

	private DungeonRepository() {}

	public static synchronized void init(MinecraftServer server) {
		DUNGEONS.clear();
		DUNGEON_FOLDERS.clear();
		DISABLED_OFFICIAL_DUNGEONS.clear();
		loadBundledOfficialDungeons();
		loadModConfigDungeons();
		loadWorldDungeons(server);
		initialized = true;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static Collection<DungeonDefinition> all() {
		return Collections.unmodifiableCollection(DUNGEONS.values());
	}

	public static Optional<DungeonDefinition> get(String name) {
		if (name == null) return Optional.empty();
		return Optional.ofNullable(DUNGEONS.get(name.toLowerCase(Locale.ROOT)));
	}

	public static boolean exists(String name) {
		if (name == null) return false;
		return DUNGEONS.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public static DungeonDefinition create(String name, int minLevel, int maxLevel, int weight, MinecraftServer server) {
		String key = name.toLowerCase(Locale.ROOT);
		DungeonDefinition dungeon = new DungeonDefinition(name, minLevel, maxLevel, weight);
		dungeon.setOfficial(false);
		dungeon.setSource("WORLD");
		DUNGEONS.put(key, dungeon);
		if (server != null) {
			String folder = allocateFolderName(server, name);
			DUNGEON_FOLDERS.put(key, folder);
			saveDungeon(server, dungeon);
		}
		return dungeon;
	}

	public static synchronized void rename(String oldName, String newName, MinecraftServer server) {
		String oldKey = oldName.toLowerCase(Locale.ROOT);
		String newKey = newName.toLowerCase(Locale.ROOT);
		DungeonDefinition def = DUNGEONS.remove(oldKey);
		if (def != null) {
			String oldFolder = DUNGEON_FOLDERS.remove(oldKey);
			def.setName(newName);
			DUNGEONS.put(newKey, def);
			if (server != null) {
				Path rootDir = getWorldDungeonsDirectory(server);
				Path oldDir = rootDir.resolve(oldFolder != null ? oldFolder : sanitizeFolderName(oldName));
				String newFolder = sanitizeFolderName(newName);
				if (!newFolder.equals(oldFolder)) {
					newFolder = allocateFolderName(server, newName);
					Path newDir = rootDir.resolve(newFolder);
					if (Files.exists(oldDir) && !oldDir.equals(newDir)) {
						try {
							Files.createDirectories(rootDir);
							Files.move(oldDir, newDir);
						} catch (IOException e) {
							RpgMod.LOGGER.error("Erro ao renomear pasta da dungeon {} -> {}: {}", oldDir, newDir, e.getMessage());
						}
					}
				}
				DUNGEON_FOLDERS.put(newKey, newFolder);
				saveDungeon(server, def);
			}
		}
	}

	public static boolean activate(String name, MinecraftServer server) {
		Optional<DungeonDefinition> opt = get(name);
		if (opt.isEmpty()) return false;
		DungeonDefinition def = opt.get();
		if (!def.validateForActivation().isEmpty()) {
			return false;
		}
		def.setActive(true);
		if (server != null) {
			saveDungeon(server, def);
		}
		return true;
	}

	public static boolean deactivate(String name, MinecraftServer server) {
		Optional<DungeonDefinition> opt = get(name);
		if (opt.isEmpty()) return false;
		DungeonDefinition def = opt.get();
		def.setActive(false);
		if (server != null) {
			saveDungeon(server, def);
		}
		return true;
	}

	public static synchronized boolean delete(String name, MinecraftServer server) {
		if (name == null) return false;
		String key = name.toLowerCase(Locale.ROOT);
		DungeonDefinition def = DUNGEONS.get(key);
		if (def == null) return false;
		// Apenas dungeons LOCAIS podem ser deletadas (nunca oficiais/do mod)
		if (def.isOfficial() || "MOD".equalsIgnoreCase(def.getSource())) {
			return false;
		}

		DUNGEONS.remove(key);
		String folder = DUNGEON_FOLDERS.remove(key);

		if (server != null) {
			try {
				br.com.rpgsystem.dungeon.world.DungeonWorldManager.despawnDungeonByName(server, name);
			} catch (Throwable ignored) {}

			Path dungeonDir = getWorldDungeonsDirectory(server).resolve(folder != null ? folder : sanitizeFolderName(name));
			deleteDirectoryRecursively(dungeonDir);
		}
		return true;
	}

	public static DungeonDefinition duplicateOfficialDungeon(String officialName, MinecraftServer server) {
		Optional<DungeonDefinition> opt = get(officialName);
		if (opt.isEmpty()) return null;
		DungeonDefinition official = opt.get();

		// Desabilita dungeon oficial
		official.setActive(false);
		DISABLED_OFFICIAL_DUNGEONS.add(official.getName().toLowerCase(Locale.ROOT));

		// Cria cópia para o mundo
		String newName = official.getName() + "_custom";
		int counter = 1;
		while (exists(newName)) {
			newName = official.getName() + "_custom" + (++counter);
		}

		// Clona em JSON para deep-copy
		JsonObject json = serializeDungeon(official);
		json.addProperty("name", newName);
		json.addProperty("official", false);
		json.addProperty("source", "WORLD");
		json.addProperty("active", false);

		DungeonDefinition copy = deserializeDungeon(json);
		String copyKey = newName.toLowerCase(Locale.ROOT);
		DUNGEONS.put(copyKey, copy);

		if (server != null) {
			String folder = allocateFolderName(server, newName);
			DUNGEON_FOLDERS.put(copyKey, folder);
			saveDungeon(server, copy);
			saveDisabledOfficialDungeons(server);
		}
		return copy;
	}

	public static Path getWorldDungeonsDirectory(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("rpg_dungeons");
	}

	public static Path getDungeonDirectory(MinecraftServer server, String dungeonName) {
		String key = dungeonName != null ? dungeonName.toLowerCase(Locale.ROOT) : "";
		String folder = DUNGEON_FOLDERS.get(key);
		if (folder == null) {
			folder = allocateFolderName(server, dungeonName);
			if (!key.isEmpty()) {
				DUNGEON_FOLDERS.put(key, folder);
			}
		}
		Path dir = getWorldDungeonsDirectory(server).resolve(folder);
		try {
			if (!Files.exists(dir)) Files.createDirectories(dir);
		} catch (IOException ignored) {}
		return dir;
	}

	public static String sanitizeFolderName(String name) {
		if (name == null || name.isBlank()) return "dungeon";
		String sanitized = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
		return sanitized.isBlank() ? "dungeon" : sanitized;
	}

	private static String allocateFolderName(MinecraftServer server, String dungeonName) {
		String base = sanitizeFolderName(dungeonName);
		Path root = getWorldDungeonsDirectory(server);
		String candidate = base;
		int suffix = 2;
		while (Files.exists(root.resolve(candidate)) && isFolderUsedByOther(candidate, dungeonName)) {
			candidate = base + "_" + suffix++;
		}
		return candidate;
	}

	private static boolean isFolderUsedByOther(String folder, String dungeonName) {
		String targetKey = dungeonName != null ? dungeonName.toLowerCase(Locale.ROOT) : "";
		for (Map.Entry<String, String> entry : DUNGEON_FOLDERS.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(folder) && !entry.getKey().equals(targetKey)) {
				return true;
			}
		}
		return false;
	}

	private static void deleteDirectoryRecursively(Path dir) {
		if (dir == null || !Files.exists(dir)) return;
		try (var walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ex) {
					RpgMod.LOGGER.warn("Erro ao deletar arquivo da dungeon {}: {}", p, ex.getMessage());
				}
			});
		} catch (IOException ex) {
			RpgMod.LOGGER.warn("Erro ao remover pasta da dungeon {}: {}", dir, ex.getMessage());
		}
	}

	public static String importSchematicToDungeon(MinecraftServer server, String dungeonName, String rawPath) {
		return importSchematicToDungeon(server, dungeonName, rawPath, null, null);
	}

	public static String importSchematicToDungeon(MinecraftServer server, String dungeonName, String rawPath,
	                                              String previousPath, String otherSlotPath) {
		if (rawPath == null || rawPath.isBlank()) return "";
		if (rawPath.startsWith("rpgsystem:")) return rawPath;
		if (server == null) return rawPath;

		Path dungeonDir = getDungeonDirectory(server, dungeonName).toAbsolutePath().normalize();
		Path source;
		try {
			source = Path.of(rawPath);
		} catch (Exception e) {
			return rawPath;
		}

		String resultPath;
		if (!source.isAbsolute()) {
			Path resolved = dungeonDir.resolve(source).normalize();
			if (Files.isRegularFile(resolved)) {
				resultPath = dungeonDir.relativize(resolved).toString().replace('\\', '/');
			} else {
				resultPath = rawPath.replace('\\', '/');
			}
		} else {
			source = source.toAbsolutePath().normalize();
			if (source.startsWith(dungeonDir)) {
				resultPath = dungeonDir.relativize(source).toString().replace('\\', '/');
			} else if (!Files.isRegularFile(source)) {
				return rawPath;
			} else {
				String originalName = source.getFileName().toString();
				boolean canOverwriteSameName = previousPath != null
						&& previousPath.equalsIgnoreCase(originalName)
						&& (otherSlotPath == null || !otherSlotPath.equalsIgnoreCase(originalName));
				Path target = canOverwriteSameName
						? dungeonDir.resolve(originalName)
						: resolveUniqueTargetFile(dungeonDir, originalName, otherSlotPath);
				try {
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
					resultPath = target.getFileName().toString();
				} catch (IOException e) {
					RpgMod.LOGGER.error("Erro ao copiar esquemática '{}' para pasta da dungeon '{}': {}", source, dungeonDir, e.getMessage());
					return rawPath;
				}
			}
		}

		deleteOldSchematicIfUnused(dungeonDir, previousPath, resultPath, otherSlotPath);
		return resultPath;
	}

	public static void deleteOldSchematicIfUnused(Path dungeonDir, String oldPath, String newPath, String otherSlotPath) {
		if (dungeonDir == null || oldPath == null || oldPath.isBlank() || oldPath.startsWith("rpgsystem:")) {
			return;
		}
		if (oldPath.equalsIgnoreCase(newPath) || (otherSlotPath != null && oldPath.equalsIgnoreCase(otherSlotPath))) {
			return;
		}
		try {
			Path candidate = Path.of(oldPath);
			if (candidate.isAbsolute()) return;
			Path resolved = dungeonDir.resolve(candidate).normalize();
			if (resolved.startsWith(dungeonDir) && Files.isRegularFile(resolved)) {
				Files.deleteIfExists(resolved);
			}
		} catch (Exception ex) {
			RpgMod.LOGGER.warn("Erro ao remover esquemática antiga '{}' em '{}': {}", oldPath, dungeonDir, ex.getMessage());
		}
	}

	private static Path resolveUniqueTargetFile(Path dir, String fileName, String otherSlotPath) {
		Path candidate = dir.resolve(fileName);
		if (!Files.exists(candidate) && (otherSlotPath == null || !otherSlotPath.equalsIgnoreCase(fileName))) {
			return candidate;
		}
		int dot = fileName.lastIndexOf('.');
		String base = dot > 0 ? fileName.substring(0, dot) : fileName;
		String ext = dot > 0 ? fileName.substring(dot) : "";
		int counter = 1;
		while (Files.exists(candidate) || (otherSlotPath != null && otherSlotPath.equalsIgnoreCase(candidate.getFileName().toString()))) {
			candidate = dir.resolve(base + "_" + counter++ + ext);
		}
		return candidate;
	}

	public static Path getModConfigDirectory() {
		try {
			if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
				return FabricLoader.getInstance().getConfigDir().resolve("rpgsystem").resolve("dungeons");
			}
		} catch (Throwable ignored) {}
		return Path.of("config", "rpgsystem", "dungeons");
	}

	public static StructureTemplate loadStructureTemplate(ServerLevel level, DungeonDefinition dungeon, String pathStr) {
		if (pathStr == null || pathStr.isBlank()) return null;

		// 1. Tenta recurso embutido se começar com rpgsystem:
		if (pathStr.startsWith("rpgsystem:")) {
			String resName = pathStr.substring("rpgsystem:".length());
			String resPath = "/data/rpgsystem/structures/" + resName;
			try (InputStream in = DungeonRepository.class.getResourceAsStream(resPath)) {
				if (in != null) {
					CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
					if (tag != null) {
						return loadTemplateFromTag(level, tag);
					}
				}
			} catch (Exception e) {
				RpgMod.LOGGER.error("Erro ao ler esquemática interna: {}", pathStr, e);
			}
			return null;
		}

		// 2. Resolve caminho relativo à pasta da dungeon ou caminho absoluto
		Path path = null;
		try {
			Path candidate = Path.of(pathStr);
			if (!candidate.isAbsolute() && dungeon != null && level.getServer() != null) {
				Path inDungeon = getDungeonDirectory(level.getServer(), dungeon.getName()).resolve(candidate).normalize();
				if (Files.exists(inDungeon)) {
					path = inDungeon;
				}
			}
			if (path == null && Files.exists(candidate)) {
				path = candidate;
			}
		} catch (Exception ignored) {}

		if (path != null && Files.exists(path)) {
			try {
				CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
				if (tag != null) {
					return loadTemplateFromTag(level, tag);
				}
			} catch (Exception e) {
				RpgMod.LOGGER.error("Erro ao ler esquemática: {}", path, e);
			}
		}

		return null;
	}

	private static StructureTemplate loadTemplateFromTag(ServerLevel level, CompoundTag tag) {
		if (level.getServer() != null) {
			int dataVersion = NbtUtils.getDataVersion(tag, 500);
			tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(level.getServer().getFixerUpper(), tag, dataVersion);
		}
		StructureTemplate template = new StructureTemplate();
		template.load(level.holderLookup(Registries.BLOCK), tag);
		return template;
	}

	public static synchronized void saveDungeon(MinecraftServer server, DungeonDefinition def) {
		if (server == null || def == null) return;
		if ("MOD".equalsIgnoreCase(def.getSource()) && def.isOfficial()) return;
		Path dir = getDungeonDirectory(server, def.getName());
		Path file = dir.resolve("dungeon.json");
		try {
			Files.createDirectories(dir);
			Files.writeString(file, GSON.toJson(serializeDungeon(def)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RpgMod.LOGGER.error("Erro ao salvar dungeon '{}': {}", def.getName(), file, e);
		}
	}

	public static synchronized void saveWorldDungeons(MinecraftServer server) {
		if (server == null) return;
		saveDisabledOfficialDungeons(server);
		for (DungeonDefinition def : DUNGEONS.values()) {
			if ("WORLD".equalsIgnoreCase(def.getSource()) || !def.isOfficial()) {
				saveDungeon(server, def);
			}
		}
	}

	private static void saveDisabledOfficialDungeons(MinecraftServer server) {
		Path file = getWorldDungeonsDirectory(server).resolve("disabled_official.json");
		if (DISABLED_OFFICIAL_DUNGEONS.isEmpty()) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {}
			return;
		}
		JsonObject root = new JsonObject();
		JsonArray disabledOfficial = new JsonArray();
		for (String dis : DISABLED_OFFICIAL_DUNGEONS) {
			disabledOfficial.add(dis);
		}
		root.add("disabled_official_dungeons", disabledOfficial);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RpgMod.LOGGER.error("Erro ao salvar dungeons oficiais desabilitadas: {}", file, e);
		}
	}

	private static void loadBundledOfficialDungeons() {
		try (InputStream in = DungeonRepository.class.getResourceAsStream("/data/rpgsystem/dungeons/official_dungeons.json")) {
			if (in != null) {
				String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				JsonObject root = JsonParser.parseString(content).getAsJsonObject();
				if (root.has("dungeons") && root.get("dungeons").isJsonArray()) {
					for (JsonElement el : root.getAsJsonArray("dungeons")) {
						DungeonDefinition def = deserializeDungeon(el.getAsJsonObject());
						def.setOfficial(true);
						def.setSource("MOD");
						DUNGEONS.put(def.getName().toLowerCase(Locale.ROOT), def);
					}
				}
			}
		} catch (Exception e) {
			RpgMod.LOGGER.warn("Nenhuma dungeon oficial embutida encontrada ou erro ao carregar: {}", e.getMessage());
		}
	}

	private static void loadModConfigDungeons() {
		Path dir = getModConfigDirectory();
		if (!Files.exists(dir)) return;
		try (var stream = Files.walk(dir, 1)) {
			stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
				try {
					String content = Files.readString(p, StandardCharsets.UTF_8);
					JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
					if (obj.has("dungeons") && obj.get("dungeons").isJsonArray()) {
						for (JsonElement el : obj.getAsJsonArray("dungeons")) {
							DungeonDefinition def = deserializeDungeon(el.getAsJsonObject());
							def.setOfficial(true);
							def.setSource("MOD");
							DUNGEONS.put(def.getName().toLowerCase(Locale.ROOT), def);
						}
					} else if (obj.has("name")) {
						DungeonDefinition def = deserializeDungeon(obj);
						def.setOfficial(true);
						def.setSource("MOD");
						DUNGEONS.put(def.getName().toLowerCase(Locale.ROOT), def);
					}
				} catch (Exception ex) {
					RpgMod.LOGGER.warn("Erro ao ler dungeon em config: {}", p, ex);
				}
			});
		} catch (Exception ignored) {}
	}

	private static void loadWorldDungeons(MinecraftServer server) {
		Path rootDir = getWorldDungeonsDirectory(server);
		if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) return;

		Path disabledFile = rootDir.resolve("disabled_official.json");
		if (Files.isRegularFile(disabledFile)) {
			try {
				String content = Files.readString(disabledFile, StandardCharsets.UTF_8);
				JsonObject root = JsonParser.parseString(content).getAsJsonObject();
				if (root.has("disabled_official_dungeons")) {
					for (JsonElement el : root.getAsJsonArray("disabled_official_dungeons")) {
						String key = el.getAsString().toLowerCase(Locale.ROOT);
						DISABLED_OFFICIAL_DUNGEONS.add(key);
						DungeonDefinition official = DUNGEONS.get(key);
						if (official != null) {
							official.setActive(false);
						}
					}
				}
			} catch (Exception ex) {
				RpgMod.LOGGER.error("Erro ao carregar dungeons oficiais desabilitadas: {}", disabledFile, ex);
			}
		}

		try (var stream = Files.list(rootDir)) {
			stream.filter(Files::isDirectory).forEach(subDir -> {
				Path jsonFile = subDir.resolve("dungeon.json");
				if (!Files.isRegularFile(jsonFile)) return;
				try {
					String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
					JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
					DungeonDefinition def = deserializeDungeon(obj);
					def.setSource("WORLD");
					def.setOfficial(false);
					String key = def.getName().toLowerCase(Locale.ROOT);
					DUNGEONS.put(key, def);
					DUNGEON_FOLDERS.put(key, subDir.getFileName().toString());
				} catch (Exception ex) {
					RpgMod.LOGGER.error("Erro ao carregar dungeon da pasta '{}': {}", subDir, ex.getMessage());
				}
			});
		} catch (Exception ex) {
			RpgMod.LOGGER.error("Erro ao listar pasta de dungeons do mundo: {}", rootDir, ex);
		}
	}

	public static JsonObject serializeDungeon(DungeonDefinition def) {
		JsonObject json = new JsonObject();
		json.addProperty("name", def.getName());
		json.addProperty("minLevel", def.getMinLevel());
		json.addProperty("maxLevel", def.getMaxLevel());
		json.addProperty("weight", def.getWeight());
		json.addProperty("active", def.isActive());
		json.addProperty("official", def.isOfficial());
		json.addProperty("source", def.getSource());

		// Entrada
		JsonObject entrance = new JsonObject();
		EntranceStructureData ent = def.getEntranceStructure();
		if (ent != null) {
			entrance.addProperty("structureName", ent.getStructureName());
			entrance.addProperty("schematicPath", ent.getSchematicPath());
			entrance.addProperty("subsolo", ent.isSubsolo());
			entrance.addProperty("ceu", ent.isCeu());
			JsonArray dims = new JsonArray();
			ent.getDimensions().forEach(dims::add);
			entrance.add("dimensions", dims);
			if (ent.getEntrancePortal() != null) {
				entrance.add("entrancePortal", serializePortal(ent.getEntrancePortal()));
			}
		}
		json.add("entranceStructure", entrance);

		// Dungeon
		JsonObject dung = new JsonObject();
		DungeonStructureData dStruct = def.getDungeonStructure();
		if (dStruct != null) {
			dung.addProperty("structureName", dStruct.getStructureName());
			dung.addProperty("schematicPath", dStruct.getSchematicPath());
			dung.addProperty("timeTicks", dStruct.getTimeTicks());
			dung.addProperty("daylightCycle", dStruct.isDaylightCycle());
			if (dStruct.getEntrancePortal() != null) {
				dung.add("entrancePortal", serializePortal(dStruct.getEntrancePortal()));
			}
			if (dStruct.getExitPortal() != null) {
				dung.add("exitPortal", serializePortal(dStruct.getExitPortal()));
			}
			if (dStruct.getPlayerSpawnPos() != null) {
				dung.add("playerSpawnPos", serializePos(dStruct.getPlayerSpawnPos()));
				dung.addProperty("playerSpawnYaw", dStruct.getPlayerSpawnYaw());
			}

			// Waves
			JsonArray wavesArr = new JsonArray();
			for (WaveData wave : dStruct.getWaves()) {
				JsonObject w = new JsonObject();
				w.addProperty("order", wave.getOrder());
				w.addProperty("name", wave.getName());
				w.addProperty("spawnMode", wave.getSpawnMode().name());
				w.addProperty("activationAreaName", wave.getActivationAreaName());
				JsonArray mobsArr = new JsonArray();
				for (MobSpawnData mob : wave.getMobs()) {
					JsonObject m = new JsonObject();
					m.addProperty("category", mob.getCategory().name());
					m.addProperty("entityId", mob.getEntityId());
					m.addProperty("customName", mob.getCustomName());
					m.addProperty("particleId", mob.getParticleId());
					m.addProperty("particleIntensity", mob.getParticleIntensity());
					m.addProperty("soundId", mob.getSoundId());
					m.addProperty("dropChance", mob.getDropChance());
					m.addProperty("lootTableName", mob.getLootTableName());
					m.addProperty("scale", mob.getScale());
					m.addProperty("healthMultiplier", mob.getHealthMultiplier());
					m.addProperty("attackMultiplier", mob.getAttackMultiplier());
					m.addProperty("speedMultiplier", mob.getSpeedMultiplier());
					m.addProperty("constantParticles", mob.isConstantParticles());
					m.addProperty("dropEquipment", mob.isDropEquipment());
					m.add("equipment", serializeEquipment(mob.getEquipment()));
					m.add("relativePos", serializePos(mob.getRelativePos()));
					mobsArr.add(m);
				}
				w.add("mobs", mobsArr);
				wavesArr.add(w);
			}
			dung.add("waves", wavesArr);

			// Chests
			JsonArray chestsArr = new JsonArray();
			for (ChestSpawnData chest : dStruct.getChests()) {
				JsonObject c = new JsonObject();
				c.addProperty("chestName", chest.getChestName());
				c.addProperty("lootTableName", chest.getLootTableName());
				c.addProperty("condition", chest.getCondition().name());
				c.addProperty("waveOrder", chest.getWaveOrder());
				c.addProperty("targetAreaName", chest.getTargetAreaName());
				c.addProperty("facing", chest.getFacing().getName());
				c.add("relativePos", serializePos(chest.getRelativePos()));
				chestsArr.add(c);
			}
			dung.add("chests", chestsArr);

			// Areas
			JsonArray areasArr = new JsonArray();
			for (AreaTriggerData area : dStruct.getAreas()) {
				JsonObject a = new JsonObject();
				a.addProperty("name", area.getName());
				a.addProperty("mode", area.getMode().name());
				a.addProperty("waveOrder", area.getWaveOrder());
				a.add("minPos", serializePos(area.getMinPos()));
				a.add("maxPos", serializePos(area.getMaxPos()));
				a.addProperty("targetWaveName", area.getTargetWaveName());
				a.addProperty("targetChestName", area.getTargetChestName());
				a.addProperty("musicId", area.getMusicId());
				a.addProperty("replaceMusic", area.isReplaceMusic());
				areasArr.add(a);
			}
			dung.add("areas", areasArr);
		}
		json.add("dungeonStructure", dung);

		return json;
	}

	public static DungeonDefinition deserializeDungeon(JsonObject json) {
		String name = json.has("name") ? json.get("name").getAsString() : "Dungeon";
		int minLevel = json.has("minLevel") ? json.get("minLevel").getAsInt() : 1;
		int maxLevel = json.has("maxLevel") ? json.get("maxLevel").getAsInt() : 1;
		int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;

		DungeonDefinition def = new DungeonDefinition(name, minLevel, maxLevel, weight);
		if (json.has("active")) def.setActive(json.get("active").getAsBoolean());
		if (json.has("official")) def.setOfficial(json.get("official").getAsBoolean());
		if (json.has("source")) def.setSource(json.get("source").getAsString());

		if (json.has("entranceStructure")) {
			JsonObject entJson = json.getAsJsonObject("entranceStructure");
			EntranceStructureData ent = new EntranceStructureData();
			if (entJson.has("structureName")) ent.setStructureName(entJson.get("structureName").getAsString());
			if (entJson.has("schematicPath")) ent.setSchematicPath(entJson.get("schematicPath").getAsString());
			if (entJson.has("subsolo")) ent.setSubsolo(entJson.get("subsolo").getAsBoolean());
			if (entJson.has("ceu")) ent.setCeu(entJson.get("ceu").getAsBoolean());
			if (entJson.has("dimensions")) {
				ent.getDimensions().clear();
				for (JsonElement el : entJson.getAsJsonArray("dimensions")) {
					ent.getDimensions().add(el.getAsString());
				}
			}
			if (entJson.has("entrancePortal")) {
				ent.setEntrancePortal(deserializePortal(entJson.getAsJsonObject("entrancePortal")));
			}
			def.setEntranceStructure(ent);
		}

		if (json.has("dungeonStructure")) {
			JsonObject dJson = json.getAsJsonObject("dungeonStructure");
			DungeonStructureData dStruct = new DungeonStructureData();
			if (dJson.has("structureName")) dStruct.setStructureName(dJson.get("structureName").getAsString());
			if (dJson.has("schematicPath")) dStruct.setSchematicPath(dJson.get("schematicPath").getAsString());
			if (dJson.has("timeTicks")) dStruct.setTimeTicks(dJson.get("timeTicks").getAsLong());
			if (dJson.has("daylightCycle")) dStruct.setDaylightCycle(dJson.get("daylightCycle").getAsBoolean());
			if (dJson.has("entrancePortal")) {
				dStruct.setEntrancePortal(deserializePortal(dJson.getAsJsonObject("entrancePortal")));
			}
			if (dJson.has("exitPortal")) {
				dStruct.setExitPortal(deserializePortal(dJson.getAsJsonObject("exitPortal")));
			}
			if (dJson.has("playerSpawnPos")) {
				dStruct.setPlayerSpawnPos(deserializePos(dJson.getAsJsonObject("playerSpawnPos")));
			}
			if (dJson.has("playerSpawnYaw")) {
				dStruct.setPlayerSpawnYaw(dJson.get("playerSpawnYaw").getAsFloat());
			}

			if (dJson.has("waves")) {
				for (JsonElement wEl : dJson.getAsJsonArray("waves")) {
					JsonObject wObj = wEl.getAsJsonObject();
					WaveData wave = new WaveData();
					if (wObj.has("order")) wave.setOrder(wObj.get("order").getAsInt());
					if (wObj.has("name")) wave.setName(wObj.get("name").getAsString());
					if (wObj.has("spawnMode")) wave.setSpawnMode(WaveData.SpawnMode.valueOf(wObj.get("spawnMode").getAsString()));
					if (wObj.has("activationAreaName")) wave.setActivationAreaName(wObj.get("activationAreaName").getAsString());

					if (wObj.has("mobs")) {
						for (JsonElement mEl : wObj.getAsJsonArray("mobs")) {
							JsonObject mObj = mEl.getAsJsonObject();
							MobSpawnData mob = new MobSpawnData();
							if (mObj.has("category")) mob.setCategory(MobSpawnData.Category.valueOf(mObj.get("category").getAsString()));
							if (mObj.has("entityId")) mob.setEntityId(mObj.get("entityId").getAsString());
							if (mObj.has("customName")) mob.setCustomName(mObj.get("customName").getAsString());
							if (mObj.has("particleId")) mob.setParticleId(mObj.get("particleId").getAsString());
							if (mObj.has("particleIntensity")) mob.setParticleIntensity(mObj.get("particleIntensity").getAsFloat());
							if (mObj.has("soundId")) mob.setSoundId(mObj.get("soundId").getAsString());
							if (mObj.has("dropChance")) mob.setDropChance(mObj.get("dropChance").getAsDouble());
							if (mObj.has("lootTableName")) mob.setLootTableName(mObj.get("lootTableName").getAsString());
							if (mObj.has("scale")) mob.setScale(mObj.get("scale").getAsFloat());
							if (mObj.has("healthMultiplier")) mob.setHealthMultiplier(mObj.get("healthMultiplier").getAsFloat());
							if (mObj.has("attackMultiplier")) mob.setAttackMultiplier(mObj.get("attackMultiplier").getAsFloat());
							if (mObj.has("speedMultiplier")) mob.setSpeedMultiplier(mObj.get("speedMultiplier").getAsFloat());
							if (mObj.has("constantParticles")) mob.setConstantParticles(mObj.get("constantParticles").getAsBoolean());
							if (mObj.has("dropEquipment")) mob.setDropEquipment(mObj.get("dropEquipment").getAsBoolean());
							if (mObj.has("equipment") && mObj.get("equipment").isJsonObject()) {
								deserializeEquipment(mObj.getAsJsonObject("equipment"), mob.getEquipment());
							}
							if (mObj.has("relativePos")) mob.setRelativePos(deserializePos(mObj.getAsJsonObject("relativePos")));
							wave.addMob(mob);
						}
					}
					dStruct.getWaves().add(wave);
				}
			}

			if (dJson.has("chests")) {
				for (JsonElement cEl : dJson.getAsJsonArray("chests")) {
					JsonObject cObj = cEl.getAsJsonObject();
					ChestSpawnData chest = new ChestSpawnData();
					if (cObj.has("chestName")) chest.setChestName(cObj.get("chestName").getAsString());
					if (cObj.has("lootTableName")) chest.setLootTableName(cObj.get("lootTableName").getAsString());
					if (cObj.has("condition")) chest.setCondition(ChestSpawnData.SpawnCondition.valueOf(cObj.get("condition").getAsString()));
					if (cObj.has("waveOrder")) chest.setWaveOrder(cObj.get("waveOrder").getAsInt());
					if (cObj.has("targetAreaName")) chest.setTargetAreaName(cObj.get("targetAreaName").getAsString());
					if (cObj.has("facing")) {
						Direction dir = Direction.byName(cObj.get("facing").getAsString());
						if (dir != null) chest.setFacing(dir);
					}
					if (cObj.has("relativePos")) chest.setRelativePos(deserializePos(cObj.getAsJsonObject("relativePos")));
					dStruct.getChests().add(chest);
				}
			}

			if (dJson.has("areas")) {
				for (JsonElement aEl : dJson.getAsJsonArray("areas")) {
					JsonObject aObj = aEl.getAsJsonObject();
					AreaTriggerData area = new AreaTriggerData();
					if (aObj.has("name")) area.setName(aObj.get("name").getAsString());
					if (aObj.has("mode")) area.setMode(AreaTriggerData.Mode.valueOf(aObj.get("mode").getAsString()));
					if (aObj.has("waveOrder")) area.setWaveOrder(aObj.get("waveOrder").getAsInt());
					if (aObj.has("minPos")) area.setMinPos(deserializePos(aObj.getAsJsonObject("minPos")));
					if (aObj.has("maxPos")) area.setMaxPos(deserializePos(aObj.getAsJsonObject("maxPos")));
					if (aObj.has("targetWaveName")) area.setTargetWaveName(aObj.get("targetWaveName").getAsString());
					if (aObj.has("targetChestName")) area.setTargetChestName(aObj.get("targetChestName").getAsString());
					if (aObj.has("musicId")) area.setMusicId(aObj.get("musicId").getAsString());
					if (aObj.has("replaceMusic")) area.setReplaceMusic(aObj.get("replaceMusic").getAsBoolean());
					dStruct.getAreas().add(area);
				}
			}

			def.setDungeonStructure(dStruct);
		}

		return def;
	}

	private static JsonObject serializePortal(PortalData portal) {
		JsonObject p = new JsonObject();
		p.addProperty("type", portal.getType().name());
		p.add("minPos", serializePos(portal.getMinPos()));
		p.add("maxPos", serializePos(portal.getMaxPos()));
		return p;
	}

	private static PortalData deserializePortal(JsonObject json) {
		PortalData.Type type = json.has("type") ? PortalData.Type.valueOf(json.get("type").getAsString()) : PortalData.Type.ENTRANCE_WHITE;
		BlockPos min = json.has("minPos") ? deserializePos(json.getAsJsonObject("minPos")) : BlockPos.ZERO;
		BlockPos max = json.has("maxPos") ? deserializePos(json.getAsJsonObject("maxPos")) : BlockPos.ZERO;
		return new PortalData(type, min, max);
	}

	private static JsonObject serializePos(BlockPos pos) {
		JsonObject p = new JsonObject();
		p.addProperty("x", pos.getX());
		p.addProperty("y", pos.getY());
		p.addProperty("z", pos.getZ());
		return p;
	}

	private static BlockPos deserializePos(JsonObject json) {
		int x = json.has("x") ? json.get("x").getAsInt() : 0;
		int y = json.has("y") ? json.get("y").getAsInt() : 0;
		int z = json.has("z") ? json.get("z").getAsInt() : 0;
		return new BlockPos(x, y, z);
	}

	private static JsonObject serializeEquipment(Map<String, MobSpawnData.EquippedItemData> map) {
		JsonObject root = new JsonObject();
		if (map == null) return root;
		for (Map.Entry<String, MobSpawnData.EquippedItemData> entry : map.entrySet()) {
			if (entry.getValue() == null || entry.getValue().getItemId().isBlank()) continue;
			JsonObject itemObj = new JsonObject();
			itemObj.addProperty("itemId", entry.getValue().getItemId());
			itemObj.addProperty("durability", entry.getValue().getDurability());
			itemObj.addProperty("maxDamage", entry.getValue().getMaxDamage());
			itemObj.addProperty("dropChance", entry.getValue().getDropChance());
			JsonObject enchObj = new JsonObject();
			for (Map.Entry<String, Integer> ench : entry.getValue().getEnchantments().entrySet()) {
				enchObj.addProperty(ench.getKey(), ench.getValue());
			}
			itemObj.add("enchantments", enchObj);
			root.add(entry.getKey(), itemObj);
		}
		return root;
	}

	private static void deserializeEquipment(JsonObject root, Map<String, MobSpawnData.EquippedItemData> map) {
		if (root == null || map == null) return;
		map.clear();
		for (String slot : root.keySet()) {
			if (!root.get(slot).isJsonObject()) continue;
			JsonObject itemObj = root.getAsJsonObject(slot);
			MobSpawnData.EquippedItemData data = new MobSpawnData.EquippedItemData();
			if (itemObj.has("itemId")) data.setItemId(itemObj.get("itemId").getAsString());
			if (itemObj.has("durability")) data.setDurability(itemObj.get("durability").getAsInt());
			if (itemObj.has("maxDamage")) data.setMaxDamage(itemObj.get("maxDamage").getAsInt());
			if (itemObj.has("dropChance")) data.setDropChance(itemObj.get("dropChance").getAsDouble());
			if (itemObj.has("enchantments") && itemObj.get("enchantments").isJsonObject()) {
				JsonObject enchObj = itemObj.getAsJsonObject("enchantments");
				for (String eKey : enchObj.keySet()) {
					data.getEnchantments().put(eKey, enchObj.get(eKey).getAsInt());
				}
			}
			map.put(slot, data);
		}
	}
}
