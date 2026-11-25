package br.com.rpgsystem.dungeon.network;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.dungeon.data.*;
import br.com.rpgsystem.dungeon.editor.DungeonEditorSession;
import br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager;
import java.util.Map;
import br.com.rpgsystem.dungeon.editor.DungeonEditorToolManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DungeonNetworking {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private DungeonNetworking() {}

	public static void register() {
		// Clientbound
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.OpenSetup.TYPE, DungeonPayloads.OpenSetup.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.OpenStructureConfig.TYPE, DungeonPayloads.OpenStructureConfig.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.OpenMobTool.TYPE, DungeonPayloads.OpenMobTool.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.OpenChestTool.TYPE, DungeonPayloads.OpenChestTool.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.OpenAreaTool.TYPE, DungeonPayloads.OpenAreaTool.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.PortalAimSync.TYPE, DungeonPayloads.PortalAimSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DungeonPayloads.PromptOfficialDuplicate.TYPE, DungeonPayloads.PromptOfficialDuplicate.CODEC);

		// Serverbound
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.StartBuild.TYPE, DungeonPayloads.StartBuild.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SaveDungeonSetup.TYPE, DungeonPayloads.SaveDungeonSetup.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.CancelSetup.TYPE, DungeonPayloads.CancelSetup.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SaveStructureConfig.TYPE, DungeonPayloads.SaveStructureConfig.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.CancelStructureConfig.TYPE, DungeonPayloads.CancelStructureConfig.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.UpdateTempStructureConfig.TYPE, DungeonPayloads.UpdateTempStructureConfig.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SetEditorTime.TYPE, DungeonPayloads.SetEditorTime.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SetEditorCycle.TYPE, DungeonPayloads.SetEditorCycle.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.EditWaveAction.TYPE, DungeonPayloads.EditWaveAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.EditPortalAction.TYPE, DungeonPayloads.EditPortalAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SaveMobTool.TYPE, DungeonPayloads.SaveMobTool.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SaveChestTool.TYPE, DungeonPayloads.SaveChestTool.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.SaveAreaTool.TYPE, DungeonPayloads.SaveAreaTool.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.DeleteAreaTool.TYPE, DungeonPayloads.DeleteAreaTool.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.CancelAreaTool.TYPE, DungeonPayloads.CancelAreaTool.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.CycleToolScroll.TYPE, DungeonPayloads.CycleToolScroll.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.ConfirmOfficialDuplicate.TYPE, DungeonPayloads.ConfirmOfficialDuplicate.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DungeonPayloads.RequestReopenSetup.TYPE, DungeonPayloads.RequestReopenSetup.CODEC);

		// Receivers
		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.StartBuild.TYPE, (payload, context) ->
				context.server().execute(() -> handleStartBuild(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SaveDungeonSetup.TYPE, (payload, context) ->
				context.server().execute(() -> handleSaveDungeonSetup(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.CancelSetup.TYPE, (payload, context) ->
				context.server().execute(() -> DungeonEditorSessionManager.cancelSession(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SetEditorTime.TYPE, (payload, context) ->
				context.server().execute(() -> handleSetEditorTime(context.player(), payload.ticks())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SetEditorCycle.TYPE, (payload, context) ->
				context.server().execute(() -> handleSetEditorCycle(context.player(), payload.daylightCycle())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SaveStructureConfig.TYPE, (payload, context) ->
				context.server().execute(() -> handleSaveStructureConfig(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.CancelStructureConfig.TYPE, (payload, context) ->
				context.server().execute(() -> handleCancelStructureConfig(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.UpdateTempStructureConfig.TYPE, (payload, context) ->
				context.server().execute(() -> handleUpdateTempStructureConfig(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.EditWaveAction.TYPE, (payload, context) ->
				context.server().execute(() -> handleEditWaveAction(context.player(), payload.waveIndex())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.EditPortalAction.TYPE, (payload, context) ->
				context.server().execute(() -> handleEditPortalAction(context.player(), payload.portalType())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SaveMobTool.TYPE, (payload, context) ->
				context.server().execute(() -> handleSaveMobTool(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SaveChestTool.TYPE, (payload, context) ->
				context.server().execute(() -> handleSaveChestTool(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.SaveAreaTool.TYPE, (payload, context) ->
				context.server().execute(() -> handleSaveAreaTool(context.player(), payload.json())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.DeleteAreaTool.TYPE, (payload, context) ->
				context.server().execute(() -> handleDeleteAreaTool(context.player(), payload.areaName())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.CancelAreaTool.TYPE, (payload, context) ->
				context.server().execute(() -> handleCancelAreaTool(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.CycleToolScroll.TYPE, (payload, context) ->
				context.server().execute(() -> handleCycleToolScroll(context.player(), payload.forward())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.ConfirmOfficialDuplicate.TYPE, (payload, context) ->
				context.server().execute(() -> handleConfirmOfficialDuplicate(context.player(), payload.officialName())));

		ServerPlayNetworking.registerGlobalReceiver(DungeonPayloads.RequestReopenSetup.TYPE, (payload, context) ->
				context.server().execute(() -> handleRequestReopenSetup(context.player())));
	}

	public static void openSetupScreen(ServerPlayer player, DungeonDefinition def) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		JsonObject obj = new JsonObject();
		obj.addProperty("dungeonName", def.getName());
		obj.addProperty("minLevel", def.getMinLevel());
		obj.addProperty("maxLevel", def.getMaxLevel());
		obj.addProperty("weight", def.getWeight());
		obj.addProperty("isOfficial", def.isOfficial());

		String entSchem = session != null && !session.getEntranceSchematicPath().isBlank()
				? session.getEntranceSchematicPath()
				: def.getEntranceStructure() != null ? def.getEntranceStructure().getSchematicPath() : "";
		String dungSchem = session != null && !session.getDungeonSchematicPath().isBlank()
				? session.getDungeonSchematicPath()
				: def.getDungeonStructure() != null ? def.getDungeonStructure().getSchematicPath() : "";

		obj.addProperty("entranceSchematic", entSchem);
		obj.addProperty("dungeonSchematic", dungSchem);

		ServerPlayNetworking.send(player, new DungeonPayloads.OpenSetup(GSON.toJson(obj)));
	}

	public static void openStructureConfigScreen(ServerPlayer player) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		// Se a sessão temporária já possui alterações salvas em memória, reenvia ela para o cliente
		if (session.getTempStructureConfig() != null) {
			ServerPlayNetworking.send(player, new DungeonPayloads.OpenStructureConfig(GSON.toJson(session.getTempStructureConfig())));
			return;
		}

		JsonObject root = new JsonObject();
		root.addProperty("selectedStructure", session.getSelectedStructure());
		root.addProperty("dungeonName", def.getName());
		root.addProperty("minLevel", def.getMinLevel());
		root.addProperty("maxLevel", def.getMaxLevel());
		root.addProperty("isOfficial", def.isOfficial());

		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			DungeonStructureData ds = def.getDungeonStructure();
			JsonObject dung = new JsonObject();
			dung.addProperty("structureName", ds.getStructureName());
			dung.addProperty("timeTicks", ds.getTimeTicks());
			dung.addProperty("daylightCycle", ds.isDaylightCycle());

			var wavesArr = new com.google.gson.JsonArray();
			for (WaveData w : ds.getWaves()) {
				JsonObject wObj = new JsonObject();
				wObj.addProperty("order", w.getOrder());
				wObj.addProperty("name", w.getName());
				wObj.addProperty("spawnMode", w.getSpawnMode().name());
				wObj.addProperty("activationAreaName", w.getActivationAreaName());
				wObj.addProperty("mobCount", w.getMobs().size());
				wavesArr.add(wObj);
			}
			dung.add("waves", wavesArr);
			root.add("dungeonStructure", dung);
		} else {
			EntranceStructureData es = def.getEntranceStructure();
			JsonObject ent = new JsonObject();
			ent.addProperty("structureName", es.getStructureName());
			ent.addProperty("subsolo", es.isSubsolo());
			ent.addProperty("ceu", es.isCeu());
			var dims = new com.google.gson.JsonArray();
			es.getDimensions().forEach(dims::add);
			ent.add("dimensions", dims);
			root.add("entranceStructure", ent);
		}

		ServerPlayNetworking.send(player, new DungeonPayloads.OpenStructureConfig(GSON.toJson(root)));
	}

	public static void openMobToolScreen(ServerPlayer player, MobSpawnData mob, MobSpawnData.Category category, BlockPos targetPos) {
		JsonObject json = new JsonObject();
		json.addProperty("category", category.name());
		json.addProperty("entityId", mob.getEntityId());
		json.addProperty("customName", mob.getCustomName());
		json.addProperty("particleId", mob.getParticleId());
		json.addProperty("particleIntensity", mob.getParticleIntensity());
		json.addProperty("soundId", mob.getSoundId());
		json.addProperty("dropChance", mob.getDropChance());
		json.addProperty("lootTableName", mob.getLootTableName());
		json.addProperty("scale", mob.getScale());
		json.addProperty("healthMultiplier", mob.getHealthMultiplier());
		json.addProperty("attackMultiplier", mob.getAttackMultiplier());
		json.addProperty("speedMultiplier", mob.getSpeedMultiplier());
		json.addProperty("constantParticles", mob.isConstantParticles());
		json.addProperty("dropEquipment", mob.isDropEquipment());

		JsonObject eqJson = new JsonObject();
		for (Map.Entry<String, MobSpawnData.EquippedItemData> eq : mob.getEquipment().entrySet()) {
			JsonObject itemObj = new JsonObject();
			itemObj.addProperty("itemId", eq.getValue().getItemId());
			itemObj.addProperty("durability", eq.getValue().getDurability());
			itemObj.addProperty("maxDamage", eq.getValue().getMaxDamage());
			itemObj.addProperty("dropChance", eq.getValue().getDropChance());
			JsonObject enchs = new JsonObject();
			for (Map.Entry<String, Integer> ench : eq.getValue().getEnchantments().entrySet()) {
				enchs.addProperty(ench.getKey(), ench.getValue());
			}
			itemObj.add("enchantments", enchs);
			eqJson.add(eq.getKey(), itemObj);
		}
		json.add("equipment", eqJson);

		var tables = new com.google.gson.JsonArray();
		var tablesData = new JsonObject();
		var lootData = br.com.rpgsystem.loot.RpgLootTableSavedData.get(player.level().getServer());
		for (String tName : lootData.allTableNames()) {
			tables.add(tName);
			lootData.getTable(tName).ifPresent(tbl -> {
				var itemsArr = new com.google.gson.JsonArray();
				for (var entry : tbl.itemsList()) {
					if (entry.item() != null && !entry.item().isEmpty()) {
						JsonObject itObj = new JsonObject();
						itObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.item().getItem()).toString());
						itObj.addProperty("count", entry.item().getCount());
						itObj.addProperty("name", entry.displayName());
						itObj.addProperty("weight", entry.weight());
						itemsArr.add(itObj);
					}
				}
				tablesData.add(tName, itemsArr);
			});
		}
		json.add("availableLootTables", tables);
		json.add("lootTablesData", tablesData);

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		DungeonDefinition def = session != null ? DungeonRepository.get(session.getDungeonName()).orElse(null) : null;

		// Áreas configuradas para baú
		var areasArr = new com.google.gson.JsonArray();
		if (def != null) {
			for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
				areasArr.add(a.getName());
			}
		}
		json.add("availableAreas", areasArr);

		// Ondas configuradas (ordem e nome)
		var wavesArr = new com.google.gson.JsonArray();
		if (def != null) {
			for (WaveData w : def.getDungeonStructure().getWaves()) {
				JsonObject wObj = new JsonObject();
				wObj.addProperty("order", w.getOrder());
				wObj.addProperty("name", w.getName());
				wavesArr.add(wObj);
			}
		}
		json.add("availableWaves", wavesArr);

		if (targetPos != null) {
			json.addProperty("hasPos", true);
			json.addProperty("posX", targetPos.getX());
			json.addProperty("posY", targetPos.getY());
			json.addProperty("posZ", targetPos.getZ());
		} else {
			json.addProperty("hasPos", false);
		}

		ServerPlayNetworking.send(player, new DungeonPayloads.OpenMobTool(GSON.toJson(json)));
	}

	public static void openChestToolScreen(ServerPlayer player, ChestSpawnData chest, BlockPos targetPos) {
		JsonObject json = new JsonObject();
		json.addProperty("chestName", chest.getChestName());
		json.addProperty("lootTableName", chest.getLootTableName());
		json.addProperty("condition", chest.getCondition().name());
		json.addProperty("waveOrder", chest.getWaveOrder());
		json.addProperty("targetAreaName", chest.getTargetAreaName());

		var tables = new com.google.gson.JsonArray();
		var tablesData = new JsonObject();
		var lootData = br.com.rpgsystem.loot.RpgLootTableSavedData.get(player.level().getServer());
		for (String tName : lootData.allTableNames()) {
			tables.add(tName);
			lootData.getTable(tName).ifPresent(tbl -> {
				var itemsArr = new com.google.gson.JsonArray();
				for (var entry : tbl.itemsList()) {
					if (entry.item() != null && !entry.item().isEmpty()) {
						JsonObject itObj = new JsonObject();
						itObj.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.item().getItem()).toString());
						itObj.addProperty("count", entry.item().getCount());
						itObj.addProperty("name", entry.displayName());
						itObj.addProperty("weight", entry.weight());
						itemsArr.add(itObj);
					}
				}
				tablesData.add(tName, itemsArr);
			});
		}
		json.add("availableLootTables", tables);
		json.add("lootTablesData", tablesData);

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		DungeonDefinition def = session != null ? DungeonRepository.get(session.getDungeonName()).orElse(null) : null;

		// Áreas configuradas para baú
		var areasArr = new com.google.gson.JsonArray();
		if (def != null) {
			for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
				areasArr.add(a.getName());
			}
		}
		json.add("availableAreas", areasArr);

		// Ondas configuradas (ordem e nome)
		var wavesArr = new com.google.gson.JsonArray();
		if (def != null) {
			for (WaveData w : def.getDungeonStructure().getWaves()) {
				JsonObject wObj = new JsonObject();
				wObj.addProperty("order", w.getOrder());
				wObj.addProperty("name", w.getName());
				wavesArr.add(wObj);
			}
		}
		json.add("availableWaves", wavesArr);

		if (targetPos != null) {
			json.addProperty("hasPos", true);
			json.addProperty("posX", targetPos.getX());
			json.addProperty("posY", targetPos.getY());
			json.addProperty("posZ", targetPos.getZ());
		} else {
			json.addProperty("hasPos", false);
		}

		ServerPlayNetworking.send(player, new DungeonPayloads.OpenChestTool(GSON.toJson(json)));
	}

	public static void openAreaToolScreen(ServerPlayer player, AreaTriggerData area) {
		JsonObject json = new JsonObject();
		json.addProperty("name", area.getName());
		json.addProperty("mode", area.getMode().name());
		json.addProperty("minX", area.getMinPos().getX());
		json.addProperty("minY", area.getMinPos().getY());
		json.addProperty("minZ", area.getMinPos().getZ());
		json.addProperty("maxX", area.getMaxPos().getX());
		json.addProperty("maxY", area.getMaxPos().getY());
		json.addProperty("maxZ", area.getMaxPos().getZ());
		json.addProperty("targetWaveName", area.getTargetWaveName());
		json.addProperty("targetChestName", area.getTargetChestName());
		json.addProperty("musicId", area.getMusicId());
		json.addProperty("replaceMusic", area.isReplaceMusic());

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		DungeonDefinition def = session != null ? DungeonRepository.get(session.getDungeonName()).orElse(null) : null;
		var areasArr = new com.google.gson.JsonArray();
		var chestsArr = new com.google.gson.JsonArray();
		if (def != null && session != null) {
			int currentWaveIdx = session.getEditingWaveIndex();
			int currentWaveOrder = currentWaveIdx + 1;
			String currentWaveName = "";
			WaveData currentWave = null;
			if (currentWaveIdx >= 0 && currentWaveIdx < def.getDungeonStructure().getWaves().size()) {
				currentWave = def.getDungeonStructure().getWaves().get(currentWaveIdx);
				currentWaveName = currentWave.getName();
			}
			json.addProperty("currentWaveOrder", currentWaveOrder);
			json.addProperty("currentWaveName", currentWaveName);

			for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
				// Inclui áreas desta onda
				boolean belongs = (a.getWaveOrder() == currentWaveOrder)
						|| (!currentWaveName.isBlank() && a.getTargetWaveName().equalsIgnoreCase(currentWaveName))
						|| (currentWave != null && currentWave.getActivationAreaName() != null && a.getName().equalsIgnoreCase(currentWave.getActivationAreaName()));
				if (belongs) {
					JsonObject aObj = new JsonObject();
					aObj.addProperty("name", a.getName());
					aObj.addProperty("mode", a.getMode().name());
					aObj.addProperty("minX", a.getMinPos().getX());
					aObj.addProperty("minY", a.getMinPos().getY());
					aObj.addProperty("minZ", a.getMinPos().getZ());
					aObj.addProperty("maxX", a.getMaxPos().getX());
					aObj.addProperty("maxY", a.getMaxPos().getY());
					aObj.addProperty("maxZ", a.getMaxPos().getZ());
					aObj.addProperty("targetWaveName", a.getTargetWaveName());
					aObj.addProperty("targetChestName", a.getTargetChestName());
					aObj.addProperty("musicId", a.getMusicId());
					aObj.addProperty("replaceMusic", a.isReplaceMusic());
					areasArr.add(aObj);
				}
			}

			for (ChestSpawnData c : def.getDungeonStructure().getChests()) {
				if (c.getChestName() != null && !c.getChestName().isBlank()) {
					chestsArr.add(c.getChestName());
				}
			}
		}
		json.add("areasList", areasArr);
		json.add("availableChests", chestsArr);

		ServerPlayNetworking.send(player, new DungeonPayloads.OpenAreaTool(GSON.toJson(json)));
	}

	private static void handleStartBuild(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		String dungeonName = json.get("dungeonName").getAsString().trim();
		int minLevel = json.get("minLevel").getAsInt();
		int maxLevel = json.get("maxLevel").getAsInt();
		int weight = json.get("weight").getAsInt();
		String selectedStructure = json.get("selectedStructure").getAsString();
		String schematicPath = json.has("schematicPath") ? json.get("schematicPath").getAsString() : "";

		DungeonDefinition def = DungeonRepository.get(dungeonName).orElse(null);
		if (def == null) {
			def = DungeonRepository.create(dungeonName, minLevel, maxLevel, weight, player.level().getServer());
		} else {
			def.setMinLevel(minLevel);
			def.setMaxLevel(maxLevel);
			def.setWeight(weight);
		}

		if (schematicPath != null && !schematicPath.isBlank()) {
			boolean isDungeon = "DUNGEON".equalsIgnoreCase(selectedStructure);
			String prevPath = isDungeon
					? def.getDungeonStructure().getSchematicPath()
					: def.getEntranceStructure().getSchematicPath();
			String otherPath = isDungeon
					? def.getEntranceStructure().getSchematicPath()
					: def.getDungeonStructure().getSchematicPath();
			schematicPath = DungeonRepository.importSchematicToDungeon(
					player.level().getServer(), def.getName(), schematicPath, prevPath, otherPath);
			if (isDungeon) {
				def.getDungeonStructure().setSchematicPath(schematicPath);
			} else {
				def.getEntranceStructure().setSchematicPath(schematicPath);
			}
		}
		DungeonRepository.saveDungeon(player.level().getServer(), def);

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session != null) {
			session.setDungeonName(def.getName());
			if (schematicPath != null && !schematicPath.isBlank()) {
				String fileName = java.nio.file.Path.of(schematicPath).getFileName().toString();
				if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
					session.setDungeonSchematicPath(schematicPath);
					session.setDungeonSchematicName(fileName);
				} else {
					session.setEntranceSchematicPath(schematicPath);
					session.setEntranceSchematicName(fileName);
				}
			}
		}

		DungeonEditorSessionManager.startBuildingStructure(player, selectedStructure, schematicPath);
	}

	private static void handleSaveDungeonSetup(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		String dungeonName = json.get("dungeonName").getAsString().trim();
		int minLevel = json.get("minLevel").getAsInt();
		int maxLevel = json.get("maxLevel").getAsInt();
		int weight = json.get("weight").getAsInt();

		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		String origName = session != null ? session.getDungeonName() : dungeonName;
		DungeonDefinition def = DungeonRepository.get(origName).orElse(null);
		if (def == null) {
			def = DungeonRepository.create(dungeonName, minLevel, maxLevel, weight, player.level().getServer());
		} else {
			if (!dungeonName.equalsIgnoreCase(def.getName())) {
				DungeonRepository.rename(def.getName(), dungeonName, player.level().getServer());
			}
			def.setMinLevel(minLevel);
			def.setMaxLevel(maxLevel);
			def.setWeight(weight);
		}
		if (json.has("entranceSchematicPath") && !json.get("entranceSchematicPath").getAsString().isBlank()) {
			String relEnt = DungeonRepository.importSchematicToDungeon(
					player.level().getServer(), def.getName(),
					json.get("entranceSchematicPath").getAsString(),
					def.getEntranceStructure().getSchematicPath(),
					def.getDungeonStructure().getSchematicPath());
			def.getEntranceStructure().setSchematicPath(relEnt);
		}
		if (json.has("dungeonSchematicPath") && !json.get("dungeonSchematicPath").getAsString().isBlank()) {
			String relDung = DungeonRepository.importSchematicToDungeon(
					player.level().getServer(), def.getName(),
					json.get("dungeonSchematicPath").getAsString(),
					def.getDungeonStructure().getSchematicPath(),
					def.getEntranceStructure().getSchematicPath());
			def.getDungeonStructure().setSchematicPath(relDung);
		}
		DungeonRepository.saveDungeon(player.level().getServer(), def);

		DungeonEditorSessionManager.cancelSession(player);
		player.sendSystemMessage(Component.literal("§aDungeon '§f" + def.getName() + "§a' salva com sucesso!"));
	}

	private static void handleSetEditorTime(ServerPlayer player, long ticks) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session != null) {
			session.setTimeTicks(ticks);
			DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
			if (def != null && def.getDungeonStructure() != null) {
				def.getDungeonStructure().setTimeTicks(ticks);
			}
		}
		ServerLevel level = ((ServerLevel) player.level());
		try {
			Optional<Holder<WorldClock>> holderOpt = level.dimensionTypeRegistration().value().defaultClock();
			if (holderOpt.isEmpty()) {
				var reg = player.level().getServer().registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
				var clockKey = ResourceKey.create(Registries.WORLD_CLOCK, RpgMod.id("dungeon_editor"));
				holderOpt = reg.get(clockKey).map(h -> h);
			}
			holderOpt.ifPresent(c -> player.level().getServer().clockManager().setTotalTicks(c, ticks));
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static void handleSetEditorCycle(ServerPlayer player, boolean daylightCycle) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session != null) {
			session.setDaylightCycle(daylightCycle);
			DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
			if (def != null && def.getDungeonStructure() != null) {
				def.getDungeonStructure().setDaylightCycle(daylightCycle);
			}
		}
		ServerLevel level = ((ServerLevel) player.level());
		try {
			Optional<Holder<WorldClock>> holderOpt = level.dimensionTypeRegistration().value().defaultClock();
			if (holderOpt.isEmpty()) {
				var reg = player.level().getServer().registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
				var clockKey = ResourceKey.create(Registries.WORLD_CLOCK, RpgMod.id("dungeon_editor"));
				holderOpt = reg.get(clockKey).map(h -> h);
			}
			holderOpt.ifPresent(c -> player.level().getServer().clockManager().setPaused(c, !daylightCycle));
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void applyWavesFromJson(DungeonStructureData ds, JsonArray wavesArr) {
		List<WaveData> existingWaves = ds.getWaves();
		List<WaveData> newWaves = new ArrayList<>();
		for (int i = 0; i < wavesArr.size(); i++) {
			JsonObject wObj = wavesArr.get(i).getAsJsonObject();
			String name = wObj.has("name") ? wObj.get("name").getAsString() : "Onda " + (i + 1);
			WaveData wave;
			if (i < existingWaves.size()) {
				wave = existingWaves.get(i);
				wave.setOrder(i + 1);
				wave.setName(name);
			} else {
				wave = new WaveData(i + 1, name);
			}
			if (wObj.has("spawnMode")) wave.setSpawnMode(WaveData.SpawnMode.valueOf(wObj.get("spawnMode").getAsString()));
			if (wObj.has("activationAreaName")) wave.setActivationAreaName(wObj.get("activationAreaName").getAsString());
			newWaves.add(wave);
		}
		ds.getWaves().clear();
		ds.getWaves().addAll(newWaves);
	}

	private static void handleUpdateTempStructureConfig(ServerPlayer player, String jsonStr) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		try {
			JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
			session.setTempStructureConfig(json);
			DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
			if (def != null && "DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
				JsonObject dungObj = json.has("dungeonStructure") && json.get("dungeonStructure").isJsonObject()
						? json.getAsJsonObject("dungeonStructure") : json;
				if (dungObj.has("timeTicks")) {
					long ticks = dungObj.get("timeTicks").getAsLong();
					session.setTimeTicks(ticks);
					def.getDungeonStructure().setTimeTicks(ticks);
				}
				if (dungObj.has("daylightCycle")) {
					boolean cycle = dungObj.get("daylightCycle").getAsBoolean();
					session.setDaylightCycle(cycle);
					def.getDungeonStructure().setDaylightCycle(cycle);
				}
				if (dungObj.has("structureName")) {
					def.getDungeonStructure().setStructureName(dungObj.get("structureName").getAsString());
				}
				com.google.gson.JsonArray wavesArr = null;
				if (dungObj.has("waves") && dungObj.get("waves").isJsonArray()) {
					wavesArr = dungObj.getAsJsonArray("waves");
				} else if (json.has("waves") && json.get("waves").isJsonArray()) {
					wavesArr = json.getAsJsonArray("waves");
				}
				if (wavesArr != null) {
					applyWavesFromJson(def.getDungeonStructure(), wavesArr);
				}
			}
		} catch (Exception ignored) {}
	}

	private static void handleSaveStructureConfig(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		// Limpa a configuração temporária em memória após o salvamento definitivo
		session.setTempStructureConfig(null);

		// Se for dungeon oficial, dispara popup para duplicar
		if (def.isOfficial()) {
			ServerPlayNetworking.send(player, new DungeonPayloads.PromptOfficialDuplicate(def.getName()));
			return;
		}

		if ("DUNGEON".equalsIgnoreCase(session.getSelectedStructure())) {
			DungeonStructureData ds = def.getDungeonStructure();
			if (json.has("structureName")) ds.setStructureName(json.get("structureName").getAsString());
			if (json.has("timeTicks")) ds.setTimeTicks(json.get("timeTicks").getAsLong());
			if (json.has("daylightCycle")) ds.setDaylightCycle(json.get("daylightCycle").getAsBoolean());

			// Atualiza ondas reorganizadas preservando mobs configurados
			if (json.has("waves") && json.get("waves").isJsonArray()) {
				applyWavesFromJson(ds, json.getAsJsonArray("waves"));
			}
		} else {
			EntranceStructureData es = def.getEntranceStructure();
			if (json.has("structureName")) es.setStructureName(json.get("structureName").getAsString());
			if (json.has("subsolo")) es.setSubsolo(json.get("subsolo").getAsBoolean());
			if (json.has("ceu")) es.setCeu(json.get("ceu").getAsBoolean());
			if (json.has("dimensions") && json.get("dimensions").isJsonArray()) {
				es.getDimensions().clear();
				for (var el : json.getAsJsonArray("dimensions")) {
					es.getDimensions().add(el.getAsString());
				}
			}
		}

		DungeonEditorSessionManager.saveStructure(player);
	}

	private static void handleCancelStructureConfig(ServerPlayer player) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		session.setTempStructureConfig(null);
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		ServerLevel level = (ServerLevel) player.level();
		try {
			var clockHolder = level.dimensionTypeRegistration().value().defaultClock();
			clockHolder.ifPresent(c -> {
				var clockMgr = player.level().getServer().clockManager();
				clockMgr.setPaused(c, true);
				clockMgr.setTotalTicks(c, 6000L);
			});
		} catch (Throwable ignored) {}

		session.setState(DungeonEditorSession.SessionState.SETUP_SCREEN);
		openSetupScreen(player, def);
	}

	private static void handleEditWaveAction(ServerPlayer player, int waveIndex) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def != null && session.getTempStructureConfig() != null) {
			JsonObject temp = session.getTempStructureConfig();
			com.google.gson.JsonArray wavesArr = null;
			if (temp.has("waves") && temp.get("waves").isJsonArray()) {
				wavesArr = temp.getAsJsonArray("waves");
			} else if (temp.has("dungeonStructure") && temp.getAsJsonObject("dungeonStructure").has("waves")) {
				wavesArr = temp.getAsJsonObject("dungeonStructure").getAsJsonArray("waves");
			}
			if (wavesArr != null) {
				applyWavesFromJson(def.getDungeonStructure(), wavesArr);
			}
		}
		session.setEditingWaveIndex(waveIndex);
		session.setState(DungeonEditorSession.SessionState.TOOL_WAVE_EDIT);
		DungeonEditorToolManager.giveWaveEditingTools(player, session);
		player.sendSystemMessage(Component.literal("§aModo de edição da Onda " + (waveIndex + 1) + " ativado!"), true);
	}

	private static void handleEditPortalAction(ServerPlayer player, String portalType) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		session.setState(DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT);
		if ("SAIDA".equalsIgnoreCase(portalType)) {
			session.setCurrentPortalType(PortalData.Type.EXIT_LIGHT_BLUE);
		} else {
			session.setCurrentPortalType(PortalData.Type.ENTRANCE_WHITE);
		}
		DungeonEditorToolManager.givePortalEditingTools(player, session);
		player.sendSystemMessage(Component.literal("§dModo de edição de portais ativado!"), true);
	}

	private static void handleSaveMobTool(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;

		MobSpawnData mobData;
		boolean hasPos = json.has("hasPos") && json.get("hasPos").getAsBoolean();
		if (hasPos) {
			BlockPos p = new BlockPos(json.get("posX").getAsInt(), json.get("posY").getAsInt(), json.get("posZ").getAsInt());
			Object existing = session.getPlacedMarkers().get(p);
			if (existing instanceof MobSpawnData m) mobData = m;
			else {
				mobData = new MobSpawnData(session.getCurrentMobCategory(), p);
				session.getPlacedMarkers().put(p, mobData);
			}
		} else {
			mobData = session.getDefaultMobData();
		}

		if (json.has("entityId")) mobData.setEntityId(json.get("entityId").getAsString());
		if (json.has("customName")) mobData.setCustomName(json.get("customName").getAsString());
		if (json.has("particleId")) mobData.setParticleId(json.get("particleId").getAsString());
		if (json.has("particleIntensity")) mobData.setParticleIntensity(json.get("particleIntensity").getAsFloat());
		if (json.has("soundId")) mobData.setSoundId(json.get("soundId").getAsString());
		if (json.has("dropChance")) mobData.setDropChance(json.get("dropChance").getAsDouble());
		if (json.has("lootTableName")) mobData.setLootTableName(json.get("lootTableName").getAsString());
		if (json.has("scale")) mobData.setScale(json.get("scale").getAsFloat());
		if (json.has("healthMultiplier")) mobData.setHealthMultiplier(json.get("healthMultiplier").getAsFloat());
		if (json.has("attackMultiplier")) mobData.setAttackMultiplier(json.get("attackMultiplier").getAsFloat());
		if (json.has("speedMultiplier")) mobData.setSpeedMultiplier(json.get("speedMultiplier").getAsFloat());
		if (json.has("constantParticles")) mobData.setConstantParticles(json.get("constantParticles").getAsBoolean());
		if (json.has("dropEquipment")) mobData.setDropEquipment(json.get("dropEquipment").getAsBoolean());
		if (json.has("equipment")) {
			mobData.getEquipment().clear();
			JsonObject eqObj = json.getAsJsonObject("equipment");
			for (String slotKey : eqObj.keySet()) {
				JsonObject itemObj = eqObj.getAsJsonObject(slotKey);
				MobSpawnData.EquippedItemData eqItem = new MobSpawnData.EquippedItemData();
				if (itemObj.has("itemId")) eqItem.setItemId(itemObj.get("itemId").getAsString());
				if (itemObj.has("durability")) eqItem.setDurability(itemObj.get("durability").getAsInt());
				if (itemObj.has("maxDamage")) eqItem.setMaxDamage(itemObj.get("maxDamage").getAsInt());
				if (itemObj.has("dropChance")) eqItem.setDropChance(itemObj.get("dropChance").getAsDouble());
				if (itemObj.has("enchantments")) {
					JsonObject enchObj = itemObj.getAsJsonObject("enchantments");
					for (String enchKey : enchObj.keySet()) {
						eqItem.getEnchantments().put(enchKey, enchObj.get(enchKey).getAsInt());
					}
				}
				mobData.getEquipment().put(slotKey, eqItem);
			}
		}

		player.sendSystemMessage(Component.literal("§aConfigurações de mob salvas!"), true);
	}

	private static void handleSaveChestTool(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;

		ChestSpawnData chestData;
		boolean hasPos = json.has("hasPos") && json.get("hasPos").getAsBoolean();
		if (hasPos) {
			BlockPos p = new BlockPos(json.get("posX").getAsInt(), json.get("posY").getAsInt(), json.get("posZ").getAsInt());
			Object existing = session.getPlacedMarkers().get(p);
			if (existing instanceof ChestSpawnData c) chestData = c;
			else {
				chestData = new ChestSpawnData("Baú", p);
				session.getPlacedMarkers().put(p, chestData);
			}
		} else {
			chestData = session.getDefaultChestData();
		}

		if (json.has("chestName")) chestData.setChestName(json.get("chestName").getAsString());
		if (json.has("lootTableName")) chestData.setLootTableName(json.get("lootTableName").getAsString());
		if (json.has("condition")) chestData.setCondition(ChestSpawnData.SpawnCondition.valueOf(json.get("condition").getAsString()));
		if (json.has("waveOrder")) chestData.setWaveOrder(json.get("waveOrder").getAsInt());
		if (json.has("targetAreaName")) chestData.setTargetAreaName(json.get("targetAreaName").getAsString());

		player.sendSystemMessage(Component.literal("§6Configurações de baú salvas!"), true);
	}

	private static void handleSaveAreaTool(ServerPlayer player, String jsonStr) {
		JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		String name = json.get("name").getAsString();
		AreaTriggerData.Mode mode = AreaTriggerData.Mode.valueOf(json.get("mode").getAsString());
		int x1 = json.get("minX").getAsInt();
		int y1 = json.get("minY").getAsInt();
		int z1 = json.get("minZ").getAsInt();
		int x2 = json.get("maxX").getAsInt();
		int y2 = json.get("maxY").getAsInt();
		int z2 = json.get("maxZ").getAsInt();
		BlockPos min = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
		BlockPos max = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

		AreaTriggerData area = new AreaTriggerData(name, mode, min, max);
		int waveIdx = session.getEditingWaveIndex();
		int waveOrder = waveIdx + 1;
		area.setWaveOrder(waveOrder);

		String currentWaveName = "";
		WaveData currentWave = null;
		if (waveIdx >= 0 && waveIdx < def.getDungeonStructure().getWaves().size()) {
			currentWave = def.getDungeonStructure().getWaves().get(waveIdx);
			currentWaveName = currentWave.getName();
		}

		if (mode == AreaTriggerData.Mode.START_WAVE) {
			area.setTargetWaveName(currentWaveName);
			if (currentWave != null) {
				currentWave.setActivationAreaName(name);
			}
		} else {
			if (json.has("targetWaveName")) area.setTargetWaveName(json.get("targetWaveName").getAsString());
		}

		if (json.has("targetChestName")) area.setTargetChestName(json.get("targetChestName").getAsString());
		if (json.has("musicId")) area.setMusicId(json.get("musicId").getAsString());
		if (json.has("replaceMusic")) area.setReplaceMusic(json.get("replaceMusic").getAsBoolean());

		AreaTriggerData existing = null;
		for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
			if (a.getName().equalsIgnoreCase(name)) {
				existing = a;
				break;
			}
		}

		if (existing != null) {
			def.getDungeonStructure().getAreas().remove(existing);
		}
		def.getDungeonStructure().getAreas().add(area);

		session.getUndoRedo().push(new br.com.rpgsystem.dungeon.editor.DungeonUndoRedo.ActionRecord(
				br.com.rpgsystem.dungeon.editor.DungeonUndoRedo.ActionType.PLACE_AREA, min, List.of(min, max),
				existing != null ? br.com.rpgsystem.dungeon.block.DungeonBlocks.AREA_OVERLAY.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
				br.com.rpgsystem.dungeon.block.DungeonBlocks.AREA_OVERLAY.defaultBlockState(),
				existing, area));

		DungeonRepository.saveWorldDungeons(player.level().getServer());
		DungeonEditorToolManager.spawnAreaBoxParticles((ServerLevel) player.level(), min, max);
		DungeonEditorToolManager.refreshAreaOverlays((ServerLevel) player.level(), session, def);
		player.sendSystemMessage(Component.literal("§bÁrea '" + name + "' salva com sucesso!"), true);
	}

	private static void handleDeleteAreaTool(ServerPlayer player, String areaName) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		if (def == null) return;

		AreaTriggerData toRemove = null;
		for (AreaTriggerData a : def.getDungeonStructure().getAreas()) {
			if (a.getName().equalsIgnoreCase(areaName)) {
				toRemove = a;
				break;
			}
		}

		if (toRemove != null) {
			def.getDungeonStructure().getAreas().remove(toRemove);
			for (WaveData w : def.getDungeonStructure().getWaves()) {
				if (toRemove.getName().equalsIgnoreCase(w.getActivationAreaName())) {
					w.setActivationAreaName("");
				}
			}
			session.getUndoRedo().push(new br.com.rpgsystem.dungeon.editor.DungeonUndoRedo.ActionRecord(
					br.com.rpgsystem.dungeon.editor.DungeonUndoRedo.ActionType.REMOVE_AREA, toRemove.getMinPos(), List.of(toRemove.getMinPos(), toRemove.getMaxPos()),
					br.com.rpgsystem.dungeon.block.DungeonBlocks.AREA_OVERLAY.defaultBlockState(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), toRemove, null));

			DungeonRepository.saveWorldDungeons(player.level().getServer());
			DungeonEditorToolManager.refreshAreaOverlays((ServerLevel) player.level(), session, def);
			player.sendSystemMessage(Component.literal("§cÁrea '" + areaName + "' deletada!"), true);
		}
	}

	private static void handleCancelAreaTool(ServerPlayer player) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
		DungeonEditorToolManager.refreshAreaOverlays((ServerLevel) player.level(), session, def);
	}

	private static void handleCycleToolScroll(ServerPlayer player, boolean forward) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;
		int slot = player.getInventory().getSelectedSlot();
		DungeonEditorToolManager.cycleToolMode(player, session, slot, forward);
	}

	private static void handleConfirmOfficialDuplicate(ServerPlayer player, String officialName) {
		DungeonDefinition copy = DungeonRepository.duplicateOfficialDungeon(officialName, player.level().getServer());
		if (copy != null) {
			DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
			if (session != null) {
				session.setDungeonName(copy.getName());
			}
			player.sendSystemMessage(Component.literal("§aDungeon oficial duplicada para o mundo como '" + copy.getName() + "'. A original foi desabilitada."));
			DungeonEditorSessionManager.saveStructure(player);
		}
	}

	private static void handleRequestReopenSetup(ServerPlayer player) {
		DungeonEditorSession session = DungeonEditorSessionManager.getSession(player.getUUID());
		if (session == null) return;

		// Se estiver construindo ou editando ondas/portais, a tecla P abre a tela de configuração da estrutura
		if (session.getState() == DungeonEditorSession.SessionState.FREE_BUILD
				|| session.getState() == DungeonEditorSession.SessionState.TOOL_WAVE_EDIT
				|| session.getState() == DungeonEditorSession.SessionState.TOOL_PORTAL_EDIT) {
			openStructureConfigScreen(player);
		} else {
			// Estado de setup inicial: abre a tela de setup da dungeon
			DungeonDefinition def = DungeonRepository.get(session.getDungeonName()).orElse(null);
			if (def != null) {
				openSetupScreen(player, def);
			}
		}
	}
}
