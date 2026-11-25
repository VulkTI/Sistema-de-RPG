package br.com.rpgsystem.dungeon.editor;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.dungeon.data.*;
import br.com.rpgsystem.dungeon.network.DungeonNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DungeonEditorSessionManager {
	public static final ResourceKey<Level> EDITOR_DIMENSION =
			ResourceKey.create(Registries.DIMENSION, RpgMod.id("dungeon_editor"));

	private static final Map<UUID, DungeonEditorSession> SESSIONS = new HashMap<>();

	private DungeonEditorSessionManager() {}

	public static DungeonEditorSession getSession(UUID playerId) {
		return SESSIONS.get(playerId);
	}

	public static boolean isInEditorSession(UUID playerId) {
		return SESSIONS.containsKey(playerId);
	}

	public static void startSession(ServerPlayer player, DungeonDefinition dungeon) {
		ServerLevel editorLevel = player.level().getServer().getLevel(EDITOR_DIMENSION);
		if (editorLevel == null) {
			// Fallback caso a dimensão não tenha sido criada pelo datapack: usar overworld void offset
			editorLevel = player.level().getServer().overworld();
		}

		DungeonEditorSession session = new DungeonEditorSession(player.getUUID(), dungeon.getName());

		// Salva estado original do jogador
		session.setPreviousDimension(player.level().dimension());
		session.setPreviousPos(player.position());
		session.setPreviousYaw(player.getYRot());
		session.setPreviousPitch(player.getXRot());
		session.setPreviousGameMode(player.gameMode.getGameModeForPlayer());

		// Salva inventário
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack item = player.getInventory().getItem(i);
			if (!item.isEmpty()) {
				session.getSavedInventory().put(i, item.copy());
			}
		}

		// Salva buffs
		for (MobEffectInstance effect : player.getActiveEffects()) {
			session.getSavedEffects().add(new MobEffectInstance(effect));
		}

		// Aplica estado de edição
		player.removeAllEffects();
		player.getInventory().clearContent();
		player.setGameMode(GameType.CREATIVE);

		if (dungeon.getDungeonStructure() != null) {
			session.setTimeTicks(dungeon.getDungeonStructure().getTimeTicks());
			session.setDaylightCycle(dungeon.getDungeonStructure().isDaylightCycle());
		}

		// Configura o relógio da dimensão de edição com os dados da dungeon sem tocar no Overworld
		try {
			Optional<Holder<WorldClock>> holderOpt = editorLevel.dimensionTypeRegistration().value().defaultClock();
			if (holderOpt.isEmpty()) {
				var reg = player.level().getServer().registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
				var clockKey = ResourceKey.create(Registries.WORLD_CLOCK, RpgMod.id("dungeon_editor"));
				holderOpt = reg.get(clockKey).map(h -> h);
			}
			holderOpt.ifPresent(c -> {
				var clockMgr = player.level().getServer().clockManager();
				clockMgr.setPaused(c, !session.isDaylightCycle());
				clockMgr.setTotalTicks(c, session.getTimeTicks());
			});
		} catch (Throwable t) {
			t.printStackTrace();
		}

		// Limpa qualquer estrutura anterior deixada no mundo void e coloca o bloco base
		clearEditorWorkspace(editorLevel, session);
		player.teleportTo(editorLevel, 0.5, 65.0, 0.5, Set.of(), 0.0F, 0.0F, true);

		SESSIONS.put(player.getUUID(), session);

		// Abre a interface inicial de edição dungeon
		DungeonNetworking.openSetupScreen(player, dungeon);
	}

	public static void cancelSession(ServerPlayer player) {
		DungeonEditorSession session = SESSIONS.remove(player.getUUID());
		if (session == null) return;

		// Limpa blocos posicionados na dimensão de void
		ServerLevel editorLevel = player.level().getServer().getLevel(EDITOR_DIMENSION);
		if (editorLevel != null) {
			clearEditorWorkspace(editorLevel, session);
		}

		// Restaura dimensão e posição
		ServerLevel prevLevel = player.level().getServer().getLevel(session.getPreviousDimension());
		if (prevLevel == null) prevLevel = player.level().getServer().overworld();
		Vec3 prevPos = session.getPreviousPos();
		player.teleportTo(prevLevel, prevPos.x, prevPos.y, prevPos.z, Set.of(), session.getPreviousYaw(), session.getPreviousPitch(), true);

		// Restaura modo de jogo
		player.setGameMode(session.getPreviousGameMode());

		// Restaura inventário para os mesmos slots
		player.getInventory().clearContent();
		for (Map.Entry<Integer, ItemStack> entry : session.getSavedInventory().entrySet()) {
			player.getInventory().setItem(entry.getKey(), entry.getValue());
		}
		player.containerMenu.broadcastChanges();

		// Restaura buffs
		for (MobEffectInstance effect : session.getSavedEffects()) {
			player.addEffect(new MobEffectInstance(effect));
		}
	}

	public static Set<BlockPos> collectAllPlacedBlocks(ServerLevel editorLevel, Set<BlockPos> fallback) {
		Set<BlockPos> blocks = new HashSet<>(fallback);
		int minChunkX = -1, maxChunkX = 1;
		int minChunkZ = -1, maxChunkZ = 1;
		if (fallback != null && !fallback.isEmpty()) {
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos p : fallback) {
				minX = Math.min(minX, p.getX());
				maxX = Math.max(maxX, p.getX());
				minZ = Math.min(minZ, p.getZ());
				maxZ = Math.max(maxZ, p.getZ());
			}
			minChunkX = (minX >> 4) - 1;
			maxChunkX = (maxX >> 4) + 1;
			minChunkZ = (minZ >> 4) - 1;
			maxChunkZ = (maxZ >> 4) + 1;
		}

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!editorLevel.hasChunk(cx, cz)) continue;
				var chunk = editorLevel.getChunk(cx, cz);
				var sections = chunk.getSections();
				for (int sIdx = 0; sIdx < sections.length; sIdx++) {
					var section = sections[sIdx];
					if (section == null || section.hasOnlyAir()) continue;
					int baseY = editorLevel.getSectionYFromSectionIndex(sIdx) << 4;
					for (int lx = 0; lx < 16; lx++) {
						for (int ly = 0; ly < 16; ly++) {
							for (int lz = 0; lz < 16; lz++) {
								var state = section.getBlockState(lx, ly, lz);
								if (!state.isAir()) {
									blocks.add(new BlockPos((cx << 4) + lx, baseY + ly, (cz << 4) + lz));
								}
							}
						}
					}
				}
			}
		}
		return blocks;
	}

	public static void clearEditorWorkspace(ServerLevel editorLevel, DungeonEditorSession session) {
		Set<BlockPos> toClear = collectAllPlacedBlocks(editorLevel, session != null ? session.getPlacedBlocks() : Set.of());
		for (BlockPos p : toClear) {
			editorLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
		}
		if (session != null) {
			session.getPlacedBlocks().clear();
			session.getPlacedMarkers().clear();
			session.getUndoRedo().clear();
		}
		// Bloco de pedra base em (0, 64, 0)
		editorLevel.setBlock(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState(), 3);
	}

	public static void startBuildingStructure(ServerPlayer player, String structureType, String schematicPath) {
		DungeonEditorSession session = SESSIONS.get(player.getUUID());
		if (session == null) return;

		session.setSelectedStructure(structureType);
		session.setState(DungeonEditorSession.SessionState.FREE_BUILD);

		ServerLevel editorLevel = player.level().getServer().getLevel(EDITOR_DIMENSION);
		if (editorLevel == null) editorLevel = player.level().getServer().overworld();

		// Limpa a dimensão void para não misturar construções antigas
		clearEditorWorkspace(editorLevel, session);

		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		String path = schematicPath;
		if ((path == null || path.isBlank()) && def != null) {
			path = "DUNGEON".equalsIgnoreCase(structureType)
					? def.getDungeonStructure().getSchematicPath()
					: def.getEntranceStructure().getSchematicPath();
		}

		// Se o jogador carregou uma esquemática válida, posiciona no mundo void para edição
		if (path != null && !path.isBlank()) {
			StructureTemplate template = DungeonRepository.loadStructureTemplate(editorLevel, path);
			if (template != null) {
				BlockPos origin = new BlockPos(0, 64, 0);
				template.placeInWorld(editorLevel, origin, origin, new StructurePlaceSettings(), editorLevel.getRandom(), 2);

				Vec3i size = template.getSize();
				for (int x = 0; x < size.getX(); x++) {
					for (int y = 0; y < size.getY(); y++) {
						for (int z = 0; z < size.getZ(); z++) {
							BlockPos p = origin.offset(x, y, z);
							if (!editorLevel.getBlockState(p).isAir()) {
								session.getPlacedBlocks().add(p);
							}
						}
					}
				}
			}
		}

		player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aModo de construção livre! Use §e/rpg dungeon config §apara abrir as configurações."), false);
	}

	public static void saveStructure(ServerPlayer player) {
		DungeonEditorSession session = SESSIONS.get(player.getUUID());
		if (session == null) return;

		ServerLevel editorLevel = player.level().getServer().getLevel(EDITOR_DIMENSION);
		if (editorLevel == null) editorLevel = player.level().getServer().overworld();

		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		// Coleta TODOS os blocos colocados na dimensão
		Set<BlockPos> allBlocks = collectAllPlacedBlocks(editorLevel, session.getPlacedBlocks());

		// Se tiver apenas o bloco de pedra original (0,64,0), remove para salvar limpo se o usuário construiu algo em volta
		if (allBlocks.size() > 1) {
			BlockPos stonePos = new BlockPos(0, 64, 0);
			if (editorLevel.getBlockState(stonePos).is(Blocks.STONE)) {
				// Verifica se há outros blocos; se houver, o stonePos é considerado parte da estrutura se conectado
			}
		}

		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		if (allBlocks.isEmpty()) {
			minX = maxX = 0;
			minY = maxY = 64;
			minZ = maxZ = 0;
		} else {
			for (BlockPos p : allBlocks) {
				minX = Math.min(minX, p.getX());
				minY = Math.min(minY, p.getY());
				minZ = Math.min(minZ, p.getZ());
				maxX = Math.max(maxX, p.getX());
				maxY = Math.max(maxY, p.getY());
				maxZ = Math.max(maxZ, p.getZ());
			}
		}

		BlockPos minPos = new BlockPos(minX, minY, minZ);
		Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

		// O portal de saída (portal azul) NUNCA deve ser gravado dentro da esquemática salva.
		// Ele só é colocado dinamicamente quando a última onda da dungeon for concluída.
		List<BlockPos> bluePortalPositions = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(minPos, minPos.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (editorLevel.getBlockState(p).is(br.com.rpgsystem.dungeon.block.DungeonBlocks.BLUE_PORTAL)) {
				bluePortalPositions.add(p.immutable());
			}
		}
		for (BlockPos p : bluePortalPositions) {
			editorLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
		}

		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(editorLevel, minPos, size, true, List.of(Blocks.STRUCTURE_VOID));
		CompoundTag tag = template.save(new CompoundTag());

		// Restaura visualmente no editor
		for (BlockPos p : bluePortalPositions) {
			editorLevel.setBlock(p, br.com.rpgsystem.dungeon.block.DungeonBlocks.BLUE_PORTAL.defaultBlockState(), 2);
		}

		String rawName = "DUNGEON".equalsIgnoreCase(session.getSelectedStructure())
				? def.getDungeonStructure().getStructureName()
				: def.getEntranceStructure().getStructureName();
		String safeDungeon = def.getName().trim().replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase(java.util.Locale.ROOT);
		String typeSuffix = session.getSelectedStructure().toLowerCase(java.util.Locale.ROOT);
		String structName = (rawName == null || rawName.isBlank() || rawName.equalsIgnoreCase("Entrada") || rawName.equalsIgnoreCase("Dungeon"))
				? safeDungeon + "_" + typeSuffix
				: rawName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");

		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			def.getDungeonStructure().setStructureName(structName);
		} else {
			def.getEntranceStructure().setStructureName(structName);
		}

		Path structDir = DungeonRepository.getWorldStructuresDirectory(player.level().getServer());
		Path structFile = structDir.resolve(structName + ".nbt");

		try {
			NbtIo.writeCompressed(tag, structFile);
		} catch (IOException e) {
			RpgMod.LOGGER.error("Erro ao salvar arquivo de esquemática .nbt: {}", structFile, e);
		}

		// Atualiza o caminho completo da esquemática na dungeon
		String fullPath = structFile.toAbsolutePath().toString();
		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			def.getDungeonStructure().setSchematicPath(fullPath);
			session.setDungeonSchematicName(structName + ".nbt");
			session.setDungeonSchematicPath(fullPath);
		} else {
			def.getEntranceStructure().setSchematicPath(fullPath);
			session.setEntranceSchematicName(structName + ".nbt");
			session.setEntranceSchematicPath(fullPath);
		}

		// Normaliza e converte coordenadas do editor para relativas à esquemática
		if ("ENTRADA".equalsIgnoreCase(session.getSelectedStructure())) {
			PortalData in = def.getEntranceStructure().getEntrancePortal();
			if (in != null && (in.getMinPos().getY() >= 50 || in.getMaxPos().getY() >= 50)) {
				BlockPos pMin = in.getMinPos();
				BlockPos pMax = in.getMaxPos();
				BlockPos realMin = new BlockPos(Math.min(pMin.getX(), pMax.getX()), Math.min(pMin.getY(), pMax.getY()), Math.min(pMin.getZ(), pMax.getZ())).subtract(minPos);
				BlockPos realMax = new BlockPos(Math.max(pMin.getX(), pMax.getX()), Math.max(pMin.getY(), pMax.getY()), Math.max(pMin.getZ(), pMax.getZ())).subtract(minPos);
				in.setMinPos(realMin);
				in.setMaxPos(realMax);
			}
		} else {
			// Normaliza Player Spawn Pos
			if (session.getPlayerSpawnPos() != null) {
				BlockPos sp = session.getPlayerSpawnPos();
				BlockPos relSpawn = sp.getY() >= 50 ? sp.subtract(minPos) : sp;
				def.getDungeonStructure().setPlayerSpawnPos(relSpawn);
				def.getDungeonStructure().setPlayerSpawnYaw(session.getPlayerSpawnYaw());
			} else if (def.getDungeonStructure().getPlayerSpawnPos() != null) {
				BlockPos sp = def.getDungeonStructure().getPlayerSpawnPos();
				if (sp.getY() >= 50) {
					def.getDungeonStructure().setPlayerSpawnPos(sp.subtract(minPos));
				}
			}

			PortalData in = def.getDungeonStructure().getEntrancePortal();
			if (in != null && (in.getMinPos().getY() >= 50 || in.getMaxPos().getY() >= 50)) {
				BlockPos pMin = in.getMinPos();
				BlockPos pMax = in.getMaxPos();
				BlockPos realMin = new BlockPos(Math.min(pMin.getX(), pMax.getX()), Math.min(pMin.getY(), pMax.getY()), Math.min(pMin.getZ(), pMax.getZ())).subtract(minPos);
				BlockPos realMax = new BlockPos(Math.max(pMin.getX(), pMax.getX()), Math.max(pMin.getY(), pMax.getY()), Math.max(pMin.getZ(), pMax.getZ())).subtract(minPos);
				in.setMinPos(realMin);
				in.setMaxPos(realMax);
			}
			PortalData out = def.getDungeonStructure().getExitPortal();
			if (out != null && (out.getMinPos().getY() >= 50 || out.getMaxPos().getY() >= 50)) {
				BlockPos pMin = out.getMinPos();
				BlockPos pMax = out.getMaxPos();
				BlockPos realMin = new BlockPos(Math.min(pMin.getX(), pMax.getX()), Math.min(pMin.getY(), pMax.getY()), Math.min(pMin.getZ(), pMax.getZ())).subtract(minPos);
				BlockPos realMax = new BlockPos(Math.max(pMin.getX(), pMax.getX()), Math.max(pMin.getY(), pMax.getY()), Math.max(pMin.getZ(), pMax.getZ())).subtract(minPos);
				out.setMinPos(realMin);
				out.setMaxPos(realMax);
			}
			for (WaveData w : def.getDungeonStructure().getWaves()) {
				for (MobSpawnData m : w.getMobs()) {
					if (m.getRelativePos() != null && m.getRelativePos().getY() >= 50) {
						m.setRelativePos(m.getRelativePos().subtract(minPos));
					}
				}
			}
			for (ChestSpawnData c : def.getDungeonStructure().getChests()) {
				if (c.getRelativePos() != null && c.getRelativePos().getY() >= 50) {
					c.setRelativePos(c.getRelativePos().subtract(minPos));
				}
			}
			for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
				if (a.getMinPos() != null && (a.getMinPos().getY() >= 50 || a.getMaxPos().getY() >= 50)) {
					BlockPos pMin = a.getMinPos();
					BlockPos pMax = a.getMaxPos();
					BlockPos realMin = new BlockPos(Math.min(pMin.getX(), pMax.getX()), Math.min(pMin.getY(), pMax.getY()), Math.min(pMin.getZ(), pMax.getZ())).subtract(minPos);
					BlockPos realMax = new BlockPos(Math.max(pMin.getX(), pMax.getX()), Math.max(pMin.getY(), pMax.getY()), Math.max(pMin.getZ(), pMax.getZ())).subtract(minPos);
					a.setMinPos(realMin);
					a.setMaxPos(realMax);
				}
			}
		}

		// Salva configurações de dungeons do mundo
		DungeonRepository.saveWorldDungeons(player.level().getServer());

		// Apaga tudo o que o jogador construiu na dimensão do VOID
		clearEditorWorkspace(editorLevel, session);
		player.teleportTo(editorLevel, 0.5, 65.0, 0.5, Set.of(), 0.0F, 0.0F, true);

		// Troca a interface para a interface de edição dungeon com a estrutura agora referenciada com ✅
		session.setState(DungeonEditorSession.SessionState.SETUP_SCREEN);
		DungeonNetworking.openSetupScreen(player, def);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		DungeonEditorSession session = SESSIONS.get(player.getUUID());
		if (session == null) return;

		// Mantém o jogador na dimensão e estado
		player.setGameMode(GameType.CREATIVE);
		if (session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			DungeonEditorToolManager.giveWaveEditingTools(player, session);
		} else if (session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			DungeonEditorToolManager.givePortalEditingTools(player, session);
		}
	}

	public static void onWorldTick(ServerLevel level) {
		if (SESSIONS.isEmpty()) return;

		// Trava clima: sem chuva ou tempestade no editor
		if (level.isRaining() || level.isThundering()) {
			try {
				var weather = level.getWeatherData();
				if (weather != null) {
					weather.setRaining(false);
					weather.setThundering(false);
					weather.setRainTime(0);
					weather.setThunderTime(0);
					weather.setClearWeatherTime(60000);
				}
			} catch (Throwable ignored) {}
		}
	}
}
