package br.com.rpgsystem.classes;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.config.XpConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;

/** Classe focada em fundição e fabricação de itens metálicos. */
public final class FerreiroClass extends RpgClass {
	private static final Identifier ID = RpgMod.id("ferreiro");
	@Override public Identifier id() { return ID; }
	@Override public String name() { return "Ferreiro"; }
	@Override public String description() { return "Transforma minério e metal em equipamento de grande valor."; }
	@Override public List<Identifier> perkIds() { return List.of(RpgMod.id("ferreiro_afixos"), RpgMod.id("ferreiro_reparacao"), RpgMod.id("ferreiro_durabilidade")); }
	@Override public int xpFor(RpgAction action) { return switch (action) {
		case SMELTED_ORE_COLLECTED -> 1;
		case NETHERITE_INGOT_CRAFTED -> XpConfig.ferreiro("netherite_ingot_crafted", 500);
		case METAL_ITEM_CRAFTED -> XpConfig.ferreiro("metal_item_crafted_base", 10);
		default -> 0;
	}; }

	public int smeltedItemXp(ItemStack stack) {
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		double each = switch (item) {
			case "copper_ingot" -> XpConfig.ferreiro("smelted_copper_ingot", 4);
			case "iron_ingot" -> XpConfig.ferreiro("smelted_iron_ingot", 10);
			case "gold_ingot" -> XpConfig.ferreiro("smelted_gold_ingot", 15);
			case "copper_nugget" -> XpConfig.ferreiro("smelted_copper_ingot", 4) / 9.0;
			case "iron_nugget" -> XpConfig.ferreiro("smelted_iron_ingot", 10) / 9.0;
			case "gold_nugget" -> XpConfig.ferreiro("smelted_gold_ingot", 15) / 9.0;
			default -> 0;
		};
		if (each <= 0.0 || stack.getCount() <= 0) return 0;
		// Cada nugget vale 1/9 de uma barra; a progressão usa pontos inteiros.
		return Math.max(1, (int) Math.round(each * stack.getCount()));
	}

	public int craftedItemXp(Identifier itemId, int outputCount, int bars) {
		String path = itemId.getPath();
		int metalBonus = path.contains("netherite") ? XpConfig.ferreiro("material_bonus_netherite", 50)
				: path.contains("diamond") ? XpConfig.ferreiro("material_bonus_diamond", 35)
				: path.contains("gold") ? XpConfig.ferreiro("material_bonus_gold", 20)
				: path.contains("copper") ? XpConfig.ferreiro("material_bonus_copper", 5)
				: path.contains("iron") ? XpConfig.ferreiro("material_bonus_iron", 10) : 0;
		double barsMultiplier = XpConfig.ferreiroDouble("bars_multiplier", 0.30);
		long perItem = Math.round((xpFor(RpgAction.METAL_ITEM_CRAFTED) + metalBonus) * (1.0 + barsMultiplier * bars));
		return (int) Math.min(Integer.MAX_VALUE, perItem * outputCount);
	}
	@Override public List<SkillDefinition> skillTree() { return List.of(); }
}
