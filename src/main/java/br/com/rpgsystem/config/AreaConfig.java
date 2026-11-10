package br.com.rpgsystem.config;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.mining.MiningLocation;
import br.com.rpgsystem.mining.MiningZone;
import br.com.rpgsystem.mobs.LevelRange;
import br.com.rpgsystem.mobs.MobLevelRanges;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuração unificada e centralizada de áreas (biomas e estruturas) em config/rpgsystem_areas.json.
 * Fornece faixas de nível para mobs e zonas/níveis para mineração a partir do mesmo arquivo.
 */
public final class AreaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JsonObject root;

	private static final Map<String, LevelRange> BIOME_RANGES = new LinkedHashMap<>();
	private static final Map<Identifier, MiningLocation> MINING_BIOMES = new LinkedHashMap<>();
	private static final List<MobLevelRanges.StructureRange> STRUCTURE_RANGES = new java.util.ArrayList<>();
	private static final List<MiningStructureRule> MINING_STRUCTURES = new java.util.ArrayList<>();

	public record MiningStructureRule(Identifier id, MiningLocation location) {}

	private AreaConfig() {}

	private static Path getConfigFile() {
		try {
			if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
				return FabricLoader.getInstance().getConfigDir().resolve("rpgsystem_areas.json");
			}
		} catch (Throwable ignored) {}
		return Path.of("config", "rpgsystem_areas.json");
	}

	public static synchronized void load() {
		Path file = getConfigFile();
		try {
			if (Files.exists(file)) {
				root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			} else {
				root = defaults();
				save(file);
			}
		} catch (Exception exception) {
			root = defaults();
			RpgMod.LOGGER.warn("Não foi possível ler {}. Usando padrões.", file, exception);
		}
		parseCache();
	}

	private static void ensureLoaded() {
		if (root == null) {
			load();
		}
	}

	private static void save(Path file) throws IOException {
		if (file.getParent() != null) Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static JsonObject defaults() {
		try (java.io.InputStream in = AreaConfig.class.getResourceAsStream("/data/rpgsystem/areas.json")) {
			if (in != null) {
				return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (Exception exception) {
			RpgMod.LOGGER.warn("Não foi possível carregar /data/rpgsystem/areas.json embutido.", exception);
		}

		JsonObject json = new JsonObject();
		JsonObject biomes = new JsonObject();
		MobLevelRanges.defaultBiomeRanges().forEach((biomeId, range) -> {
			JsonObject b = new JsonObject();
			b.addProperty("min_level", range.min());
			b.addProperty("max_level", range.max());
			MiningZone zone = deriveZone(biomeId, range.min());
			b.addProperty("mining_zone", zone.name());
			biomes.add(biomeId, b);
		});
		json.add("biomes", biomes);

		JsonObject structures = new JsonObject();
		for (MobLevelRanges.StructureRange s : MobLevelRanges.defaultStructureRanges()) {
			JsonObject st = new JsonObject();
			st.addProperty("min_level", s.range().min());
			st.addProperty("max_level", s.range().max());
			MiningZone zone = deriveStructureMiningZone(s.id().getPath());
			st.addProperty("mining_zone", zone.name());
			structures.add(s.id().toString(), st);
		}
		json.add("structures", structures);
		return json;
	}

	private static MiningZone deriveZone(String biomeId, int minLevel) {
		if (biomeId.startsWith("minecraft:")) biomeId = biomeId.substring("minecraft:".length());
		if (biomeId.contains("the_end") || biomeId.contains("end_")) return MiningZone.NONE;
		if (biomeId.contains("nether") || biomeId.contains("warped") || biomeId.contains("crimson")
				|| biomeId.contains("soul_sand") || biomeId.contains("basalt")) {
			return MiningZone.ZONE_4;
		}
		if (minLevel >= 35) return MiningZone.ZONE_3;
		if (minLevel >= 11) return MiningZone.ZONE_2;
		return MiningZone.ZONE_1;
	}

	private static MiningZone deriveStructureMiningZone(String path) {
		return switch (path) {
			case "ancient_city", "trial_chambers" -> MiningZone.ZONE_3;
			case "fortress", "bastion_remnant" -> MiningZone.ZONE_4;
			default -> MiningZone.NONE;
		};
	}

	private static void parseCache() {
		BIOME_RANGES.clear();
		MINING_BIOMES.clear();
		STRUCTURE_RANGES.clear();
		MINING_STRUCTURES.clear();

		if (root == null) root = defaults();

		if (root.has("biomes") && root.get("biomes").isJsonObject()) {
			JsonObject biomes = root.getAsJsonObject("biomes");
			for (Map.Entry<String, JsonElement> entry : biomes.entrySet()) {
				String key = entry.getKey();
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject obj = entry.getValue().getAsJsonObject();
				int min = obj.has("min_level") ? obj.get("min_level").getAsInt() : 1;
				int max = obj.has("max_level") ? obj.get("max_level").getAsInt() : min;
				String zoneStr = obj.has("mining_zone") ? obj.get("mining_zone").getAsString() : null;
				MiningZone zone = parseMiningZone(zoneStr, deriveZone(key, min));

				LevelRange range = new LevelRange(min, max);
				BIOME_RANGES.put(key, range);

				Identifier id = Identifier.tryParse(key);
				if (id != null) {
					MINING_BIOMES.put(id, new MiningLocation(zone, min));
				}
			}
		}

		if (root.has("structures") && root.get("structures").isJsonObject()) {
			JsonObject structures = root.getAsJsonObject("structures");
			for (Map.Entry<String, JsonElement> entry : structures.entrySet()) {
				String key = entry.getKey();
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject obj = entry.getValue().getAsJsonObject();
				int min = obj.has("min_level") ? obj.get("min_level").getAsInt() : 1;
				int max = obj.has("max_level") ? obj.get("max_level").getAsInt() : min;
				Identifier id = Identifier.tryParse(key);
				if (id == null) continue;

				LevelRange range = new LevelRange(min, max);
				STRUCTURE_RANGES.add(new MobLevelRanges.StructureRange(id, range));

				String zoneStr = obj.has("mining_zone") ? obj.get("mining_zone").getAsString() : null;
				MiningZone zone = parseMiningZone(zoneStr, deriveStructureMiningZone(id.getPath()));
				MINING_STRUCTURES.add(new MiningStructureRule(id, new MiningLocation(zone, min)));
			}
		}
	}

	private static MiningZone parseMiningZone(String name, MiningZone fallback) {
		if (name == null) return fallback;
		try {
			return MiningZone.valueOf(name.trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}

	public static Map<String, LevelRange> biomeRanges() {
		ensureLoaded();
		return Map.copyOf(BIOME_RANGES);
	}

	public static List<MobLevelRanges.StructureRange> structureRanges() {
		ensureLoaded();
		return List.copyOf(STRUCTURE_RANGES);
	}

	public static MiningLocation miningLocationForBiome(Identifier id) {
		ensureLoaded();
		return MINING_BIOMES.getOrDefault(id, MiningLocation.UNDEFINED);
	}

	public static List<MiningStructureRule> miningStructures() {
		ensureLoaded();
		return List.copyOf(MINING_STRUCTURES);
	}

	public static Map<Identifier, MiningLocation> allMiningBiomes() {
		ensureLoaded();
		return Map.copyOf(MINING_BIOMES);
	}
}
