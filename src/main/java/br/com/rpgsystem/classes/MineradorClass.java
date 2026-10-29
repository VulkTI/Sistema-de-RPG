package br.com.rpgsystem.classes;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.config.XpConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Classe focada em escavação e coleta de recursos minerais. */
public final class MineradorClass extends RpgClass {
	private static final Identifier ID = RpgMod.id("minerador");

	@Override public Identifier id() { return ID; }
	@Override public String name() { return "Minerador"; }
	@Override public String description() { return "Extrai minérios com eficiência e aproveita cada golpe da picareta."; }
	@Override public List<Identifier> perkIds() {
		return List.of(RpgMod.id("minerador_durabilidade"), RpgMod.id("minerador_velocidade"), RpgMod.id("minerador_rendimento"));
	}
	@Override public int xpFor(RpgAction action) { return action == RpgAction.MINING_COMMON_BLOCKS ? XpConfig.mineradorStoneXp() : 0; }
	public int commonBlocksPerReward() { return XpConfig.mineradorBlocksPerReward(); }

	/** XP de cada minério vanilla, incluindo as variantes de ardosiabissal. */
	public int oreXp(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
		return XpConfig.mineradorOreXp(path);
	}

	public boolean isCommonMiningBlock(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.equals("stone") || path.equals("deepslate");
	}
}
