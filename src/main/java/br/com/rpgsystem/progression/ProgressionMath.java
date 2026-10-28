package br.com.rpgsystem.progression;

/** Matemática independente usada tanto pela progressão do jogador quanto pela da classe. */
public final class ProgressionMath {
	public static final int MAX_LEVEL = 1_000_000;
	private static final long FIRST_LEVEL_COST = 100L;
	private static final long COST_INCREASE = 50L;

	private ProgressionMath() {}

	/** XP necessário para sair do nível informado e chegar ao próximo. */
	public static long xpForNextLevel(int level) {
		int safeLevel = Math.max(1, Math.min(level, MAX_LEVEL));
		return FIRST_LEVEL_COST + (long) (safeLevel - 1) * COST_INCREASE;
	}

	/** XP total mínimo para estar no início do nível informado. O nível 1 começa em 0 XP. */
	public static long totalXpAtLevel(int level) {
		long steps = Math.max(0, Math.min(level, MAX_LEVEL) - 1L);
		return steps * FIRST_LEVEL_COST + COST_INCREASE * steps * (steps - 1L) / 2L;
	}

	public static int levelForXp(long totalXp) {
		long xp = Math.max(0L, totalXp);
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
		return Math.max(0L, totalXp) - totalXpAtLevel(level);
	}

	/** Em operações por nível, add/remove preserva o progresso da barra sempre que possível. */
	public static long change(long currentXp, int quantity, Change change, Unit unit) {
		long current = Math.max(0L, currentXp);
		int amount = Math.max(0, quantity);
		if (unit == Unit.POINTS) {
			return switch (change) {
				case SET -> amount;
				case ADD -> saturatingAdd(current, amount);
				case REMOVE -> Math.max(0L, current - amount);
			};
		}
		if (change == Change.SET) return totalXpAtLevel(Math.max(1, amount));

		int oldLevel = levelForXp(current);
		int newLevel = change == Change.ADD
				? (int) Math.min(MAX_LEVEL, (long) oldLevel + amount)
				: Math.max(1, oldLevel - amount);
		long progress = Math.min(xpIntoLevel(current), xpForNextLevel(newLevel) - 1L);
		return totalXpAtLevel(newLevel) + progress;
	}

	private static long saturatingAdd(long left, long right) {
		return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
	}

	public enum Change { ADD, REMOVE, SET }
	public enum Unit { LEVELS, POINTS }
}
