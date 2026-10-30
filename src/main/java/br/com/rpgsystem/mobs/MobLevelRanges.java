package br.com.rpgsystem.mobs;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fonte única dos padrões documentados de biomas e estruturas. */
public final class MobLevelRanges {
	private MobLevelRanges() {}

	public record StructureRange(Identifier id, LevelRange range) {}

	public static Map<String, LevelRange> defaultBiomeRanges() {
		Map<String, LevelRange> ranges = new LinkedHashMap<>();
		// Zona 1 - Níveis 1-14
		put(ranges, "plains", 1, 5);
		put(ranges, "sunflower_plains", 1, 5);
		put(ranges, "mushroom_fields", 1, 6);
		put(ranges, "beach", 2, 7);
		put(ranges, "river", 2, 7);
		put(ranges, "ocean", 2, 8);
		put(ranges, "forest", 3, 8);
		put(ranges, "flower_forest", 3, 8);
		put(ranges, "lukewarm_ocean", 3, 9);
		put(ranges, "birch_forest", 4, 9);
		put(ranges, "deep_ocean", 4, 10);
		put(ranges, "deep_lukewarm_ocean", 4, 10);
		put(ranges, "dappled_forest", 5, 11);
		put(ranges, "meadow", 5, 11);
		put(ranges, "cherry_grove", 6, 12);
		put(ranges, "swamp", 6, 12);
		put(ranges, "old_growth_birch_forest", 7, 13);
		put(ranges, "lush_caves", 8, 14);
		// Zona 2 - Níveis 15-34
		put(ranges, "stony_shore", 11, 15);
		put(ranges, "cold_ocean", 11, 15);
		put(ranges, "warm_ocean", 11, 15);
		put(ranges, "windswept_hills", 12, 16);
		put(ranges, "windswept_forest", 12, 17);
		put(ranges, "deep_cold_ocean", 13, 17);
		put(ranges, "dark_forest", 14, 18);
		put(ranges, "mangrove_swamp", 14, 19);
		put(ranges, "windswept_gravelly_hills", 15, 19);
		put(ranges, "sparse_jungle", 16, 20);
		put(ranges, "pale_garden", 17, 21);
		put(ranges, "jungle", 18, 22);
		put(ranges, "savanna", 19, 23);
		put(ranges, "bamboo_jungle", 20, 24);
		put(ranges, "savanna_plateau", 21, 25);
		put(ranges, "desert", 22, 26);
		put(ranges, "windswept_savanna", 23, 27);
		put(ranges, "wooded_badlands", 24, 28);
		put(ranges, "stony_peaks", 25, 29);
		put(ranges, "badlands", 26, 30);
		put(ranges, "sulfur_caves", 28, 34);
		put(ranges, "eroded_badlands", 28, 34);
		// Zona 3 - Níveis 35-59
		put(ranges, "taiga", 35, 39);
		put(ranges, "old_growth_pine_taiga", 36, 40);
		put(ranges, "old_growth_spruce_taiga", 37, 41);
		put(ranges, "snowy_taiga", 38, 42);
		put(ranges, "snowy_plains", 39, 43);
		put(ranges, "frozen_river", 40, 44);
		put(ranges, "snowy_beach", 41, 45);
		put(ranges, "frozen_ocean", 42, 46);
		put(ranges, "deep_frozen_ocean", 43, 47);
		put(ranges, "grove", 45, 49);
		put(ranges, "ice_spikes", 47, 51);
		put(ranges, "snowy_slopes", 49, 53);
		put(ranges, "frozen_peaks", 52, 56);
		put(ranges, "jagged_peaks", 55, 59);
		// Zona 4 - Níveis 60-79
		put(ranges, "warped_forest", 60, 65);
		put(ranges, "crimson_forest", 63, 68);
		put(ranges, "nether_wastes", 66, 71);
		put(ranges, "soul_sand_valley", 69, 74);
		put(ranges, "basalt_deltas", 75, 79);
		// Zona 5 - Níveis 80-89
		put(ranges, "small_end_islands", 81, 85);
		put(ranges, "end_barrens", 82, 86);
		put(ranges, "end_midlands", 83, 87);
		put(ranges, "the_end", 84, 88);
		put(ranges, "end_highlands", 85, 89);
		// Zona 6 Níveis 92-97
		put(ranges, "deep_dark", 92, 97);
		return ranges;
	}

	public static List<StructureRange> defaultStructureRanges() {
		// A ordem é determinística caso uma peça de estrutura pertença a mais de uma regra.
		return List.of(
				structure("ancient_city", 94, 99), structure("end_city", 84, 89), structure("bastion_remnant", 74, 79),
				structure("fortress", 63, 67), structure("trial_chambers", 36, 41));
	}

	private static void put(Map<String, LevelRange> target, String path, int min, int max) {
		target.put("minecraft:" + path, new LevelRange(min, max));
	}

	private static StructureRange structure(String path, int min, int max) {
		return new StructureRange(Identifier.withDefaultNamespace(path), new LevelRange(min, max));
	}
}
