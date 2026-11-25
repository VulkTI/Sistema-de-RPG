package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mobs.MobLevelAccess;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float rpgsystem$universalDamageReduction(float amount, ServerLevel level, DamageSource source) {
		if (source.getEntity() instanceof MobLevelAccess attackerAccess && !(source.getEntity() instanceof ServerPlayer)) {
			float mult = attackerAccess.rpgsystem$getAttackMultiplier();
			if (mult > 0.0f && mult != 1.0f) {
				if (source.getEntity() instanceof LivingEntity livingAttacker) {
					// Projéteis e dano indireto (ou mob sem atributo ATTACK_DAMAGE)
					if (source.getDirectEntity() != source.getEntity() || !livingAttacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
						amount *= mult;
					}
				} else {
					amount *= mult;
				}
			}
		}
		if ((Object) this instanceof ServerPlayer player) return (float) (amount * UniversalSkillService.damageTakenMultiplier(player));
		return amount;
	}
}
