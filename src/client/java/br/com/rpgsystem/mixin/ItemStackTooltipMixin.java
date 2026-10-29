package br.com.rpgsystem.mixin;

import br.com.rpgsystem.affixes.AffixTooltipState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

@Mixin(ItemStack.class)
public final class ItemStackTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("HEAD"))
	private void rpgsystem$beginTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
			CallbackInfoReturnable<List<Component>> callback) {
		AffixTooltipState.setShiftDown(InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
	}

	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void rpgsystem$endTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
			CallbackInfoReturnable<List<Component>> callback) {
		List<Component> original = callback.getReturnValue();
		int affixIndex = -1;
		for (int index = 0; index < original.size(); index++) {
			if (original.get(index).getString().startsWith("Afixo: ")) {
				affixIndex = index;
				break;
			}
		}
		if (affixIndex >= 0) {
			List<Component> reordered = new ArrayList<>(original);
			List<Component> affixBlock = new ArrayList<>(reordered.subList(affixIndex, reordered.size()));
			reordered.subList(affixIndex, reordered.size()).clear();
			reordered.addAll(Math.min(1, reordered.size()), affixBlock);
			original.clear();
			original.addAll(reordered);
		}
		AffixTooltipState.setShiftDown(false);
	}
}
