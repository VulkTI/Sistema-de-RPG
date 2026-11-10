package br.com.rpgsystem.client;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgIconWidget;
import br.com.rpgsystem.client.ui.RpgScrollArea;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.network.RpgPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tela de seleção de classe reestruturada sobre a nova base de UI:
 * - Ícone com animação de hover e atualização em tempo real
 * - Área de rolagem modular usando RpgScrollArea
 * - Botões de navegação lateral (< e >) com feedback sonoro
 */
public final class ClassSelectionScreen extends RpgBaseScreen {

	private static final int ICON_SIZE = 22;

	private int selectedIndex;
	private JsonArray classes;
	private RpgScrollArea scrollArea;
	private RpgIconWidget classIconWidget;

	public ClassSelectionScreen() {
		super(Component.literal("Escolha sua classe"));
		this.classes = RpgClientState.snapshot().getAsJsonArray("classes");
		this.showCloseButton = false;
		this.renderWindowHeader = false;
	}

	@Override
	protected void layoutWindow() {
		panelWidth = Math.min(220, Math.max(170, width - 60));
		panelHeight = Math.min(240, Math.max(160, height - 48));
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(6, (height - panelHeight - 24) / 2);
		headerHeight = 32;
		windowAccentColor = RpgUi.BLUE;
	}

	@Override
	protected void init() {
		classes = RpgClientState.snapshot().getAsJsonArray("classes");
		super.init();

		int navBtnWidth = 18;
		int navBtnHeight = 22;
		int navBtnY = panelY + panelHeight / 2 - navBtnHeight / 2;

		// Botões de navegação lateral
		addRenderableWidget(new RpgButton(
				panelX - navBtnWidth - 6,
				navBtnY,
				navBtnWidth,
				navBtnHeight,
				Component.literal("<"),
				RpgUi.BLUE,
				this::previous
		).setHoverEffect(RpgButton.HoverEffect.PULSE));

		addRenderableWidget(new RpgButton(
				panelX + panelWidth + 6,
				navBtnY,
				navBtnWidth,
				navBtnHeight,
				Component.literal(">"),
				RpgUi.BLUE,
				this::next
		).setHoverEffect(RpgButton.HoverEffect.PULSE));

		// Botão de confirmação no rodapé
		int confirmWidth = 118;
		int confirmHeight = 16;
		addRenderableWidget(new RpgButton(
				(width - confirmWidth) / 2,
				panelY + panelHeight + 6,
				confirmWidth,
				confirmHeight,
				Component.literal("ESCOLHER CLASSE"),
				RpgUi.CYAN,
				this::confirm
		).setHoverEffect(RpgButton.HoverEffect.PULSE));

		// Ícone dinâmico no cabeçalho
		int iconX = panelX + 7;
		int iconY = panelY + 5;
		classIconWidget = addRenderableWidget(new RpgIconWidget(iconX, iconY, ICON_SIZE, () -> {
			JsonObject choice = getCurrentClass();
			return choice != null ? new ItemStack(classIcon(stringValue(choice, "id", ""))) : ItemStack.EMPTY;
		})
				.setHoverEffect(RpgIconWidget.HoverEffect.PULSE)
				.setTooltip(() -> {
					JsonObject choice = getCurrentClass();
					if (choice == null) return java.util.List.of();
					String name = stringValue(choice, "name", "Classe");
					String guide = "Como ganhar XP: " + classXpGuide(stringValue(choice, "id", ""));
					return RpgUi.tooltip(name, guide);
				}));

		// Área de rolagem para descrição e perks
		int pad = 7;
		int contentX = panelX + pad;
		int contentY = panelY + headerHeight + 2;
		int contentW = panelWidth - pad * 2;
		int contentH = panelHeight - headerHeight - pad - 4;

		scrollArea = new RpgScrollArea(contentX, contentY, contentW, contentH);
	}

	private JsonObject getCurrentClass() {
		if (classes == null || classes.isEmpty()) return null;
		int idx = Math.floorMod(selectedIndex, classes.size());
		return classes.get(idx).getAsJsonObject();
	}

	private void previous() {
		if (classes != null && !classes.isEmpty()) {
			RpgUiSounds.playTab();
			selectedIndex = Math.floorMod(selectedIndex - 1, classes.size());
			if (scrollArea != null) scrollArea.setScroll(0);
		}
	}

	private void next() {
		if (classes != null && !classes.isEmpty()) {
			RpgUiSounds.playTab();
			selectedIndex = (selectedIndex + 1) % classes.size();
			if (scrollArea != null) scrollArea.setScroll(0);
		}
	}

	private void confirm() {
		JsonObject choice = getCurrentClass();
		if (choice == null) return;
		RpgUiSounds.playSkillLearn();
		String id = choice.get("id").getAsString();
		ClientPlayNetworking.send(new RpgPayloads.SelectClass(id));
		minecraft.gui.setScreen(null);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (classes == null || classes.isEmpty()) {
			RpgScreenText.draw(graphics, font, "Nenhuma classe foi registrada.", width / 2, panelY + panelHeight / 2 - 4, RpgUi.TEXT, true, true);
			return;
		}

		JsonObject choice = getCurrentClass();
		if (choice == null) return;

		// Nome da classe ao lado do ícone
		int nameX = panelX + 7 + ICON_SIZE + 6;
		int nameY = panelY + 5 + (ICON_SIZE - font.lineHeight) / 2;
		RpgScreenText.draw(graphics, font, stringValue(choice, "name", "Classe"), nameX, nameY, RpgUi.TEXT, false, true);

		// Indicador de página (Ex: 1/2) no canto superior direito
		String indexStr = (Math.floorMod(selectedIndex, classes.size()) + 1) + "/" + classes.size();
		RpgScreenText.draw(graphics, font, indexStr, panelX + panelWidth - font.width(indexStr) - 8, nameY, RpgUi.TEXT_DIM, false, true);

		// Fundo da área de conteúdo interna
		int pad = 7;
		int contentX = panelX + pad;
		int contentY = panelY + headerHeight + 2;
		int contentW = panelWidth - pad * 2;
		int contentH = panelHeight - headerHeight - pad - 4;

		RpgUi.panel(graphics, contentX, contentY, contentW, contentH, 0xF90F1723, 0xFF2B3F58);

		// Renderizar conteúdo com RpgScrollArea
		scrollArea.setBounds(contentX + 1, contentY + 1, contentW - 2, contentH - 2);

		int textWidth = scrollArea.getVisibleContentWidth() - 8;
		int textX = contentX + 4;

		scrollArea.render(graphics, mouseX, mouseY, delta, innerGraphics -> {
			int curY = contentY + 5;

			// Descrição
			curY = RpgScreenText.drawWrapped(innerGraphics, font, stringValue(choice, "description", "Sem descrição."),
					textX, curY, textWidth, RpgUi.TEXT_MUTED) + 6;

			// Divisor
			RpgUi.divider(innerGraphics, textX, curY, textWidth);
			curY += 8;

			// Lista de Perks
			JsonArray perks = choice.getAsJsonArray("perks");
			for (int i = 0; perks != null && i < perks.size(); i++) {
				JsonObject perk = perks.get(i).getAsJsonObject();
				String perkName = stringValue(perk, "name", "Perk " + (i + 1));
				String perkDesc = stringValue(perk, "description", "");

				RpgScreenText.draw(innerGraphics, font, perkName + ":", textX, curY, RpgUi.GOLD, false, true);
				curY += font.lineHeight + 2;
				curY = RpgScreenText.drawWrapped(innerGraphics, font, perkDesc, textX, curY, textWidth, RpgUi.TEXT) + 7;
			}

			int totalContentHeight = curY - (contentY + 5);
			scrollArea.setContentHeight(totalContentHeight);
		});
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (scrollArea != null && scrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (scrollArea != null && scrollArea.mouseClicked(event)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (scrollArea != null && scrollArea.mouseDragged(event, dragX, dragY)) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (scrollArea != null && scrollArea.mouseReleased(event)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 263) { previous(); return true; } // Seta esquerda
		if (event.key() == 262) { next(); return true; }     // Seta direita
		if (event.key() == 257 || event.key() == 335) { confirm(); return true; } // Enter
		return super.keyPressed(event);
	}

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
