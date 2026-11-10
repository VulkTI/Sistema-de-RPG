package br.com.rpgsystem.skills;

import br.com.rpgsystem.data.RpgSavedData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniversalSkillPointsTest {
	@Test
	void firstUniversalLevelAwardsPointOnlyWhenReachingLevelOneXp() {
		RpgSavedData.Profile profile = new RpgSavedData.Profile("", 0L, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		long requiredLevelOne = UniversalProgression.totalXpAtLevel(UniversalSkill.COMBATE, 1);

		// XP menor que o necessário para o nível 1 mantém no nível 0 com 0 pontos
		RpgSavedData.Profile levelZero = profile.withUniversalXp(UniversalSkill.COMBATE.id(), requiredLevelOne - 1);
		assertEquals(0, UniversalProgression.levelForXp(UniversalSkill.COMBATE, requiredLevelOne - 1));
		assertEquals(0, levelZero.availableSkillPoints("universal:combate"));

		// Atingindo o XP necessário para o nível 1, upa para nível 1 e ganha 1 ponto
		RpgSavedData.Profile levelOne = profile.withUniversalXp(UniversalSkill.COMBATE.id(), requiredLevelOne);
		assertEquals(1, UniversalProgression.levelForXp(UniversalSkill.COMBATE, requiredLevelOne));
		assertEquals(1, levelOne.availableSkillPoints("universal:combate"));
		assertEquals(0, profile.withClassXp("rpgsystem:minerador", 0L).availableSkillPoints("rpgsystem:minerador"));

		// Atingindo o nível 2, ganha o segundo ponto
		RpgSavedData.Profile levelTwo = levelOne.withUniversalXp(UniversalSkill.COMBATE.id(),
				UniversalProgression.totalXpAtLevel(UniversalSkill.COMBATE, 2));
		assertEquals(2, levelTwo.availableSkillPoints("universal:combate"));
	}
}
