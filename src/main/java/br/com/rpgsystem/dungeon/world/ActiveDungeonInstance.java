package br.com.rpgsystem.dungeon.world;

import br.com.rpgsystem.dungeon.block.DungeonBlocks;
import br.com.rpgsystem.dungeon.data.*;
import br.com.rpgsystem.loot.RpgLootEntry;
import br.com.rpgsystem.loot.RpgLootTable;
import br.com.rpgsystem.loot.RpgLootTableSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class ActiveDungeonInstance {
	private final UUID instanceId = UUID.randomUUID();
	private final DungeonDefinition dungeon;
	private final int level;
	private final BlockPos entrancePos;
	private final ServerLevel worldLevel;
	private final ServerLevel dungeonLevel;
	private final BlockPos dungeonOrigin;
	private net.minecraft.world.level.block.Rotation entranceRotation = net.minecraft.world.level.block.Rotation.NONE;

	// Despawn timer
	private boolean discovered = false;
	private int despawnTimerTicks = 20 * 60 * 15; // 15 minutos padrão após descoberta
	private boolean completed = false;
	private int completedDespawnTicks = 20 * 90; // 90 segundos para despawnar após conclusão

	// Bloco de restauração para dungeons subterrâneas
	private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> carvedBlocks = new HashMap<>();
	private net.minecraft.core.Vec3i dungeonSize = net.minecraft.core.Vec3i.ZERO;

	// Estado do combate na dungeon
	private int currentWaveIndex = -1;
	private int completedWaveIndex = -1;
	private boolean waveInProgress = false;
	private final List<LivingEntity> activeWaveMobs = new ArrayList<>();
	private final Map<UUID, MobSpawnData> spawnedMobData = new HashMap<>();
	private float totalWaveMaxHealth = 0.0F;
	private ServerBossEvent bossBar = null;

	// Fila de spawn gradual
	private final Queue<MobSpawnData> pendingGradualSpawns = new ArrayDeque<>();
	private int gradualSpawnTick = 0;

	public ActiveDungeonInstance(DungeonDefinition dungeon, int level, BlockPos entrancePos,
	                             ServerLevel worldLevel, ServerLevel dungeonLevel, BlockPos dungeonOrigin) {
		this.dungeon = dungeon;
		this.level = level;
		this.entrancePos = entrancePos;
		this.worldLevel = worldLevel;
		this.dungeonLevel = dungeonLevel;
		this.dungeonOrigin = dungeonOrigin;
	}

	public UUID getInstanceId() { return instanceId; }
	public DungeonDefinition getDungeon() { return dungeon; }
	public int getLevel() { return level; }
	public BlockPos getEntrancePos() { return entrancePos; }
	public ServerLevel getWorldLevel() { return worldLevel; }
	public ServerLevel getDungeonLevel() { return dungeonLevel; }
	public BlockPos getDungeonOrigin() { return dungeonOrigin; }
	public net.minecraft.world.level.block.Rotation getEntranceRotation() { return entranceRotation; }
	public void setEntranceRotation(net.minecraft.world.level.block.Rotation rot) { this.entranceRotation = rot; }

	public boolean isDiscovered() { return discovered; }
	public void setDiscovered(boolean discovered) { this.discovered = discovered; }

	public int getDespawnTimerTicks() { return despawnTimerTicks; }
	public boolean isCompleted() { return completed; }
	public void setCompleted(boolean completed) { this.completed = completed; }
	public int getCompletedDespawnTicks() { return completedDespawnTicks; }
	public int getCompletedWaveIndex() { return completedWaveIndex; }
	public boolean isWaveInProgress() { return waveInProgress; }

	public Map<BlockPos, net.minecraft.world.level.block.state.BlockState> getCarvedBlocks() { return carvedBlocks; }
	public net.minecraft.core.Vec3i getDungeonSize() { return dungeonSize; }
	public void setDungeonSize(net.minecraft.core.Vec3i dungeonSize) { this.dungeonSize = dungeonSize; }

	public void cleanupMobs() {
		for (LivingEntity m : activeWaveMobs) {
			if (m.isAlive()) m.discard();
		}
		activeWaveMobs.clear();
		spawnedMobData.clear();
		pendingGradualSpawns.clear();
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}
	}

	public void tick() {
		// Contagem regressiva de despawn
		if (discovered && !completed) {
			if (despawnTimerTicks > 0) {
				despawnTimerTicks--;
			}
		} else if (completed) {
			if (completedDespawnTicks > 0) {
				completedDespawnTicks--;
			}
		}

		// Checa jogadores perto da entrada para iniciar o cronômetro
		if (!discovered) {
			AABB discoveryBox = new AABB(entrancePos).inflate(48.0);
			if (!worldLevel.getEntitiesOfClass(ServerPlayer.class, discoveryBox).isEmpty()) {
				discovered = true;
			}
		}

		// Spawn gradual de mobs (1 a cada 0.5s = 10 ticks)
		if (!pendingGradualSpawns.isEmpty()) {
			gradualSpawnTick++;
			if (gradualSpawnTick >= 10) {
				gradualSpawnTick = 0;
				MobSpawnData mobData = pendingGradualSpawns.poll();
				if (mobData != null) {
					spawnSingleMob(mobData);
				}
			}
		}

		// Atualiza barra de vida dos mobs da onda atual
		if (waveInProgress && currentWaveIndex >= 0) {
			activeWaveMobs.removeIf(m -> !m.isAlive() || m.isRemoved());
			float currentTotalHp = 0.0F;
			for (LivingEntity m : activeWaveMobs) {
				currentTotalHp += m.getHealth();
			}

			if (bossBar != null) {
				if (totalWaveMaxHealth > 0.0F) {
					bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, currentTotalHp / totalWaveMaxHealth)));
				}

				// Atualiza lista de jogadores na bossbar
				AABB dungeonBox = new AABB(dungeonOrigin).inflate(150.0);
				List<ServerPlayer> players = dungeonLevel.getEntitiesOfClass(ServerPlayer.class, dungeonBox);
				bossBar.getPlayers().forEach(p -> {
					if (!players.contains(p)) bossBar.removePlayer(p);
				});
				for (ServerPlayer p : players) {
					if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
				}
			}

			// Onda finalizada (todos os mobs mortos e sem fila de spawn pendente)
			if (activeWaveMobs.isEmpty() && pendingGradualSpawns.isEmpty()) {
				waveInProgress = false;
				completedWaveIndex = currentWaveIndex;
				onWaveCleared(currentWaveIndex);
			}
		}

		// Checa áreas de gatilho para os jogadores dentro da dungeon
		checkAreaTriggers();

		// Emissão de partículas constantes para mobs com constantParticles ativado
		if (!activeWaveMobs.isEmpty() && dungeonLevel.getServer().getTickCount() % 8 == 0) {
			for (LivingEntity m : activeWaveMobs) {
				if (m.isAlive() && !m.isRemoved()) {
					MobSpawnData md = spawnedMobData.get(m.getUUID());
					if (md != null && md.isConstantParticles()) {
						emitMobHitboxParticles(m, md.getParticleId(), md.getParticleIntensity() * 0.25F);
					}
				}
			}
		}
	}

	private final Map<String, Set<UUID>> playersInAreas = new HashMap<>();

	private void checkAreaTriggers() {
		AABB dungeonBox = new AABB(dungeonOrigin).inflate(150.0);
		List<ServerPlayer> players = dungeonLevel.getEntitiesOfClass(ServerPlayer.class, dungeonBox);
		if (players.isEmpty()) {
			playersInAreas.clear();
			return;
		}

		for (AreaTriggerData area : dungeon.getDungeonStructure().getAreas()) {
			BlockPos relMin = DungeonWorldManager.ensureRelative(area.getMinPos());
			BlockPos relMax = DungeonWorldManager.ensureRelative(area.getMaxPos());
			BlockPos min = dungeonOrigin.offset(Math.min(relMin.getX(), relMax.getX()), Math.min(relMin.getY(), relMax.getY()), Math.min(relMin.getZ(), relMax.getZ()));
			BlockPos max = dungeonOrigin.offset(Math.max(relMin.getX(), relMax.getX()), Math.max(relMin.getY(), relMax.getY()), Math.max(relMin.getZ(), relMax.getZ()));
			AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);

			Set<UUID> insideSet = playersInAreas.computeIfAbsent(area.getName(), k -> new HashSet<>());

			for (ServerPlayer p : players) {
				boolean inside = box.contains(p.position());
				if (inside) {
					if (insideSet.add(p.getUUID())) {
						triggerArea(area, p);
					}
				} else {
					insideSet.remove(p.getUUID());
				}
			}
		}
	}

	private void triggerArea(AreaTriggerData area, ServerPlayer player) {
		if (area.getMode() == AreaTriggerData.Mode.START_WAVE) {
			// Inicia a onda correspondente se ainda não iniciou e as anteriores foram concluídas
			String waveName = area.getTargetWaveName();
			List<WaveData> waves = dungeon.getDungeonStructure().getWaves();
			for (int i = 0; i < waves.size(); i++) {
				WaveData w = waves.get(i);
				boolean matches = (waveName != null && !waveName.isBlank() && w.getName().equalsIgnoreCase(waveName))
						|| w.getOrder() == area.getWaveOrder()
						|| (w.getActivationAreaName() != null && !w.getActivationAreaName().isBlank() && area.getName().equalsIgnoreCase(w.getActivationAreaName()));
				// Proteção de progressão de ondas:
				// Só inicia se o jogador pisar na área correspondente E já tiver completado as ondas anteriores
				if (matches && !completed && !waveInProgress && i == completedWaveIndex + 1) {
					startWave(i);
					break;
				}
			}
		} else if (area.getMode() == AreaTriggerData.Mode.SPAWN_CHEST) {
			for (ChestSpawnData c : dungeon.getDungeonStructure().getChests()) {
				if (c.getCondition() == ChestSpawnData.SpawnCondition.ENTER_AREA
						&& (area.getName().equalsIgnoreCase(c.getTargetAreaName())
						|| (area.getTargetChestName() != null && !area.getTargetChestName().isBlank() && area.getTargetChestName().equalsIgnoreCase(c.getChestName())))) {
					spawnChest(c);
				}
			}
		} else if (area.getMode() == AreaTriggerData.Mode.PLAY_MUSIC) {
			String music = area.getMusicId();
			if (music != null && !music.isBlank()) {
				Identifier loc = Identifier.tryParse(music);
				if (loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
					SoundEvent se = BuiltInRegistries.SOUND_EVENT.getValue(loc);
					SoundSource source = (music.contains("music_disc") || music.contains("record"))
							? SoundSource.RECORDS
							: SoundSource.MUSIC;

					if (area.isReplaceMusic()) {
						player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, SoundSource.MUSIC));
						player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, SoundSource.RECORDS));
					}

					player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
							BuiltInRegistries.SOUND_EVENT.wrapAsHolder(se),
							source,
							player,
							1.0F,
							1.0F,
							dungeonLevel.getRandom().nextLong()
					));
				}
			}
		}
	}

	public void startWave(int waveIndex) {
		List<WaveData> waves = dungeon.getDungeonStructure().getWaves();
		if (waveIndex < 0 || waveIndex >= waves.size()) return;
		if (waveInProgress || waveIndex != completedWaveIndex + 1) return;

		currentWaveIndex = waveIndex;
		waveInProgress = true;
		WaveData wave = waves.get(waveIndex);

		// Som de início de onda
		dungeonLevel.playSound(null, dungeonOrigin.getX(), dungeonOrigin.getY(), dungeonOrigin.getZ(),
				BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:event.raid.horn")),
				SoundSource.HOSTILE, 2.0F, 1.0F);

		// Configura Raid Boss Bar
		if (bossBar == null) {
			bossBar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Invasão: " + wave.getName()),
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		} else {
			bossBar.setName(Component.literal("Invasão: " + wave.getName()));
		}

		activeWaveMobs.clear();
		pendingGradualSpawns.clear();
		totalWaveMaxHealth = 0.0F;

		if (wave.getSpawnMode() == WaveData.SpawnMode.TOTAL) {
			for (MobSpawnData m : wave.getMobs()) {
				spawnSingleMob(m);
			}
		} else {
			// Gradual: enfileira e spawna 1 a cada 0.5s
			pendingGradualSpawns.addAll(wave.getMobs());
		}
	}

	private void spawnSingleMob(MobSpawnData mobData) {
		BlockPos relPos = DungeonWorldManager.ensureRelative(mobData.getRelativePos());
		BlockPos spawnPos = dungeonOrigin.offset(relPos);
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(mobData.getEntityId()))
				.orElseGet(() -> BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie")));

		Entity entity = type.create(dungeonLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (entity instanceof LivingEntity living) {
			living.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

			// Escala de nível do mob conforme especificado:
			// - Somente os chefes têm o nível total da dungeon
			// - Mini-chefes são muito próximos do nível da dungeon (redução de 2% a 8%)
			// - Mobs comuns são abaixo do nível da dungeon, mas não distantes (redução de 10% a 22%)
			int dungeonLvl = Math.max(1, this.level);
			int mobLevel;
			Random rnd = new Random();

			if (mobData.getCategory() == MobSpawnData.Category.BOSS) {
				mobLevel = dungeonLvl;
			} else if (mobData.getCategory() == MobSpawnData.Category.MINI_BOSS) {
				int maxRed = Math.max(1, (int) Math.round(dungeonLvl * 0.08));
				int minRed = Math.max(1, (int) Math.round(dungeonLvl * 0.02));
				int red = minRed + rnd.nextInt(Math.max(1, maxRed - minRed + 1));
				mobLevel = Math.max(1, dungeonLvl - red);
			} else {
				int minRed = Math.max(2, (int) Math.round(dungeonLvl * 0.10));
				int maxRed = Math.max(minRed, (int) Math.round(dungeonLvl * 0.22));
				int red = minRed + rnd.nextInt(Math.max(1, maxRed - minRed + 1));
				mobLevel = Math.max(1, dungeonLvl - red);
			}

			if (living instanceof br.com.rpgsystem.mobs.MobLevelAccess access) {
				access.rpgsystem$setMobLevel(mobLevel);
			}
			if (living instanceof Mob mob) {
				br.com.rpgsystem.mobs.MobLevelManager.applyAttributes(mob, mobLevel, true);
			}

			// Multiplicador de vida
			if (mobData.getHealthMultiplier() > 0.0F && mobData.getHealthMultiplier() != 1.0F) {
				var maxHealthAttr = living.getAttribute(Attributes.MAX_HEALTH);
				if (maxHealthAttr != null) {
					Identifier dungeonHealthModId = br.com.rpgsystem.RpgMod.id("dungeon_mob_health_mult");
					maxHealthAttr.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							dungeonHealthModId,
							mobData.getHealthMultiplier() - 1.0,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
					));
					living.setHealth(living.getMaxHealth());
				}
			}

			// Multiplicador de ataque (inclusive projéteis)
			if (living instanceof br.com.rpgsystem.mobs.MobLevelAccess access) {
				access.rpgsystem$setAttackMultiplier(mobData.getAttackMultiplier());
			}
			if (mobData.getAttackMultiplier() > 0.0F && mobData.getAttackMultiplier() != 1.0F) {
				var attackAttr = living.getAttribute(Attributes.ATTACK_DAMAGE);
				if (attackAttr != null) {
					Identifier dungeonAttackModId = br.com.rpgsystem.RpgMod.id("dungeon_mob_attack_mult");
					attackAttr.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							dungeonAttackModId,
							mobData.getAttackMultiplier() - 1.0,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
					));
				}
			}

			// Multiplicador de velocidade
			if (mobData.getSpeedMultiplier() > 0.0F && mobData.getSpeedMultiplier() != 1.0F) {
				var speedAttr = living.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speedAttr != null) {
					Identifier dungeonSpeedModId = br.com.rpgsystem.RpgMod.id("dungeon_mob_speed_mult");
					speedAttr.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							dungeonSpeedModId,
							mobData.getSpeedMultiplier() - 1.0,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
					));
				}
			}

			// Equipamentos configurados
			if (!mobData.getEquipment().isEmpty()) {
				for (Map.Entry<String, MobSpawnData.EquippedItemData> eqEntry : mobData.getEquipment().entrySet()) {
					net.minecraft.world.entity.EquipmentSlot slot = parseEquipmentSlot(eqEntry.getKey());
					if (slot != null && eqEntry.getValue() != null && !eqEntry.getValue().getItemId().isBlank()) {
						ItemStack itemStack = buildEquippedStack(dungeonLevel, eqEntry.getValue());
						if (!itemStack.isEmpty()) {
							boolean isHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
							boolean canEquip = isHand ? canMobHold(living) : canMobWear(living);
							if (canEquip) {
								living.setItemSlot(slot, itemStack);
								if (living instanceof Mob mob) {
									mob.setDropChance(slot, 0.0F);
								}
							}
						}
					}
				}
			}

			if (!mobData.getCustomName().isBlank()) {
				living.setCustomName(Component.literal(mobData.getCustomName()));
				living.setCustomNameVisible(true);
			}

			// Aplica escala em mini-chefe e chefe
			if (mobData.getScale() > 0.0F && living.getAttributes().hasAttribute(Attributes.SCALE)) {
				living.getAttribute(Attributes.SCALE).setBaseValue(mobData.getScale());
			}

			// Adiciona à lista de vida da onda
			totalWaveMaxHealth += living.getMaxHealth();
			activeWaveMobs.add(living);
			spawnedMobData.put(living.getUUID(), mobData);
			dungeonLevel.addFreshEntity(living);

			// Partículas geradas de acordo com a hitbox do mob
			emitMobHitboxParticles(living, mobData.getParticleId(), mobData.getParticleIntensity());

			// Som configurado
			if (mobData.getSoundId() != null && !mobData.getSoundId().isBlank()) {
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(mobData.getSoundId()));
				if (sound != null) {
					dungeonLevel.playSound(null, spawnPos, sound, SoundSource.HOSTILE, 1.0F, 1.0F);
				}
			}
		}
	}

	private void emitMobHitboxParticles(LivingEntity entity, String particleId, float intensity) {
		AABB bb = entity.getBoundingBox();
		int count = Math.max(8, (int) (intensity * 20));
		ParticleOptions particle = ParticleTypes.SMOKE;
		try {
			var opt = BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.parse(particleId));
			if (opt.isPresent() && opt.get() instanceof ParticleOptions po) {
				particle = po;
			}
		} catch (Throwable ignored) {}

		for (int i = 0; i < count; i++) {
			double px = bb.minX + dungeonLevel.getRandom().nextDouble() * (bb.maxX - bb.minX);
			double py = bb.minY + dungeonLevel.getRandom().nextDouble() * (bb.maxY - bb.minY);
			double pz = bb.minZ + dungeonLevel.getRandom().nextDouble() * (bb.maxZ - bb.minZ);
			dungeonLevel.sendParticles(particle, px, py, pz, 1, 0.0, 0.05, 0.0, 0.02);
		}
	}

	public void handleMobDeath(LivingEntity mob) {
		activeWaveMobs.remove(mob);
		MobSpawnData md = spawnedMobData.remove(mob.getUUID());
		if (md == null) {
			for (WaveData w : dungeon.getDungeonStructure().getWaves()) {
				for (MobSpawnData d : w.getMobs()) {
					if (d.getEntityId().equalsIgnoreCase(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())) {
						md = d;
						break;
					}
				}
				if (md != null) break;
			}
		}

		if (md != null) {
			// 1. Drop da Tabela de Loot (se houver tabela configurada e rolar a chance)
			if (md.getLootTableName() != null && !md.getLootTableName().isBlank()) {
				if (dungeonLevel.getRandom().nextDouble() * 100.0 < md.getDropChance()) {
					rollLootDrop(md.getLootTableName(), mob.blockPosition());
				}
			}

			// 2. Drop de Itens Equipados (se dropEquipment estiver ativado)
			if (md.isDropEquipment()) {
				for (Map.Entry<String, MobSpawnData.EquippedItemData> entry : md.getEquipment().entrySet()) {
					MobSpawnData.EquippedItemData eqData = entry.getValue();
					if (eqData == null || eqData.getItemId().isBlank()) continue;
					double chance = eqData.getDropChance();
					// Rolagem individual para cada item equipado
					if (dungeonLevel.getRandom().nextDouble() * 100.0 < chance) {
						ItemStack dropStack = buildEquippedStack(dungeonLevel, eqData);
						if (!dropStack.isEmpty()) {
							ItemEntity itemEntity = new ItemEntity(
									dungeonLevel,
									mob.getX(),
									mob.getY() + 0.5,
									mob.getZ(),
									dropStack
							);
							itemEntity.setDefaultPickUpDelay();
							dungeonLevel.addFreshEntity(itemEntity);
						}
					}
				}
			}
		}
	}

	private void rollLootDrop(String tableName, BlockPos pos) {
		Optional<RpgLootTable> tableOpt = RpgLootTableSavedData.get(dungeonLevel.getServer()).getTable(tableName);
		if (tableOpt.isEmpty()) return;
		RpgLootTable table = tableOpt.get();
		List<RpgLootEntry> entries = table.itemsList();
		if (entries.isEmpty()) return;

		// Roleta 1 item da tabela de acordo com o peso de cada um
		double totalWeight = entries.stream().mapToDouble(RpgLootEntry::weight).sum();
		if (totalWeight <= 0) return;

		double roll = dungeonLevel.getRandom().nextDouble() * totalWeight;
		double current = 0;
		for (RpgLootEntry entry : entries) {
			current += entry.weight();
			if (roll <= current) {
				ItemStack stack = entry.item().copy();
				if (!stack.isEmpty()) {
					ItemEntity itemEntity = new ItemEntity(dungeonLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
					dungeonLevel.addFreshEntity(itemEntity);
				}
				break;
			}
		}
	}

	private void onWaveCleared(int waveIndex) {
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}

		// Spawna baús configurados para "Após onda"
		for (ChestSpawnData c : dungeon.getDungeonStructure().getChests()) {
			if (c.getCondition() == ChestSpawnData.SpawnCondition.AFTER_WAVE && c.getWaveOrder() == waveIndex + 1) {
				spawnChest(c);
			}
		}

		// Som de onda completada com sucesso
		dungeonLevel.playSound(null, dungeonOrigin.getX(), dungeonOrigin.getY(), dungeonOrigin.getZ(),
				BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:entity.player.levelup")),
				SoundSource.PLAYERS, 1.5F, 1.2F);

		// Se for a última onda, completa e spawna o portal de saída
		if (waveIndex >= dungeon.getDungeonStructure().getWaves().size() - 1) {
			onDungeonCompleted();
		}
	}

	private void onDungeonCompleted() {
		completed = true;
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}

		// Spawna o Portal de Saída (portal azul claro)
		PortalData exitPortal = dungeon.getDungeonStructure().getExitPortal();
		if (exitPortal != null) {
			BlockPos relMin = DungeonWorldManager.ensureRelative(exitPortal.getMinPos());
			BlockPos relMax = DungeonWorldManager.ensureRelative(exitPortal.getMaxPos());
			BlockPos min = dungeonOrigin.offset(Math.min(relMin.getX(), relMax.getX()), Math.min(relMin.getY(), relMax.getY()), Math.min(relMin.getZ(), relMax.getZ()));
			BlockPos max = dungeonOrigin.offset(Math.max(relMin.getX(), relMax.getX()), Math.max(relMin.getY(), relMax.getY()), Math.max(relMin.getZ(), relMax.getZ()));
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				dungeonLevel.setBlock(p, DungeonBlocks.BLUE_PORTAL.defaultBlockState(), 3);
			}
		}

		// Toca som de vitória
		dungeonLevel.playSound(null, dungeonOrigin.getX(), dungeonOrigin.getY(), dungeonOrigin.getZ(),
				BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:ui.toast.challenge_complete")),
				SoundSource.PLAYERS, 2.0F, 1.0F);
	}

	private void spawnChest(ChestSpawnData data) {
		BlockPos relPos = DungeonWorldManager.ensureRelative(data.getRelativePos());
		BlockPos chestPos = dungeonOrigin.offset(relPos);
		Direction facing = data.getFacing() != null ? data.getFacing() : Direction.NORTH;
		dungeonLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
		BlockEntity be = dungeonLevel.getBlockEntity(chestPos);
		if (be instanceof ChestBlockEntity chestBe && !data.getLootTableName().isBlank()) {
			Optional<RpgLootTable> tableOpt = RpgLootTableSavedData.get(dungeonLevel.getServer()).getTable(data.getLootTableName());
			if (tableOpt.isPresent()) {
				List<ItemStack> items = tableOpt.get().rollLoot(dungeonLevel.getRandom());
				int slot = 0;
				for (ItemStack s : items) {
					if (slot < chestBe.getContainerSize()) {
						chestBe.setItem(slot++, s);
					}
				}
				chestBe.setChanged();
			}
		}
	}

	private static boolean canMobWear(LivingEntity entity) {
		if (entity == null) return false;
		return entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| entity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
				|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
				|| entity instanceof net.minecraft.world.entity.player.Player
				|| entity instanceof net.minecraft.world.entity.decoration.ArmorStand;
	}

	private static boolean canMobHold(LivingEntity entity) {
		if (entity == null) return false;
		return entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| entity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
				|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
				|| entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager
				|| entity instanceof net.minecraft.world.entity.monster.Witch
				|| entity instanceof net.minecraft.world.entity.monster.Enderman
				|| entity instanceof net.minecraft.world.entity.monster.Vex
				|| entity instanceof net.minecraft.world.entity.animal.fox.Fox
				|| entity instanceof net.minecraft.world.entity.animal.allay.Allay
				|| entity instanceof net.minecraft.world.entity.player.Player
				|| entity instanceof net.minecraft.world.entity.decoration.ArmorStand;
	}

	private EquipmentSlot parseEquipmentSlot(String slotName) {
		if (slotName == null || slotName.isBlank()) return null;
		try {
			EquipmentSlot byName = EquipmentSlot.byName(slotName.toLowerCase(Locale.ROOT));
			if (byName != null) return byName;
		} catch (Throwable ignored) {}
		try {
			return EquipmentSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
		} catch (Throwable ignored) {}
		return null;
	}

	private ItemStack buildEquippedStack(ServerLevel level, MobSpawnData.EquippedItemData itemData) {
		if (itemData == null || itemData.getItemId().isBlank()) return ItemStack.EMPTY;
		Identifier id = Identifier.tryParse(itemData.getItemId());
		if (id == null) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) return ItemStack.EMPTY;

		ItemStack stack = new ItemStack(item);
		if (stack.isDamageableItem()) {
			int maxDmg = stack.getMaxDamage();
			if (itemData.getDurability() > 0 && itemData.getDurability() <= maxDmg) {
				stack.setDamageValue(Math.max(0, maxDmg - itemData.getDurability()));
			}
		}

		if (!itemData.getEnchantments().isEmpty()) {
			var enchLookup = level.registryAccess().lookup(Registries.ENCHANTMENT);
			if (enchLookup.isPresent()) {
				for (Map.Entry<String, Integer> e : itemData.getEnchantments().entrySet()) {
					Identifier enchId = Identifier.tryParse(e.getKey());
					if (enchId != null) {
						var optEnch = enchLookup.get().get(ResourceKey.create(Registries.ENCHANTMENT, enchId));
						if (optEnch.isPresent()) {
							stack.enchant(optEnch.get(), Math.max(1, e.getValue()));
						}
					}
				}
			}
		}

		return stack;
	}
}
