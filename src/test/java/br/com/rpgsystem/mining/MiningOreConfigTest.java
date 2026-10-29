package br.com.rpgsystem.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningOreConfigTest {
	@Test
	void baseValuesAreCentralizedExactly() {
		assertArrayEquals(new int[]{45, 25}, MiningOreConfig.Ore.COAL.attempts());
		assertArrayEquals(new int[]{105}, MiningOreConfig.Ore.COPPER.attempts());
		assertArrayEquals(new int[]{45, 15, 15}, MiningOreConfig.Ore.IRON.attempts());
		assertArrayEquals(new int[]{2, 1}, MiningOreConfig.Ore.ANCIENT_DEBRIS.attempts());
		assertArrayEquals(new int[]{9, 9, 5}, MiningOreConfig.Ore.IRON.sizes());
		assertArrayEquals(new int[]{2, 3}, MiningOreConfig.Ore.ANCIENT_DEBRIS.sizes());
	}

	@Test
	void originProgressionReachesTheExactPeak() {
		MiningZoneConfig.ZoneBounds bounds = MiningZoneConfig.bounds(MiningZone.ZONE_2);
		assertEquals(0.75D, MiningOreConfig.frequencyMultiplier(
				MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_2, bounds.min())), 0.000001D);
		int middle = (bounds.min() + bounds.max()) / 2;
		double expectedMiddle = 0.75D + 0.75D * ((middle - bounds.min()) / (double) (bounds.max() - bounds.min()));
		assertEquals(expectedMiddle, MiningOreConfig.frequencyMultiplier(
				MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_2, middle)), 0.000001D);
		assertEquals(1.50D, MiningOreConfig.frequencyMultiplier(
				MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_2, bounds.max())), 0.000001D);
		assertEquals(0.75D, MiningOreConfig.sizeMultiplier(
				MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_2, bounds.min())), 0.000001D);
		assertEquals(1.35D, MiningOreConfig.sizeMultiplier(
				MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_2, bounds.max())), 0.000001D);
	}

	@Test
	void upperZonesDropAndThenStayStable() {
		MiningLocation zone3 = new MiningLocation(MiningZone.ZONE_3, 60);
		assertEquals(0.05D, MiningOreConfig.frequencyMultiplier(MiningOreConfig.Ore.IRON, zone3), 0.000001D);
		assertEquals(0.20D, MiningOreConfig.sizeMultiplier(MiningOreConfig.Ore.IRON, zone3), 0.000001D);
		assertEquals(0.05D, MiningOreConfig.frequencyMultiplier(MiningOreConfig.Ore.IRON,
				new MiningLocation(MiningZone.ZONE_3, 999)), 0.000001D);
		assertEquals(0.20D, MiningOreConfig.sizeMultiplier(MiningOreConfig.Ore.IRON,
				new MiningLocation(MiningZone.ZONE_3, 999)), 0.000001D);
	}

	@Test
	void directionAndNetherRulesAreExplicit() {
		assertTrue(MiningOreConfig.allowed(MiningOreConfig.Ore.COAL, new MiningLocation(MiningZone.ZONE_1, 1)));
		assertTrue(MiningOreConfig.allowed(MiningOreConfig.Ore.COAL, new MiningLocation(MiningZone.ZONE_2, 20)));
		assertTrue(MiningOreConfig.allowed(MiningOreConfig.Ore.COAL, new MiningLocation(MiningZone.ZONE_3, 20)));
		assertFalse(MiningOreConfig.allowed(MiningOreConfig.Ore.IRON, new MiningLocation(MiningZone.ZONE_1, 1)));
		assertFalse(MiningOreConfig.allowed(MiningOreConfig.Ore.DIAMOND, new MiningLocation(MiningZone.ZONE_2, 20)));
		assertTrue(MiningOreConfig.allowed(MiningOreConfig.Ore.ANCIENT_DEBRIS, new MiningLocation(MiningZone.ZONE_4, 0)));
		assertFalse(MiningOreConfig.allowed(MiningOreConfig.Ore.DIAMOND, new MiningLocation(MiningZone.ZONE_4, 0)));
		assertEquals(1D, MiningOreConfig.frequencyMultiplier(MiningOreConfig.Ore.ANCIENT_DEBRIS,
				new MiningLocation(MiningZone.ZONE_4, 999)), 0.000001D);
		assertEquals(MiningZone.NONE, MiningZoneConfig.biome(net.minecraft.resources.Identifier.withDefaultNamespace("the_end")).zone());
	}
}
