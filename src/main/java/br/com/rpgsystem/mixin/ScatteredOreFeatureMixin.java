package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mining.MiningGenerationController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScatteredOreFeature.class)
public abstract class ScatteredOreFeatureMixin {
	@WrapOperation(method = "place", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/feature/ScatteredOreFeature;size:I"))
	private int rpgsystem$scaleScatteredOreSize(ScatteredOreFeature feature, Operation<Integer> original) {
		return MiningGenerationController.scaledSize(feature);
	}
}
