package br.com.rpgsystem.mining;

import br.com.rpgsystem.config.AreaConfig;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Zonas e níveis de mineração resolvidos dinamicamente a partir de AreaConfig (config/rpgsystem_areas.json).
 */
public final class MiningZoneConfig {
	public record StructureRule(Identifier id, MiningLocation location) {}
	public record ZoneBounds(int min, int max) {}

	private MiningZoneConfig() {}

	public static MiningLocation biome(Identifier id) {
		return AreaConfig.miningLocationForBiome(id);
	}

	public static List<StructureRule> structures() {
		return AreaConfig.miningStructures().stream()
				.map(rule -> new StructureRule(rule.id(), rule.location()))
				.toList();
	}

	public static ZoneBounds bounds(MiningZone zone) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (MiningLocation value : biomes().values()) {
			if (value.zone() != zone) continue;
			min = Math.min(min, value.level());
			max = Math.max(max, value.level());
		}
		return min == Integer.MAX_VALUE ? new ZoneBounds(0, 0) : new ZoneBounds(min, max);
	}

	public static Map<Identifier, MiningLocation> biomes() {
		return AreaConfig.allMiningBiomes();
	}
}
