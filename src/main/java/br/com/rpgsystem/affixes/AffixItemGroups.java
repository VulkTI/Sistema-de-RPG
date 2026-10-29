package br.com.rpgsystem.affixes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Grupos de itens aceitos pelos JSONs de afixo, incluindo interseções como "Leather Armors". */
public final class AffixItemGroups extends SimpleJsonResourceReloadListener<AffixItemGroups.JsonGroup>
		implements IdentifiableResourceReloadListener {
	private static final String MINECRAFT = "minecraft";
	private static final Map<String, Set<Identifier>> MAIN_GROUPS = createMainGroups();
	private static final Map<String, Set<Identifier>> CUSTOM_GROUPS = createCustomGroups();
	private static volatile Map<String, JsonGroup> JSON_GROUPS = Map.of();

	private AffixItemGroups() { super(JsonGroup.CODEC, FileToIdConverter.json("affix_groups")); }

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new AffixItemGroups());
	}

	public static boolean matches(String groupName, Identifier itemId) {
		if (groupName == null || itemId == null) return false;
		String normalized = normalize(groupName);
		String[] compound = normalized.split(" ", 2);
		if (compound.length == 1) return resolve(compound[0], new LinkedHashSet<>()).contains(itemId);
		return resolve(compound[0], new LinkedHashSet<>()).contains(itemId)
				&& resolve(compound[1], new LinkedHashSet<>()).contains(itemId);
	}

	private static Set<Identifier> resolve(String name, Set<String> visiting) {
		String normalized = normalize(name);
		if (normalized.equals("all")) {
			Set<Identifier> all = new LinkedHashSet<>();
			MAIN_GROUPS.values().forEach(all::addAll);
			CUSTOM_GROUPS.values().forEach(all::addAll);
			for (String group : JSON_GROUPS.keySet()) {
				if (!group.equals("all")) all.addAll(resolve(group, new LinkedHashSet<>()));
			}
			return Set.copyOf(all);
		}
		Set<Identifier> direct = MAIN_GROUPS.get(normalized);
		if (direct == null) direct = CUSTOM_GROUPS.get(normalized);
		if (direct != null) return direct;
		JsonGroup json = JSON_GROUPS.get(normalized);
		if (json == null || !visiting.add(normalized)) return Set.of();
		Set<Identifier> result = new LinkedHashSet<>(json.items());
		for (String reference : json.groups()) result.addAll(resolve(reference, visiting));
		visiting.remove(normalized);
		return Set.copyOf(result);
	}

	public static Set<String> availableNames() {
		Set<String> names = new LinkedHashSet<>();
		names.addAll(MAIN_GROUPS.keySet());
		names.addAll(CUSTOM_GROUPS.keySet());
		names.addAll(JSON_GROUPS.keySet());
		return Set.copyOf(names);
	}

	@Override public Identifier getFabricId() { return Identifier.fromNamespaceAndPath("rpgsystem", "affix_groups"); }
	@Override public java.util.Collection<Identifier> getFabricDependencies() { return List.of(); }
	@Override protected void apply(Map<Identifier, JsonGroup> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<String, JsonGroup> groups = new LinkedHashMap<>();
		loaded.forEach((id, group) -> groups.put(normalize(id.getPath()), group));
		JSON_GROUPS = Map.copyOf(groups);
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

	public record JsonGroup(List<Identifier> items, List<String> groups) {
		public static final Codec<JsonGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(JsonGroup::items),
				Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(JsonGroup::groups)
		).apply(instance, JsonGroup::new));

		public JsonGroup {
			items = items == null ? List.of() : List.copyOf(items);
			groups = groups == null ? List.of() : groups.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
		}
	}
}
