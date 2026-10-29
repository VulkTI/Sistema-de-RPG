package br.com.rpgsystem.mining;

/** Localização resolvida para um ponto de geração natural. */
public record MiningLocation(MiningZone zone, int level) {
	public static final MiningLocation UNDEFINED = new MiningLocation(MiningZone.NONE, 0);
}
