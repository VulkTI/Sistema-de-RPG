package br.com.rpgsystem.affixes;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Rarity;

import com.mojang.serialization.Codec;

/** Raridades disponíveis para afixos. A ordem também define o nível de raridade. */
public enum AffixRarity implements StringRepresentable {
	COMUM("comum", "Comum", ChatFormatting.GRAY, 60.0),
	INCOMUM("incomum", "Incomum", ChatFormatting.GREEN, 25.0),
	RARO("raro", "Raro", ChatFormatting.AQUA, 10.0),
	EPICO("epico", "Épico", ChatFormatting.LIGHT_PURPLE, 4.0),
	LENDARIO("lendario", "Lendário", ChatFormatting.GOLD, 0.9),
	MITICO("mitico", "Mítico", ChatFormatting.RED, 0.1);

	public static final Codec<AffixRarity> CODEC = StringRepresentable.fromEnum(AffixRarity::values);

	private final String serializedName;
	private final String displayName;
	private final ChatFormatting color;
	private final double baseWeight;

	AffixRarity(String serializedName, String displayName, ChatFormatting color, double baseWeight) {
		this.serializedName = serializedName;
		this.displayName = displayName;
		this.color = color;
		this.baseWeight = baseWeight;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	public String displayName() {
		return displayName;
	}

	public ChatFormatting color() {
		return color;
	}

	public net.minecraft.network.chat.Style style() {
		return net.minecraft.network.chat.Style.EMPTY.withColor(color)
				.withBold(this == LENDARIO || this == MITICO);
	}

	public double baseWeight() {
		return baseWeight;
	}

	public Rarity itemRarity() {
		return switch (this) {
			case COMUM -> Rarity.COMMON;
			case INCOMUM -> Rarity.UNCOMMON;
			case RARO -> Rarity.RARE;
			case EPICO, LENDARIO, MITICO -> Rarity.EPIC;
		};
	}

	public static AffixRarity fromItemRarity(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> COMUM;
			case UNCOMMON -> INCOMUM;
			case RARE -> RARO;
			case EPIC -> EPICO;
		};
	}
}
