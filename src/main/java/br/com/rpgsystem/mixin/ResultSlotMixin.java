package br.com.rpgsystem.mixin;

import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.affixes.RpgAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void rpgsystem$rollAffixOnCraft(Player entity, ItemStack craftedStack, CallbackInfo ci) {
		if (!(entity instanceof ServerPlayer player) || craftedStack.isEmpty()) return;
		if (craftedStack.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX) != null) return;

		double luck = player.getAttributeValue(RpgAttributes.AFFIX_LUCK);
		AffixDefinition definition = AffixService.rollFor(craftedStack, luck, player.getRandom()).orElse(null);
		if (definition != null) AffixService.apply(craftedStack, definition);
	}
}
