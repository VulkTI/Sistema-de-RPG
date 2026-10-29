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
		put(ranges, "plains", 1, 5); put(ranges, "sunflower_plains", 1, 5); put(ranges, "forest", 3, 8);
		put(ranges, "birch_forest", 4, 9); put(ranges, "flower_forest", 3, 8); put(ranges, "old_growth_birch_forest", 6, 12);
		put(ranges, "meadow", 5, 11); put(ranges, "cherry_grove", 5, 12); put(ranges, "swamp", 6, 12);
		put(ranges, "mushroom_fields", 1, 8); put(ranges, "beach", 2, 7); put(ranges, "ocean", 2, 8);
		put(ranges, "lukewarm_ocean", 3, 9); put(ranges, "deep_ocean", 4, 10); put(ranges, "deep_lukewarm_ocean", 4, 10);
		put(ranges, "river", 2, 7); put(ranges, "lush_caves", 5, 12); put(ranges, "dripstone_caves", 6, 13);

		put(ranges, "dark_forest", 10, 18); put(ranges, "pale_garden", 12, 20); put(ranges, "jungle", 15, 23);
		put(ranges, "sparse_jungle", 12, 20); put(ranges, "bamboo_jungle", 17, 25); put(ranges, "savanna", 14, 22);
		put(ranges, "savanna_plateau", 17, 25); put(ranges, "windswept_savanna", 20, 28); put(ranges, "desert", 18, 27);
		put(ranges, "badlands", 22, 30); put(ranges, "wooded_badlands", 20, 29); put(ranges, "eroded_badlands", 24, 32);
		put(ranges, "mangrove_swamp", 12, 21); put(ranges, "windswept_hills", 10, 19); put(ranges, "windswept_gravelly_hills", 13, 22);
		put(ranges, "windswept_forest", 10, 19); put(ranges, "stony_peaks", 19, 28); put(ranges, "stony_shore", 8, 16);
		put(ranges, "cold_ocean", 8, 16); put(ranges, "deep_cold_ocean", 10, 18); put(ranges, "warm_ocean", 8, 16);

		put(ranges, "taiga", 28, 37); put(ranges, "old_growth_pine_taiga", 30, 39); put(ranges, "old_growth_spruce_taiga", 32, 41);
		put(ranges, "snowy_taiga", 34, 43); put(ranges, "snowy_plains", 35, 44); put(ranges, "frozen_river", 35, 43);
		put(ranges, "snowy_beach", 35, 44); put(ranges, "frozen_ocean", 36, 45); put(ranges, "deep_frozen_ocean", 38, 47);
		put(ranges, "ice_spikes", 40, 49); put(ranges, "grove", 38, 47); put(ranges, "snowy_slopes", 41, 50);
		put(ranges, "frozen_peaks", 44, 53); put(ranges, "jagged_peaks", 45, 54);

		put(ranges, "warped_forest", 60, 65); put(ranges, "crimson_forest", 63, 69); put(ranges, "nether_wastes", 66, 72);
		put(ranges, "soul_sand_valley", 69, 75); put(ranges, "basalt_deltas", 73, 79);
		put(ranges, "small_end_islands", 80, 83); put(ranges, "end_barrens", 81, 85); put(ranges, "end_midlands", 83, 87);
		put(ranges, "end_highlands", 86, 89); put(ranges, "the_end", 87, 89); put(ranges, "deep_dark", 90, 100);
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
