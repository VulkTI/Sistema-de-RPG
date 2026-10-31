package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "doBrew", at = @At("TAIL"))
	private static void rpgsystem$awardBrew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci) {
		Player nearest = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8D,
				entity -> entity instanceof ServerPlayer);
		if (nearest instanceof ServerPlayer player) UniversalSkillService.addXp(player, UniversalSkill.CONHECIMENTO,
				UniversalSkillConfig.activityXp(UniversalSkill.CONHECIMENTO, "brew", 12));
	}
}
