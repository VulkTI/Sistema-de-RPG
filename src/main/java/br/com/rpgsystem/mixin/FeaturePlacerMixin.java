package br.com.rpgsystem.mixin;

import br.com.rpgsystem.mining.MiningGenerationController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Intercepta a chamada vanilla final; nenhum placed feature é registrado novamente. */
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerMixin {
	@Redirect(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/feature/Feature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean rpgsystem$controlOre(Feature feature, WorldGenLevel level, ChunkGenerator generator,
			RandomSource random, BlockPos pos) {
		return MiningGenerationController.place(feature, level, generator, random, pos);
	}
}
