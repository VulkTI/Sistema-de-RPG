package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Premia o escambo somente quando a resposta já foi preparada para ser entregue ao jogador. */
@Mixin(targets = "net.minecraft.world.entity.monster.piglin.PiglinAi")
public abstract class PiglinAiMixin {
	@Inject(method = "throwItemsTowardPlayer", at = @At("TAIL"))
	private static void rpgsystem$awardCompletedBarter(Piglin piglin, Player player, List<ItemStack> items, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || items.stream().allMatch(ItemStack::isEmpty)) return;
		UniversalSkillService.addXp(serverPlayer, UniversalSkill.COMERCIO,
				UniversalSkillConfig.activityXp(UniversalSkill.COMERCIO, "piglin_trade", 10));
	}
}
