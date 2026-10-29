package br.com.rpgsystem.mining;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapeamento independente do sistema de níveis de mobs.
 * Cada entrada possui um nível de mineração fixo; não há fallback silencioso.
 */
public final class MiningZoneConfig {
	public record StructureRule(Identifier id, MiningLocation location) {}
	public record ZoneBounds(int min, int max) {}

	private static final Map<Identifier, MiningLocation> BIOMES = createBiomes();
	private static final List<StructureRule> STRUCTURES = List.of(
			structure("ancient_city", MiningZone.ZONE_3, 60),
			structure("trial_chambers", MiningZone.ZONE_3, 50),
			structure("fortress", MiningZone.ZONE_4, 0),
			structure("bastion_remnant", MiningZone.ZONE_4, 0),
			// End City é uma regra explícita sem minérios controlados.
			structure("end_city", MiningZone.NONE, 0));

	private MiningZoneConfig() {}

	public static MiningLocation biome(Identifier id) {
		return BIOMES.getOrDefault(id, MiningLocation.UNDEFINED);
	}

	public static List<StructureRule> structures() {
		return STRUCTURES;
	}

	public static ZoneBounds bounds(MiningZone zone) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (MiningLocation value : BIOMES.values()) {
			if (value.zone() != zone) continue;
			min = Math.min(min, value.level());
			max = Math.max(max, value.level());
		}
		return min == Integer.MAX_VALUE ? new ZoneBounds(0, 0) : new ZoneBounds(min, max);
	}

	public static Map<Identifier, MiningLocation> biomes() {
		return BIOMES;
	}

	private static Map<Identifier, MiningLocation> createBiomes() {
		Map<Identifier, MiningLocation> result = new LinkedHashMap<>();
		// Zona 1: níveis iniciais do Overworld.
		put(result, MiningZone.ZONE_1, 1, "plains", "sunflower_plains", "mushroom_fields");
		put(result, MiningZone.ZONE_1, 2, "beach", "ocean", "river");
		put(result, MiningZone.ZONE_1, 3, "forest", "flower_forest", "lukewarm_ocean");
		put(result, MiningZone.ZONE_1, 4, "birch_forest", "deep_ocean", "deep_lukewarm_ocean");
		put(result, MiningZone.ZONE_1, 5, "meadow", "cherry_grove", "lush_caves");
		put(result, MiningZone.ZONE_1, 6, "old_growth_birch_forest", "swamp", "dripstone_caves");

		// Zona 2: progressão intermediária.
		put(result, MiningZone.ZONE_2, 19, "stony_shore", "cold_ocean", "warm_ocean");
		put(result, MiningZone.ZONE_2, 21, "dark_forest", "windswept_hills", "windswept_forest", "deep_cold_ocean");
		put(result, MiningZone.ZONE_2, 23, "pale_garden", "sparse_jungle", "mangrove_swamp");
		put(result, MiningZone.ZONE_2, 24, "jungle", "savanna");
		put(result, MiningZone.ZONE_2, 27, "bamboo_jungle", "savanna_plateau");
		put(result, MiningZone.ZONE_2, 28, "desert");
		put(result, MiningZone.ZONE_2, 30, "windswept_savanna", "wooded_badlands", "stony_peaks");
		put(result, MiningZone.ZONE_2, 32, "badlands");
		put(result, MiningZone.ZONE_2, 34, "eroded_badlands");

		// Zona 3: progressão avançada do Overworld.
		put(result, MiningZone.ZONE_3, 41, "taiga");
		put(result, MiningZone.ZONE_3, 43, "old_growth_pine_taiga");
		put(result, MiningZone.ZONE_3, 45, "old_growth_spruce_taiga");
		put(result, MiningZone.ZONE_3, 47, "snowy_taiga", "frozen_river");
		put(result, MiningZone.ZONE_3, 48, "snowy_plains", "snowy_beach");
		put(result, MiningZone.ZONE_3, 49, "frozen_ocean");
		put(result, MiningZone.ZONE_3, 51, "deep_frozen_ocean", "grove");
		put(result, MiningZone.ZONE_3, 53, "ice_spikes");
		put(result, MiningZone.ZONE_3, 55, "snowy_slopes");
		put(result, MiningZone.ZONE_3, 58, "frozen_peaks");
		put(result, MiningZone.ZONE_3, 60, "jagged_peaks", "deep_dark");

		// Zona 4: Nether. O nível não participa da progressão do Nether.
		put(result, MiningZone.ZONE_4, 1, "nether_wastes");
		put(result, MiningZone.ZONE_4, 2, "warped_forest");
		put(result, MiningZone.ZONE_4, 3, "crimson_forest");
		put(result, MiningZone.ZONE_4, 4, "soul_sand_valley");
		put(result, MiningZone.ZONE_4, 5, "basalt_deltas");
		return Map.copyOf(result);
	}

	private static void put(Map<Identifier, MiningLocation> target, MiningZone zone, int level, String... paths) {
		for (String path : paths) target.put(Identifier.withDefaultNamespace(path), new MiningLocation(zone, level));
	}

	private static StructureRule structure(String path, MiningZone zone, int level) {
		return new StructureRule(Identifier.withDefaultNamespace(path), new MiningLocation(zone, level));
	}
}
