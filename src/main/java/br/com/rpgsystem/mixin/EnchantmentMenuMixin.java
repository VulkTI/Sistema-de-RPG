package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
	@Inject(method = "clickMenuButton", at = @At("TAIL"))
	private void rpgsystem$awardEnchant(Player player, int button, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && player instanceof ServerPlayer serverPlayer)
			UniversalSkillService.addXp(serverPlayer, UniversalSkill.CONHECIMENTO,
					UniversalSkillConfig.activityXp(UniversalSkill.CONHECIMENTO, "enchant", 20));
	}
}
