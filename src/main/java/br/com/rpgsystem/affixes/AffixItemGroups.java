package br.com.rpgsystem.affixes;

import br.com.rpgsystem.RpgMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Grupos de itens aceitos pelos JSONs de afixo, carregados dinamicamente de item_groups/. */
public final class AffixItemGroups extends SimpleJsonResourceReloadListener<AffixItemGroups.JsonGroup>
		implements IdentifiableResourceReloadListener {
	private static volatile Map<String, JsonGroup> JSON_GROUPS = Map.of();

	private AffixItemGroups() {
		super(JsonGroup.CODEC, FileToIdConverter.json("item_groups"));
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new AffixItemGroups());
	}

	public static boolean matches(String groupName, Identifier itemId) {
		return matches(groupName, itemId, null);
	}

	public static boolean matches(String groupName, Item item) {
		if (item == null) return false;
		return matches(groupName, BuiltInRegistries.ITEM.getKey(item), item);
	}

	public static boolean matches(String groupName, Identifier itemId, Item item) {
		if (groupName == null || itemId == null) return false;
		String trimmed = groupName.trim();

		// 1. Tag direta iniciada por '#' (ex: "#minecraft:swords", "#c:tools")
		if (trimmed.startsWith("#")) {
			TagKey<Item> tagKey = parseItemTag(trimmed);
			return tagKey != null && isItemInTag(itemId, item, tagKey);
		}

		String normalized = normalize(trimmed);

		// 2. Grupo composto por espaço (ex: "leather armors", "metal #minecraft:swords")
		//    Testa interseção: o item precisa satisfazer TODOS os termos.
		String[] compound = normalized.split(" ", 2);
		if (compound.length > 1) {
			boolean firstMatch = matchesPart(compound[0], itemId, item);
			boolean secondMatch = matchesPart(compound[1], itemId, item);
			return firstMatch && secondMatch;
		}

		// 3. Grupo simples — verifica diretamente via tags e itens do grupo.
		return matchesGroup(normalized, itemId, item, new LinkedHashSet<>());
	}

	/**
	 * Verifica se o item pertence ao grupo pelo nome normalizado.
	 * Usa isItemInTag() para tags (verificação direta no holder do item, sem expandir o registry),
	 * garantindo que funcione mesmo com tags de data pack carregadas dinamicamente.
	 */
	private static boolean matchesGroup(String normalized, Identifier itemId, Item item, Set<String> visiting) {
		if (normalized.equals("all")) {
			for (String group : JSON_GROUPS.keySet()) {
				if (!group.equals("all") && matchesGroup(group, itemId, item, new LinkedHashSet<>())) {
					return true;
				}
			}
			return false;
		}
		if (normalized.startsWith("#")) {
			TagKey<Item> tagKey = parseItemTag(normalized);
			return tagKey != null && isItemInTag(itemId, item, tagKey);
		}
		JsonGroup json = JSON_GROUPS.get(normalized);
		if (json == null || !visiting.add(normalized)) return false;

		// Itens diretos (incluindo "#tag" no campo items)
		for (String itemEntry : json.items()) {
			if (itemEntry.startsWith("#")) {
				TagKey<Item> tagKey = parseItemTag(itemEntry);
				if (tagKey != null && isItemInTag(itemId, item, tagKey)) {
					visiting.remove(normalized);
					return true;
				}
			} else {
				Identifier expected = Identifier.tryParse(itemEntry);
				if (expected != null && expected.equals(itemId)) {
					visiting.remove(normalized);
					return true;
				}
			}
		}

		// Tags do campo "tags" — verificação direta no holder do item
		for (String tagEntry : json.tags()) {
			TagKey<Item> tagKey = parseItemTag(tagEntry);
			if (tagKey != null && isItemInTag(itemId, item, tagKey)) {
				visiting.remove(normalized);
				return true;
			}
		}

		// Grupos aninhados
		for (String reference : json.groups()) {
			if (matchesGroup(reference.startsWith("#") ? reference : normalize(reference), itemId, item, visiting)) {
				visiting.remove(normalized);
				return true;
			}
		}

		visiting.remove(normalized);
		return false;
	}

	private static boolean matchesPart(String part, Identifier itemId, Item item) {
		if (part.startsWith("#")) {
			TagKey<Item> tagKey = parseItemTag(part);
			return tagKey != null && isItemInTag(itemId, item, tagKey);
		}
		return matchesGroup(part, itemId, item, new LinkedHashSet<>());
	}

	public static boolean isItemInTag(Identifier itemId, Item item, TagKey<Item> tagKey) {
		if (tagKey == null) return false;
		if (item != null) return item.builtInRegistryHolder().is(tagKey);
		return BuiltInRegistries.ITEM.get(itemId).map(holder -> holder.is(tagKey)).orElse(false);
	}

	public static TagKey<Item> parseItemTag(String tagString) {
		if (tagString == null || tagString.isBlank()) return null;
		String clean = tagString.trim();
		if (clean.startsWith("#")) clean = clean.substring(1).trim();
		Identifier id = Identifier.tryParse(clean.contains(":") ? clean : "minecraft:" + clean);
		return id != null ? TagKey.create(Registries.ITEM, id) : null;
	}

	private static Set<Identifier> resolveTagItems(TagKey<Item> tagKey) {
		if (tagKey == null) return Set.of();
		Set<Identifier> items = new LinkedHashSet<>();
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
			Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
			if (id != null) items.add(id);
		}
		return items;
	}

	public static Set<Identifier> resolve(String name, Set<String> visiting) {
		String normalized = normalize(name);
		if (normalized.equals("all")) {
			Set<Identifier> all = new LinkedHashSet<>();
			for (String group : JSON_GROUPS.keySet()) {
				if (!group.equals("all")) {
					all.addAll(resolve(group, new LinkedHashSet<>()));
				}
			}
			return Set.copyOf(all);
		}
		if (normalized.startsWith("#")) {
			TagKey<Item> tagKey = parseItemTag(normalized);
			return resolveTagItems(tagKey);
		}
		JsonGroup json = JSON_GROUPS.get(normalized);
		if (json == null || !visiting.add(normalized)) return Set.of();
		Set<Identifier> result = new LinkedHashSet<>();

		// Processa itens diretos (incluindo se algum item for uma tag "#tag")
		for (String itemEntry : json.items()) {
			if (itemEntry.startsWith("#")) {
				result.addAll(resolveTagItems(parseItemTag(itemEntry)));
			} else {
				Identifier itemId = Identifier.tryParse(itemEntry);
				if (itemId != null) result.add(itemId);
			}
		}

		// Processa tags do campo "tags"
		for (String tagEntry : json.tags()) {
			result.addAll(resolveTagItems(parseItemTag(tagEntry)));
		}

		// Processa grupos aninhados
		for (String reference : json.groups()) {
			if (reference.startsWith("#")) {
				result.addAll(resolveTagItems(parseItemTag(reference)));
			} else {
				result.addAll(resolve(reference, visiting));
			}
		}

		visiting.remove(normalized);
		return Set.copyOf(result);
	}

	public static Set<String> availableNames() {
		Set<String> names = new LinkedHashSet<>(JSON_GROUPS.keySet());
		names.add("all");
		return Set.copyOf(names);
	}

	@Override
	public Identifier getFabricId() {
		return RpgMod.id("item_groups");
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of();
	}

	@Override
	protected void apply(Map<Identifier, JsonGroup> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<String, JsonGroup> groups = new LinkedHashMap<>();
		loaded.forEach((id, group) -> groups.put(normalize(id.getPath()), group));
		JSON_GROUPS = Map.copyOf(groups);
		RpgMod.LOGGER.info("{} grupo(s) de itens carregado(s).", JSON_GROUPS.size());
	}

	private static String normalize(String value) {
		String trimmed = value.trim().toLowerCase(Locale.ROOT);
		if (trimmed.startsWith("#")) return trimmed;
		return trimmed.replace('_', ' ');
	}

	public record JsonGroup(List<String> items, List<String> groups, List<String> tags) {
		public static final Codec<JsonGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(JsonGroup::items),
				Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(JsonGroup::groups),
				Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(JsonGroup::tags)
		).apply(instance, JsonGroup::new));

		public JsonGroup {
			items = items == null ? List.of() : items.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
			groups = groups == null ? List.of() : groups.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
			tags = tags == null ? List.of() : tags.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
		}
	}
}
