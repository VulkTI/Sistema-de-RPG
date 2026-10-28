package br.com.rpgsystem.client;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.network.RpgPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ClassSelectionScreen extends Screen {
	private int selectedIndex;
	private JsonArray classes;

	public ClassSelectionScreen() {
		super(Component.literal("Escolha sua classe"));
		classes = RpgClientState.snapshot().getAsJsonArray("classes");
	}

	@Override
	protected void init() {
		classes = RpgClientState.snapshot().getAsJsonArray("classes");
		if (classes == null || classes.isEmpty()) return;
		int center = width / 2;
		int buttonY = height - 54;
		addRenderableWidget(Button.builder(Component.literal("<"), button ->
				selectedIndex = Math.floorMod(selectedIndex - 1, classes.size()))
				.bounds(center - 112, buttonY, 36, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), button ->
				selectedIndex = (selectedIndex + 1) % classes.size())
				.bounds(center + 76, buttonY, 36, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Confirmar classe"), button -> confirm())
				.bounds(center - 70, buttonY, 142, 20).build());
	}

	private void confirm() {
		if (classes == null || classes.isEmpty()) return;
		String id = classes.get(selectedIndex).getAsJsonObject().get("id").getAsString();
		ClientPlayNetworking.send(new RpgPayloads.SelectClass(id));
		minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int panelWidth = Math.min(360, width - 24);
		int left = (width - panelWidth) / 2;
		int top = Math.max(24, height / 2 - 112);
		int bottom = Math.min(height - 30, height / 2 + 86);
		RpgScreenText.draw(graphics, font, "Selecione sua classe", width / 2, top + 14, 0xFFFFFFFF, true, true);

		if (classes == null || classes.isEmpty()) {
			RpgScreenText.draw(graphics, font, "Nenhuma classe foi registrada.", width / 2, top + 58, 0xFFFFFFFF, true, true);
			return;
		}
		selectedIndex = Math.floorMod(selectedIndex, classes.size());
		JsonObject choice = classes.get(selectedIndex).getAsJsonObject();
		RpgScreenText.draw(graphics, font, choice.get("name").getAsString(), width / 2, top + 38, 0xFFFFFFFF, true, true);
		RpgScreenText.drawWrapped(graphics, font, choice.get("description").getAsString(), left + 18, top + 58, panelWidth - 36, 0xFFFFFFFF);

		RpgScreenText.draw(graphics, font, "Perks", left + 18, top + 103, 0xFFFFFFFF, false, true);
		JsonArray perks = choice.getAsJsonArray("perks");
		int y = top + 118;
		for (int i = 0; perks != null && i < perks.size() && i < 3; i++) {
			JsonObject perk = perks.get(i).getAsJsonObject();
			RpgScreenText.draw(graphics, font, "• " + perk.get("name").getAsString(), left + 18, y, 0xFFFFFFFF, false, true);
			RpgScreenText.drawWrapped(graphics, font, perk.get("description").getAsString(), left + 28, y + 11, panelWidth - 46, 0xFFFFFFFF);
			y += 31;
		}
		RpgScreenText.draw(graphics, font, (selectedIndex + 1) + " / " + classes.size(), width / 2, height - 72, 0xFFFFFFFF, true, false);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
