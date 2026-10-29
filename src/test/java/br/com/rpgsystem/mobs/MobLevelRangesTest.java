package br.com.rpgsystem.mobs;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobLevelRangesTest {
	@Test
	void everyDocumentedBiomeHasItsExactInclusiveRange() {
		Map<String, LevelRange> actual = MobLevelRanges.defaultBiomeRanges();
		Map<String, LevelRange> expected = Map.ofEntries(
				entry("plains", 1, 5), entry("sunflower_plains", 1, 5), entry("forest", 3, 8), entry("birch_forest", 4, 9), entry("flower_forest", 3, 8), entry("old_growth_birch_forest", 6, 12), entry("meadow", 5, 11), entry("cherry_grove", 5, 12), entry("swamp", 6, 12), entry("mushroom_fields", 1, 8), entry("beach", 2, 7), entry("ocean", 2, 8), entry("lukewarm_ocean", 3, 9), entry("deep_ocean", 4, 10), entry("deep_lukewarm_ocean", 4, 10), entry("river", 2, 7), entry("lush_caves", 5, 12), entry("dripstone_caves", 6, 13),
				entry("dark_forest", 10, 18), entry("pale_garden", 12, 20), entry("jungle", 15, 23), entry("sparse_jungle", 12, 20), entry("bamboo_jungle", 17, 25), entry("savanna", 14, 22), entry("savanna_plateau", 17, 25), entry("windswept_savanna", 20, 28), entry("desert", 18, 27), entry("badlands", 22, 30), entry("wooded_badlands", 20, 29), entry("eroded_badlands", 24, 32), entry("mangrove_swamp", 12, 21), entry("windswept_hills", 10, 19), entry("windswept_gravelly_hills", 13, 22), entry("windswept_forest", 10, 19), entry("stony_peaks", 19, 28), entry("stony_shore", 8, 16), entry("cold_ocean", 8, 16), entry("deep_cold_ocean", 10, 18), entry("warm_ocean", 8, 16),
				entry("taiga", 28, 37), entry("old_growth_pine_taiga", 30, 39), entry("old_growth_spruce_taiga", 32, 41), entry("snowy_taiga", 34, 43), entry("snowy_plains", 35, 44), entry("frozen_river", 35, 43), entry("snowy_beach", 35, 44), entry("frozen_ocean", 36, 45), entry("deep_frozen_ocean", 38, 47), entry("ice_spikes", 40, 49), entry("grove", 38, 47), entry("snowy_slopes", 41, 50), entry("frozen_peaks", 44, 53), entry("jagged_peaks", 45, 54),
				entry("warped_forest", 60, 65), entry("crimson_forest", 63, 69), entry("nether_wastes", 66, 72), entry("soul_sand_valley", 69, 75), entry("basalt_deltas", 73, 79), entry("small_end_islands", 80, 83), entry("end_barrens", 81, 85), entry("end_midlands", 83, 87), entry("end_highlands", 86, 89), entry("the_end", 87, 89), entry("deep_dark", 90, 100));
		assertEquals(expected, actual);
	}

	@Test
	void documentedStructureRangesAndPriorityAreExact() {
		assertEquals(java.util.List.of("ancient_city", "end_city", "bastion_remnant", "fortress", "trial_chambers"),
				MobLevelRanges.defaultStructureRanges().stream().map(value -> value.id().getPath()).toList());
		assertEquals(new LevelRange(63, 67), MobLevelRanges.defaultStructureRanges().get(3).range());
		assertEquals(new LevelRange(74, 79), MobLevelRanges.defaultStructureRanges().get(2).range());
		assertEquals(new LevelRange(36, 41), MobLevelRanges.defaultStructureRanges().get(4).range());
		assertEquals(new LevelRange(84, 89), MobLevelRanges.defaultStructureRanges().get(1).range());
		assertEquals(new LevelRange(94, 99), MobLevelRanges.defaultStructureRanges().getFirst().range());
	}

	@Test
	void fixedBossLevelsCannotUseBiomeOrStructureRanges() {
		assertEquals(90, MobLevelManager.fixedLevelForId(Identifier.withDefaultNamespace("ender_dragon")));
		assertEquals(100, MobLevelManager.fixedLevelForId(Identifier.withDefaultNamespace("warden")));
		assertEquals(80, MobLevelManager.fixedLevelForId(Identifier.withDefaultNamespace("wither")));
	}

	private static Map.Entry<String, LevelRange> entry(String path, int min, int max) {
		return Map.entry(Identifier.withDefaultNamespace(path).toString(), new LevelRange(min, max));
	}
}
