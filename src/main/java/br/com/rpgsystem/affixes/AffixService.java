package br.com.rpgsystem.affixes;

import br.com.rpgsystem.RpgMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Operações de aplicação, remoção e rolagem de afixos. */
public final class AffixService {
	private static final String MODIFIER_PATH_PREFIX = "affix/";
	private static final double BASE_NO_AFFIX_CHANCE = 0.33;

	private AffixService() {}

	public static Optional<AffixDefinition> rollFor(ItemStack stack, ServerPlayer player) {
		if (stack.isEmpty()) return Optional.empty();
		return rollFor(stack, affixLuck(player), player.getRandom());
	}

	public static Optional<AffixDefinition> rollFor(ItemStack stack, double luck, RandomSource random) {
		if (stack.isEmpty()) return Optional.empty();
		List<AffixDefinition> candidates = AffixRepository.all().stream()
				.filter(definition -> definition.matches(stack.getItem()))
				.toList();
		if (candidates.isEmpty()) return Optional.empty();

		// Zero is neutral: the internal multiplier starts at 1 and receives the luck bonus.
		final double luckMultiplier = Math.max(0.0, 1.0 + luck);
		final double noAffixChance = Math.min(1.0,
				BASE_NO_AFFIX_CHANCE / Math.max(0.000001, luckMultiplier));
		if (random.nextDouble() < noAffixChance) return Optional.empty();

		double totalWeight = candidates.stream().mapToDouble(definition ->
				definition.rarity().baseWeight() * Math.pow(luckMultiplier, definition.rarity().ordinal())).sum();
		if (totalWeight <= 0.0) return Optional.empty();

		double target = random.nextDouble() * totalWeight;
		for (AffixDefinition candidate : candidates) {
			target -= candidate.rarity().baseWeight() * Math.pow(luckMultiplier, candidate.rarity().ordinal());
			if (target <= 0.0) return Optional.of(candidate);
		}
		return Optional.of(candidates.get(candidates.size() - 1));
	}

	public static boolean apply(ItemStack stack, AffixDefinition definition) {
		if (stack.isEmpty() || !definition.matches(stack.getItem())) return false;
		removeAffixModifiers(stack);
		stack.set(DataComponents.RARITY, definition.rarity().itemRarity());
		stack.set(DataComponents.CUSTOM_NAME,
				Component.translatable(stack.getItem().getDescriptionId()).withStyle(definition.rarity().style()));
		stack.set(AffixComponents.AFFIX, definition);

		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		ItemAttributeModifiers existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
		existing.modifiers().stream().filter(entry -> !isAffixModifier(entry.modifier())).forEach(entry ->
				builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display()));

		for (int index = 0; index < definition.modifiers().size(); index++) {
			AffixModifier modifier = definition.modifiers().get(index);
			Identifier attributeId = Identifier.tryParse(modifier.attribute());
			Optional<Holder.Reference<Attribute>> attribute = attributeId == null
					? Optional.empty() : BuiltInRegistries.ATTRIBUTE.get(attributeId);
			if (attribute.isEmpty()) continue;
			Identifier modifierId = RpgMod.id(MODIFIER_PATH_PREFIX + definition.id().getNamespace()
					+ "/" + definition.id().getPath().replace('/', '_') + "/" + index);
			builder.add(attribute.get(), new AttributeModifier(modifierId, modifier.amount(), modifier.operation()),
					modifier.slot());
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		return true;
	}

	public static boolean remove(ItemStack stack) {
		if (stack.get(AffixComponents.AFFIX) == null) return false;
		removeAffixModifiers(stack);
		stack.remove(AffixComponents.AFFIX);
		stack.remove(DataComponents.RARITY);
		stack.remove(DataComponents.CUSTOM_NAME);
		return true;
	}

	public static double affixLuck(ServerPlayer player) {
		AttributeInstance instance = player.getAttribute(RpgAttributes.AFFIX_LUCK);
		return instance == null ? 1.0 : Math.max(0.0, instance.getValue());
	}

	private static void removeAffixModifiers(ItemStack stack) {
		ItemAttributeModifiers existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
		List<ItemAttributeModifiers.Entry> kept = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : existing.modifiers()) {
			if (!isAffixModifier(entry.modifier())) kept.add(entry);
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept));
	}

	private static boolean isAffixModifier(AttributeModifier modifier) {
		return modifier.id().getNamespace().equals(br.com.rpgsystem.RpgMod.MOD_ID)
				&& modifier.id().getPath().startsWith(MODIFIER_PATH_PREFIX);
	}
}
