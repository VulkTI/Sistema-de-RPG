package br.com.rpgsystem.dungeon.world;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.config.AreaConfig;
import br.com.rpgsystem.dungeon.block.DungeonBlocks;
import br.com.rpgsystem.dungeon.data.DungeonDefinition;
import br.com.rpgsystem.dungeon.data.DungeonRepository;
import br.com.rpgsystem.dungeon.data.EntranceStructureData;
import br.com.rpgsystem.dungeon.data.PortalData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DungeonWorldManager {
	public static final ResourceKey<Level> DUNGEON_DIMENSION =
			ResourceKey.create(Registries.DIMENSION, RpgMod.id("dungeon_instance"));

	private static final List<ActiveDungeonInstance> ACTIVE_INSTANCES = new ArrayList<>();
	private static final Map<UUID, Long> PORTAL_COOLDOWN = new HashMap<>();
	private static int nextSpawnTimerTicks = 20 * 60 * 10; // 10 minutos inicial
	private static int instanceOffsetCounter = 0;

	private DungeonWorldManager() {}

	public static void tick(MinecraftServer server) {
		// Tick de instâncias ativas
		for (Iterator<ActiveDungeonInstance> it = ACTIVE_INSTANCES.iterator(); it.hasNext(); ) {
			ActiveDungeonInstance inst = it.next();
			inst.tick();

			// Checa se o tempo de despawn acabou (não concluída e timer expirado)
			if (inst.isDiscovered() && inst.getDespawnTimerTicks() <= 0 && !inst.isCompleted()) {
				despawnInstance(inst);
				it.remove();
				continue;
			}

			// Checa se o tempo pós-conclusão acabou
			if (inst.isCompleted() && inst.getCompletedDespawnTicks() <= 0) {
				despawnInstance(inst);
				it.remove();
				continue;
			}
		}

		// 1. Sincroniza jogadores no mundo exterior: cada jogador recebe sync da instância MAIS PRÓXIMA (dentro de 48 blocos)
		boolean syncThisTick = server.getTickCount() % 20 == 0;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().equals(DUNGEON_DIMENSION)) continue;
			for (ServerPlayer p : level.players()) {
				ActiveDungeonInstance closest = null;
				double closestDistSq = 48.0 * 48.0;
				for (ActiveDungeonInstance inst : ACTIVE_INSTANCES) {
					if (inst.getWorldLevel() != level) continue;
					double distSq = p.blockPosition().distSqr(inst.getEntrancePos());
					if (distSq < closestDistSq) {
						closestDistSq = distSq;
						closest = inst;
					}
				}
				if (closest != null) {
					if (!closest.isDiscovered()) {
						closest.setDiscovered(true);
					}
					if (syncThisTick) {
						int secondsLeft = closest.isCompleted()
								? Math.max(0, closest.getCompletedDespawnTicks() / 20)
								: Math.max(0, closest.getDespawnTimerTicks() / 20);
						net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
								p,
								new br.com.rpgsystem.dungeon.network.DungeonPayloads.PortalAimSync(
										closest.getDungeon().getName(),
										closest.getLevel(),
										secondsLeft,
										closest.isCompleted()
								)
						);
					}
				}
			}
		}

		// 2. Sincroniza jogadores dentro da dimensão de dungeons: encontra a instância específica pelo dungeonOrigin
		if (syncThisTick) {
			ServerLevel dungeonDim = server.getLevel(DUNGEON_DIMENSION);
			if (dungeonDim != null) {
				for (ServerPlayer p : dungeonDim.players()) {
					ActiveDungeonInstance inside = findInstanceInsideDungeon(dungeonDim, p.blockPosition());
					if (inside != null) {
						int secondsLeft = inside.isCompleted()
								? Math.max(0, inside.getCompletedDespawnTicks() / 20)
								: Math.max(0, inside.getDespawnTimerTicks() / 20);
						net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
								p,
								new br.com.rpgsystem.dungeon.network.DungeonPayloads.PortalAimSync(
										inside.getDungeon().getName(),
										inside.getLevel(),
										secondsLeft,
										inside.isCompleted()
								)
						);
					}
				}
			}
		}

		// Timer de spawn randômico
		if (nextSpawnTimerTicks > 0) {
			nextSpawnTimerTicks--;
		} else {
			// Randomiza novo intervalo entre 8 e 20 minutos
			nextSpawnTimerTicks = 20 * 60 * (8 + server.overworld().getRandom().nextInt(13));
			tryRandomSpawnDungeon(server);
		}
	}

	public static void despawnDungeonByName(MinecraftServer server, String name) {
		for (Iterator<ActiveDungeonInstance> it = ACTIVE_INSTANCES.iterator(); it.hasNext(); ) {
			ActiveDungeonInstance inst = it.next();
			if (inst.getDungeon().getName().equalsIgnoreCase(name)) {
				despawnInstance(inst);
				it.remove();
			}
		}
	}

	public static void onWorldLoad(MinecraftServer server) {
		ACTIVE_INSTANCES.clear();
		nextSpawnTimerTicks = 20 * 60 * 3; // 3 minutos após carregar o mundo
	}

	public static boolean tryRandomSpawnDungeon(MinecraftServer server) {
		List<DungeonDefinition> activeList = DungeonRepository.all().stream().filter(DungeonDefinition::isActive).toList();
		if (activeList.isEmpty()) return false;

		// 1. Escolhe dungeon com base nos pesos
		int totalWeight = activeList.stream().mapToInt(DungeonDefinition::getWeight).sum();
		if (totalWeight <= 0) return false;

		int roll = server.overworld().getRandom().nextInt(totalWeight);
		int current = 0;
		DungeonDefinition selectedDungeon = activeList.getFirst();
		for (DungeonDefinition def : activeList) {
			current += def.getWeight();
			if (roll < current) {
				selectedDungeon = def;
				break;
			}
		}

		// 2. Nível randômico no intervalo [minLevel, maxLevel]
		int minLvl = selectedDungeon.getMinLevel();
		int maxLvl = Math.max(minLvl, selectedDungeon.getMaxLevel());
		int chosenLevel = minLvl + server.overworld().getRandom().nextInt(maxLvl - minLvl + 1);

		// 3. Spawna eletivo
		return spawnDungeonElective(server, selectedDungeon, chosenLevel);
	}

	public static boolean spawnDungeonElective(MinecraftServer server, DungeonDefinition dungeon, int level) {
		ServerLevel overworld = server.overworld();

		// Procura coordenadas válidas nas chunks carregadas
		BlockPos spawnPos = null;
		EntranceStructureData ent = dungeon.getEntranceStructure();
		Random random = new Random();

		// Ancora a busca em torno de jogadores online para não gerar em chunks distantes descarregadas
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		int anchorX = 0;
		int anchorZ = 0;
		if (!players.isEmpty()) {
			ServerPlayer anchor = players.get(random.nextInt(players.size()));
			if (anchor.level() == overworld) {
				anchorX = anchor.getBlockX();
				anchorZ = anchor.getBlockZ();
			} else {
				BlockPos spawn = overworld.getRespawnData().pos();
				anchorX = spawn.getX();
				anchorZ = spawn.getZ();
			}
		} else {
			BlockPos spawn = overworld.getRespawnData().pos();
			anchorX = spawn.getX();
			anchorZ = spawn.getZ();
		}

		int anchorChunkX = anchorX >> 4;
		int anchorChunkZ = anchorZ >> 4;

		for (int attempt = 0; attempt < 40; attempt++) {
			int chunkX = anchorChunkX + (random.nextInt(31) - 15);
			int chunkZ = anchorChunkZ + (random.nextInt(31) - 15);

			// CRÍTICO PARA PERFORMANCE: Só verifica chunks que já estão carregadas na memória do servidor!
			// Chamar getHeight() em chunks não carregadas dispara geração de mundo síncrona pesada travando o servidor.
			if (!overworld.hasChunk(chunkX, chunkZ)) continue;

			int cx = chunkX * 16 + 8;
			int cz = chunkZ * 16 + 8;
			BlockPos checkPos = new BlockPos(cx, 64, cz);

			// Não permite gerar próximo a outra dungeon ativa (distância mínima de 250 blocos)
			boolean tooClose = false;
			for (ActiveDungeonInstance act : ACTIVE_INSTANCES) {
				if (act.getEntrancePos().closerThan(checkPos, 250)) {
					tooClose = true;
					break;
				}
			}
			if (tooClose) continue;

			// Checa bioma compatível com o nível
			var biomeHolder = overworld.getBiome(checkPos);
			String biomeKey = biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			var biomeRange = AreaConfig.biomeRanges().get(biomeKey);
			if (biomeRange != null && (level < biomeRange.min() || level > biomeRange.max())) {
				continue;
			}

			// Valida posição no solo sem flutuar
			if (ent.isCeu()) {
				spawnPos = new BlockPos(cx, 190 + random.nextInt(25), cz);
				break;
			} else if (ent.isSubsolo()) {
				int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
				int subY = Math.max(-40, surfaceY - 25 - random.nextInt(30));
				spawnPos = new BlockPos(cx, subY, cz);
				break;
			} else {
				int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
				BlockPos groundCheck = new BlockPos(cx, surfaceY - 1, cz);
				if (!overworld.getBlockState(groundCheck).isAir()) {
					spawnPos = new BlockPos(cx, surfaceY, cz);
					break;
				}
			}
		}

		if (spawnPos == null) {
			// Fallback: procura posição em chunks já carregadas
			for (int fb = 0; fb < 25; fb++) {
				int chunkX = anchorChunkX + (random.nextInt(31) - 15);
				int chunkZ = anchorChunkZ + (random.nextInt(31) - 15);
				if (!overworld.hasChunk(chunkX, chunkZ)) continue;

				int cx = chunkX * 16 + 8;
				int cz = chunkZ * 16 + 8;
				int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
				BlockPos candidate = new BlockPos(cx, surfaceY, cz);
				boolean tooClose = false;
				for (ActiveDungeonInstance act : ACTIVE_INSTANCES) {
					if (act.getEntrancePos().closerThan(candidate, 250)) {
						tooClose = true;
						break;
					}
				}
				if (!tooClose) {
					spawnPos = candidate;
					break;
				}
			}
		}

		if (spawnPos == null) return false;

		return spawnDungeonAt(server, dungeon, level, overworld, spawnPos);
	}

	public static boolean spawnDungeonHere(ServerPlayer player, DungeonDefinition dungeon) {
		return spawnDungeonHere(player, dungeon, null);
	}

	public static boolean spawnDungeonHere(ServerPlayer player, DungeonDefinition dungeon, Integer forcedLevel) {
		ServerLevel level = ((ServerLevel) player.level());
		int min = dungeon.getMinLevel();
		int max = Math.max(min, dungeon.getMaxLevel());
		int dungeonLevel = (forcedLevel != null && forcedLevel > 0)
				? forcedLevel
				: (min == max ? min : min + level.getRandom().nextInt(max - min + 1));

		// Calcula posição logo à frente do jogador sem sobrepor sua hitbox
		var lookVec = player.getLookAngle().normalize();
		int targetX = (int) Math.round(player.getX() + lookVec.x * 6.0);
		int targetY = (int) Math.round(player.getY());
		int targetZ = (int) Math.round(player.getZ() + lookVec.z * 6.0);
		BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

		return spawnDungeonAt(player.level().getServer(), dungeon, dungeonLevel, level, targetPos);
	}

	public static boolean spawnDungeonAt(MinecraftServer server, DungeonDefinition dungeon, int level, ServerLevel worldLevel, BlockPos entrancePos) {
		ServerLevel dungeonLevel = server.getLevel(DUNGEON_DIMENSION);
		if (dungeonLevel == null) dungeonLevel = server.overworld();

		BlockPos dungeonOrigin = new BlockPos(instanceOffsetCounter++ * 1000, 64, 0);
		ActiveDungeonInstance instance = new ActiveDungeonInstance(dungeon, level, entrancePos, worldLevel, dungeonLevel, dungeonOrigin);

		// Configura o relógio independente da dimensão de dungeon com o horário da estrutura
		try {
			Optional<Holder<WorldClock>> holderOpt = dungeonLevel.dimensionTypeRegistration().value().defaultClock();
			if (holderOpt.isEmpty()) {
				var reg = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
				var clockKey = ResourceKey.create(Registries.WORLD_CLOCK, RpgMod.id("dungeon_instance"));
				holderOpt = reg.get(clockKey).map(h -> h);
			}
			holderOpt.ifPresent(c -> {
				var clockMgr = server.clockManager();
				clockMgr.setTotalTicks(c, dungeon.getDungeonStructure().getTimeTicks());
				clockMgr.setPaused(c, !dungeon.getDungeonStructure().isDaylightCycle());
			});
		} catch (Throwable t) {
			t.printStackTrace();
		}

		// 1. Gera Estrutura de Entrada no mundo com rotação aleatória (N, S, L, O)
		Rotation entRotation = Rotation.getRandom(worldLevel.getRandom());
		instance.setEntranceRotation(entRotation);
		loadAndPlaceSchematic(worldLevel, entrancePos, dungeon, dungeon.getEntranceStructure().getSchematicPath(), instance, true, entRotation);

		// 2. Coloca portal branco de entrada se configurado (respeitando a rotação da entrada)
		PortalData entPortal = dungeon.getEntranceStructure().getEntrancePortal();
		if (entPortal != null) {
			StructurePlaceSettings entSettings = new StructurePlaceSettings().setRotation(entRotation);
			BlockPos p1 = entrancePos.offset(StructureTemplate.calculateRelativePosition(entSettings, ensureRelative(entPortal.getMinPos())));
			BlockPos p2 = entrancePos.offset(StructureTemplate.calculateRelativePosition(entSettings, ensureRelative(entPortal.getMaxPos())));
			BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
			BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				BlockPos imm = p.immutable();
				if (!instance.getCarvedBlocks().containsKey(imm)) {
					instance.getCarvedBlocks().put(imm, worldLevel.getBlockState(imm));
				}
				worldLevel.setBlock(imm, DungeonBlocks.WHITE_PORTAL.defaultBlockState(), 3);
			}
		}

		// 3. Gera Estrutura da Dungeon na dimensão de instâncias (sem rotação para manter coordenadas relativas do editor)
		loadAndPlaceSchematic(dungeonLevel, dungeonOrigin, dungeon, dungeon.getDungeonStructure().getSchematicPath(), instance, false, Rotation.NONE);

		// Limpa qualquer bloco de portal azul (saída) que possa ter vindo de esquemáticas antigas
		PortalData dungExitPortal = dungeon.getDungeonStructure().getExitPortal();
		if (dungExitPortal != null) {
			BlockPos relMin = ensureRelative(dungExitPortal.getMinPos());
			BlockPos relMax = ensureRelative(dungExitPortal.getMaxPos());
			BlockPos min = dungeonOrigin.offset(Math.min(relMin.getX(), relMax.getX()), Math.min(relMin.getY(), relMax.getY()), Math.min(relMin.getZ(), relMax.getZ()));
			BlockPos max = dungeonOrigin.offset(Math.max(relMin.getX(), relMax.getX()), Math.max(relMin.getY(), relMax.getY()), Math.max(relMin.getZ(), relMax.getZ()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				if (dungeonLevel.getBlockState(p).is(DungeonBlocks.BLUE_PORTAL)) {
					dungeonLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}

		// Coloca portal branco de entrada dentro da dungeon
		PortalData dungInPortal = dungeon.getDungeonStructure().getEntrancePortal();
		if (dungInPortal != null) {
			BlockPos relMin = ensureRelative(dungInPortal.getMinPos());
			BlockPos relMax = ensureRelative(dungInPortal.getMaxPos());
			BlockPos min = dungeonOrigin.offset(Math.min(relMin.getX(), relMax.getX()), Math.min(relMin.getY(), relMax.getY()), Math.min(relMin.getZ(), relMax.getZ()));
			BlockPos max = dungeonOrigin.offset(Math.max(relMin.getX(), relMax.getX()), Math.max(relMin.getY(), relMax.getY()), Math.max(relMin.getZ(), relMax.getZ()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				dungeonLevel.setBlock(p, DungeonBlocks.WHITE_PORTAL.defaultBlockState(), 3);
			}
		}

		ACTIVE_INSTANCES.add(instance);
		RpgMod.LOGGER.info("Dungeon '{}' gerada com sucesso em {} no nível {}.", dungeon.getName(), entrancePos, level);
		return true;
	}

	private static void loadAndPlaceSchematic(ServerLevel level, BlockPos origin, DungeonDefinition dungeon, String pathStr,
	                                          ActiveDungeonInstance instance, boolean isEntrance, Rotation rotation) {
		if (pathStr == null || pathStr.isBlank()) {
			level.setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
			return;
		}

		StructureTemplate template = DungeonRepository.loadStructureTemplate(level, dungeon, pathStr);

		if (template != null) {
			StructurePlaceSettings settings = new StructurePlaceSettings();
			if (rotation != null) {
				settings.setRotation(rotation);
			}

			Vec3i size = template.getSize();
			if (instance != null) {
				if (isEntrance) {
					// Salva os blocos originais para restauração ao despawnar (apenas blocos da estrutura)
					List<StructureTemplate.Palette> palettes = null;
					for (java.lang.reflect.Field f : StructureTemplate.class.getDeclaredFields()) {
						if (List.class.isAssignableFrom(f.getType())) {
							f.setAccessible(true);
							try {
								Object val = f.get(template);
								if (val instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof StructureTemplate.Palette) {
									@SuppressWarnings("unchecked")
									List<StructureTemplate.Palette> casted = (List<StructureTemplate.Palette>) list;
									palettes = casted;
									break;
								}
							} catch (Throwable ignored) {}
						}
					}
					if (palettes != null && !palettes.isEmpty()) {
						StructureTemplate.Palette pal = settings.getRandomPalette(palettes, origin);
						for (StructureTemplate.StructureBlockInfo info : pal.blocks()) {
							if (info.state().is(Blocks.STRUCTURE_VOID) || info.state().isAir()) continue;
							BlockPos relPos = StructureTemplate.calculateRelativePosition(settings, info.pos());
							BlockPos worldPos = origin.offset(relPos);
							if (!instance.getCarvedBlocks().containsKey(worldPos)) {
								instance.getCarvedBlocks().put(worldPos, level.getBlockState(worldPos));
							}
						}
					}
				} else {
					instance.setDungeonSize(size);
				}
			}
			template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
		} else {
			level.setBlock(origin, Blocks.COBBLESTONE.defaultBlockState(), 3);
		}
	}

	private static void despawnInstance(ActiveDungeonInstance instance) {
		ServerLevel world = instance.getWorldLevel();
		BlockPos origin = instance.getEntrancePos();

		// Restaura TODOS os blocos que existiam antes da estrutura ser gerada usando flag 18 (2 | 16) para não travar com físicas de bloco
		if (!instance.getCarvedBlocks().isEmpty()) {
			for (Map.Entry<BlockPos, BlockState> entry : instance.getCarvedBlocks().entrySet()) {
				world.setBlock(entry.getKey(), entry.getValue(), 18);
			}
			instance.getCarvedBlocks().clear();
		} else {
			PortalData portal = instance.getDungeon().getEntranceStructure().getEntrancePortal();
			if (portal != null) {
				StructurePlaceSettings entSettings = new StructurePlaceSettings().setRotation(instance.getEntranceRotation());
				BlockPos p1 = origin.offset(StructureTemplate.calculateRelativePosition(entSettings, ensureRelative(portal.getMinPos())));
				BlockPos p2 = origin.offset(StructureTemplate.calculateRelativePosition(entSettings, ensureRelative(portal.getMaxPos())));
				BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
				BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
				for (BlockPos p : BlockPos.betweenClosed(min, max)) {
					world.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
				}
			}
		}

		// Limpa mobs ativos da dungeon
		instance.cleanupMobs();

		// Se houver jogadores ainda dentro da dimensão desta instância, teleporta-os com segurança para fora
		ServerLevel dungeonDim = instance.getDungeonLevel();
		BlockPos outPos = findSafeExitPos(world, origin, instance.getDungeon().getEntranceStructure().getEntrancePortal(), instance.getEntranceRotation());
		for (ServerPlayer p : dungeonDim.players()) {
			if (Math.abs(p.getX() - instance.getDungeonOrigin().getX()) < 500
					&& Math.abs(p.getZ() - instance.getDungeonOrigin().getZ()) < 500) {
				world.getChunk(outPos.getX() >> 4, outPos.getZ() >> 4);
				p.teleportTo(world, outPos.getX() + 0.5, outPos.getY(), outPos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cA dungeon expirou e você foi transportado para fora!"));
			}
		}
	}

	public static BlockPos ensureRelative(BlockPos pos) {
		if (pos == null) return BlockPos.ZERO;
		if (pos.getY() >= 50) {
			return new BlockPos(pos.getX(), pos.getY() - 64, pos.getZ());
		}
		return pos;
	}

	public static boolean checkAndApplyPortalCooldown(ServerPlayer player) {
		long now = System.currentTimeMillis();
		Long nextAvailable = PORTAL_COOLDOWN.get(player.getUUID());
		if (nextAvailable != null && now < nextAvailable) {
			return false;
		}
		PORTAL_COOLDOWN.put(player.getUUID(), now + 2500);
		return true;
	}

	public static ActiveDungeonInstance findInstanceInsideDungeon(ServerLevel level, BlockPos pos) {
		for (ActiveDungeonInstance inst : ACTIVE_INSTANCES) {
			if (inst.getDungeonLevel() != level) continue;
			if (Math.abs(pos.getX() - inst.getDungeonOrigin().getX()) < 500
					&& Math.abs(pos.getZ() - inst.getDungeonOrigin().getZ()) < 500) {
				return inst;
			}
		}
		return null;
	}

	public static ActiveDungeonInstance findInstanceAtEntrance(Level level, BlockPos pos) {
		// 1. Procura primeiro por colisão exata com a área do portal de entrada (rotacionado)
		for (ActiveDungeonInstance inst : ACTIVE_INSTANCES) {
			if (inst.getWorldLevel() != level) continue;

			PortalData portal = inst.getDungeon().getEntranceStructure().getEntrancePortal();
			if (portal != null) {
				StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(inst.getEntranceRotation());
				BlockPos p1 = inst.getEntrancePos().offset(StructureTemplate.calculateRelativePosition(settings, ensureRelative(portal.getMinPos())));
				BlockPos p2 = inst.getEntrancePos().offset(StructureTemplate.calculateRelativePosition(settings, ensureRelative(portal.getMaxPos())));
				BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
				BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
				if (pos.getX() >= min.getX() && pos.getX() <= max.getX()
						&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
						&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
					return inst;
				}
			}
		}

		// 2. Fallback: encontra a instância mais próxima da posição do portal (raio máximo 24 blocos)
		ActiveDungeonInstance closest = null;
		double closestDistSq = 24.0 * 24.0;
		for (ActiveDungeonInstance inst : ACTIVE_INSTANCES) {
			if (inst.getWorldLevel() != level) continue;
			double distSq = pos.distSqr(inst.getEntrancePos());
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = inst;
			}
		}
		return closest;
	}

	private static BlockPos findSafeExitPos(ServerLevel level, BlockPos origin, PortalData portal, Rotation rotation) {
		if (portal == null) return origin.offset(0, 1, 2);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation != null ? rotation : Rotation.NONE);
		BlockPos p1 = origin.offset(StructureTemplate.calculateRelativePosition(settings, ensureRelative(portal.getMinPos())));
		BlockPos p2 = origin.offset(StructureTemplate.calculateRelativePosition(settings, ensureRelative(portal.getMaxPos())));
		BlockPos center = new BlockPos(
				(p1.getX() + p2.getX()) / 2,
				Math.min(p1.getY(), p2.getY()),
				(p1.getZ() + p2.getZ()) / 2
		);
		for (BlockPos offset : List.of(new BlockPos(0, 0, 2), new BlockPos(0, 0, -2), new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1))) {
			BlockPos check = center.offset(offset);
			if (level.getBlockState(check).isAir() && level.getBlockState(check.above()).isAir()) {
				return check;
			}
		}
		return center.offset(0, 0, 2);
	}

	private static BlockPos findSafeInteriorPos(ServerLevel level, BlockPos origin, PortalData portal) {
		if (portal == null) return origin.offset(0, 1, 1);
		BlockPos relMin = ensureRelative(portal.getMinPos());
		BlockPos relMax = ensureRelative(portal.getMaxPos());
		BlockPos center = origin.offset(
				(relMin.getX() + relMax.getX()) / 2,
				Math.min(relMin.getY(), relMax.getY()),
				(relMin.getZ() + relMax.getZ()) / 2
		);
		for (BlockPos offset : List.of(new BlockPos(0, 0, 2), new BlockPos(0, 0, -2), new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1))) {
			BlockPos check = center.offset(offset);
			if (level.getBlockState(check).isAir() && level.getBlockState(check.above()).isAir()) {
				return check;
			}
		}
		return center.offset(0, 0, 2);
	}

	public static void onPlayerTouchEntrancePortal(ServerPlayer player, BlockPos portalPos) {
		// Em sessão de edição, portais não funcionam
		if (player.level().dimension().equals(br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.EDITOR_DIMENSION)
				|| br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.isInEditorSession(player.getUUID())) {
			return;
		}

		if (!checkAndApplyPortalCooldown(player)) {
			return;
		}

		// Caso 1: Jogador está DENTRO da dungeon e tocou no portal branco de entrada (QUER SAIR!)
		if (player.level() instanceof ServerLevel serverLevel) {
			ActiveDungeonInstance inside = findInstanceInsideDungeon(serverLevel, portalPos);
			if (inside != null) {
				BlockPos outPos = findSafeExitPos(inside.getWorldLevel(), inside.getEntrancePos(), inside.getDungeon().getEntranceStructure().getEntrancePortal(), inside.getEntranceRotation());
				player.level().getServer().execute(() -> {
					inside.getWorldLevel().getChunk(outPos.getX() >> 4, outPos.getZ() >> 4);
					player.teleportTo(inside.getWorldLevel(), outPos.getX() + 0.5, outPos.getY() + 0.1, outPos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
					player.level().playSound(null, outPos, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.portal.travel")),
							SoundSource.PLAYERS, 1.0F, 1.0F);
					player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eVocê saiu da dungeon " + inside.getDungeon().getName() + "."));
				});
				return;
			}
		}

		// Caso 2: Jogador está no mundo exterior e tocou no portal branco de entrada (QUER ENTRAR!)
		ActiveDungeonInstance inst = findInstanceAtEntrance(player.level(), portalPos);
		if (inst == null) return;

		if (inst.isCompleted()) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cEsta dungeon já foi concluída!"));
			return;
		}

		BlockPos spawnRel = inst.getDungeon().getDungeonStructure().getPlayerSpawnPos();
		float spawnYaw = inst.getDungeon().getDungeonStructure().getPlayerSpawnYaw();
		final BlockPos dest;
		final float yaw;
		if (spawnRel != null) {
			dest = inst.getDungeonOrigin().offset(ensureRelative(spawnRel));
			yaw = spawnYaw;
		} else {
			PortalData interiorPortal = inst.getDungeon().getDungeonStructure().getEntrancePortal();
			dest = findSafeInteriorPos(inst.getDungeonLevel(), inst.getDungeonOrigin(), interiorPortal);
			yaw = 0.0F;
		}

		player.level().getServer().execute(() -> {
			inst.getDungeonLevel().getChunk(dest.getX() >> 4, dest.getZ() >> 4);
			player.teleportTo(inst.getDungeonLevel(), dest.getX() + 0.5, dest.getY() + 0.1, dest.getZ() + 0.5, Set.of(), yaw, 0.0F, true);
			player.level().playSound(null, dest, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.portal.travel")),
					SoundSource.PLAYERS, 1.0F, 1.0F);
		});
	}

	public static void onPlayerTouchExitPortal(ServerPlayer player, BlockPos portalPos) {
		// Em sessão de edição, portais não funcionam
		if (player.level().dimension().equals(br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.EDITOR_DIMENSION)
				|| br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.isInEditorSession(player.getUUID())) {
			return;
		}

		if (!checkAndApplyPortalCooldown(player)) {
			return;
		}

		if (player.level() instanceof ServerLevel serverLevel) {
			ActiveDungeonInstance inst = findInstanceInsideDungeon(serverLevel, portalPos);
			if (inst != null) {
				// Marca como completado
				inst.setCompleted(true);

				// Teleporta de volta para a entrada no mundo exterior
				BlockPos outPos = findSafeExitPos(inst.getWorldLevel(), inst.getEntrancePos(), inst.getDungeon().getEntranceStructure().getEntrancePortal(), inst.getEntranceRotation());
				player.level().getServer().execute(() -> {
					inst.getWorldLevel().getChunk(outPos.getX() >> 4, outPos.getZ() >> 4);
					player.teleportTo(inst.getWorldLevel(), outPos.getX() + 0.5, outPos.getY() + 0.1, outPos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
					player.level().playSound(null, outPos, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.portal.travel")),
							SoundSource.PLAYERS, 1.0F, 1.0F);

					// Sincroniza estado de completado imediatamente no HUD
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
							player,
							new br.com.rpgsystem.dungeon.network.DungeonPayloads.PortalAimSync(
									inst.getDungeon().getName(),
									inst.getLevel(),
									Math.max(0, inst.getCompletedDespawnTicks() / 20),
									true
							)
					);

					player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§lParabéns! Você completou a dungeon " + inst.getDungeon().getName() + "!"));
				});
			}
		}
	}
}
