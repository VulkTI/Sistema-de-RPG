package br.com.rpgsystem.client.screen;

import br.com.rpgsystem.client.RpgClientState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class PlayerStatusScreen extends Screen {
	public PlayerStatusScreen() { super(Component.literal("Status do jogador")); }

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - 205, height - 36, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Árvore de habilidades"), button ->
				Minecraft.getInstance().gui.setScreen(new SkillTreeScreen()))
				.bounds(width / 2 + 5, height - 36, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		JsonObject data = RpgClientState.snapshot();
		int left = width / 2 - 150;
		int top = 36;
		RpgScreenText.draw(graphics, font, "Status do jogador", width / 2, top, 0xFFFFFFFF, true, true);
		RpgScreenText.draw(graphics, font, "Nível: " + data.get("playerLevel").getAsInt(), left, top + 24, 0xFFFFFFFF, false, true);
		drawExperienceBar(graphics, left, top + 42, 300,
				data.get("playerXpCurrent").getAsLong(), data.get("playerXpNext").getAsLong(), 0xFF5CCB96);
		RpgScreenText.draw(graphics, font,
				"XP do jogador: " + data.get("playerXpCurrent").getAsLong() + " / " + data.get("playerXpNext").getAsLong(),
				left, top + 50, 0xFFFFFFFF, false, true);
		RpgScreenText.draw(graphics, font, "Classe: " + data.get("selectedClassName").getAsString()
				+ "  |  Nível da classe: " + data.get("classLevel").getAsInt(), left, top + 70, 0xFFFFFFFF, false, true);
		drawExperienceBar(graphics, left, top + 88, 300,
				data.get("classXpCurrent").getAsLong(), data.get("classXpNext").getAsLong(), 0xFFBA77F5);
		RpgScreenText.draw(graphics, font,
				"XP da classe: " + data.get("classXpCurrent").getAsLong() + " / " + data.get("classXpNext").getAsLong(),
				left, top + 96, 0xFFFFFFFF, false, true);

		JsonArray options = data.getAsJsonArray("classes");
		if (options != null) {
			for (int i = 0; i < options.size(); i++) {
				JsonObject option = options.get(i).getAsJsonObject();
				if (!option.get("id").getAsString().equals(data.get("selectedClassId").getAsString())) continue;
				RpgScreenText.drawWrapped(graphics, font, option.get("description").getAsString(), left, top + 114, 300, 0xFFFFFFFF);
				JsonArray perks = option.getAsJsonArray("perks");
				int y = top + 150;
				RpgScreenText.draw(graphics, font, "Perks", left, y, 0xFFFFFFFF, false, true);
				for (int perkIndex = 0; perks != null && perkIndex < perks.size(); perkIndex++) {
					JsonObject perk = perks.get(perkIndex).getAsJsonObject();
					RpgScreenText.draw(graphics, font, "• " + perk.get("name").getAsString(), left + 8, y + 16, 0xFFFFFFFF, false, true);
					RpgScreenText.drawWrapped(graphics, font, perk.get("description").getAsString(), left + 16, y + 29, 284, 0xFFFFFFFF);
					y += 48;
				}
			}
		}
	}

	/** Barra desenhada com as primitivas vanilla da GUI, com a fração atual e os valores numéricos. */
	private void drawExperienceBar(GuiGraphicsExtractor graphics, int x, int y, int width,
			long current, long required, int fillColor) {
		graphics.fill(x, y, x + width, y + 6, 0xFF202020);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 5, 0xFF555555);
		float fraction = required <= 0L ? 0.0F : (float) current / (float) required;
		int filled = Mth.clamp((int) ((width - 2) * fraction), 0, width - 2);
		if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + 5, fillColor);
	}
}
