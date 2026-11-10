package br.com.rpgsystem.client;

import br.com.rpgsystem.client.screen.RpgButton;
import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.network.RpgPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tela de seleção de classe conforme layout com navegação lateral (< e >),
 * formato estreito, proporção contida e cabeçalho alinhado.
 */
public final class ClassSelectionScreen extends Screen {
	private static final int ICON_BOX_SIZE = 20;

	private int selectedIndex;
	private JsonArray classes;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentScroll;
	private int maxContentScroll;
	private boolean isDraggingScroll;
	private double scrollDragStartY;
	private int scrollStartPos;

	private static final float DESIGN_SCALE = 1.0F;
	private int scaleOffsetX;
	private int scaleOffsetY;

	public ClassSelectionScreen() {
		super(Component.literal("Escolha sua classe"));
		classes = RpgClientState.snapshot().getAsJsonArray("classes");
	}

	@Override
	protected void init() {
		classes = RpgClientState.snapshot().getAsJsonArray("classes");
		layout();
		updateScaleOffsets();

		int navBtnWidth = 18;
		int navBtnHeight = 22;
		int navBtnY = panelY + panelHeight / 2 - navBtnHeight / 2;

		addRenderableWidget(new RpgButton(sx(panelX - navBtnWidth - 5), sy(navBtnY), scaled(navBtnWidth), scaled(navBtnHeight),
				Component.literal("<"), RpgUi.BLUE, this::previous));
		addRenderableWidget(new RpgButton(sx(panelX + panelWidth + 5), sy(navBtnY), scaled(navBtnWidth), scaled(navBtnHeight),
				Component.literal(">"), RpgUi.BLUE, this::next));

		int confirmWidth = 114;
		int confirmHeight = 16;
		addRenderableWidget(new RpgButton(sx(width / 2 - confirmWidth / 2), sy(panelY + panelHeight + 6), scaled(confirmWidth), scaled(confirmHeight),
				Component.literal("ESCOLHER CLASSE"), RpgUi.CYAN, this::confirm));
	}

	private void layout() {
		// Painel mais estreito (200px) e com altura contida (240px)
		panelWidth = Math.min(200, Math.max(150, width - 60));
		panelHeight = Math.min(240, Math.max(160, height - 48));
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(6, (height - panelHeight - 24) / 2);
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

	private void previous() {
		if (classes != null && !classes.isEmpty()) {
			selectedIndex = Math.floorMod(selectedIndex - 1, classes.size());
			contentScroll = 0;
		}
	}

	private void next() {
		if (classes != null && !classes.isEmpty()) {
			selectedIndex = (selectedIndex + 1) % classes.size();
			contentScroll = 0;
		}
	}

	private void confirm() {
		if (classes == null || classes.isEmpty()) return;
		String id = classes.get(Math.floorMod(selectedIndex, classes.size())).getAsJsonObject().get("id").getAsString();
		ClientPlayNetworking.send(new RpgPayloads.SelectClass(id));
		minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		layout();
		updateScaleOffsets();
		RpgUi.dim(graphics, width, height);

		graphics.pose().pushMatrix();
		graphics.pose().translate(scaleOffsetX, scaleOffsetY);
		graphics.pose().scale(DESIGN_SCALE, DESIGN_SCALE);

		int designMouseX = inverseX(mouseX);
		int designMouseY = inverseY(mouseY);

		// Moldura principal da janela de seleção
		RpgUi.panel(graphics, panelX, panelY, panelWidth, panelHeight, 0xF4162234, 0xFF3D5677);
		RpgUi.accent(graphics, panelX, panelY, panelWidth, RpgUi.BLUE);

		if (classes == null || classes.isEmpty()) {
			RpgScreenText.draw(graphics, font, "Nenhuma classe foi registrada.", width / 2, panelY + panelHeight / 2 - 4, RpgUi.TEXT, true, true);
			graphics.pose().popMatrix();
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			return;
		}

		selectedIndex = Math.floorMod(selectedIndex, classes.size());
		JsonObject choice = classes.get(selectedIndex).getAsJsonObject();

		// Cabeçalho: Caixa quadrada do ícone + Nome da classe
		int headerPadding = 7;
		int iconBoxX = panelX + headerPadding;
		int iconBoxY = panelY + headerPadding + 2;

		// Caixa com contorno quadrado para o ícone com espaçamento interno justo (20x20 para item 16x16)
		RpgUi.panel(graphics, iconBoxX, iconBoxY, ICON_BOX_SIZE, ICON_BOX_SIZE, 0xFF141F2E, 0xFF4A6A92);
		graphics.item(new ItemStack(classIcon(stringValue(choice, "id", ""))),
				iconBoxX + (ICON_BOX_SIZE - 16) / 2, iconBoxY + (ICON_BOX_SIZE - 16) / 2);

		// Nome da classe ao lado do ícone
		int nameX = iconBoxX + ICON_BOX_SIZE + 6;
		int nameY = iconBoxY + (ICON_BOX_SIZE - font.lineHeight) / 2;
		RpgScreenText.draw(graphics, font, stringValue(choice, "name", "Classe"), nameX, nameY, RpgUi.TEXT, false, true);

		// Indicador sutil de página no canto superior direito
		String indexStr = (selectedIndex + 1) + "/" + classes.size();
		RpgScreenText.draw(graphics, font, indexStr, panelX + panelWidth - font.width(indexStr) - 7, iconBoxY + (ICON_BOX_SIZE - font.lineHeight) / 2, RpgUi.TEXT_DIM, false, true);

		// Caixa interna retangular (área de conteúdo com scroll à direita)
		int contentX = panelX + headerPadding;
		int contentY = iconBoxY + ICON_BOX_SIZE + 6;
		int contentWidth = panelWidth - headerPadding * 2;
		int contentHeight = panelHeight - (contentY - panelY) - headerPadding;

		RpgUi.panel(graphics, contentX, contentY, contentWidth, contentHeight, 0xF90F1723, 0xFF2B3F58);

		// Barra de scroll à direita dentro da caixa interna
		int scrollBarWidth = 10;
		int scrollTrackX = contentX + contentWidth - scrollBarWidth;
		int scrollTrackY = contentY + 1;
		int scrollTrackHeight = contentHeight - 2;

		// Trilha da barra de rolagem
		graphics.fill(scrollTrackX, scrollTrackY, contentX + contentWidth - 1, scrollTrackY + scrollTrackHeight, 0xFF182333);
		graphics.fill(scrollTrackX - 1, scrollTrackY, scrollTrackX, scrollTrackY + scrollTrackHeight, 0xFF2B3F58);

		// Conteúdo com Scissor
		int textX = contentX + 6;
		int textWidth = Math.max(20, contentWidth - scrollBarWidth - 12);

		graphics.enableScissor(contentX + 1, contentY + 1, scrollTrackX - 1, contentY + contentHeight - 1);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, -contentScroll);

		int curY = contentY + 7;

		// "Descrição da classe aqui..."
		curY = RpgScreenText.drawWrapped(graphics, font, stringValue(choice, "description", "Sem descrição."),
				textX, curY, textWidth, RpgUi.TEXT_MUTED) + 6;

		// Linha divisória horizontal
		RpgUi.divider(graphics, textX, curY, textWidth);
		curY += 8;

		// Lista de Perks
		JsonArray perks = choice.getAsJsonArray("perks");
		for (int i = 0; perks != null && i < perks.size(); i++) {
			JsonObject perk = perks.get(i).getAsJsonObject();
			String perkName = stringValue(perk, "name", "Perk " + (i + 1));
			String perkDesc = stringValue(perk, "description", "");

			RpgScreenText.draw(graphics, font, perkName + ":", textX, curY, RpgUi.GOLD, false, true);
			curY += font.lineHeight + 2;
			curY = RpgScreenText.drawWrapped(graphics, font, perkDesc, textX, curY, textWidth, RpgUi.TEXT) + 7;
		}

		int totalContentHeight = curY - (contentY + 7);
		graphics.pose().popMatrix();
		graphics.disableScissor();

		maxContentScroll = Math.max(0, totalContentHeight - (contentHeight - 12));
		contentScroll = Math.max(0, Math.min(contentScroll, maxContentScroll));

		// Thumb da barra de rolagem
		int thumbHeight = maxContentScroll <= 0 ? scrollTrackHeight
				: Math.max(16, scrollTrackHeight * scrollTrackHeight / (scrollTrackHeight + maxContentScroll));
		int thumbY = scrollTrackY + (maxContentScroll <= 0 ? 0 : (scrollTrackHeight - thumbHeight) * contentScroll / maxContentScroll);

		boolean hoveringThumb = designMouseX >= scrollTrackX && designMouseX < contentX + contentWidth
				&& designMouseY >= thumbY && designMouseY < thumbY + thumbHeight;
		int thumbColor = isDraggingScroll ? 0xFF9EC4EE : (hoveringThumb ? 0xFF769DC9 : 0xFF476487);
		graphics.fill(scrollTrackX + 1, thumbY, contentX + contentWidth - 2, thumbY + thumbHeight, thumbColor);

		// Tooltip no ícone/título
		if (designMouseX >= iconBoxX && designMouseX < iconBoxX + ICON_BOX_SIZE + 100
				&& designMouseY >= iconBoxY && designMouseY < iconBoxY + ICON_BOX_SIZE) {
			RpgUi.tooltip(graphics, font, mouseX, mouseY, stringValue(choice, "name", "Classe"),
					"Como ganhar XP: " + classXpGuide(stringValue(choice, "id", "")));
		}

		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int designMouseX = inverseX((int) mouseX);
		int designMouseY = inverseY((int) mouseY);

		int headerPadding = 7;
		int contentX = panelX + headerPadding;
		int contentY = panelY + headerPadding + 2 + ICON_BOX_SIZE + 6;
		int contentWidth = panelWidth - headerPadding * 2;
		int contentHeight = panelHeight - (contentY - panelY) - headerPadding;

		if (designMouseX >= contentX && designMouseX < contentX + contentWidth
				&& designMouseY >= contentY && designMouseY < contentY + contentHeight) {
			contentScroll = Math.max(0, Math.min(maxContentScroll, contentScroll - (int) (verticalAmount * 16)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == 1) {
			int clickX = inverseX((int) event.x());
			int clickY = inverseY((int) event.y());

			int headerPadding = 7;
			int contentX = panelX + headerPadding;
			int contentY = panelY + headerPadding + 2 + ICON_BOX_SIZE + 6;
			int contentWidth = panelWidth - headerPadding * 2;
			int contentHeight = panelHeight - (contentY - panelY) - headerPadding;

			int scrollBarWidth = 10;
			int scrollTrackX = contentX + contentWidth - scrollBarWidth;
			int scrollTrackY = contentY + 1;
			int scrollTrackHeight = contentHeight - 2;

			if (clickX >= scrollTrackX && clickX < contentX + contentWidth
					&& clickY >= scrollTrackY && clickY < scrollTrackY + scrollTrackHeight) {
				int thumbHeight = maxContentScroll <= 0 ? scrollTrackHeight
						: Math.max(16, scrollTrackHeight * scrollTrackHeight / (scrollTrackHeight + maxContentScroll));
				int thumbY = scrollTrackY + (maxContentScroll <= 0 ? 0 : (scrollTrackHeight - thumbHeight) * contentScroll / maxContentScroll);

				if (clickY >= thumbY && clickY < thumbY + thumbHeight) {
					isDraggingScroll = true;
					scrollDragStartY = clickY;
					scrollStartPos = contentScroll;
				} else if (maxContentScroll > 0) {
					float fraction = (float) (clickY - scrollTrackY - thumbHeight / 2) / Math.max(1, scrollTrackHeight - thumbHeight);
					contentScroll = Math.max(0, Math.min(maxContentScroll, Math.round(fraction * maxContentScroll)));
					isDraggingScroll = true;
					scrollDragStartY = clickY;
					scrollStartPos = contentScroll;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (isDraggingScroll && maxContentScroll > 0) {
			int headerPadding = 7;
			int contentY = panelY + headerPadding + 2 + ICON_BOX_SIZE + 6;
			int contentHeight = panelHeight - (contentY - panelY) - headerPadding;
			int scrollTrackHeight = contentHeight - 2;
			int thumbHeight = Math.max(16, scrollTrackHeight * scrollTrackHeight / (scrollTrackHeight + maxContentScroll));
			int scrollableTrack = scrollTrackHeight - thumbHeight;

			if (scrollableTrack > 0) {
				int currentMouseY = inverseY((int) event.y());
				double deltaY = currentMouseY - scrollDragStartY;
				contentScroll = Math.max(0, Math.min(maxContentScroll, scrollStartPos + (int) Math.round(deltaY * maxContentScroll / scrollableTrack)));
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (isDraggingScroll) {
			isDraggingScroll = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 263) { previous(); return true; } // Esquerda
		if (event.key() == 262) { next(); return true; }     // Direita
		if (event.key() == 257 || event.key() == 335) { confirm(); return true; } // Enter
		if (event.key() == 80) { onClose(); return true; }    // Tecla P fecha a tela se já aberta
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() { return false; }

	private static Item classIcon(String id) {
		return id.endsWith("minerador") ? Items.IRON_PICKAXE : id.endsWith("ferreiro") ? Items.ANVIL : Items.PLAYER_HEAD;
	}

	private static String classXpGuide(String id) {
		return id.endsWith("minerador") ? "minere pedra comum e minérios."
				: id.endsWith("ferreiro") ? "colete barras fundidas e fabrique itens metálicos."
				: "realize as atividades da classe.";
	}

	private static String stringValue(JsonObject value, String key, String fallback) {
		return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback;
	}
}
