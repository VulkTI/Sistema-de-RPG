package br.com.rpgsystem.skills;

import java.util.Arrays;

/** Progressão longa e exclusiva das habilidades universais. */
public final class UniversalProgression {
	public static final int MAX_LEVEL = 20;

	private UniversalProgression() {}

	public static long xpForNextLevel(UniversalSkill skill, int level) {
		int safe = Math.clamp(level, 1, MAX_LEVEL);
		return safe == MAX_LEVEL ? 0L : UniversalSkillConfig.xpToNext(skill, safe);
	}

	public static long totalXpAtLevel(UniversalSkill skill, int level) {
		int target = Math.clamp(level, 1, MAX_LEVEL);
		long total = 0L;
		for (int current = 1; current < target; current++) total = saturatingAdd(total, xpForNextLevel(skill, current));
		return total;
	}

	public static long maxXp(UniversalSkill skill) { return totalXpAtLevel(skill, MAX_LEVEL); }

	public static int levelForXp(UniversalSkill skill, long totalXp) {
		long xp = Math.clamp(totalXp, 0L, maxXp(skill));
		int low = 1, high = MAX_LEVEL;
		while (low < high) {
			int middle = low + (high - low + 1) / 2;
			if (totalXpAtLevel(skill, middle) <= xp) low = middle;
			else high = middle - 1;
		}
		return low;
	}

	public static long xpIntoLevel(UniversalSkill skill, long totalXp) {
		long xp = Math.clamp(totalXp, 0L, maxXp(skill));
		return xp - totalXpAtLevel(skill, levelForXp(skill, xp));
	}

	private static long saturatingAdd(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
}
