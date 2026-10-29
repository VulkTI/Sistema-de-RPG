package br.com.rpgsystem.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Resolve bioma/estrutura uma vez por chunk durante a geração natural. */
public final class MiningZoneResolver {
	private static final int CACHE_LIMIT = 4096;
	private static final Map<ServerLevel, LinkedHashMap<Long, MiningLocation>> CACHE = new WeakHashMap<>();

	private MiningZoneResolver() {}

	public static MiningLocation resolve(WorldGenLevel level, BlockPos pos) {
		ServerLevel serverLevel = level.getLevel();
		if (serverLevel.dimension() == Level.NETHER) return new MiningLocation(MiningZone.ZONE_4, 0);
		if (serverLevel.dimension() == Level.END) return MiningLocation.UNDEFINED;

		long chunk = net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		synchronized (CACHE) {
			LinkedHashMap<Long, MiningLocation> byChunk = CACHE.computeIfAbsent(serverLevel,
					ignored -> new LinkedHashMap<>(128, 0.75F, true));
			MiningLocation cached = byChunk.get(chunk);
			if (cached != null) return cached;
			MiningLocation resolved = resolveOverworld(serverLevel, pos);
			byChunk.put(chunk, resolved);
			while (byChunk.size() > CACHE_LIMIT) byChunk.remove(byChunk.keySet().iterator().next());
			return resolved;
		}
	}

	private static MiningLocation resolveOverworld(ServerLevel level, BlockPos pos) {
		for (MiningZoneConfig.StructureRule rule : MiningZoneConfig.structures()) {
			Optional<net.minecraft.world.level.levelgen.structure.Structure> structure = level.registryAccess()
					.lookupOrThrow(Registries.STRUCTURE).getOptional(rule.id());
			if (structure.isEmpty()) continue;
			try {
				StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(rule.id()));
				if (start.isValid()) return rule.location();
			} catch (RuntimeException ignored) {
				// Estruturas ainda não disponíveis não impedem a regra do bioma.
			}
		}
		Identifier biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier()).orElse(null);
		return biome == null ? MiningLocation.UNDEFINED : MiningZoneConfig.biome(biome);
	}
}
