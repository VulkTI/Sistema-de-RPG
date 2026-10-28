package br.com.rpgsystem.affixes;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Grupos de itens aceitos pelos JSONs de afixo, incluindo interseções como "Leather Armors". */
public final class AffixItemGroups {
	private static final String MINECRAFT = "minecraft";
	private static final Map<String, Set<Identifier>> MAIN_GROUPS = createMainGroups();
	private static final Map<String, Set<Identifier>> CUSTOM_GROUPS = createCustomGroups();

	private AffixItemGroups() {}

	public static boolean matches(String groupName, Identifier itemId) {
		if (groupName == null || itemId == null) return false;
		String normalized = normalize(groupName);
		Set<Identifier> direct = MAIN_GROUPS.get(normalized);
		if (direct == null) direct = CUSTOM_GROUPS.get(normalized);
		if (direct != null) return direct.contains(itemId);

		String[] compound = normalized.split(" ", 2);
		if (compound.length != 2) return false;
		Set<Identifier> material = CUSTOM_GROUPS.get(compound[0]);
		Set<Identifier> main = MAIN_GROUPS.get(compound[1]);
		return material != null && main != null && material.contains(itemId) && main.contains(itemId);
	}

	public static Set<String> availableNames() {
		Set<String> names = new LinkedHashSet<>();
		names.addAll(MAIN_GROUPS.keySet());
		names.addAll(CUSTOM_GROUPS.keySet());
		return Set.copyOf(names);
	}

	private static Map<String, Set<Identifier>> createMainGroups() {
		Map<String, Set<Identifier>> groups = new LinkedHashMap<>();
		groups.put("swords", items(
				"wooden_sword", "stone_sword", "copper_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword"));
		groups.put("spears", items(
				"wooden_spear", "stone_spear", "copper_spear", "iron_spear", "golden_spear", "diamond_spear", "netherite_spear"));
		groups.put("pickaxes", items(
				"wooden_pickaxe", "stone_pickaxe", "copper_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe"));
		groups.put("axes", items(
				"wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe"));
		groups.put("shovels", items(
				"wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel"));
		groups.put("hoes", items(
				"wooden_hoe", "stone_hoe", "copper_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe"));
		groups.put("helmets", items(
				"leather_helmet", "copper_helmet", "chainmail_helmet", "iron_helmet", "golden_helmet", "diamond_helmet", "netherite_helmet"));
		groups.put("chestplates", items(
				"leather_chestplate", "copper_chestplate", "chainmail_chestplate", "iron_chestplate", "golden_chestplate", "diamond_chestplate", "netherite_chestplate"));
		groups.put("leggings", items(
				"leather_leggings", "copper_leggings", "chainmail_leggings", "iron_leggings", "golden_leggings", "diamond_leggings", "netherite_leggings"));
		groups.put("boots", items(
				"leather_boots", "copper_boots", "chainmail_boots", "iron_boots", "golden_boots", "diamond_boots", "netherite_boots"));

		Set<Identifier> armors = new LinkedHashSet<>();
		armors.addAll(groups.get("helmets"));
		armors.addAll(groups.get("chestplates"));
		armors.addAll(groups.get("leggings"));
		armors.addAll(groups.get("boots"));
		groups.put("armors", Set.copyOf(armors));
		return Map.copyOf(groups);
	}

	private static Map<String, Set<Identifier>> createCustomGroups() {
		Set<Identifier> allItems = new LinkedHashSet<>();
		MAIN_GROUPS.values().forEach(allItems::addAll);
		Set<Identifier> metal = allItems.stream()
				.filter(item -> List.of("copper_", "iron_", "golden_", "diamond_", "netherite_")
						.stream().anyMatch(prefix -> item.getPath().startsWith(prefix)))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<Identifier> leather = allItems.stream()
				.filter(item -> item.getPath().startsWith("leather_"))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return Map.of("metal", Set.copyOf(metal), "leather", Set.copyOf(leather));
	}

	private static Set<Identifier> items(String... paths) {
		return Set.of(java.util.Arrays.stream(paths)
				.map(path -> Identifier.fromNamespaceAndPath(MINECRAFT, path))
				.toArray(Identifier[]::new));
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
