package br.com.rpgsystem.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExplorationKeysTest {
	@Test
	void structureIdentityIncludesDimensionTypeAndInstancePosition() {
		String first = ExplorationKeys.structure("minecraft:overworld", "minecraft:village", 100, 200);
		assertEquals(first, ExplorationKeys.structure("minecraft:overworld", "minecraft:village", 100, 200));
		assertNotEquals(first, ExplorationKeys.structure("minecraft:overworld", "minecraft:village", 300, 400));
		assertNotEquals(first, ExplorationKeys.structure("minecraft:the_nether", "minecraft:village", 100, 200));
	}

	@Test
	void chestIdentityIsPositionAndDimensionSpecific() {
		assertNotEquals(ExplorationKeys.chest("minecraft:overworld", 1L), ExplorationKeys.chest("minecraft:overworld", 2L));
		assertNotEquals(ExplorationKeys.chest("minecraft:overworld", 1L), ExplorationKeys.chest("minecraft:the_nether", 1L));
	}
}
