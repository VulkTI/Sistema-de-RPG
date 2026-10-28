package br.com.rpgsystem.affixes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.List;
import java.util.function.Consumer;

/** Definição imutável de um afixo, usada tanto pelo JSON quanto pelo componente do item. */
public record AffixDefinition(Identifier id, String name, AffixRarity rarity, List<String> groups,
		List<AffixModifier> modifiers) implements TooltipProvider {
	private static final Identifier UNRESOLVED_ID = Identifier.fromNamespaceAndPath("rpgsystem", "unresolved");

	/** Codec dos arquivos data/<namespace>/affixes/*.json. O ID vem do nome do arquivo. */
	public static final Codec<AffixDefinition> RESOURCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(AffixDefinition::name),
			AffixRarity.CODEC.fieldOf("rarity").forGetter(AffixDefinition::rarity),
			Codec.STRING.listOf().fieldOf("groups").forGetter(AffixDefinition::groups),
			AffixModifier.CODEC.listOf().fieldOf("modifiers").forGetter(AffixDefinition::modifiers)
	).apply(instance, (name, rarity, groups, modifiers) ->
			new AffixDefinition(UNRESOLVED_ID, name, rarity, groups, modifiers)));

	/** Codec persistente do componente rpgsystem:affix. */
	public static final Codec<AffixDefinition> COMPONENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("id").forGetter(AffixDefinition::id),
			Codec.STRING.fieldOf("name").forGetter(AffixDefinition::name),
			AffixRarity.CODEC.fieldOf("rarity").forGetter(AffixDefinition::rarity),
			Codec.STRING.listOf().fieldOf("groups").forGetter(AffixDefinition::groups),
			AffixModifier.CODEC.listOf().fieldOf("modifiers").forGetter(AffixDefinition::modifiers)
	).apply(instance, AffixDefinition::new));

	public AffixDefinition {
		if (id == null || name == null || name.isBlank() || rarity == null) {
			throw new IllegalArgumentException("ID, nome e raridade são obrigatórios em um afixo.");
		}
		if (groups == null || groups.isEmpty() || groups.stream().anyMatch(group -> group == null || group.isBlank())) {
			throw new IllegalArgumentException("Um afixo precisa de pelo menos um grupo de itens: " + name);
		}
		if (modifiers == null || modifiers.isEmpty()) {
			throw new IllegalArgumentException("Um afixo precisa de pelo menos um modificador: " + name);
		}
		name = name.trim();
		groups = groups.stream().map(String::trim).toList();
		modifiers = List.copyOf(modifiers);
	}

	public AffixDefinition withId(Identifier resourceId) {
		return new AffixDefinition(resourceId, name, rarity, groups, modifiers);
	}

	public boolean matches(Item item) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
		return groups.stream().anyMatch(group -> AffixItemGroups.matches(group, itemId));
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag,
			net.minecraft.core.component.DataComponentGetter components) {
		consumer.accept(Component.literal("Afixo: " + name + " [" + rarity.displayName() + "]")
				.withStyle(rarity.style()));
	}
}
