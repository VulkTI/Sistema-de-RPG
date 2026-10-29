package br.com.rpgsystem.mining;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;

/** Controle único da geração, sem duplicar os placed features vanilla. */
public final class MiningGenerationController {
	private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

	private MiningGenerationController() {}

	public static boolean place(Feature feature, WorldGenLevel level, ChunkGenerator generator,
			RandomSource random, BlockPos pos) {
		if (!(feature instanceof AbstractOreFeature oreFeature)) return feature.place(level, generator, random, pos);
		MiningOreConfig.Ore ore = classify(oreFeature);
		if (ore == null) return feature.place(level, generator, random, pos);

		MiningLocation location = MiningZoneResolver.resolve(level, pos);
		double frequency = MiningOreConfig.frequencyMultiplier(ore, location);
		if (frequency <= 0D || random.nextDouble() >= Math.min(1D, frequency)) return false;

		Context previous = CONTEXT.get();
		Context current = new Context(ore, MiningOreConfig.sizeMultiplier(ore, location));
		CONTEXT.set(current);
		try {
			boolean placed = feature.place(level, generator, random, pos);
			if (frequency > 1D && random.nextDouble() < frequency - 1D) {
				placed |= feature.place(level, generator, random, pos);
			}
			return placed;
		} finally {
			if (previous == null) CONTEXT.remove(); else CONTEXT.set(previous);
		}
	}

	/** Chamado pelos mixins no lugar de ler diretamente AbstractOreFeature.size. */
	public static int scaledSize(AbstractOreFeature feature) {
		Context context = CONTEXT.get();
		if (context == null) return feature.size();
		int base = context.ore() == MiningOreConfig.Ore.IRON && feature.size() == 4 ? 5 : feature.size();
		return Math.max(1, (int) Math.round(base * context.sizeMultiplier()));
	}

	private static MiningOreConfig.Ore classify(AbstractOreFeature feature) {
		for (BlockReplacement replacement : feature.targetStates()) {
			BlockState state = replacement.state();
			String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
			switch (path) {
				case "coal_ore" -> { return MiningOreConfig.Ore.COAL; }
				case "copper_ore" -> { return MiningOreConfig.Ore.COPPER; }
				case "iron_ore" -> { return MiningOreConfig.Ore.IRON; }
				case "redstone_ore" -> { return MiningOreConfig.Ore.REDSTONE; }
				case "lapis_ore" -> { return MiningOreConfig.Ore.LAPIS; }
				case "gold_ore" -> { return MiningOreConfig.Ore.GOLD; }
				case "diamond_ore" -> { return MiningOreConfig.Ore.DIAMOND; }
				case "ancient_debris" -> { return MiningOreConfig.Ore.ANCIENT_DEBRIS; }
				default -> {}
			}
		}
		return null;
	}

	private record Context(MiningOreConfig.Ore ore, double sizeMultiplier) {}
}
