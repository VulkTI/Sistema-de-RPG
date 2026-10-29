package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mining.MineradorEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Preserva a tentativa vanilla de Inquebrável quando a perk não evita o gasto. */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
	@Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true)
	private void rpgsystem$mineradorPreservesPickaxe(int amount, ServerLevel level, ServerPlayer player,
			Consumer<ItemStack> onBreak, CallbackInfo ci) {
		if (MineradorEffects.shouldPreventPickaxeDamage((ItemStack) (Object) this, player)) ci.cancel();
	}
}
