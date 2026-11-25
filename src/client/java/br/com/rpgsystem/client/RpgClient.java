package br.com.rpgsystem.client;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.client.screen.PlayerStatusScreen;
import br.com.rpgsystem.dungeon.client.DungeonClientEditorState;
import br.com.rpgsystem.dungeon.client.hud.DungeonClientAimState;
import br.com.rpgsystem.dungeon.client.hud.DungeonPortalHudOverlay;
import br.com.rpgsystem.dungeon.client.screen.*;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import br.com.rpgsystem.network.RpgPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RpgClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(RpgMod.id("main"));
	private static final KeyMapping OPEN_STATUS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.rpgsystem.open_status", InputConstants.Type.KEYBOARD, InputConstants.KEY_P, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(RpgPayloads.ClearChat.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().gui != null && context.client().gui.hud != null && context.client().gui.hud.getChat() != null) {
						context.client().gui.hud.getChat().clearMessages(false);
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(RpgPayloads.Snapshot.TYPE, (payload, context) ->
				context.client().execute(() -> {
					RpgClientState.update(payload.json());
					if (context.client().gui.screen() instanceof br.com.rpgsystem.client.screen.SkillTreeScreen) {
						context.client().gui.setScreen(new br.com.rpgsystem.client.screen.SkillTreeScreen());
					}
					if (RpgClientState.snapshot().get("openMenu").getAsBoolean()) {
						context.client().gui.setScreen(new ClassSelectionScreen());
					}
				}));

		// Receivers de Telas e Dados do Sistema de Dungeon
		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.OpenSetup.TYPE, (payload, context) ->
				context.client().execute(() -> {
					try {
						var obj = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
						String name = obj.has("dungeonName") ? obj.get("dungeonName").getAsString() : (obj.has("name") ? obj.get("name").getAsString() : "");
						int min = obj.has("minLevel") ? obj.get("minLevel").getAsInt() : 1;
						int max = obj.has("maxLevel") ? obj.get("maxLevel").getAsInt() : 1;
						DungeonClientEditorState.enterSession(name, min, max);
					} catch (Exception ignored) {
						DungeonClientEditorState.enterSession();
					}
					context.client().gui.setScreen(new DungeonSetupScreen(payload.json()));
				}));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.OpenStructureConfig.TYPE, (payload, context) ->
				context.client().execute(() -> {
					try {
						var obj = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
						String name = obj.has("dungeonName") ? obj.get("dungeonName").getAsString() : "";
						int min = obj.has("minLevel") ? obj.get("minLevel").getAsInt() : DungeonClientEditorState.getMinLevel();
						int max = obj.has("maxLevel") ? obj.get("maxLevel").getAsInt() : DungeonClientEditorState.getMaxLevel();
						DungeonClientEditorState.enterSession(name, min, max);
					} catch (Exception ignored) {
						DungeonClientEditorState.enterSession();
					}
					context.client().gui.setScreen(new StructureConfigScreen(payload.json()));
				}));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.OpenMobTool.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new MobSpawnerToolScreen(payload.json()))));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.OpenChestTool.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new ChestToolScreen(payload.json()))));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.OpenAreaTool.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new AreaTriggerToolScreen(payload.json()))));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.PromptOfficialDuplicate.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new OfficialDungeonPromptScreen(payload.officialName()))));

		ClientPlayNetworking.registerGlobalReceiver(DungeonPayloads.PortalAimSync.TYPE, (payload, context) ->
				context.client().execute(() -> DungeonClientAimState.update(payload.dungeonName(), payload.dungeonLevel(), payload.timeLeftSeconds(), payload.completed())));

		// HUD Reticle Overlay para visualização de informações do portal
		HudElementRegistry.addLast(RpgMod.id("dungeon_portal_hud"), new DungeonPortalHudOverlay());

		// Interceptação de Shift + Scroll na hotbar durante modo de ferramentas
		ClientHotbarScrollEvents.ALLOW.register((inventory, currentSlot, targetSlot, horizontal, vertical) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.options.keyShift.isDown()) {
				ItemStack currentStack = inventory.getItem(currentSlot);
				if (currentStack.is(Items.SEA_LANTERN) || currentStack.is(Items.CHEST)
						|| currentStack.is(Items.RECOVERY_COMPASS) || currentStack.is(Items.END_PORTAL_FRAME)) {
					boolean forward = vertical > 0 || targetSlot > currentSlot;
					ClientPlayNetworking.send(new DungeonPayloads.CycleToolScroll(forward));
					return false;
				}
			}
			return true;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_STATUS.consumeClick()) {
				// Durante sessão de edição/criação de uma estrutura, a tecla P deve abrir DungeonSetupScreen
				if (DungeonClientEditorState.isInSession()) {
					ClientPlayNetworking.send(new DungeonPayloads.RequestReopenSetup());
					return;
				}

				if (client.player != null && RpgClientState.hasSnapshot()) {
					var snapshot = RpgClientState.snapshot();
					String selectedClass = snapshot.has("selectedClassId") && !snapshot.get("selectedClassId").isJsonNull()
							? snapshot.get("selectedClassId").getAsString() : "";
					if (selectedClass.isBlank()) {
						client.gui.setScreen(new ClassSelectionScreen());
					} else {
						client.gui.setScreen(new PlayerStatusScreen());
					}
				}
			}
		});

	}
}
