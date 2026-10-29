package br.com.rpgsystem.mining;

import java.util.EnumMap;
import java.util.Map;

/** Valores-base e regras matemáticas centralizados para os minérios controlados. */
public final class MiningOreConfig {
	public static final double FALLBACK_FREQUENCY = 0.05D;
	public static final double FALLBACK_SIZE = 0.20D;

	public enum Ore {
		COAL(MiningZone.ZONE_1, new int[]{45, 25}, new int[]{17, 17}),
		COPPER(MiningZone.ZONE_1, new int[]{105}, new int[]{10, 20}),
		IRON(MiningZone.ZONE_2, new int[]{45, 15, 15}, new int[]{9, 9, 5}),
		REDSTONE(MiningZone.ZONE_2, new int[0], new int[]{8}),
		LAPIS(MiningZone.ZONE_2, new int[0], new int[]{7}),
		GOLD(MiningZone.ZONE_3, new int[0], new int[]{9}),
		DIAMOND(MiningZone.ZONE_3, new int[0], new int[]{4, 8, 12}),
		ANCIENT_DEBRIS(MiningZone.ZONE_4, new int[]{2, 1}, new int[]{2, 3});

		private final MiningZone origin;
		private final int[] attempts;
		private final int[] sizes;

		Ore(MiningZone origin, int[] attempts, int[] sizes) {
			this.origin = origin;
			this.attempts = attempts;
			this.sizes = sizes;
		}

		public MiningZone origin() { return origin; }
		public int[] attempts() { return attempts.clone(); }
		public int[] sizes() { return sizes.clone(); }
	}

	private static final Map<Ore, MiningZone> ORIGINS = new EnumMap<>(Ore.class);

	static {
		for (Ore ore : Ore.values()) ORIGINS.put(ore, ore.origin());
	}

	private MiningOreConfig() {}

	public static MiningZone origin(Ore ore) { return ORIGINS.get(ore); }

	public static boolean allowed(Ore ore, MiningLocation location) {
		MiningZone actual = location.zone();
		if (actual == MiningZone.NONE || actual.order() < ore.origin().order()) return false;
		if (ore == Ore.ANCIENT_DEBRIS) return actual == MiningZone.ZONE_4;
		if (actual == ore.origin()) return true;
		return switch (actual) {
			case ZONE_2 -> ore == Ore.COAL || ore == Ore.COPPER;
			case ZONE_3 -> ore != Ore.GOLD && ore != Ore.DIAMOND && ore != Ore.ANCIENT_DEBRIS;
			default -> false;
		};
	}

	public static double frequencyMultiplier(Ore ore, MiningLocation location) {
		if (!allowed(ore, location)) return 0D;
		// O Nether é independente: detritos não usam o nível dos biomas do Nether.
		if (ore == Ore.ANCIENT_DEBRIS) return 1D;
		if (location.zone() != ore.origin()) return FALLBACK_FREQUENCY;
		MiningZoneConfig.ZoneBounds bounds = MiningZoneConfig.bounds(ore.origin());
		double denominator = bounds.max() - bounds.min();
		double t = denominator <= 0D ? 1D : Math.clamp((location.level() - bounds.min()) / denominator, 0D, 1D);
		return 0.75D + 0.75D * t;
	}

	public static double sizeMultiplier(Ore ore, MiningLocation location) {
		if (!allowed(ore, location)) return 0D;
		if (ore == Ore.ANCIENT_DEBRIS) return 1D;
		if (location.zone() != ore.origin()) return FALLBACK_SIZE;
		MiningZoneConfig.ZoneBounds bounds = MiningZoneConfig.bounds(ore.origin());
		double denominator = bounds.max() - bounds.min();
		double t = denominator <= 0D ? 1D : Math.clamp((location.level() - bounds.min()) / denominator, 0D, 1D);
		return 0.75D + 0.60D * t;
	}
}
