package br.com.rpgsystem.smithing;

import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.FerreiroClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.progression.ProgressionMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

public final class SmithingUpgradeHelper {
	private static final ThreadLocal<Boolean> SHIFT_UPGRADE = ThreadLocal.withInitial(() -> false);

	private SmithingUpgradeHelper() {}

	public static void prepare(ServerPlayer player, SmithingMenu menu) {
		ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
		if (result.isEmpty()) return;
		ItemStack base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		AffixDefinition inherited = base.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX);
		if (inherited != null) {
			AffixService.applyInherited(result, inherited);
			return;
		}
		if (result.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX) == null) {
			AffixDefinition rolled = AffixService.rollFor(result, player.getAttributeValue(RpgAttributes.AFFIX_LUCK),
					player.getRandom(), 0.5).orElse(null);
			if (rolled != null) AffixService.apply(result, rolled);
		}
	}

	public static void beginShiftUpgrade() {
		SHIFT_UPGRADE.set(true);
	}

	public static boolean isShiftUpgradeInProgress() {
		return SHIFT_UPGRADE.get();
	}

	public static void finishShiftUpgrade(ServerPlayer player, ItemStack result) {
		SHIFT_UPGRADE.remove();
		if (result.isEmpty()) return;

		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		if (!profile.selectedClassId().equals("rpgsystem:ferreiro")) return;
		FerreiroClass blacksmith = ClassRegistry.byId(Identifier.fromNamespaceAndPath("rpgsystem", "ferreiro"))
				.filter(FerreiroClass.class::isInstance).map(FerreiroClass.class::cast).orElse(null);
		if (blacksmith == null) return;
		int xp = blacksmith.craftedItemXp(BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount(), 1);
		if (xp <= 0) return;
		String classId = blacksmith.id().toString();
		long next = ProgressionMath.change(profile.xpForClass(classId), xp,
				ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
		data.update(player.getUUID(), current -> current.withClassXp(classId, next));
		RpgNetworking.sendSnapshot(player, false);
	}

	public static void prepareInherited(SmithingMenu menu) {
		ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
		ItemStack base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
		if (result.isEmpty() || base.isEmpty()) return;
		AffixDefinition inherited = base.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX);
		if (inherited != null) AffixService.applyInherited(result, inherited);
	}
}
