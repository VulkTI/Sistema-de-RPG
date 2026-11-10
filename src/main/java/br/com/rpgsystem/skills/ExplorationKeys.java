package br.com.rpgsystem.skills;

/** Identidades estáveis usadas para não confundir duas instâncias de uma estrutura. */
public final class ExplorationKeys {
	private ExplorationKeys() {}

	public static String structure(String dimension, String structureId, int minX, int minZ) {
		return dimension + "|" + structureId + "|" + minX + "," + minZ;
	}

	public static String chest(String dimension, long blockPos) {
		return dimension + "|chest|" + blockPos;
	}
}
