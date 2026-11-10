package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float rpgsystem$universalDamageReduction(float amount, ServerLevel level, DamageSource source) {
		if ((Object) this instanceof ServerPlayer player) return (float) (amount * UniversalSkillService.damageTakenMultiplier(player));
		return amount;
	}
}
