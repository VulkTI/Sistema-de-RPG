package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
	@Shadow private Merchant merchant;
	@Unique private int rpgsystem$villagerLevel;

	@Inject(method = "onTake", at = @At("HEAD"))
	private void rpgsystem$rememberTradeLevel(Player player, ItemStack stack, CallbackInfo ci) {
		if (merchant instanceof Villager villager) rpgsystem$villagerLevel = villager.getVillagerData().level();
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void rpgsystem$awardTradeXp(Player player, ItemStack stack, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;
		UniversalSkillService.addXp(serverPlayer, UniversalSkill.COMERCIO,
				UniversalSkillConfig.activityXp(UniversalSkill.COMERCIO, "villager_trade", 15));
		if (merchant instanceof Villager villager && villager.getVillagerData().level() > rpgsystem$villagerLevel)
			UniversalSkillService.addXp(serverPlayer, UniversalSkill.COMERCIO,
					UniversalSkillConfig.activityXp(UniversalSkill.COMERCIO, "villager_level_up", 35));
	}
}
