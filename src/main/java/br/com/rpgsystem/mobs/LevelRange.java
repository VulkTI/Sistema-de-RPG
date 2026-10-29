package br.com.rpgsystem.mobs;

/** Faixa inclusiva de níveis, limitada ao intervalo suportado pelo sistema. */
public record LevelRange(int min, int max) {
	public LevelRange {
		min = Math.max(1, Math.min(100, min));
		max = Math.max(min, Math.min(100, max));
	}

	public int random(net.minecraft.util.RandomSource random) {
		return min + random.nextInt(max - min + 1);
	}
}
