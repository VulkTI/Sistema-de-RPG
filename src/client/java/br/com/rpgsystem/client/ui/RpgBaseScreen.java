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
		super.onClose();
	}

	public boolean isAnyTextInputFocused() {
		var focused = getFocused();
		if (isWidgetTextInputFocused(focused)) {
			return true;
		}
		for (var child : children()) {
			if (isWidgetTextInputFocused(child)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWidgetTextInputFocused(Object widget) {
		if (widget instanceof net.minecraft.client.gui.components.EditBox eb) {
			return eb.canConsumeInput() || eb.isFocused();
		}
		if (widget instanceof br.com.rpgsystem.dungeon.client.ui.RpgWaveListWidget waveList && waveList.getNewWaveInput() != null) {
			return waveList.getNewWaveInput().canConsumeInput() || waveList.getNewWaveInput().isFocused();
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		var focused = getFocused();
		if (focused instanceof net.minecraft.client.gui.components.EditBox editBox) {
			if (editBox.keyPressed(event)) {
				return true;
			}
		}

		// Se qualquer campo de texto estiver ativo ou focado, NÃO fecha a tela com a tecla P nem bloqueia teclas
		if (isAnyTextInputFocused()) {
			return super.keyPressed(event);
		}

		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_P) { // Tecla P
			onClose();
			return true;
		}
		// Intercepta teclas de seta para evitar que naveguem e ativem ou fechem o menu
		if (event.isLeft() || event.isRight() || event.isUp() || event.isDown()
				|| event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT
				|| event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT
				|| event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_UP
				|| event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN) {
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
