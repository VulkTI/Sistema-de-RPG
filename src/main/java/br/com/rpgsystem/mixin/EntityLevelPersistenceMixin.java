package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mobs.MobLevelAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O dado fica no NBT da entidade; o atributo em si é reconstruído no carregamento. */
@Mixin(Entity.class)
public abstract class EntityLevelPersistenceMixin {
	private static final String MOB_LEVEL_KEY = "rpgsystem.mob_level";

	@Inject(method = "saveWithoutId", at = @At("TAIL"))
	private void rpgsystem$saveMobLevel(ValueOutput output, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob && mob instanceof MobLevelAccess level && level.rpgsystem$hasMobLevel()) {
			output.putInt(MOB_LEVEL_KEY, level.rpgsystem$getMobLevel());
		}
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void rpgsystem$loadMobLevel(ValueInput input, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob && mob instanceof MobLevelAccess level) {
			input.getInt(MOB_LEVEL_KEY).ifPresent(value -> level.rpgsystem$setMobLevel(value));
		}
	}
}
