package br.com.rpgsystem.affixes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.List;
import java.util.function.Consumer;

/** Definição imutável de um afixo, usada tanto pelo JSON quanto pelo componente do item. */
public record AffixDefinition(Identifier id, String name, AffixRarity rarity, List<String> groups,
		List<String> items, List<String> tags, String description, List<AffixModifier> modifiers) implements TooltipProvider {
	private static final Identifier UNRESOLVED_ID = Identifier.fromNamespaceAndPath("rpgsystem", "unresolved");

	/** Codec dos arquivos data/<namespace>/affixes/*.json. O ID vem do nome do arquivo. */
	public static final Codec<AffixDefinition> RESOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(AffixDefinition::name),
			AffixRarity.CODEC.fieldOf("rarity").forGetter(AffixDefinition::rarity),
			Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(AffixDefinition::groups),
			Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(AffixDefinition::items),
			Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(AffixDefinition::tags),
			Codec.STRING.optionalFieldOf("description", "").forGetter(AffixDefinition::description),
			AffixModifier.CODEC.listOf().fieldOf("modifiers").forGetter(AffixDefinition::modifiers)
	).apply(instance, (name, rarity, groups, items, tags, description, modifiers) ->
			new AffixDefinition(UNRESOLVED_ID, name, rarity, groups, items, tags, description, modifiers)));

	/** Codec persistente do componente rpgsystem:affix. */
	public static final Codec<AffixDefinition> COMPONENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("id").forGetter(AffixDefinition::id),
			Codec.STRING.fieldOf("name").forGetter(AffixDefinition::name),
			AffixRarity.CODEC.fieldOf("rarity").forGetter(AffixDefinition::rarity),
			Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(AffixDefinition::groups),
			Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(AffixDefinition::items),
			Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(AffixDefinition::tags),
			Codec.STRING.optionalFieldOf("description", "").forGetter(AffixDefinition::description),
			AffixModifier.CODEC.listOf().fieldOf("modifiers").forGetter(AffixDefinition::modifiers)
	).apply(instance, AffixDefinition::new));

	public AffixDefinition {
		if (id == null || name == null || name.isBlank() || rarity == null) {
			throw new IllegalArgumentException("ID, nome e raridade são obrigatórios em um afixo.");
		}
		if ((groups == null || groups.isEmpty()) && (items == null || items.isEmpty()) && (tags == null || tags.isEmpty())) {
			throw new IllegalArgumentException("Um afixo precisa de pelo menos um grupo, tag ou item específico: " + name);
		}
		if (modifiers == null || modifiers.isEmpty()) {
			throw new IllegalArgumentException("Um afixo precisa de pelo menos um modificador: " + name);
		}
		name = name.trim();
		description = description == null ? "" : description.trim();
		groups = groups == null ? List.of() : groups.stream().map(String::trim).toList();
		items = items == null ? List.of() : items.stream().map(String::trim).toList();
		tags = tags == null ? List.of() : tags.stream().map(String::trim).toList();
		if (groups.stream().anyMatch(String::isBlank) || items.stream().anyMatch(String::isBlank) || tags.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("Grupos, tags e itens específicos não podem ser vazios: " + name);
		}
		modifiers = List.copyOf(modifiers);
	}

	public AffixDefinition withId(Identifier resourceId) {
		return new AffixDefinition(resourceId, name, rarity, groups, items, tags, description, modifiers);
	}

	public boolean matches(Item item) {
		if (item == null) return false;
		Identifier itemId = BuiltInRegistries.ITEM.getKey(item);

		// 1. Testa itens diretos (incluindo tags caso o usuário tenha escrito "#tag" em items)
		for (String itemEntry : items) {
			if (itemEntry.startsWith("#")) {
				TagKey<Item> tagKey = AffixItemGroups.parseItemTag(itemEntry);
				if (tagKey != null && AffixItemGroups.isItemInTag(itemId, item, tagKey)) return true;
			} else {
				Identifier expected = Identifier.tryParse(itemEntry);
				if (expected != null && expected.equals(itemId)) return true;
			}
		}

		// 2. Testa tags diretas declaradas em "tags"
		for (String tagEntry : tags) {
			TagKey<Item> tagKey = AffixItemGroups.parseItemTag(tagEntry);
			if (tagKey != null && AffixItemGroups.isItemInTag(itemId, item, tagKey)) return true;
		}

		// 3. Testa grupos (que suportam nomes de grupos, grupos compostos e "#tags")
		return groups.stream().anyMatch(group -> AffixItemGroups.matches(group, itemId, item));
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag,
			net.minecraft.core.component.DataComponentGetter components) {
		consumer.accept(Component.literal("Afixo: " + name + " [" + rarity.displayName() + "]")
				.withStyle(rarity.style()));
		String tooltipDescription = displayDescription();
		if (AffixTooltipState.isShiftDown() && !tooltipDescription.isBlank()) {
			consumer.accept(Component.literal(tooltipDescription).withStyle(net.minecraft.ChatFormatting.WHITE));
		} else if (!AffixTooltipState.isShiftDown() && !tooltipDescription.isBlank()) {
			consumer.accept(Component.literal("[SHIFT] para exibir").withStyle(net.minecraft.ChatFormatting.GRAY,
					net.minecraft.ChatFormatting.ITALIC));
		}
	}

	/** Compatibilidade: afixos antigos guardavam uma descrição em cada modificador. */
	private String displayDescription() {
		if (!description.isBlank()) return description;
		return modifiers.stream().map(AffixModifier::description)
				.filter(text -> text != null && !text.isBlank())
				.distinct().collect(java.util.stream.Collectors.joining(" "));
	}
}
