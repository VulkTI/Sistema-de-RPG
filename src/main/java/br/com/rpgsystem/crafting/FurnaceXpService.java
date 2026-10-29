package br.com.rpgsystem.crafting;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.FerreiroClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.progression.ProgressionMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class FurnaceXpService {
	private FurnaceXpService() {}

	public static void award(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) return;
		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		if (!profile.selectedClassId().equals("rpgsystem:ferreiro")) return;
		FerreiroClass blacksmith = ClassRegistry.byCommandName("ferreiro")
				.filter(FerreiroClass.class::isInstance).map(FerreiroClass.class::cast).orElse(null);
		if (blacksmith == null) return;
		int gained = blacksmith.smeltedItemXp(stack);
		if (gained <= 0) return;
		long next = ProgressionMath.change(profile.xpForClass("rpgsystem:ferreiro"), gained,
				ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
		data.update(player.getUUID(), current -> current.withClassXp("rpgsystem:ferreiro", next));
		RpgNetworking.sendSnapshot(player, false);
	}
}
