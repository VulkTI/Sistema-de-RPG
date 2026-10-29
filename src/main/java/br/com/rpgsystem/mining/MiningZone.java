package br.com.rpgsystem.mining;

/** As quatro zonas de mineração. NONE representa biomas/estruturas sem regra. */
public enum MiningZone {
	NONE(0), ZONE_1(1), ZONE_2(2), ZONE_3(3), ZONE_4(4);

	private final int order;

	MiningZone(int order) {
		this.order = order;
	}

	public int order() {
		return order;
	}
}
