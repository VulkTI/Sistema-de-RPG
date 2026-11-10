package br.com.rpgsystem.client.screen;

import br.com.rpgsystem.client.RpgClientState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Painel compacto central de status do personagem conforme layout do design. */
public final class PlayerStatusScreen extends Screen {
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	private int classX;
	private int classY;
	private int classWidth;
	private int classHeight;

	private int headerY;
	private int sectionTitleY;

	private int cardsX;
	private int cardsY;
	private int cardWidth;
	private int cardHeight;
	private int cardGapX;
	private int cardGapY;

	private static final float DESIGN_SCALE = 1.0F;
	private int scaleOffsetX;
	private int scaleOffsetY;

	public PlayerStatusScreen() { super(Component.literal("Personagem")); }

	@Override
	protected void init() {
		layout();
		updateScaleOffsets();
		int btnWidth = 84;
		int btnHeight = 16;
		int btnY = cardsY + 3 * cardHeight + 2 * cardGapY + 5;
		addRenderableWidget(new RpgButton(
				sx(width / 2 - btnWidth / 2),
				sy(btnY),
				scaled(btnWidth),
				scaled(btnHeight),
				Component.literal("SAIR"),
				RpgUi.CYAN,
				this::onClose));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		JsonObject data = RpgClientState.snapshot();
		layout();
		updateScaleOffsets();

		RpgUi.dim(graphics, width, height);

		graphics.pose().pushMatrix();
		graphics.pose().translate(scaleOffsetX, scaleOffsetY);
		graphics.pose().scale(DESIGN_SCALE, DESIGN_SCALE);

		int designMouseX = inverseX(mouseX);
		int designMouseY = inverseY(mouseY);

		// Moldura central de dimensões compactas
		RpgUi.panel(graphics, panelX, panelY, panelWidth, panelHeight, 0xF4162234, 0xFF3D5677);
		RpgUi.accent(graphics, panelX, panelY, panelWidth, RpgUi.BLUE);

		int padX = 8;
		int gap = 8;
		int colWidth = (panelWidth - padX * 2 - gap) / 2;
		int playerCardX = panelX + padX;

		// Bloco do jogador (texto solto, sem moldura)
		drawPlayerCard(graphics, data, playerCardX, headerY, colWidth, classHeight);
		// Bloco da classe (card com moldura e ícone)
		drawClassCard(graphics, data, classX, headerY, classWidth, classHeight, designMouseX, designMouseY);

		RpgScreenText.draw(graphics, font, "HABILIDADES DE JOGADOR", width / 2, sectionTitleY, RpgUi.TEXT, true, true);
		drawUniversalCards(graphics, data, designMouseX, designMouseY);

		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void layout() {
		int padX = 8;
		int padY = 8;
		int gap = 8;

		panelWidth = Math.min(264, Math.max(200, width - 20));
		classWidth = (panelWidth - padX * 2 - gap) / 2;
		classHeight = 50; // Altura aumentada para acomodar o ícone da classe sem sobrepor o XP

		cardGapX = 8;
		cardGapY = 4;
		cardWidth = classWidth;
		cardHeight = 35;

		int totalCardsHeight = 3 * cardHeight + 2 * cardGapY;
		int btnHeight = 16;
		int spacingAboveBtn = 5;
		int spacingBelowBtn = 5;

		// Altura compacta justa: apenas o espaço necessário para cabeçalho, título, cards e botão
		int requiredHeight = padY + classHeight + 6 + 11 + totalCardsHeight + spacingAboveBtn + btnHeight + spacingBelowBtn;
		panelHeight = Math.min(requiredHeight, Math.max(180, height - 16));

		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		headerY = panelY + padY;
		classX = panelX + padX + classWidth + gap;
		classY = headerY;

		sectionTitleY = headerY + classHeight + 6;

		cardsX = panelX + padX;
		cardsY = sectionTitleY + 11;
	}

	private void drawPlayerCard(GuiGraphicsExtractor graphics, JsonObject data, int x, int y, int width, int height) {
		// Sem quadrado de fundo: texto totalmente solto conforme o desenho
		String playerName = Minecraft.getInstance().player == null ? "Jogador" : Minecraft.getInstance().player.getName().getString();
		String xp = RpgUi.number(longValue(data, "playerXpCurrent")) + "/" + RpgUi.number(longValue(data, "playerXpNext"));

		// Linha 1: Nick do jogador (centralizado)
		RpgScreenText.draw(graphics, font, playerName, x + width / 2, y + 5, RpgUi.TEXT, true, true);

		// Linha 2: Nível à esquerda e XP à direita com espaçamento vertical livre
		int line2Y = y + 23;
		RpgScreenText.draw(graphics, font, "Nível " + intValue(data, "playerLevel"), x + 2, line2Y, RpgUi.CYAN, false, true);
		RpgScreenText.draw(graphics, font, xp, x + width - font.width(xp) - 2, line2Y, RpgUi.TEXT_MUTED, false, true);

		// Linha 3: Barra de XP com espaçamento
		int barY = line2Y + font.lineHeight + 3;
		RpgUi.bar(graphics, x + 2, barY, width - 4, 6, longValue(data, "playerXpCurrent"), longValue(data, "playerXpNext"), RpgUi.BLUE);
	}

	private void drawClassCard(GuiGraphicsExtractor graphics, JsonObject data, int x, int y, int width, int height, int mouseX, int mouseY) {
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		int points = intValue(data, "skillPoints");
		boolean learnable = boolValue(data, "classHasLearnableSkill");
		int border = statusBorder(points, learnable, hovered);

		RpgUi.panel(graphics, x, y, width, height, hovered ? RpgUi.PANEL_HOVER : RpgUi.PANEL_RAISED, border);
		RpgUi.accent(graphics, x, y, width, RpgUi.PURPLE);

		// Caixa quadrada do ícone no topo à direita
		int classIconBox = 18;
		int iconX = x + width - classIconBox - 4;
		int iconY = y + 3;
		RpgUi.panel(graphics, iconX, iconY, classIconBox, classIconBox, 0xFF172236, RpgUi.BORDER);
		ItemStack icon = new ItemStack(classIcon(stringValue(data, "selectedClassId", "")));
		graphics.item(icon, iconX + 1, iconY + 1);

		String name = stringValue(data, "selectedClassName", "Sem classe");
		String xp = RpgUi.number(longValue(data, "classXpCurrent")) + "/" + RpgUi.number(longValue(data, "classXpNext"));

		// Linha 1: Nome da classe
		RpgScreenText.draw(graphics, font, name, x + 5, y + 5, RpgUi.PURPLE, false, true);

		// Linha 2: Nível e XP posicionados abaixo do ícone da classe para não sobrepor
		int line2Y = y + 23;
		RpgScreenText.draw(graphics, font, "Nível " + intValue(data, "classLevel"), x + 5, line2Y, RpgUi.TEXT, false, true);
		RpgScreenText.draw(graphics, font, xp, x + width - font.width(xp) - 5, line2Y, RpgUi.TEXT_MUTED, false, true);

		// Linha 3: Barra de XP com espaçamento
		int barY = line2Y + font.lineHeight + 3;
		RpgUi.bar(graphics, x + 5, barY, width - 10, 6, longValue(data, "classXpCurrent"), longValue(data, "classXpNext"), RpgUi.PURPLE);

		if (hovered) {
			RpgUi.tooltip(graphics, font, mouseX, mouseY, name,
					append("Como ganhar XP: " + classXpGuide(stringValue(data, "selectedClassId", "")), statusLines(points, learnable)));
		}
	}

	private void drawUniversalCards(GuiGraphicsExtractor graphics, JsonObject data, int mouseX, int mouseY) {
		JsonArray skills = data.getAsJsonArray("universalSkills");
		for (int i = 0; skills != null && i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			int col = i % 2;
			int row = i / 2;
			int x = cardsX + col * (cardWidth + cardGapX);
			int y = cardsY + row * (cardHeight + cardGapY);
			boolean hovered = inside(mouseX, mouseY, x, y, cardWidth, cardHeight);
			int color = universalColor(i);
			int points = intValue(skill, "skillPoints");
			boolean learnable = boolValue(skill, "hasLearnableSkill");

			RpgUi.panel(graphics, x, y, cardWidth, cardHeight, hovered ? RpgUi.PANEL_HOVER : RpgUi.PANEL_MUTED,
					statusBorder(points, learnable, hovered));
			RpgUi.accent(graphics, x, y, cardWidth, color);

			ItemStack icon = new ItemStack(universalIcon(stringValue(skill, "id", "")));
			String name = stringValue(skill, "name", "Skill universal");

			// Caixa quadrada do ícone
			int iconBox = 20;
			int iconY = y + (cardHeight - iconBox) / 2;
			RpgUi.panel(graphics, x + 4, iconY, iconBox, iconBox, 0xFF172236, RpgUi.BORDER);
			graphics.item(icon, x + 6, iconY + 2);

			int textX = x + 28;
			int rightBound = x + cardWidth - 5;
			String xp = RpgUi.number(longValue(skill, "xpCurrent")) + "/" + RpgUi.number(longValue(skill, "xpNext"));

			// Linha 1: Nome da habilidade
			RpgScreenText.draw(graphics, font, name, textX, y + 3, RpgUi.TEXT, false, true);

			// Linha 2: Nível à esquerda e XP à direita com espaçamento
			int line2Y = y + 3 + font.lineHeight + 3;
			RpgScreenText.draw(graphics, font, "Nível " + intValue(skill, "level"), textX, line2Y, color, false, true);
			RpgScreenText.draw(graphics, font, xp, rightBound - font.width(xp), line2Y, RpgUi.TEXT_MUTED, false, true);

			// Linha 3: Barra de XP com espaçamento
			int barY = line2Y + font.lineHeight + 3;
			RpgUi.bar(graphics, textX, barY, rightBound - textX, 5, longValue(skill, "xpCurrent"), longValue(skill, "xpNext"), color);

			if (hovered) {
				RpgUi.tooltip(graphics, font, mouseX, mouseY, name,
						append("Buffs atuais: " + stringValue(skill, "bonusText", "Nenhum bônus informado."), statusLines(points, learnable)));
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 1) return false;

		int clickX = inverseX((int) event.x());
		int clickY = inverseY((int) event.y());
		JsonObject data = RpgClientState.snapshot();

		if (inside(clickX, clickY, classX, classY, classWidth, classHeight)) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			Minecraft.getInstance().gui.setScreen(new SkillTreeScreen());
			return true;
		}

		JsonArray skills = data.getAsJsonArray("universalSkills");
		for (int i = 0; skills != null && i < skills.size(); i++) {
			int x = cardsX + (i % 2) * (cardWidth + cardGapX);
			int y = cardsY + (i / 2) * (cardHeight + cardGapY);
			if (inside(clickX, clickY, x, y, cardWidth, cardHeight)) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				Minecraft.getInstance().gui.setScreen(new SkillTreeScreen(stringValue(skills.get(i).getAsJsonObject(), "id", "")));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 80) { // Tecla P
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void updateScaleOffsets() {
		scaleOffsetX = Math.round(width * (1.0F - DESIGN_SCALE) / 2.0F);
		scaleOffsetY = Math.round(height * (1.0F - DESIGN_SCALE) / 2.0F);
	}

	private int sx(int value) { return Math.round(scaleOffsetX + value * DESIGN_SCALE); }
	private int sy(int value) { return Math.round(scaleOffsetY + value * DESIGN_SCALE); }
	private int scaled(int value) { return Math.max(1, Math.round(value * DESIGN_SCALE)); }
	private int inverseX(int value) { return Math.round((value - scaleOffsetX) / DESIGN_SCALE); }
	private int inverseY(int value) { return Math.round((value - scaleOffsetY) / DESIGN_SCALE); }

	private static int statusBorder(int points, boolean learnable, boolean hovered) {
		if (learnable) return RpgUi.RED;
		if (points > 0) return RpgUi.GOLD;
		return hovered ? RpgUi.BLUE : RpgUi.BORDER_SOFT;
	}

	private static String[] statusLines(int points, boolean learnable) {
		if (points > 0 && learnable) return new String[] {
			"Pontos não atribuídos: " + points,
			"Há habilidades prontas para aprender agora."
		};
		if (points > 0) return new String[] { "Pontos não atribuídos: " + points };
		if (learnable) return new String[] { "Há habilidades prontas para aprender agora." };
		return new String[0];
	}

	private static String[] append(String first, String[] rest) {
		String[] result = new String[rest.length + 1];
		result[0] = first;
		System.arraycopy(rest, 0, result, 1, rest.length);
		return result;
	}

	private static Item classIcon(String id) {
		return id.endsWith("minerador") ? Items.IRON_PICKAXE : id.endsWith("ferreiro") ? Items.ANVIL : Items.PLAYER_HEAD;
	}

	private static Item universalIcon(String id) {
		return switch (id) {
			case "combate" -> Items.DIAMOND_SWORD;
			case "mineracao" -> Items.IRON_PICKAXE;
			case "agricultura" -> Items.WHEAT;
			case "conhecimento" -> Items.ENCHANTING_TABLE;
			case "exploracao" -> Items.COMPASS;
			case "comercio" -> Items.EMERALD;
			default -> Items.NETHER_STAR;
		};
	}

	private static int universalColor(int index) {
		return switch (index % 6) {
			case 0 -> RpgUi.RED;
			case 1 -> RpgUi.CYAN;
			case 2 -> RpgUi.GREEN;
			case 3 -> RpgUi.BLUE;
			case 4 -> RpgUi.PURPLE;
			default -> 0xFFE486B5;
		};
	}

	private static String classXpGuide(String id) {
		return id.endsWith("minerador") ? "minere pedra comum e minérios." : id.endsWith("ferreiro") ? "colete barras fundidas e fabrique itens metálicos." : "realize as atividades da classe.";
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private static String stringValue(JsonObject value, String key, String fallback) { return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback; }
	private static int intValue(JsonObject value, String key) { return value != null && value.has(key) ? value.get(key).getAsInt() : 0; }
	private static long longValue(JsonObject value, String key) { return value != null && value.has(key) ? value.get(key).getAsLong() : 0L; }
	private static boolean boolValue(JsonObject value, String key) { return value != null && value.has(key) && value.get(key).getAsBoolean(); }
}
