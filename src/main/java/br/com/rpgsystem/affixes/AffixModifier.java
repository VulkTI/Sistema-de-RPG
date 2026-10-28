package br.com.rpgsystem.affixes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Locale;

/** Um modificador de afixo: atributo, operação, valor, slot e texto explicativo. */
public record AffixModifier(String attribute, double amount, AttributeModifier.Operation operation,
		EquipmentSlotGroup slot, String description) {
	public static final Codec<AffixModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("attribute").forGetter(AffixModifier::attribute),
			Codec.DOUBLE.fieldOf("amount").forGetter(AffixModifier::amount),
			AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
					.forGetter(AffixModifier::operation),
			EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY)
					.forGetter(AffixModifier::slot),
			Codec.STRING.fieldOf("description").forGetter(AffixModifier::description)
	).apply(instance, AffixModifier::new));

	public AffixModifier {
		if (attribute == null || attribute.isBlank()) {
			throw new IllegalArgumentException("O atributo de um afixo não pode ser vazio.");
		}
		if (!Double.isFinite(amount)) {
			throw new IllegalArgumentException("O valor do modificador deve ser finito: " + attribute);
		}
		if (operation == null || slot == null) {
			throw new IllegalArgumentException("Operação e slot são obrigatórios: " + attribute);
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("Cada modificador precisa de uma descrição: " + attribute);
		}
		attribute = attribute.trim();
		description = description.trim();
	}

	public String formattedAmount() {
		if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
				|| operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
			return String.format(Locale.ROOT, "%+.2f%%", amount * 100.0);
		}
		return String.format(Locale.ROOT, "%+.2f", amount);
	}

}
