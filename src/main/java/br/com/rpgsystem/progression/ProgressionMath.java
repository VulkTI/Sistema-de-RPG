package br.com.rpgsystem.progression;

/** Matemática independente usada tanto pela progressão do jogador quanto pela da classe. */
public final class ProgressionMath {
	public static final int MAX_LEVEL = 100;

	private ProgressionMath() {}

	/** XP necessário para sair do nível informado e chegar ao próximo. */
	public static long xpForNextLevel(int level) {
		int safeLevel = Math.max(1, Math.min(level, MAX_LEVEL));
		if (safeLevel == MAX_LEVEL) return 0L;
		long step = safeLevel - 1L;
		return Math.round(100.0 + 5.0 * step + step * step / 2.0);
	}

	/** XP total mínimo para estar no início do nível informado. O nível 1 começa em 0 XP. */
	public static long totalXpAtLevel(int level) {
		long total = 0L;
		int target = Math.max(1, Math.min(level, MAX_LEVEL));
		for (int currentLevel = 1; currentLevel < target; currentLevel++) {
			total += xpForNextLevel(currentLevel);
		}
		return total;
	}

	public static int levelForXp(long totalXp) {
		long xp = Math.min(totalXpAtLevel(MAX_LEVEL), Math.max(0L, totalXp));
		int low = 1;
		int high = MAX_LEVEL;
		while (low < high) {
			int middle = low + (high - low + 1) / 2;
			if (totalXpAtLevel(middle) <= xp) low = middle;
			else high = middle - 1;
		}
		return low;
	}

	public static long xpIntoLevel(long totalXp) {
		int level = levelForXp(totalXp);
		return Math.min(totalXpAtLevel(MAX_LEVEL), Math.max(0L, totalXp)) - totalXpAtLevel(level);
	}

	/** Em operações por nível, add/remove preserva o progresso da barra sempre que possível. */
	public static long change(long currentXp, int quantity, Change change, Unit unit) {
		long current = Math.min(totalXpAtLevel(MAX_LEVEL), Math.max(0L, currentXp));
		int amount = Math.max(0, quantity);
		if (unit == Unit.POINTS) {
			return switch (change) {
				case SET -> Math.min(totalXpAtLevel(MAX_LEVEL), amount);
				case ADD -> Math.min(totalXpAtLevel(MAX_LEVEL), saturatingAdd(current, amount));
				case REMOVE -> Math.max(0L, current - amount);
			};
		}
		if (change == Change.SET) return totalXpAtLevel(Math.max(1, amount));

		int oldLevel = levelForXp(current);
		int newLevel = change == Change.ADD
				? (int) Math.min(MAX_LEVEL, (long) oldLevel + amount)
				: Math.max(1, oldLevel - amount);
		if (newLevel == MAX_LEVEL) return totalXpAtLevel(MAX_LEVEL);
		long progress = Math.min(xpIntoLevel(current), xpForNextLevel(newLevel) - 1L);
		return totalXpAtLevel(newLevel) + progress;
	}

	private static long saturatingAdd(long left, long right) {
		return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
	}

	public enum Change { ADD, REMOVE, SET }
	public enum Unit { LEVELS, POINTS }
}
