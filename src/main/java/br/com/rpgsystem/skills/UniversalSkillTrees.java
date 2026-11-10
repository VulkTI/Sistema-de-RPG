package br.com.rpgsystem.skills;

import br.com.rpgsystem.classes.SkillDefinition;
import br.com.rpgsystem.classes.SkillType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Árvores universais carregadas de JSON, sem vínculo com as árvores de classe. */
public final class UniversalSkillTrees {
	private static final Map<UniversalSkill, List<SkillDefinition>> TREES = new EnumMap<>(UniversalSkill.class);
	private UniversalSkillTrees() {}

	public static void load() {
		TREES.clear();
		for (UniversalSkill skill : UniversalSkill.values()) TREES.put(skill, read(skill));
	}

	public static List<SkillDefinition> tree(UniversalSkill skill) {
		if (TREES.isEmpty()) load();
		return TREES.getOrDefault(skill, List.of());
	}

	private static List<SkillDefinition> read(UniversalSkill skill) {
		String file = switch (skill) {
			case COMBATE -> "combat";
			case MINERACAO -> "mining";
			case AGRICULTURA -> "farming";
			case CONHECIMENTO -> "knowledge";
			case EXPLORACAO -> "exploration";
			case COMERCIO -> "trading";
		};
		try (var in = UniversalSkillTrees.class.getResourceAsStream("/data/rpgsystem/universal_skills/" + file + ".json")) {
			if (in == null) return List.of();
			JsonArray nodes = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("nodes");
			java.util.ArrayList<SkillDefinition> result = new java.util.ArrayList<>();
			for (int i = 0; i < nodes.size(); i++) {
				JsonObject node = nodes.get(i).getAsJsonObject();
				java.util.ArrayList<String> dependencies = new java.util.ArrayList<>();
				JsonArray deps = node.has("dependencies") ? node.getAsJsonArray("dependencies") : new JsonArray();
				for (int j = 0; j < deps.size(); j++) dependencies.add(deps.get(j).getAsString());
				result.add(new SkillDefinition(node.get("id").getAsString(), node.get("name").getAsString(),
						node.get("description").getAsString(), node.get("cost").getAsInt(),
						SkillType.valueOf(node.get("type").getAsString()), dependencies,
						node.get("effect").getAsString(), node.get("amount").getAsDouble()));
			}
			return List.copyOf(result);
		} catch (Exception exception) {
			throw new IllegalStateException("Árvore universal inválida: " + skill.id(), exception);
		}
	}
}
