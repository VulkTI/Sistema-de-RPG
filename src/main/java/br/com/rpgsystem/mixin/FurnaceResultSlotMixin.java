package br.com.rpgsystem.mixin;

import br.com.rpgsystem.crafting.FurnaceXpService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void rpgsystem$awardSmeltingXp(Player entity, ItemStack stack, CallbackInfo ci) {
		if (!(entity instanceof ServerPlayer player) || stack.isEmpty()) return;
		FurnaceXpService.award(player, stack);
	}
}
