package br.com.rpgsystem.config;

import br.com.rpgsystem.RpgMod;
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

/** Configuração independente de XP para níveis e escalonamento de mobs. */
public final class MobLevelConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JsonObject root;

	private MobLevelConfig() {}

	private static Path getConfigFile() {
		try {
			if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
				return FabricLoader.getInstance().getConfigDir().resolve("rpgsystem_mob_levels.json");
			}
		} catch (Throwable ignored) {}
		return Path.of("config", "rpgsystem_mob_levels.json");
	}

	public static void load() {
		Path file = getConfigFile();
		try {
			if (Files.exists(file)) root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			else { root = defaults(); save(file); }
		} catch (Exception exception) {
			root = defaults();
			RpgMod.LOGGER.warn("Não foi possível ler {}. Usando padrões.", file, exception);
		}
	}

	private static void save(Path file) throws IOException {
		if (file.getParent() != null) Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static JsonObject defaults() {
		JsonObject json = new JsonObject();
		json.add("fallback_range", range(new LevelRange(1, 5)));
		json.addProperty("health_per_level", 0.015D);
		json.addProperty("damage_per_level", 0.020D);
		json.addProperty("armor_per_level", 0.250D);
		json.addProperty("armor_bonus_cap", 20.0D);
		json.add("entity_overrides", defaultEntityOverrides());
		json.add("biome_ranges", ranges(MobLevelRanges.defaultBiomeRanges()));
		Map<String, LevelRange> structures = new LinkedHashMap<>();
		for (MobLevelRanges.StructureRange structure : MobLevelRanges.defaultStructureRanges()) structures.put(structure.id().toString(), structure.range());
		json.add("structure_ranges", ranges(structures));
		return json;
	}

	private static JsonObject defaultEntityOverrides() {
		JsonObject json = new JsonObject();
		json.addProperty("minecraft:warden", 100);
		json.addProperty("minecraft:ender_dragon", 90);
		json.addProperty("minecraft:wither", 80);
		return json;
	}

	private static JsonObject ranges(Map<String, LevelRange> values) {
		JsonObject json = new JsonObject();
		values.forEach((id, value) -> json.add(id, range(value)));
		return json;
	}

	private static JsonObject range(LevelRange value) {
		JsonObject json = new JsonObject(); json.addProperty("min", value.min()); json.addProperty("max", value.max()); return json;
	}

	private static LevelRange readRange(JsonElement element, LevelRange fallback) {
		if (element == null || !element.isJsonObject()) return fallback;
		try {
			JsonObject json = element.getAsJsonObject();
			if (!json.has("min") || !json.has("max")) return fallback;
			return new LevelRange(json.get("min").getAsInt(), json.get("max").getAsInt());
		} catch (RuntimeException ignored) { return fallback; }
	}

	public static LevelRange entityOverride(Identifier id) {
		if (id == null) return null;
		String key = id.toString();
		if (root != null && root.has("entity_overrides") && root.get("entity_overrides").isJsonObject()) {
			JsonObject overrides = root.getAsJsonObject("entity_overrides");
			if (overrides.has(key)) {
				JsonElement element = overrides.get(key);
				if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
					int val = element.getAsInt();
					return new LevelRange(val, val);
				}
				if (element.isJsonObject()) {
					return readRange(element, null);
				}
			}
		}
		// Padrões fixos conhecidos como fallback
		if ("minecraft:warden".equals(key)) return new LevelRange(100, 100);
		if ("minecraft:ender_dragon".equals(key)) return new LevelRange(90, 90);
		if ("minecraft:wither".equals(key)) return new LevelRange(80, 80);
		return null;
	}

	public static LevelRange fallbackRange() { return readRange(root == null ? null : root.get("fallback_range"), new LevelRange(1, 5)); }
	public static double healthPerLevel() { return number("health_per_level", 0.015D, 0D, 1D); }
	public static double damagePerLevel() { return number("damage_per_level", 0.020D, 0D, 1D); }
	public static double armorPerLevel() { return number("armor_per_level", 0.250D, 0D, 5D); }
	public static double armorBonusCap() { return number("armor_bonus_cap", 20D, 0D, 30D); }

	public static Map<String, LevelRange> biomeRanges() { return configuredRanges("biome_ranges", MobLevelRanges.defaultBiomeRanges()); }
	public static List<MobLevelRanges.StructureRange> structureRanges() {
		Map<String, LevelRange> configured = configuredRanges("structure_ranges", defaultStructures());
		return MobLevelRanges.defaultStructureRanges().stream()
				.map(entry -> new MobLevelRanges.StructureRange(entry.id(), configured.getOrDefault(entry.id().toString(), entry.range()))).toList();
	}

	private static Map<String, LevelRange> defaultStructures() {
		Map<String, LevelRange> values = new LinkedHashMap<>();
		for (MobLevelRanges.StructureRange entry : MobLevelRanges.defaultStructureRanges()) values.put(entry.id().toString(), entry.range());
		return values;
	}

	private static Map<String, LevelRange> configuredRanges(String key, Map<String, LevelRange> defaults) {
		Map<String, LevelRange> result = new LinkedHashMap<>(defaults);
		if (root == null || !root.has(key) || !root.get(key).isJsonObject()) return result;
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
			if (!defaults.containsKey(entry.getKey())) {
				RpgMod.LOGGER.warn("Ignorando faixa de {} desconhecida na configuração de níveis.", entry.getKey());
				continue;
			}
			result.put(entry.getKey(), readRange(entry.getValue(), defaults.get(entry.getKey())));
		}
		return result;
	}

	private static double number(String key, double fallback, double min, double max) {
		try { return Math.clamp(root.get(key).getAsDouble(), min, max); }
		catch (RuntimeException ignored) { return fallback; }
	}
}
