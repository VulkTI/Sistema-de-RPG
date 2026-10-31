package br.com.rpgsystem.skills;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Configuração editável em config/rpgsystem_universal_skills.json. */
public final class UniversalSkillConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = configFile();
	private static JsonObject root;
	private static final Map<UniversalSkill, long[]> XP = new EnumMap<>(UniversalSkill.class);

	private UniversalSkillConfig() {}

	public static void load() {
		try {
			if (FILE != null && Files.exists(FILE)) root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			else {
				root = defaults();
				if (FILE != null) { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8); }
			}
		} catch (Exception exception) {
			root = defaults();
			System.err.println("Não foi possível ler " + FILE + "; usando os valores padrão: " + exception.getMessage());
		}
		XP.clear();
		for (UniversalSkill skill : UniversalSkill.values()) {
			long[] values = new long[UniversalProgression.MAX_LEVEL - 1];
			JsonObject section = section(skill.id());
			for (int i = 0; i < values.length; i++) {
				long fallback = defaults().getAsJsonObject(skill.id()).getAsJsonArray("xp_to_next").get(i).getAsLong();
				values[i] = Math.max(1L, section.has("xp_to_next") && section.getAsJsonArray("xp_to_next").size() > i
						? section.getAsJsonArray("xp_to_next").get(i).getAsLong() : fallback);
			}
			XP.put(skill, values);
		}
	}

	private static Path configFile() {
		try { return FabricLoader.getInstance().getConfigDir().resolve("rpgsystem_universal_skills.json"); }
		catch (Throwable ignored) { return null; }
	}

	public static long xpToNext(UniversalSkill skill, int level) {
		long[] values = XP.get(skill);
		if (values == null) { load(); values = XP.get(skill); }
		return values[Math.clamp(level, 1, UniversalProgression.MAX_LEVEL) - 1];
	}

	public static int activityXp(UniversalSkill skill, String activity, int fallback) {
		JsonObject section = section(skill.id());
		JsonObject activities = section.has("activities") && section.get("activities").isJsonObject()
				? section.getAsJsonObject("activities") : new JsonObject();
		return Math.max(0, activities.has(activity) ? activities.get(activity).getAsInt() : fallback);
	}

	public static double activityMultiplier(UniversalSkill skill, String activity, double fallback) {
		JsonObject section = section(skill.id());
		JsonObject multipliers = section.has("multipliers") && section.get("multipliers").isJsonObject()
				? section.getAsJsonObject("multipliers") : new JsonObject();
		return multipliers.has(activity) ? Math.max(0D, multipliers.get(activity).getAsDouble()) : fallback;
	}

	public static double bonus(UniversalSkill skill, String name, double fallback) {
		JsonObject values = section(skill.id());
		JsonObject bonuses = values.has("bonuses") && values.get("bonuses").isJsonObject()
				? values.getAsJsonObject("bonuses") : new JsonObject();
		return bonuses.has(name) ? Math.max(0D, bonuses.get(name).getAsDouble()) : fallback;
	}

	public static double structureXp(String id, double fallback) {
		JsonObject structures = root.has("exploracao") ? section("exploracao").getAsJsonObject("structures") : new JsonObject();
		return structures.has(id) ? Math.max(0D, structures.get(id).getAsDouble()) : fallback;
	}

	public static double genericStructureXp() { return number("exploracao", "generic_structure_xp", 20D); }
	public static double firstStructureMultiplier() { return number("exploracao", "first_structure_multiplier", 2D); }
	public static double peacefulCombatMultiplier() { return number("combate", "peaceful_multiplier", 0.25D); }

	private static double number(String skill, String key, double fallback) {
		JsonObject section = section(skill);
		return section.has(key) ? Math.max(0D, section.get(key).getAsDouble()) : fallback;
	}

	private static JsonObject section(String name) { return root != null && root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : new JsonObject(); }

	private static JsonObject defaults() {
		try (var in = UniversalSkillConfig.class.getResourceAsStream("/data/rpgsystem/universal_skills.json")) {
			if (in != null) return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception ignored) {}
		JsonObject fallback = new JsonObject();
		for (UniversalSkill skill : UniversalSkill.values()) {
			JsonObject section = new JsonObject();
			var xp = new com.google.gson.JsonArray();
			for (int level = 1; level < UniversalProgression.MAX_LEVEL; level++) xp.add(250L + level * level * 75L);
			section.add("xp_to_next", xp);
			fallback.add(skill.id(), section);
		}
		return fallback;
	}
}
