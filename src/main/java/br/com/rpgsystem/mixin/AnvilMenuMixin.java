package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow private DataSlot cost;
	@Unique private int rpgsystem$baseCost;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void rpgsystem$rememberAnvilCost(CallbackInfo ci) { rpgsystem$baseCost = cost.get(); }

	@Inject(method = "mayPickup", at = @At("HEAD"))
	private void rpgsystem$discountAnvil(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
		if (!(player instanceof ServerPlayer serverPlayer) || rpgsystem$baseCost <= 0) return;
		cost.set(Math.max(1, (int) Math.floor(rpgsystem$baseCost * UniversalSkillService.anvilCostMultiplier(serverPlayer))));
	}
}
