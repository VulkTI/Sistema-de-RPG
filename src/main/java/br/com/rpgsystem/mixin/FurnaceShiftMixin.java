package br.com.rpgsystem.mixin;

import br.com.rpgsystem.crafting.FurnaceXpService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public abstract class FurnaceShiftMixin {
	private static final ThreadLocal<ItemStack> RPGSYSTEM_SHIFT_RESULT = new ThreadLocal<>();

	@Inject(method = "quickMoveStack", at = @At("HEAD"))
	private void rpgsystem$captureOutput(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer && index == 2) {
			RPGSYSTEM_SHIFT_RESULT.set(((AbstractFurnaceMenu) (Object) this).getSlot(index).getItem().copy());
		}
	}

	@Inject(method = "quickMoveStack", at = @At("RETURN"))
	private void rpgsystem$awardOutput(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack original = RPGSYSTEM_SHIFT_RESULT.get();
		RPGSYSTEM_SHIFT_RESULT.remove();
		if (player instanceof ServerPlayer server && index == 2 && original != null) {
			FurnaceXpService.award(server, original);
		}
	}
}
