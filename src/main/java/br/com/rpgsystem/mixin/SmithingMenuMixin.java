package br.com.rpgsystem.mixin;

import br.com.rpgsystem.smithing.SmithingUpgradeHelper;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Inject(method = "createResult", at = @At("TAIL"))
	private void rpgsystem$preserveSmithingAffix(CallbackInfo ci) {
		SmithingUpgradeHelper.prepareInherited((SmithingMenu) (Object) this);
	}
	@Inject(method = "onTake", at = @At("HEAD"))
	private void rpgsystem$processSmithing(Player player, ItemStack result, CallbackInfo ci) {
		if (SmithingUpgradeHelper.isShiftUpgradeInProgress()) return;
		if (!(player instanceof ServerPlayer server) || result.isEmpty()) return;
		RpgSavedData data = RpgSavedData.get(server.level().getServer());
		RpgSavedData.Profile profile = data.profile(server.getUUID());
		if (!profile.selectedClassId().equals("rpgsystem:ferreiro")) return;
		FerreiroClass ferreiro = ClassRegistry.byId(Identifier.fromNamespaceAndPath("rpgsystem", "ferreiro"))
				.filter(FerreiroClass.class::isInstance).map(FerreiroClass.class::cast).orElse(null);
		if (ferreiro == null) return;
		SmithingUpgradeHelper.prepare(server, (SmithingMenu) (Object) this);
		Identifier itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
		int xp = ferreiro.craftedItemXp(itemId, result.getCount(), 1);
		long next = ProgressionMath.change(profile.xpForClass("rpgsystem:ferreiro"), xp,
				ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
		data.update(server.getUUID(), current -> current.withClassXp("rpgsystem:ferreiro", next));
		RpgNetworking.sendSnapshot(server, false);
	}

}
