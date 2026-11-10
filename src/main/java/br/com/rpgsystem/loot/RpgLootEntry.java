package br.com.rpgsystem.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Entrada individual em uma tabela de looting personalizada. */
public record RpgLootEntry(int id, ItemStack item, double weight, String customLabel) {
	public static final Codec<RpgLootEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(RpgLootEntry::id),
			ItemStack.CODEC.fieldOf("item").forGetter(RpgLootEntry::item),
			Codec.DOUBLE.fieldOf("weight").forGetter(RpgLootEntry::weight),
			Codec.STRING.optionalFieldOf("custom_label", "").forGetter(RpgLootEntry::customLabel)
	).apply(instance, RpgLootEntry::new));

	public RpgLootEntry(int id, ItemStack item, double weight) {
		this(id, item, weight, "");
	}

	public RpgLootEntry {
		customLabel = customLabel == null ? "" : customLabel;
		if (item != null && item.isEmpty()) {
			throw new IllegalArgumentException("O item de uma entrada de loot não pode ser vazio.");
		}
		if (weight <= 0.0) {
			throw new IllegalArgumentException("O peso deve ser maior que 0. Valor recebido: " + weight);
		}
	}

	public String displayName() {
		if (!customLabel.isBlank()) return customLabel;
		if (item == null) return "Item #" + id;
		String base = item.getHoverName().getString();
		return item.getCount() > 1 ? item.getCount() + "x " + base : base;
	}

	public String itemNameOnly() {
		if (!customLabel.isBlank()) return customLabel;
		if (item == null) return "Item #" + id;
		return item.getHoverName().getString();
	}

	public boolean matches(ItemStack other, String otherLabel) {
		if (this.item != null && other != null) {
			return ItemStack.isSameItemSameComponents(this.item, other);
		}
		if (this.item == null && other == null && !this.customLabel.isBlank() && otherLabel != null) {
			return this.customLabel.equalsIgnoreCase(otherLabel);
		}
		return false;
	}

	public RpgLootEntry withWeight(double newWeight) {
		return new RpgLootEntry(id, item != null ? item.copy() : null, newWeight, customLabel);
	}
}
