package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mining.MiningGenerationController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OreFeature.class)
public abstract class OreFeatureMixin {
	@WrapOperation(method = {"place", "doPlace"}, at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/feature/OreFeature;size:I"))
	private int rpgsystem$scaleOreSize(OreFeature feature, Operation<Integer> original) {
		return MiningGenerationController.scaledSize(feature);
	}
}
