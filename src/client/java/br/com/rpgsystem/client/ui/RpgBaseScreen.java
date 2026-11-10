package br.com.rpgsystem.client.ui;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Tela base com identidade visual padronizada do mod RPG:
 * - Centralização responsiva e cálculo automático de margens
 * - Cabeçalho com título, subtítulo e botão de fechar/voltar
 * - Fundo escuro com vinheta suave
 * - Efeitos sonoros automáticos de abertura e fechamento
 * - Tratamento padrão de teclado (ESC e tecla P)
 */
public abstract class RpgBaseScreen extends Screen {

	protected int panelX;
	protected int panelY;
	protected int panelWidth;
	protected int panelHeight;

	protected int headerHeight = 28;
	protected int windowAccentColor = RpgUi.BLUE;
	protected String subtitle = "";
	protected boolean showCloseButton = true;
	protected boolean hasWindowPanel = true;
	protected boolean renderWindowHeader = true;

	protected RpgBaseScreen(Component title) {
		super(title);
	}

	protected RpgBaseScreen(Component title, String subtitle) {
		super(title);
		this.subtitle = subtitle;
	}

	@Override
	protected void init() {
		super.init();
		layoutWindow();
		RpgUiSounds.playOpen();

		if (showCloseButton && hasWindowPanel) {
			int closeBtnSize = 14;
			int btnX = panelX + panelWidth - closeBtnSize - 6;
			int btnY = panelY + 6;
			addRenderableWidget(new RpgButton(btnX, btnY, closeBtnSize, closeBtnSize, Component.literal("✕"), RpgUi.RED, this::onClose)
					.setHoverEffect(RpgButton.HoverEffect.STATIC)
					.setTooltip("Fechar", "Retornar ao jogo"));
		}
	}

	/**
	 * Define as dimensões da janela central. Pode ser sobrescrito por telas filhas.
	 */
	protected void layoutWindow() {
		panelWidth = Math.min(300, Math.max(180, width - 24));
		panelHeight = Math.min(260, Math.max(160, height - 32));
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Fundo escuro com vinheta
		RpgUi.dim(graphics, width, height);

		if (hasWindowPanel) {
			// Janela central
			RpgUi.panel(graphics, panelX, panelY, panelWidth, panelHeight, 0xF4141E2E, 0xFF364E6D);
			RpgUi.accent(graphics, panelX, panelY, panelWidth, windowAccentColor);

			// Cabeçalho da janela
			if (renderWindowHeader && headerHeight > 0) {
				int textX = panelX + 8;
				int textY = panelY + 7;
				RpgScreenText.draw(graphics, font, getTitle().getString(), textX, textY, RpgUi.TEXT, false, true);
				if (subtitle != null && !subtitle.isBlank()) {
					RpgScreenText.draw(graphics, font, subtitle, textX, textY + font.lineHeight + 2, RpgUi.TEXT_DIM, false, true);
				}
				RpgUi.divider(graphics, panelX + 6, panelY + headerHeight, panelWidth - 12);
			}
		}

		drawContent(graphics, mouseX, mouseY, delta);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	/**
	 * Ponto de extensão para renderização de conteúdo extra da tela (gráficos, conexões, textos soltos).
	 */
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

	@Override
	public void onClose() {
		RpgUiSounds.playClose();
		super.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 80) { // Tecla P
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	protected int getContentTop() {
		return panelY + headerHeight + 6;
	}

	protected int getContentLeft() {
		return panelX + 8;
	}

	protected int getContentWidth() {
		return panelWidth - 16;
	}

	protected int getContentHeight() {
		return panelHeight - headerHeight - 14;
	}

	protected static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
