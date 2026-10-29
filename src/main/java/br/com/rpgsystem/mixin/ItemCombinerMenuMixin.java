package br.com.rpgsystem.mixin;

import br.com.rpgsystem.smithing.SmithingUpgradeHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
	@Inject(method = "quickMoveStack", at = @At("HEAD"))
	private void rpgsystem$prepareSmithingShift(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer server && (Object) this instanceof SmithingMenu menu
				&& index == SmithingMenu.RESULT_SLOT) {
			SmithingUpgradeHelper.prepare(server, menu);
			SmithingUpgradeHelper.beginShiftUpgrade();
		}
	}

	@Inject(method = "quickMoveStack", at = @At("RETURN"))
	private void rpgsystem$finishSmithingShift(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer server && (Object) this instanceof SmithingMenu
				&& index == SmithingMenu.RESULT_SLOT && SmithingUpgradeHelper.isShiftUpgradeInProgress()) {
			SmithingUpgradeHelper.finishShiftUpgrade(server, cir.getReturnValue());
		}
	}
}
