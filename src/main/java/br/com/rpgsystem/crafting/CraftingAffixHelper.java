package br.com.rpgsystem.crafting;

import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.affixes.AffixItemGroups;
import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.FerreiroClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.progression.ProgressionMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class CraftingAffixHelper {
	private static final ThreadLocal<Integer> SHIFT_CRAFT_BARS = ThreadLocal.withInitial(() -> -1);

	private CraftingAffixHelper() {}

	public static void beginShiftCraft(AbstractContainerMenu menu) {
		int gridSize = menu instanceof InventoryMenu ? 4 : 9;
		int bars = 0;
		for (int slot = 1; slot <= gridSize && slot < menu.slots.size(); slot++) {
			ItemStack ingredient = menu.getSlot(slot).getItem();
			if (!ingredient.isEmpty()
					&& BuiltInRegistries.ITEM.getKey(ingredient.getItem()).getPath().endsWith("_ingot")) {
				bars += ingredient.getCount();
			}
		}
		SHIFT_CRAFT_BARS.set(bars);
	}

	public static boolean isShiftCraftInProgress() {
		return SHIFT_CRAFT_BARS.get() >= 0;
	}

	public static void finishShiftCraft(ServerPlayer player, ItemStack craftedStack) {
		int bars = SHIFT_CRAFT_BARS.get();
		SHIFT_CRAFT_BARS.remove();
		if (craftedStack.isEmpty()) return;

		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		FerreiroClass blacksmith = profile.selectedClassId().equals("rpgsystem:ferreiro")
				? ClassRegistry.byId(Identifier.fromNamespaceAndPath("rpgsystem", "ferreiro"))
					.filter(FerreiroClass.class::isInstance).map(FerreiroClass.class::cast).orElse(null)
				: null;

		if (craftedStack.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX) == null) {
			AffixDefinition definition = AffixService.rollFor(craftedStack,
					player.getAttributeValue(RpgAttributes.AFFIX_LUCK), player.getRandom(),
					blacksmith == null ? 0.0 : 0.5).orElse(null);
			if (definition != null) AffixService.apply(craftedStack, definition);
		}

		if (blacksmith == null) return;
		Identifier itemId = BuiltInRegistries.ITEM.getKey(craftedStack.getItem());
		int xp = 0;
		if (itemId.equals(Identifier.fromNamespaceAndPath("minecraft", "netherite_ingot"))) {
			xp += blacksmith.xpFor(br.com.rpgsystem.classes.RpgAction.NETHERITE_INGOT_CRAFTED)
					* craftedStack.getCount();
		}
		if (AffixItemGroups.matches("metal", craftedStack.getItem())) {
			int maxDamage = craftedStack.getMaxDamage();
			if (maxDamage > 0) craftedStack.set(DataComponents.MAX_DAMAGE, (int) Math.ceil(maxDamage * 1.10));
			xp += blacksmith.craftedItemXp(itemId, craftedStack.getCount(), Math.max(0, bars));
		}
		addXp(player, data, profile, blacksmith, xp);
	}

	private static void addXp(ServerPlayer player, RpgSavedData data, RpgSavedData.Profile profile,
			FerreiroClass blacksmith, int amount) {
		if (amount <= 0) return;
		String classId = blacksmith.id().toString();
		long next = ProgressionMath.change(profile.xpForClass(classId), amount,
				ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
		data.update(player.getUUID(), current -> current.withClassXp(classId, next));
		RpgNetworking.sendSnapshot(player, false);
	}

	public static void prepareAffix(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || stack.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX) != null) return;
		RpgSavedData.Profile profile = RpgSavedData.get(player.level().getServer()).profile(player.getUUID());
		double luck = player.getAttributeValue(RpgAttributes.AFFIX_LUCK);
		AffixDefinition definition = AffixService.rollFor(stack, luck, player.getRandom(),
				profile.selectedClassId().equals("rpgsystem:ferreiro") ? 0.5 : 0.0).orElse(null);
		if (definition != null) AffixService.apply(stack, definition);
	}
}
