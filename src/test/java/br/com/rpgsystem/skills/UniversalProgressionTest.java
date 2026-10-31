package br.com.rpgsystem.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalProgressionTest {
	@Test
	void eachSkillHasIndependentLongCurveAndLevelTwentyIsCapped() {
		for (UniversalSkill skill : UniversalSkill.values()) {
			assertEquals(1, UniversalProgression.levelForXp(skill, 0));
			assertEquals(UniversalProgression.maxXp(skill), UniversalProgression.totalXpAtLevel(skill, 20));
			assertEquals(20, UniversalProgression.levelForXp(skill, Long.MAX_VALUE));
			assertEquals(0, UniversalProgression.xpForNextLevel(skill, 20));
			assertTrue(UniversalProgression.totalXpAtLevel(skill, 20) > UniversalProgression.totalXpAtLevel(skill, 10));
		}
	}

	@Test
	void xpIntoLevelDoesNotLeakPastTheMaximum() {
		for (UniversalSkill skill : UniversalSkill.values()) {
			assertEquals(0, UniversalProgression.xpIntoLevel(skill, UniversalProgression.maxXp(skill)));
			assertEquals(0, UniversalProgression.xpIntoLevel(skill, Long.MAX_VALUE));
		}
	}
}
