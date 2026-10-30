package br.com.rpgsystem.mobs;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobLevelRangesTest {
	@Test
	void everyDocumentedBiomeHasItsExactInclusiveRange() {
		Map<String, LevelRange> actual = MobLevelRanges.defaultBiomeRanges();
		assertEquals(MobLevelRanges.defaultBiomeRanges().size(), actual.size());
		for (Map.Entry<String, LevelRange> entry : MobLevelRanges.defaultBiomeRanges().entrySet()) {
			assertEquals(entry.getValue(), actual.get(entry.getKey()), "Mismatch for " + entry.getKey());
		}
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
}
