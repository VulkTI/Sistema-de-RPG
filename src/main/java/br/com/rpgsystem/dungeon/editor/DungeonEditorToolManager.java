package br.com.rpgsystem.dungeon.editor;

import br.com.rpgsystem.dungeon.block.BluePortalBlock;
import br.com.rpgsystem.dungeon.block.DungeonBlocks;
import br.com.rpgsystem.dungeon.block.WhitePortalBlock;
import br.com.rpgsystem.dungeon.data.*;
import br.com.rpgsystem.dungeon.network.DungeonNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonEditorToolManager {
	public static final BlockPos EDITOR_ORIGIN = new BlockPos(0, 64, 0);

	public static BlockPos toEditorPos(BlockPos pos) {
		if (pos == null) return EDITOR_ORIGIN;
		if (pos.getY() < 50) {
			return EDITOR_ORIGIN.offset(pos);
		}
		return pos;
	}

	public static BlockPos toRelativePos(BlockPos pos) {
		if (pos == null) return BlockPos.ZERO;
		if (pos.getY() >= 50) {
			return pos.subtract(EDITOR_ORIGIN);
		}
		return pos;
	}

	private DungeonEditorToolManager() {}

	public static void giveWaveEditingTools(ServerPlayer player, DungeonEditorSession session) {
		player.getInventory().clearContent();

		// Slot 1: Lâmpada (Mob Spawner)
		player.getInventory().setItem(0, createMobSpawnerTool(session.getCurrentMobCategory()));

		// Slot 2: Baú (Chest Spawner)
		player.getInventory().setItem(1, createChestSpawnerTool(session.getCurrentChestCondition(), session.getDefaultChestData().getTargetAreaName()));

		// Slot 3: Seletor de Áreas
		player.getInventory().setItem(2, createAreaSelectorTool(session.getCurrentAreaMode()));

		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		int waveIdx = session.getEditingWaveIndex();

		// Slot 4: Definir Spawn do Jogador (Apenas na Onda 1)
		if (waveIdx == 0) {
			if (session.getPlayerSpawnPos() == null && def != null && def.getDungeonStructure().getPlayerSpawnPos() != null) {
				session.setPlayerSpawnPos(toEditorPos(def.getDungeonStructure().getPlayerSpawnPos()));
				session.setPlayerSpawnYaw(def.getDungeonStructure().getPlayerSpawnYaw());
			}
			player.getInventory().setItem(3, createPlayerSpawnTool(session.getPlayerSpawnPos(), session.getPlayerSpawnYaw()));
		} else {
			player.getInventory().setItem(3, ItemStack.EMPTY);
		}

		// Slots 7, 8, 9
		player.getInventory().setItem(6, createUndoTool());
		player.getInventory().setItem(7, createRedoTool());
		player.getInventory().setItem(8, createSaveTool());

		player.containerMenu.broadcastChanges();

		// Coloca os blocos de marcadores desta onda no mundo para visualização e edição
		if (def != null && "DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			if (waveIdx >= 0 && waveIdx < def.getDungeonStructure().getWaves().size()) {
				WaveData wave = def.getDungeonStructure().getWaves().get(waveIdx);
				ServerLevel level = (ServerLevel) player.level();
				for (MobSpawnData m : wave.getMobs()) {
					if (m.getRelativePos() != null) {
						BlockPos editorPos = toEditorPos(m.getRelativePos());
						m.setRelativePos(editorPos);
						BlockState markerState = m.getCategory() == MobSpawnData.Category.BOSS
								? Blocks.REDSTONE_BLOCK.defaultBlockState()
								: m.getCategory() == MobSpawnData.Category.MINI_BOSS
								? Blocks.GOLD_BLOCK.defaultBlockState()
								: Blocks.EMERALD_BLOCK.defaultBlockState();
						level.setBlock(editorPos, markerState, 3);
						session.getPlacedBlocks().add(editorPos);
						session.getPlacedMarkers().put(editorPos, m);
					}
				}
				for (ChestSpawnData c : def.getDungeonStructure().getChests()) {
					if (c.getWaveOrder() == wave.getOrder() && c.getRelativePos() != null) {
						BlockPos editorPos = toEditorPos(c.getRelativePos());
						c.setRelativePos(editorPos);
						Direction f = c.getFacing() != null ? c.getFacing() : Direction.NORTH;
						level.setBlock(editorPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f), 3);
						session.getPlacedBlocks().add(editorPos);
						session.getPlacedMarkers().put(editorPos, c);
					}
				}
				// Coloca portal de entrada da dungeon se houver
				PortalData inPortal = def.getDungeonStructure().getEntrancePortal();
				if (inPortal != null) {
					BlockPos min = toEditorPos(inPortal.getMinPos());
					BlockPos max = toEditorPos(inPortal.getMaxPos());
					for (BlockPos b : BlockPos.betweenClosed(min, max)) {
						BlockPos imm = b.immutable();
						level.setBlock(imm, DungeonBlocks.WHITE_PORTAL.defaultBlockState(), 3);
						session.getPlacedBlocks().add(imm);
					}
				}
				// Coloca portal de saída (azul) se houver
				PortalData exitPortal = def.getDungeonStructure().getExitPortal();
				if (exitPortal != null) {
					BlockPos min = toEditorPos(exitPortal.getMinPos());
					BlockPos max = toEditorPos(exitPortal.getMaxPos());
					for (BlockPos b : BlockPos.betweenClosed(min, max)) {
						BlockPos imm = b.immutable();
						level.setBlock(imm, DungeonBlocks.BLUE_PORTAL.defaultBlockState(), 3);
						session.getPlacedBlocks().add(imm);
					}
				}
				refreshAreaOverlays(level, session, def, wave.getOrder());
			}
		}
	}

	public static void givePortalEditingTools(ServerPlayer player, DungeonEditorSession session) {
		player.getInventory().clearContent();

		// Se estiver editando a dungeon, entrega as duas ferramentas diretamente na hotbar:
		// Slot 1 (índice 0): Portal de Entrada (branco)
		// Slot 2 (índice 1): Portal de Saída (azul claro)
		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			player.getInventory().setItem(0, createPortalTool(PortalData.Type.ENTRANCE_WHITE));
			player.getInventory().setItem(1, createPortalTool(PortalData.Type.EXIT_LIGHT_BLUE));
		} else {
			// Na estrutura de Entrada, só existe portal de entrada
			player.getInventory().setItem(0, createPortalTool(PortalData.Type.ENTRANCE_WHITE));
		}

		// Slots 7, 8, 9
		player.getInventory().setItem(6, createUndoTool());
		player.getInventory().setItem(7, createRedoTool());
		player.getInventory().setItem(8, createSaveTool());

		player.containerMenu.broadcastChanges();
	}

	public static ItemStack createMobSpawnerTool(MobSpawnData.Category category) {
		ItemStack stack = new ItemStack(Items.SEA_LANTERN);
		String color = category == MobSpawnData.Category.BOSS ? "§c" : category == MobSpawnData.Category.MINI_BOSS ? "§6" : "§a";
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(color + "§lFerramenta de Spawn: " + category.displayName()));
		List<Component> lore = List.of(
				Component.literal("§7Botão Direito: Coloca spawner"),
				Component.literal("§7Botão Esquerdo: Remove spawner"),
				Component.literal("§7Shift + Botão Direito (bloco): Edita spawner"),
				Component.literal("§7Shift + Botão Direito (ar): Abre configurações"),
				Component.literal("§7Shift + Scroll: Alterna modo (Mob / Mini-chefe / Chefe)")
		);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack createChestSpawnerTool(ChestSpawnData.SpawnCondition condition, String areaName) {
		ItemStack stack = new ItemStack(Items.CHEST);
		String condStr = condition.displayName();
		if (condition == ChestSpawnData.SpawnCondition.ENTER_AREA && !areaName.isBlank()) {
			condStr += " (" + areaName + ")";
		}
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lFerramenta de Baú: " + condStr));
		List<Component> lore = List.of(
				Component.literal("§7Botão Direito: Coloca baú"),
				Component.literal("§7Botão Esquerdo: Remove baú"),
				Component.literal("§7Shift + Botão Direito: Configura baú"),
				Component.literal("§7Shift + Scroll: Alterna modo (Sempre / Após onda / Área)")
		);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack createAreaSelectorTool(AreaTriggerData.Mode mode) {
		ItemStack stack = new ItemStack(Items.RECOVERY_COMPASS);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§b§lSeletor de Áreas: " + mode.displayName()));
		List<Component> lore = List.of(
				Component.literal("§7Clique 1: Define primeiro ponto"),
				Component.literal("§7Clique 2: Define segundo ponto e cria área"),
				Component.literal("§7Shift + Scroll: Alterna modo de ativação")
		);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack createPlayerSpawnTool(BlockPos spawnPos, float yaw) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§d§lDefinir Spawn do Jogador"));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal("§7Botão Direito no bloco: Define a posição"));
		lore.add(Component.literal("§7e direção (N, S, E, W) que você está olhando"));
		lore.add(Component.literal("§7como o local de spawn do jogador na dungeon."));
		lore.add(Component.literal("§c* Obrigatório definir na Onda 1 para salvar!"));
		if (spawnPos != null) {
			Direction dir = Direction.fromYRot(yaw);
			String dirStr = switch (dir) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> "N"; };
			lore.add(Component.literal("§a✔ Definido em: " + toEditorPos(spawnPos).toShortString() + " (Direção: " + dirStr + ")"));
		} else {
			lore.add(Component.literal("§e⚠ Ainda não definido"));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack createPortalTool(PortalData.Type type) {
		ItemStack stack = new ItemStack(Items.END_PORTAL_FRAME);
		String color = type == PortalData.Type.EXIT_LIGHT_BLUE ? "§b" : "§f";
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(color + "§lFerramenta de Portal: " + type.getTitle()));
		List<Component> lore = List.of(
				Component.literal("§7Clique 1: Primeiro ponto do portal (2D)"),
				Component.literal("§7Clique 2: Segundo ponto do portal (2D)"),
				Component.literal("§7Shift + Scroll: Alterna tipo de portal")
		);
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static ItemStack createUndoTool() {
		ItemStack stack = new ItemStack(Items.ARROW);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e§lRetornar (Desfazer)"));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7Clique com Botão Direito para desfazer última ação"))));
		return stack;
	}

	public static ItemStack createRedoTool() {
		ItemStack stack = new ItemStack(Items.SPECTRAL_ARROW);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§b§lAvançar (Refazer)"));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7Clique com Botão Direito para refazer ação"))));
		return stack;
	}

	public static ItemStack createSaveTool() {
		ItemStack stack = new ItemStack(Items.NETHER_STAR);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a§lSalvar Alterações"));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7Clique com Botão Direito para revisar e salvar"))));
		return stack;
	}

	/**
	 * Alterna o modo da ferramenta atual quando o jogador gira a roda do mouse segurando Shift.
	 */
	public static void cycleToolMode(ServerPlayer player, DungeonEditorSession session, int slot, boolean forward) {
		if (session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			if (slot == 0) {
				// Mob tool
				MobSpawnData.Category[] cats = MobSpawnData.Category.values();
				int next = (session.getCurrentMobCategory().ordinal() + (forward ? 1 : cats.length - 1)) % cats.length;
				session.setCurrentMobCategory(cats[next]);
				player.getInventory().setItem(0, createMobSpawnerTool(cats[next]));
				player.sendSystemMessage(Component.literal("§aModo de Mob: " + cats[next].displayName()), true);
			} else if (slot == 1) {
				// Chest tool
				ChestSpawnData.SpawnCondition[] conds = ChestSpawnData.SpawnCondition.values();
				int next = (session.getCurrentChestCondition().ordinal() + (forward ? 1 : conds.length - 1)) % conds.length;
				session.setCurrentChestCondition(conds[next]);
				player.getInventory().setItem(1, createChestSpawnerTool(conds[next], session.getDefaultChestData().getTargetAreaName()));
				player.sendSystemMessage(Component.literal("§6Modo de Baú: " + conds[next].displayName()), true);
			} else if (slot == 2) {
				// Area selector
				AreaTriggerData.Mode[] modes = AreaTriggerData.Mode.values();
				int next = (session.getCurrentAreaMode().ordinal() + (forward ? 1 : modes.length - 1)) % modes.length;
				session.setCurrentAreaMode(modes[next]);
				player.getInventory().setItem(2, createAreaSelectorTool(modes[next]));
				player.sendSystemMessage(Component.literal("§bModo de Área: " + modes[next].displayName()), true);
			}
		} else if (session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			if (slot == 0) {
				session.setCurrentPortalType(PortalData.Type.ENTRANCE_WHITE);
				player.sendSystemMessage(Component.literal("§dTipo de Portal: " + PortalData.Type.ENTRANCE_WHITE.getTitle()), true);
			} else if (slot == 1 && "DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
				session.setCurrentPortalType(PortalData.Type.EXIT_LIGHT_BLUE);
				player.sendSystemMessage(Component.literal("§dTipo de Portal: " + PortalData.Type.EXIT_LIGHT_BLUE.getTitle()), true);
			}
		}
		player.containerMenu.broadcastChanges();
	}

	public static InteractionResult handleUseItem(ServerPlayer player, InteractionHand hand) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return InteractionResult.PASS;
		if (session.getState() != DungeonEditorSession.SessionState.TOOL_WAVE_EDIT
				&& session.getState() != DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			return InteractionResult.PASS;
		}

		int slot = player.getInventory().getSelectedSlot();

		// Slot 7: Undo
		if (slot == 6) {
			undoAction(player, session);
			return InteractionResult.SUCCESS;
		}

		// Slot 8: Redo
		if (slot == 7) {
			redoAction(player, session);
			return InteractionResult.SUCCESS;
		}

		// Slot 9: Salvar
		if (slot == 8) {
			showSaveSummary(player, session);
			return InteractionResult.SUCCESS;
		}

		// Shift + RMB no ar
		if (player.isShiftKeyDown()) {
			if (slot == 0 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
				DungeonNetworking.openMobToolScreen(player, session.getDefaultMobData(), session.getCurrentMobCategory(), null);
				return InteractionResult.SUCCESS;
			} else if (slot == 1 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
				DungeonNetworking.openChestToolScreen(player, session.getDefaultChestData(), null);
				return InteractionResult.SUCCESS;
			} else if (slot == 2 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
				DungeonNetworking.openAreaToolScreen(player, session.getDefaultAreaData());
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}

	public static InteractionResult handleUseBlock(ServerPlayer player, ServerLevel level, BlockHitResult hit, InteractionHand hand) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return InteractionResult.PASS;
		if (session.getState() != DungeonEditorSession.SessionState.TOOL_WAVE_EDIT
				&& session.getState() != DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			return InteractionResult.PASS;
		}

		BlockPos clickedPos = hit.getBlockPos();
		Direction face = hit.getDirection();
		BlockState clickedState = level.getBlockState(clickedPos);
		BlockPos targetPos = clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(face);

		int slot = player.getInventory().getSelectedSlot();

		// Shift + RMB em bloco já configurado
		if (player.isShiftKeyDown()) {
			Object existing = session.getPlacedMarkers().get(clickedPos);
			BlockPos markerPos = clickedPos;
			if (existing == null) {
				existing = session.getPlacedMarkers().get(targetPos);
				markerPos = targetPos;
			}

			if (existing instanceof MobSpawnData mob) {
				DungeonNetworking.openMobToolScreen(player, mob, mob.getCategory(), markerPos);
				return InteractionResult.SUCCESS;
			} else if (existing instanceof ChestSpawnData chest) {
				DungeonNetworking.openChestToolScreen(player, chest, markerPos);
				return InteractionResult.SUCCESS;
			} else if (slot == 2 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
				DungeonNetworking.openAreaToolScreen(player, session.getDefaultAreaData());
				return InteractionResult.SUCCESS;
			}
		}

		// Ferramenta 1: Mob Spawner
		if (slot == 0 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			BlockPos placePos = targetPos;
			BlockState markerState = session.getCurrentMobCategory() == MobSpawnData.Category.BOSS
					? Blocks.REDSTONE_BLOCK.defaultBlockState()
					: session.getCurrentMobCategory() == MobSpawnData.Category.MINI_BOSS
					? Blocks.GOLD_BLOCK.defaultBlockState()
					: Blocks.EMERALD_BLOCK.defaultBlockState();

			BlockState oldState = level.getBlockState(placePos);
			level.setBlock(placePos, markerState, 3);
			session.getPlacedBlocks().add(placePos);

			MobSpawnData mobData = session.getDefaultMobData().copy();
			mobData.setCategory(session.getCurrentMobCategory());
			mobData.setRelativePos(placePos);

			session.getPlacedMarkers().put(placePos, mobData);
			session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
					DungeonUndoRedo.ActionType.PLACE_MARKER, placePos, oldState, markerState, null, mobData));

			player.sendSystemMessage(Component.literal("§aSpawner de " + mobData.getCategory().displayName() + " posicionado!"), true);
			return InteractionResult.SUCCESS;
		}

		// Ferramenta 2: Baú
		if (slot == 1 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			BlockPos placePos = targetPos;
			Direction facing = player.getDirection().getOpposite();
			BlockState markerState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
			BlockState oldState = level.getBlockState(placePos);
			level.setBlock(placePos, markerState, 3);
			session.getPlacedBlocks().add(placePos);

			ChestSpawnData chestData = new ChestSpawnData(session.getDefaultChestData().getChestName(), placePos);
			chestData.setFacing(facing);
			chestData.setLootTableName(session.getDefaultChestData().getLootTableName());
			chestData.setCondition(session.getCurrentChestCondition());
			chestData.setWaveOrder(session.getEditingWaveIndex() + 1);
			chestData.setTargetAreaName(session.getDefaultChestData().getTargetAreaName());

			session.getPlacedMarkers().put(placePos, chestData);
			session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
					DungeonUndoRedo.ActionType.PLACE_MARKER, placePos, oldState, markerState, null, chestData));

			player.sendSystemMessage(Component.literal("§6Baú posicionado (" + chestData.getCondition().displayName() + ")!"), true);
			return InteractionResult.SUCCESS;
		}

		// Ferramenta 3: Seletor de áreas
		if (slot == 2 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
			if (session.getAreaPoint1() == null) {
				session.setAreaPoint1(targetPos);
				level.setBlock(targetPos, DungeonBlocks.AREA_OVERLAY.defaultBlockState(), 2);
				session.getActiveOverlayBlocks().add(targetPos);
				spawnPointParticles(level, targetPos);
				player.sendSystemMessage(Component.literal("§b[Área - Ponto 1] Definido em: " + targetPos.toShortString() + " §e(Clique no segundo ponto para fechar a área)"), true);
			} else {
				BlockPos p1 = session.getAreaPoint1();
				BlockPos p2 = targetPos;
				session.setAreaPoint1(null);

				BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
				BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));

				// Preenche overlay para visualização
				for (BlockPos b : BlockPos.betweenClosed(min, max)) {
					BlockPos imm = b.immutable();
					BlockState st = level.getBlockState(imm);
					if (st.isAir() || st.canBeReplaced()) {
						level.setBlock(imm, DungeonBlocks.AREA_OVERLAY.defaultBlockState(), 2);
						session.getActiveOverlayBlocks().add(imm);
					}
				}

				spawnAreaBoxParticles(level, min, max);

				int nextNum = 1;
				DungeonDefinition dungeon = DungeonRepository.get(session.getDungeonName()).orElse(null);
				if (dungeon != null) {
					for (AreaTriggerData a : dungeon.getDungeonStructure().getAreas()) {
						if (a.getName().startsWith("Área ")) {
							try {
								int n = Integer.parseInt(a.getName().substring(5).trim());
								if (n >= nextNum) nextNum = n + 1;
							} catch (NumberFormatException ignored) {}
						}
					}
				}
				String areaName = "Área " + nextNum;

				AreaTriggerData area = new AreaTriggerData(areaName, session.getCurrentAreaMode(), min, max);
				area.setWaveOrder(session.getEditingWaveIndex() + 1);
				DungeonNetworking.openAreaToolScreen(player, area);
			}
			return InteractionResult.SUCCESS;
		}

		// Ferramenta 4: Definir Spawn do Jogador (Slot 4 / index 3, apenas Onda 1)
		if (slot == 3 && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT && session.getEditingWaveIndex() == 0) {
			Direction dir = Direction.fromYRot(player.getYRot());
			float yaw = dir.toYRot();
			session.setPlayerSpawnPos(targetPos);
			session.setPlayerSpawnYaw(yaw);

			player.getInventory().setItem(3, createPlayerSpawnTool(targetPos, yaw));
			player.containerMenu.broadcastChanges();

			String dirStr = switch (dir) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> "N"; };
			spawnPointParticles(level, targetPos);
			player.sendSystemMessage(Component.literal("§d✔ Ponto de spawn do jogador definido em " + targetPos.toShortString() + " (Direção: " + dirStr + ")!"), true);
			return InteractionResult.SUCCESS;
		}

		// Ferramenta de Portais (Slot 0: Entrada, Slot 1: Saída na Dungeon)
		if ((slot == 0 || (slot == 1 && "DUNGEON".equalsIgnoreCase(session.getSelectedStructure())))
				&& session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			PortalData.Type toolType = slot == 1 ? PortalData.Type.EXIT_LIGHT_BLUE : PortalData.Type.ENTRANCE_WHITE;
			session.setCurrentPortalType(toolType);
			if (session.getPortalPoint1() == null) {
				session.setPortalPoint1(targetPos);
				level.setBlock(targetPos, DungeonBlocks.PORTAL_OVERLAY.defaultBlockState(), 2);
				session.getActiveOverlayBlocks().add(targetPos);
				spawnPointParticles(level, targetPos);
				player.sendSystemMessage(Component.literal("§d[" + toolType.getTitle() + " - Ponto 1] Definido em: " + targetPos.toShortString() + " §e(Clique no segundo ponto)"), true);
			} else {
				BlockPos p1 = session.getPortalPoint1();
				BlockPos p2 = targetPos;

				// Remove overlay do ponto 1
				level.setBlock(p1, Blocks.AIR.defaultBlockState(), 2);
				session.getActiveOverlayBlocks().remove(p1);

				// Garante que é uma área 2D (profundidade de apenas 1 bloco: plana em X, Y ou Z)
				int dx = Math.abs(p1.getX() - p2.getX());
				int dy = Math.abs(p1.getY() - p2.getY());
				int dz = Math.abs(p1.getZ() - p2.getZ());

				if (dx > 0 && dy > 0 && dz > 0) {
					player.sendSystemMessage(Component.literal("§cO portal não pode ter profundidade maior que 1 bloco! Ele deve ser plano em X, Y ou Z."), true);
					return InteractionResult.SUCCESS;
				}

				session.setPortalPoint1(null);

				BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
				BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));

				BlockState portalState = toolType == PortalData.Type.EXIT_LIGHT_BLUE
						? DungeonBlocks.BLUE_PORTAL.defaultBlockState()
						: DungeonBlocks.WHITE_PORTAL.defaultBlockState();

				PortalData portalData = new PortalData(toolType, min, max);
				List<BlockPos> createdPositions = new ArrayList<>();
				Map<BlockPos, BlockState> prevStates = new HashMap<>();

				for (BlockPos b : BlockPos.betweenClosed(min, max)) {
					BlockPos immutable = b.immutable();
					createdPositions.add(immutable);
					prevStates.put(immutable, level.getBlockState(immutable));
					level.setBlock(immutable, portalState, 3);
					session.getPlacedBlocks().add(immutable);
					session.getPlacedMarkers().put(immutable, portalData);
				}

				portalData.setReplacedStates(prevStates);

				DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
				if (def != null) {
					setPortalInDef(def, session.getSelectedStructure(), portalData);
				}

				session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
						DungeonUndoRedo.ActionType.PLACE_MARKER, min, createdPositions, Blocks.AIR.defaultBlockState(), portalState, null, portalData, prevStates));

				player.sendSystemMessage(Component.literal("§d" + toolType.getTitle() + " criado com sucesso!"), true);
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	public static boolean handleBlockBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return true;

		BlockState state = level.getBlockState(pos);
		boolean isPortalBlock = state.getBlock() instanceof WhitePortalBlock || state.getBlock() instanceof BluePortalBlock;
		boolean isPortalOverlay = state.getBlock() == DungeonBlocks.PORTAL_OVERLAY;
		boolean isAreaOverlay = state.getBlock() == DungeonBlocks.AREA_OVERLAY;

		// Se for bloco de portal fora da edição de portais, impede qualquer quebra
		if ((isPortalBlock || isPortalOverlay) && session.getState() != DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			return false;
		}

		if (session.getState() != DungeonEditorSession.SessionState.TOOL_WAVE_EDIT
				&& session.getState() != DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			return true;
		}

		ItemStack held = player.getMainHandItem();

		// Se estiver na ferramenta de portal
		if (session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			if (isPortalBlock) {
				if (!held.is(Items.END_PORTAL_FRAME)) {
					player.sendSystemMessage(Component.literal("§cVocê precisa segurar a Ferramenta de Portal para remover o portal!"), true);
					return false;
				}
				removePortalAt(player, level, session, pos);
				return false;
			}
			if (session.getPortalPoint1() != null) {
				BlockPos p1 = session.getPortalPoint1();
				session.setPortalPoint1(null);
				level.setBlock(p1, Blocks.AIR.defaultBlockState(), 2);
				session.getActiveOverlayBlocks().remove(p1);
				player.sendSystemMessage(Component.literal("§ePrimeiro ponto do portal cancelado!"), true);
				return false;
			}
			return false;
		}

		// Se estiver na ferramenta de seleção de áreas e clicou botão esquerdo com ponto 1 ativo
		if (session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT && session.getAreaPoint1() != null) {
			BlockPos p1 = session.getAreaPoint1();
			session.setAreaPoint1(null);
			level.setBlock(p1, Blocks.AIR.defaultBlockState(), 2);
			session.getActiveOverlayBlocks().remove(p1);
			player.sendSystemMessage(Component.literal("§ePrimeiro ponto de área cancelado!"), true);
			return false;
		}

		// Se for área de overlay
		if (isAreaOverlay) {
			if (!held.is(Items.RECOVERY_COMPASS)) {
				player.sendSystemMessage(Component.literal("§cVocê precisa segurar o Seletor de Áreas para quebrar esta área!"), true);
				return false;
			}
			removeAreaAt(player, level, session, pos);
			return false;
		}

		// Se quebrar um bloco de marker configurado (Mob ou Baú)
		Object existing = session.getPlacedMarkers().get(pos);
		if (existing instanceof MobSpawnData) {
			if (!held.is(Items.SEA_LANTERN)) {
				player.sendSystemMessage(Component.literal("§cVocê precisa segurar a Ferramenta de Mob Spawner para quebrar este bloco!"), true);
				return false;
			}
			session.getPlacedMarkers().remove(pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			session.getPlacedBlocks().remove(pos);
			session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
					DungeonUndoRedo.ActionType.REMOVE_MARKER, pos, state, Blocks.AIR.defaultBlockState(), existing, null));
			player.sendSystemMessage(Component.literal("§cMob Spawner removido!"), true);
			return false;
		}

		if (existing instanceof ChestSpawnData || state.is(Blocks.CHEST)) {
			if (!held.is(Items.CHEST)) {
				player.sendSystemMessage(Component.literal("§cVocê precisa segurar a Ferramenta de Baú para quebrar este bloco!"), true);
				return false;
			}
			session.getPlacedMarkers().remove(pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			session.getPlacedBlocks().remove(pos);
			DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
			if (def != null && def.getDungeonStructure() != null) {
				def.getDungeonStructure().getChests().removeIf(c ->
						(c.getRelativePos() != null && (c.getRelativePos().equals(pos) || toEditorPos(c.getRelativePos()).equals(pos))));
			}
			session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
					DungeonUndoRedo.ActionType.REMOVE_MARKER, pos, state, Blocks.AIR.defaultBlockState(), existing, null));
			player.sendSystemMessage(Component.literal("§cBaú removido!"), true);
			return false;
		}

		// Não permite quebrar blocos normais na ferramenta
		return false;
	}

	private static void undoAction(ServerPlayer player, DungeonEditorSession session) {
		if (!session.getUndoRedo().canUndo()) {
			player.sendSystemMessage(Component.literal("§cNada para desfazer!"), true);
			return;
		}
		DungeonUndoRedo.ActionRecord action = session.getUndoRedo().popUndo();
		if (action == null) return;

		ServerLevel level = ((ServerLevel) player.level());
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);

		if (action.type() == DungeonUndoRedo.ActionType.PLACE_AREA && def != null) {
			AreaTriggerData area = (AreaTriggerData) action.newData();
			def.getDungeonStructure().getAreas().removeIf(a -> a.getName().equalsIgnoreCase(area.getName()));
			for (WaveData w : def.getDungeonStructure().getWaves()) {
				if (area.getName().equalsIgnoreCase(w.getActivationAreaName())) {
					w.setActivationAreaName("");
				}
			}
			if (action.previousData() instanceof AreaTriggerData prev) {
				def.getDungeonStructure().getAreas().add(prev);
				if (prev.getMode() == AreaTriggerData.Mode.START_WAVE) {
					int wIdx = prev.getWaveOrder() - 1;
					if (wIdx >= 0 && wIdx < def.getDungeonStructure().getWaves().size()) {
						def.getDungeonStructure().getWaves().get(wIdx).setActivationAreaName(prev.getName());
					}
				}
			}
			DungeonRepository.saveWorldDungeons(player.level().getServer());
			refreshAreaOverlays(level, session, def);
			player.sendSystemMessage(Component.literal("§eCriação de área desfeita!"), true);
			return;
		}

		if (action.type() == DungeonUndoRedo.ActionType.REMOVE_AREA && def != null) {
			AreaTriggerData area = (AreaTriggerData) action.previousData();
			if (area != null) {
				def.getDungeonStructure().getAreas().removeIf(a -> a.getName().equalsIgnoreCase(area.getName()));
				def.getDungeonStructure().getAreas().add(area);
				if (area.getMode() == AreaTriggerData.Mode.START_WAVE) {
					int wIdx = area.getWaveOrder() - 1;
					if (wIdx >= 0 && wIdx < def.getDungeonStructure().getWaves().size()) {
						def.getDungeonStructure().getWaves().get(wIdx).setActivationAreaName(area.getName());
					}
				}
				DungeonRepository.saveWorldDungeons(player.level().getServer());
				refreshAreaOverlays(level, session, def);
				player.sendSystemMessage(Component.literal("§eÁrea '" + area.getName() + "' restaurada!"), true);
				return;
			}
		}

		for (BlockPos p : action.positions()) {
			BlockState restore = action.previousBlockStates() != null && action.previousBlockStates().containsKey(p)
					? action.previousBlockStates().get(p)
					: action.previousState();
			level.setBlock(p, restore, 3);
			if (action.previousData() != null) {
				session.getPlacedMarkers().put(p, action.previousData());
				session.getPlacedBlocks().add(p);
			} else {
				session.getPlacedMarkers().remove(p);
				session.getPlacedBlocks().remove(p);
			}
		}

		if (action.newData() instanceof PortalData pd && def != null) {
			removePortalFromDef(def, session.getSelectedStructure(), pd.getType());
		}
		if (action.previousData() instanceof PortalData pd && def != null) {
			setPortalInDef(def, session.getSelectedStructure(), pd);
		}

		player.sendSystemMessage(Component.literal("§eAção desfeita!"), true);
	}

	private static void redoAction(ServerPlayer player, DungeonEditorSession session) {
		if (!session.getUndoRedo().canRedo()) {
			player.sendSystemMessage(Component.literal("§cNada para refazer!"), true);
			return;
		}
		DungeonUndoRedo.ActionRecord action = session.getUndoRedo().popRedo();
		if (action == null) return;

		ServerLevel level = ((ServerLevel) player.level());
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);

		if (action.type() == DungeonUndoRedo.ActionType.PLACE_AREA && def != null) {
			AreaTriggerData area = (AreaTriggerData) action.newData();
			if (area != null) {
				def.getDungeonStructure().getAreas().removeIf(a -> a.getName().equalsIgnoreCase(area.getName()));
				def.getDungeonStructure().getAreas().add(area);
				if (area.getMode() == AreaTriggerData.Mode.START_WAVE) {
					int wIdx = area.getWaveOrder() - 1;
					if (wIdx >= 0 && wIdx < def.getDungeonStructure().getWaves().size()) {
						def.getDungeonStructure().getWaves().get(wIdx).setActivationAreaName(area.getName());
					}
				}
				DungeonRepository.saveWorldDungeons(player.level().getServer());
				refreshAreaOverlays(level, session, def);
				player.sendSystemMessage(Component.literal("§bÁrea '" + area.getName() + "' refeita!"), true);
				return;
			}
		}

		if (action.type() == DungeonUndoRedo.ActionType.REMOVE_AREA && def != null) {
			AreaTriggerData area = (AreaTriggerData) action.previousData();
			if (area != null) {
				def.getDungeonStructure().getAreas().removeIf(a -> a.getName().equalsIgnoreCase(area.getName()));
				for (WaveData w : def.getDungeonStructure().getWaves()) {
					if (area.getName().equalsIgnoreCase(w.getActivationAreaName())) {
						w.setActivationAreaName("");
					}
				}
				DungeonRepository.saveWorldDungeons(player.level().getServer());
				refreshAreaOverlays(level, session, def);
				player.sendSystemMessage(Component.literal("§cÁrea '" + area.getName() + "' removida novamente!"), true);
				return;
			}
		}

		for (BlockPos p : action.positions()) {
			level.setBlock(p, action.newState(), 3);
			if (action.newData() != null) {
				session.getPlacedMarkers().put(p, action.newData());
				session.getPlacedBlocks().add(p);
			} else {
				session.getPlacedMarkers().remove(p);
				session.getPlacedBlocks().remove(p);
			}
		}

		if (action.newData() instanceof PortalData pd && def != null) {
			setPortalInDef(def, session.getSelectedStructure(), pd);
		}
		if (action.previousData() instanceof PortalData pd && def != null) {
			removePortalFromDef(def, session.getSelectedStructure(), pd.getType());
		}

		player.sendSystemMessage(Component.literal("§bAção refeita!"), true);
	}

	public static void showSaveSummary(ServerPlayer player, DungeonEditorSession session) {
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		player.sendSystemMessage(Component.literal("§6========================================"));
		player.sendSystemMessage(Component.literal("§e§lRESUMO DAS ALTERAÇÕES NA ESTRUTURA: §f" + def.getName()));

		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			player.sendSystemMessage(Component.literal("§a§lOndas Configuradas: " + def.getDungeonStructure().getWaves().size()));
			int currentWaveIdx = session.getEditingWaveIndex();
			for (int i = 0; i < def.getDungeonStructure().getWaves().size(); i++) {
				WaveData w = def.getDungeonStructure().getWaves().get(i);
				int mobCount = 0;
				int miniBossCount = 0;
				int bossCount = 0;

				if (i == currentWaveIdx && session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT) {
					for (Object obj : session.getPlacedMarkers().values()) {
						if (obj instanceof MobSpawnData m) {
							if (m.getCategory() == MobSpawnData.Category.BOSS) bossCount++;
							else if (m.getCategory() == MobSpawnData.Category.MINI_BOSS) miniBossCount++;
							else mobCount++;
						}
					}
				} else {
					for (MobSpawnData m : w.getMobs()) {
						if (m.getCategory() == MobSpawnData.Category.BOSS) bossCount++;
						else if (m.getCategory() == MobSpawnData.Category.MINI_BOSS) miniBossCount++;
						else mobCount++;
					}
				}
				player.sendSystemMessage(Component.literal(String.format("  §e- Onda %d (%s): §f%d mobs, %d mini-chefes, %d chefes",
						w.getOrder(), w.getName(), mobCount, miniBossCount, bossCount)));
			}

			PortalData in = def.getDungeonStructure().getEntrancePortal();
			PortalData out = def.getDungeonStructure().getExitPortal();
			player.sendSystemMessage(Component.literal("§b§lPortais:"));
			player.sendSystemMessage(Component.literal("  §f- Entrada: " + (in != null ? "§aConfigurado ✅" : "§cAusente ❌")));
			player.sendSystemMessage(Component.literal("  §f- Saída: " + (out != null ? "§aConfigurado ✅" : "§cAusente ❌")));

			if (currentWaveIdx == 0) {
				boolean spawnConfigured = session.getPlayerSpawnPos() != null || def.getDungeonStructure().getPlayerSpawnPos() != null;
				player.sendSystemMessage(Component.literal("§d§lSpawn do Jogador: " + (spawnConfigured ? "§aConfigurado ✅" : "§eNão configurado ⚠ (Necessário apenas para ativar a dungeon)")));
			}
		} else {
			PortalData in = def.getEntranceStructure().getEntrancePortal();
			player.sendSystemMessage(Component.literal("§b§lPortais:"));
			player.sendSystemMessage(Component.literal("  §f- Entrada: " + (in != null ? "§aConfigurado ✅" : "§cAusente ❌")));
		}

		player.sendSystemMessage(Component.literal("§6========================================"));

		MutableComponent question = Component.literal("§fVocê deseja salvar todas essas alterações? ");

		MutableComponent simBtn = Component.literal("§a§l[SIM]")
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand("/rpg dungeon editor_save confirm"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Salvar todas as alterações"))));

		MutableComponent naoBtn = Component.literal(" §c§l[NÃO]")
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand("/rpg dungeon editor_save cancel"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Cancelar edição e descartar alterações"))));

		player.sendSystemMessage(question.append(simBtn).append(naoBtn));
	}

	private static void removePortalFromDef(DungeonDefinition def, String selectedStructure, PortalData.Type type) {
		if ("ENTRADA".equalsIgnoreCase(selectedStructure)) {
			def.getEntranceStructure().setEntrancePortal(null);
		} else {
			if (type == PortalData.Type.ENTRANCE_WHITE) {
				def.getDungeonStructure().setEntrancePortal(null);
			} else {
				def.getDungeonStructure().setExitPortal(null);
			}
		}
	}

	private static void setPortalInDef(DungeonDefinition def, String selectedStructure, PortalData pd) {
		if ("ENTRADA".equalsIgnoreCase(selectedStructure)) {
			def.getEntranceStructure().setEntrancePortal(pd);
		} else {
			if (pd.getType() == PortalData.Type.ENTRANCE_WHITE) {
				def.getDungeonStructure().setEntrancePortal(pd);
			} else {
				def.getDungeonStructure().setExitPortal(pd);
			}
		}
	}

	public static boolean removePortalAt(ServerPlayer player, ServerLevel level, DungeonEditorSession session, BlockPos pos) {
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		PortalData targetPortal = null;
		Object marker = session.getPlacedMarkers().get(pos);
		if (marker instanceof PortalData pd) {
			targetPortal = pd;
		} else if (def != null) {
			if ("ENTRADA".equalsIgnoreCase(session.getSelectedStructure())) {
				PortalData in = def.getEntranceStructure().getEntrancePortal();
				if (in != null && in.contains(pos)) targetPortal = in;
			} else {
				PortalData in = def.getDungeonStructure().getEntrancePortal();
				if (in != null && in.contains(pos)) targetPortal = in;
				PortalData out = def.getDungeonStructure().getExitPortal();
				if (out != null && out.contains(pos)) targetPortal = out;
			}
		}

		if (targetPortal != null) {
			BlockPos min = targetPortal.getMinPos();
			BlockPos max = targetPortal.getMaxPos();
			List<BlockPos> positions = new ArrayList<>();
			BlockState portalState = level.getBlockState(pos);
			Map<BlockPos, BlockState> replaced = targetPortal.getReplacedStates();

			for (BlockPos b : BlockPos.betweenClosed(min, max)) {
				BlockPos immutable = b.immutable();
				positions.add(immutable);
				BlockState restore = replaced != null ? replaced.getOrDefault(immutable, Blocks.AIR.defaultBlockState()) : Blocks.AIR.defaultBlockState();
				level.setBlock(immutable, restore, 3);
				session.getPlacedBlocks().remove(immutable);
				session.getPlacedMarkers().remove(immutable);
			}
			if (def != null) {
				removePortalFromDef(def, session.getSelectedStructure(), targetPortal.getType());
			}
			session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
					DungeonUndoRedo.ActionType.REMOVE_MARKER, min, positions, portalState, Blocks.AIR.defaultBlockState(), targetPortal, null, replaced));
			player.sendSystemMessage(Component.literal("§cPortal cancelado e removido!"), true);
			return true;
		} else {
			BlockState portalState = level.getBlockState(pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			session.getPlacedBlocks().remove(pos);
			session.getPlacedMarkers().remove(pos);
			player.sendSystemMessage(Component.literal("§cBloco de portal removido!"), true);
			return true;
		}
	}

	public static boolean removeAreaAt(ServerPlayer player, ServerLevel level, DungeonEditorSession session, BlockPos pos) {
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return false;

		AreaTriggerData targetArea = null;
		for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
			if (a.contains(pos)) {
				targetArea = a;
				break;
			}
		}

		if (targetArea == null) {
			if (session.getAreaPoint1() != null && session.getAreaPoint1().equals(pos)) {
				session.setAreaPoint1(null);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				session.getActiveOverlayBlocks().remove(pos);
				player.sendSystemMessage(Component.literal("§ePrimeiro ponto de área cancelado!"), true);
				return true;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			session.getActiveOverlayBlocks().remove(pos);
			return true;
		}

		def.getDungeonStructure().getAreas().remove(targetArea);
		for (WaveData w : def.getDungeonStructure().getWaves()) {
			if (targetArea.getName().equalsIgnoreCase(w.getActivationAreaName())) {
				w.setActivationAreaName("");
			}
		}

		session.getUndoRedo().push(new DungeonUndoRedo.ActionRecord(
				DungeonUndoRedo.ActionType.REMOVE_AREA, targetArea.getMinPos(), List.of(targetArea.getMinPos(), targetArea.getMaxPos()),
				DungeonBlocks.AREA_OVERLAY.defaultBlockState(), Blocks.AIR.defaultBlockState(), targetArea, null));

		DungeonRepository.saveWorldDungeons(player.level().getServer());
		refreshAreaOverlays(level, session, def);
		player.sendSystemMessage(Component.literal("§cÁrea '" + targetArea.getName() + "' removida!"), true);
		return true;
	}

	public static void refreshAreaOverlays(ServerLevel level, DungeonEditorSession session, DungeonDefinition def) {
		int waveOrder = session.getEditingWaveIndex() + 1;
		refreshAreaOverlays(level, session, def, waveOrder);
	}

	public static void refreshAreaOverlays(ServerLevel level, DungeonEditorSession session, DungeonDefinition def, int waveOrder) {
		for (BlockPos pos : session.getActiveOverlayBlocks()) {
			var st = level.getBlockState(pos);
			if (st.getBlock() == DungeonBlocks.AREA_OVERLAY || st.getBlock() == DungeonBlocks.PORTAL_OVERLAY) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		session.getActiveOverlayBlocks().clear();

		if (def == null) return;
		for (AreaTriggerData area : def.getDungeonStructure().getAreas()) {
			if (area.getWaveOrder() == waveOrder) {
				BlockPos min = toEditorPos(area.getMinPos());
				BlockPos max = toEditorPos(area.getMaxPos());
				for (BlockPos b : BlockPos.betweenClosed(min, max)) {
					BlockPos imm = b.immutable();
					BlockState st = level.getBlockState(imm);
					if (st.isAir() || st.canBeReplaced()) {
						level.setBlock(imm, DungeonBlocks.AREA_OVERLAY.defaultBlockState(), 2);
						session.getActiveOverlayBlocks().add(imm);
					}
				}
			}
		}
	}

	public static void spawnPointParticles(ServerLevel level, BlockPos pos) {
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 20, 0.4, 0.4, 0.4, 0.05);
	}

	public static void spawnAreaBoxParticles(ServerLevel level, BlockPos min, BlockPos max) {
		double minX = min.getX();
		double minY = min.getY();
		double minZ = min.getZ();
		double maxX = max.getX() + 1.0;
		double maxY = max.getY() + 1.0;
		double maxZ = max.getZ() + 1.0;

		double step = 0.5;
		for (double x = minX; x <= maxX; x += step) {
			level.sendParticles(ParticleTypes.END_ROD, x, minY, minZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, x, maxY, minZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, x, minY, maxZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, x, maxY, maxZ, 1, 0, 0, 0, 0);
		}
		for (double y = minY; y <= maxY; y += step) {
			level.sendParticles(ParticleTypes.END_ROD, minX, y, minZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, maxX, y, minZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, minX, y, maxZ, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, maxX, y, maxZ, 1, 0, 0, 0, 0);
		}
		for (double z = minZ; z <= maxZ; z += step) {
			level.sendParticles(ParticleTypes.END_ROD, minX, minY, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, maxX, minY, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, minX, maxY, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, maxX, maxY, z, 1, 0, 0, 0, 0);
		}
	}
}
