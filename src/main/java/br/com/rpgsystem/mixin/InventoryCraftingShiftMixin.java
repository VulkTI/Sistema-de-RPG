package br.com.rpgsystem.mixin;

import br.com.rpgsystem.crafting.CraftingAffixHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryCraftingShiftMixin {
	@Inject(method = "quickMoveStack", at = @At("HEAD"))
	private void rpgsystem$prepareShiftCraft(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer server && index == InventoryMenu.RESULT_SLOT) {
			CraftingAffixHelper.beginShiftCraft((InventoryMenu) (Object) this);
			CraftingAffixHelper.prepareAffix(server, ((InventoryMenu) (Object) this).getSlot(index).getItem());
		}
	}

	@Inject(method = "quickMoveStack", at = @At("RETURN"))
	private void rpgsystem$finishShiftCraft(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer server && index == InventoryMenu.RESULT_SLOT
				&& CraftingAffixHelper.isShiftCraftInProgress()) {
			CraftingAffixHelper.finishShiftCraft(server, cir.getReturnValue());
		}
	}
}
