package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mobs.MobLevelManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fallback para spawns criados diretamente por comandos, spawners e outros mods. */
@Mixin(Mob.class)
public abstract class MobLevelTickMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void rpgsystem$ensureLevel(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (mob.level() instanceof ServerLevel serverLevel) MobLevelManager.initialize(mob, serverLevel);
	}
}
