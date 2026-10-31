package br.com.rpgsystem.skills;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalSkillConfigTest {
	@BeforeAll
	static void loadConfig() { UniversalSkillConfig.load(); }

	@Test
	void configuredActivitiesAndLevelTwentyBonusesMatchSpecification() {
		assertEquals(35, UniversalSkillConfig.activityXp(UniversalSkill.COMBATE, "hostile_kill", 0));
		assertEquals(1.2D, UniversalSkillConfig.activityMultiplier(UniversalSkill.MINERACAO, "class_xp", 0D), 0.0001D);
		assertEquals(0.5D, UniversalSkillConfig.bonus(UniversalSkill.COMBATE, "attack_damage_per_level", 0D), 0.0001D);
		assertEquals(0.025D, UniversalSkillConfig.bonus(UniversalSkill.AGRICULTURA, "harvest_extra_chance_per_level", 0D), 0.0001D);
		assertEquals(0.005D, UniversalSkillConfig.bonus(UniversalSkill.CONHECIMENTO, "affix_luck_per_level", 0D), 0.0001D);
		assertTrue(UniversalSkillConfig.structureXp("minecraft:village", 0D) > 0D);
	}
}
