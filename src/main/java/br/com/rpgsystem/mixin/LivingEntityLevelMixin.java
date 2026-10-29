package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mobs.MobLevelAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLevelMixin implements MobLevelAccess {
	@Unique private static final EntityDataAccessor<Integer> RPGSYSTEM_MOB_LEVEL =
			SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void rpgsystem$defineMobLevel(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(RPGSYSTEM_MOB_LEVEL, 0);
	}

	@Override public int rpgsystem$getMobLevel() { return ((LivingEntity) (Object) this).getEntityData().get(RPGSYSTEM_MOB_LEVEL); }
	@Override public void rpgsystem$setMobLevel(int level) { ((LivingEntity) (Object) this).getEntityData().set(RPGSYSTEM_MOB_LEVEL, Math.clamp(level, 1, 100)); }
}
