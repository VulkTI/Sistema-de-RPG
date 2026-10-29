package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mobs.MobLevelAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Substitui a etiqueta temporária do alvo, sem alterar seu nome persistente. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLevelMixin {
	@Unique private static long rpgsystem$lastTargetCheckNano = 0;
	@Unique private static Entity rpgsystem$cachedTargetEntity = null;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
	private void rpgsystem$showTargetLevel(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (!(entity instanceof Mob) || !(entity instanceof MobLevelAccess level) || !level.rpgsystem$hasMobLevel()) return;
		if (!rpgsystem$isLookingAt(entity, tickDelta)) return;

		Component name = entity.hasCustomName() ? entity.getCustomName() : entity.getName();
		if (name != null) {
			state.nameTag = Component.literal(name.getString() + " - Nv. " + level.rpgsystem$getMobLevel());
			if (state.nameTagAttachment == null) {
				Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(tickDelta));
				state.nameTagAttachment = attachment != null ? attachment : new Vec3(0, entity.getBbHeight() + 0.5, 0);
			}
		}
	}

	@Unique
	private static boolean rpgsystem$isLookingAt(Entity entity, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity) {
			return true;
		}

		long now = System.nanoTime();
		if (now - rpgsystem$lastTargetCheckNano > 20_000_000L) {
			rpgsystem$lastTargetCheckNano = now;
			rpgsystem$cachedTargetEntity = rpgsystem$findTargetEntity(mc, tickDelta);
		}

		return rpgsystem$cachedTargetEntity == entity;
	}

	@Unique
	private static Entity rpgsystem$findTargetEntity(Minecraft mc, float tickDelta) {
		Entity cameraEntity = mc.getCameraEntity();
		if (cameraEntity == null || cameraEntity.level() == null) return null;

		double maxDistance = 20.0D;
		Vec3 eyePos = cameraEntity.getEyePosition(tickDelta);
		Vec3 viewVec = cameraEntity.getViewVector(tickDelta);
		Vec3 endPos = eyePos.add(viewVec.scale(maxDistance));

		BlockHitResult blockHit = cameraEntity.level().clip(new ClipContext(
				eyePos, endPos,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				cameraEntity
		));

		double entityMaxDistanceSq = maxDistance * maxDistance;
		if (blockHit.getType() != HitResult.Type.MISS) {
			endPos = blockHit.getLocation();
			entityMaxDistanceSq = eyePos.distanceToSqr(endPos);
		}

		AABB box = cameraEntity.getBoundingBox().expandTowards(viewVec.scale(maxDistance)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(
				cameraEntity, eyePos, endPos, box,
				e -> !e.isSpectator() && e.isPickable(),
				entityMaxDistanceSq
		);

		return hit != null ? hit.getEntity() : null;
	}
}
