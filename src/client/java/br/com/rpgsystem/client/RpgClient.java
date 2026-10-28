package br.com.rpgsystem.client;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.client.screen.PlayerStatusScreen;
import br.com.rpgsystem.network.RpgPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class RpgClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(RpgMod.id("main"));
	private static final KeyMapping OPEN_STATUS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.rpgsystem.open_status", InputConstants.Type.KEYBOARD, InputConstants.KEY_P, CATEGORY));

	@Override
	public void onInitializeClient() {
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

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_STATUS.consumeClick()) {
				if (client.player != null && RpgClientState.hasSnapshot()) {
					client.gui.setScreen(new PlayerStatusScreen());
				}
			}
		});

	}
}
