package br.com.rpgsystem.dungeon.client.ui;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.dungeon.data.WaveData;
import br.com.rpgsystem.dungeon.network.DungeonNetworking;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RpgWaveListWidget extends AbstractWidget {
	private static final int ROW_HEIGHT = 22;
	private static final int HEADER_HEIGHT = 24;

	private final List<WaveData> waves;
	private final EditBox newWaveInput;
	private final Consumer<List<WaveData>> onWavesChanged;
	private final Runnable onEditWaveSelected;

	// Drag-and-drop state & animations
	private boolean isDragging = false;
	private int draggedIndex = -1;
	private double dragStartY = 0;
	private double currentMouseY = 0;
	private int targetIndex = -1;
	private final List<Float> animatedOffsets = new ArrayList<>();

	// Popup menu state
	private boolean showHeaderMenu = false;
	private int activeMenuWaveIndex = -1;
	private boolean showSpawnSubmenu = false;
	private int popupX = 0;
	private int popupY = 0;

	public RpgWaveListWidget(int x, int y, int width, int height, List<WaveData> waves,
	                         Consumer<List<WaveData>> onWavesChanged, Runnable onEditWaveSelected) {
		super(x, y, width, height, Component.empty());
		this.waves = new ArrayList<>(waves);
		this.onWavesChanged = onWavesChanged;
		this.onEditWaveSelected = onEditWaveSelected;

		Font font = Minecraft.getInstance().font;
		this.newWaveInput = new EditBox(font, x + 4, y + 3, width - 48, 16, Component.literal("Nome da onda"));
		this.newWaveInput.setHint(Component.literal("Nova onda..."));
		this.newWaveInput.setMaxLength(64);

		syncAnimationOffsets();
	}

	public List<WaveData> getWaves() {
		return waves;
	}

	private void syncAnimationOffsets() {
		while (animatedOffsets.size() < waves.size()) animatedOffsets.add(0.0F);
		while (animatedOffsets.size() > waves.size()) animatedOffsets.removeLast();
	}

	public EditBox getNewWaveInput() {
		return newWaveInput;
	}

	private int getWaveListTop() {
		return getY() + HEADER_HEIGHT + 3 + Minecraft.getInstance().font.lineHeight + 4;
	}

	@Override
	public void setX(int x) {
		super.setX(x);
		newWaveInput.setX(x + 4);
	}

	@Override
	public void setY(int y) {
		super.setY(y);
		newWaveInput.setY(y + 3);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (newWaveInput.isFocused() && newWaveInput.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (newWaveInput.isFocused() && newWaveInput.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (newWaveInput.isFocused() && newWaveInput.keyReleased(event)) {
			return true;
		}
		return super.keyReleased(event);
	}

	private int getSubmenuX() {
		int rightX = popupX + 106;
		int subW = 85;
		if (rightX + subW > getX() + getWidth()) {
			return popupX - subW - 2;
		}
		return rightX;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (showHeaderMenu) {
			if (mouseX >= popupX && mouseX < popupX + 110 && mouseY >= popupY && mouseY < popupY + 20) return true;
		}
		if (activeMenuWaveIndex >= 0) {
			if (mouseX >= popupX && mouseX < popupX + 105 && mouseY >= popupY && mouseY < popupY + 54) return true;
			if (showSpawnSubmenu) {
				int subX = getSubmenuX();
				if (mouseX >= subX && mouseX < subX + 85 && mouseY >= popupY + 36 && mouseY < popupY + 72) return true;
			}
		}
		return super.isMouseOver(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!this.active || !this.visible) return false;
		double mx = event.x();
		double my = event.y();

		// Se popups de menu estiverem ativos, processa os cliques de menu primeiro
		if (showHeaderMenu) {
			if (handleHeaderMenuClick(mx, my)) {
				return true;
			}
			showHeaderMenu = false;
		}
		if (activeMenuWaveIndex >= 0) {
			if (handleRowMenuClick(mx, my)) {
				return true;
			}
			activeMenuWaveIndex = -1;
			showSpawnSubmenu = false;
		}

		if (isMouseOver(mx, my)) {
			onClick(event, doubleClick);
			return true;
		}
		return false;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!active) return;
		double mx = event.x();
		double my = event.y();

		// Se clicar com menu aberto, processa cliques de menu
		if (showHeaderMenu) {
			if (handleHeaderMenuClick(mx, my)) return;
			showHeaderMenu = false;
		}
		if (activeMenuWaveIndex >= 0) {
			if (handleRowMenuClick(mx, my)) return;
			activeMenuWaveIndex = -1;
			showSpawnSubmenu = false;
		}

		// 1. Botão [+] do Header
		int addBtnX = getX() + getWidth() - 40;
		int addBtnY = getY() + 3;
		if (mx >= addBtnX && mx < addBtnX + 16 && my >= addBtnY && my < addBtnY + 16) {
			String name = newWaveInput.getValue().trim();
			if (name.isBlank()) name = "Onda " + (waves.size() + 1);
			WaveData wave = new WaveData(waves.size() + 1, name);
			waves.add(wave);
			newWaveInput.setValue("");
			syncAnimationOffsets();
			RpgUiSounds.playClick();
			notifyChange();
			return;
		}

		// 2. Botão [⋮] do Header
		int menuBtnX = getX() + getWidth() - 20;
		int menuBtnY = getY() + 3;
		if (mx >= menuBtnX && mx < menuBtnX + 16 && my >= menuBtnY && my < menuBtnY + 16) {
			showHeaderMenu = !showHeaderMenu;
			popupX = menuBtnX - 80;
			popupY = menuBtnY + 18;
			RpgUiSounds.playClick();
			return;
		}

		// 3. Cliques na lista de ondas
		int listTop = getWaveListTop();
		int dotsW = 28;
		int dotsH = 16;
		int dotsX = getX() + getWidth() - 34;
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < waves.size(); i++) {
			WaveData wave = waves.get(i);
			int rowY = listTop + i * ROW_HEIGHT;
			int dotsY = rowY + 3;

			// Botão [⋮] da linha (cobre toda a área do botão da coluna Ações)
			if (mx >= dotsX && mx < dotsX + dotsW && my >= dotsY && my < dotsY + dotsH) {
				activeMenuWaveIndex = i;
				showSpawnSubmenu = false;
				popupX = dotsX - 85;
				popupY = dotsY + 18;
				RpgUiSounds.playClick();
				return;
			}

			// Clique no badge [Gradual] / [Total] para alternar diretamente
			String modeBadge = " [" + wave.getSpawnMode().getTitle() + "]";
			int nameWidth = font.width(wave.getName());
			int badgeX = getX() + 52 + nameWidth;
			int badgeW = font.width(modeBadge);
			if (mx >= badgeX - 2 && mx < badgeX + badgeW + 4 && my >= rowY && my < rowY + ROW_HEIGHT) {
				WaveData.SpawnMode nextMode = (wave.getSpawnMode() == WaveData.SpawnMode.GRADUAL)
						? WaveData.SpawnMode.TOTAL
						: WaveData.SpawnMode.GRADUAL;
				wave.setSpawnMode(nextMode);
				RpgUiSounds.playClick();
				notifyChange();
				return;
			}

			// Inicia arrastar item da onda
			if (mx >= getX() && mx < dotsX && my >= rowY && my < rowY + ROW_HEIGHT) {
				isDragging = true;
				draggedIndex = i;
				dragStartY = my;
				currentMouseY = my;
				targetIndex = i;
				return;
			}
		}

		// Clicou no campo de texto para nomear a onda
		if (newWaveInput.isMouseOver(mx, my)) {
			newWaveInput.setFocused(true);
			newWaveInput.onClick(event, doubleClick);
		} else {
			newWaveInput.setFocused(false);
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		if (isDragging && draggedIndex >= 0) {
			currentMouseY = event.y();
			int listTop = getWaveListTop();
			int slot = (int) Math.round((currentMouseY - listTop - ROW_HEIGHT / 2.0) / ROW_HEIGHT);
			targetIndex = Mth.clamp(slot, 0, waves.size() - 1);
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (isDragging && draggedIndex >= 0) {
			if (targetIndex >= 0 && targetIndex != draggedIndex && targetIndex < waves.size()) {
				WaveData moved = waves.remove(draggedIndex);
				waves.add(targetIndex, moved);
				for (int i = 0; i < waves.size(); i++) {
					waves.get(i).setOrder(i + 1);
				}
				RpgUiSounds.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2F);
				notifyChange();
			}
			isDragging = false;
			draggedIndex = -1;
			targetIndex = -1;
		}
	}

	private boolean handleHeaderMenuClick(double mx, double my) {
		if (mx >= popupX && mx < popupX + 110 && my >= popupY && my < popupY + 20) {
			waves.clear();
			syncAnimationOffsets();
			RpgUiSounds.playClick();
			notifyChange();
			showHeaderMenu = false;
			return true;
		}
		return false;
	}

	private boolean handleRowMenuClick(double mx, double my) {
		if (activeMenuWaveIndex < 0 || activeMenuWaveIndex >= waves.size()) return false;
		WaveData wave = waves.get(activeMenuWaveIndex);

		// Opção 1: Deletar onda (popupY a popupY + 18)
		if (mx >= popupX && mx < popupX + 105 && my >= popupY && my < popupY + 18) {
			waves.remove(activeMenuWaveIndex);
			for (int i = 0; i < waves.size(); i++) waves.get(i).setOrder(i + 1);
			syncAnimationOffsets();
			RpgUiSounds.playClick();
			notifyChange();
			activeMenuWaveIndex = -1;
			return true;
		}

		// Opção 2: Editar onda (popupY + 18 a popupY + 36)
		if (mx >= popupX && mx < popupX + 105 && my >= popupY + 18 && my < popupY + 36) {
			ClientPlayNetworking.send(new DungeonPayloads.EditWaveAction(activeMenuWaveIndex));
			if (onEditWaveSelected != null) {
				onEditWaveSelected.run();
			}
			activeMenuWaveIndex = -1;
			return true;
		}

		// Submenu de Modo de Spawn (avaliado antes para capturar o clique no submenu aberto)
		if (showSpawnSubmenu) {
			int subX = getSubmenuX();
			int subY = popupY + 36;
			// Gradual
			if (mx >= subX && mx < subX + 85 && my >= subY && my < subY + 18) {
				wave.setSpawnMode(WaveData.SpawnMode.GRADUAL);
				RpgUiSounds.playClick();
				notifyChange();
				activeMenuWaveIndex = -1;
				showSpawnSubmenu = false;
				return true;
			}
			// Total
			if (mx >= subX && mx < subX + 85 && my >= subY + 18 && my < subY + 36) {
				wave.setSpawnMode(WaveData.SpawnMode.TOTAL);
				RpgUiSounds.playClick();
				notifyChange();
				activeMenuWaveIndex = -1;
				showSpawnSubmenu = false;
				return true;
			}
		}

		// Opção 3: Modo de spawn > (popupY + 36 a popupY + 54)
		if (mx >= popupX && mx < popupX + 105 && my >= popupY + 36 && my < popupY + 54) {
			showSpawnSubmenu = !showSpawnSubmenu;
			RpgUiSounds.playClick();
			return true;
		}

		return false;
	}

	private void notifyChange() {
		if (onWavesChanged != null) {
			onWavesChanged.accept(new ArrayList<>(waves));
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;

		// Fundo do widget de ondas
		RpgUi.panel(graphics, getX(), getY(), getWidth(), getHeight(), 0xF4141E2E, RpgUi.BORDER);

		// Header do widget
		newWaveInput.extractWidgetRenderState(graphics, mouseX, mouseY, delta);

		// Botão [+]
		int addBtnX = getX() + getWidth() - 40;
		int addBtnY = getY() + 3;
		boolean addHover = mouseX >= addBtnX && mouseX < addBtnX + 16 && mouseY >= addBtnY && mouseY < addBtnY + 16;
		RpgUi.panel(graphics, addBtnX, addBtnY, 16, 16, addHover ? RpgUi.PANEL_HOVER : 0xFF182333, addHover ? RpgUi.GREEN : RpgUi.BORDER);
		RpgScreenText.draw(graphics, font, "+", addBtnX + 5, addBtnY + 4, RpgUi.GREEN, false, true);

		// Botão [⋮] do Header
		int menuBtnX = getX() + getWidth() - 20;
		int menuBtnY = getY() + 3;
		boolean menuHover = mouseX >= menuBtnX && mouseX < menuBtnX + 16 && mouseY >= menuBtnY && mouseY < menuBtnY + 16;
		RpgUi.panel(graphics, menuBtnX, menuBtnY, 16, 16, menuHover ? RpgUi.PANEL_HOVER : 0xFF182333, menuHover ? RpgUi.TEXT : RpgUi.BORDER);
		RpgScreenText.draw(graphics, font, "⋮", menuBtnX + 6, menuBtnY + 4, RpgUi.TEXT_MUTED, false, true);

		RpgUi.divider(graphics, getX() + 4, getY() + HEADER_HEIGHT, getWidth() - 8);

		// Cabeçalho das 3 colunas
		int headerY = getY() + HEADER_HEIGHT + 3;
		RpgScreenText.draw(graphics, font, "Ordem", getX() + 8, headerY, RpgUi.TEXT_DIM, false, true);
		RpgScreenText.draw(graphics, font, "Nome da Onda", getX() + 50, headerY, RpgUi.TEXT_DIM, false, true);
		RpgScreenText.draw(graphics, font, "Ações", getX() + getWidth() - 34, headerY, RpgUi.TEXT_DIM, false, true);

		int listTop = getWaveListTop();
		RpgUi.divider(graphics, getX() + 4, listTop - 2, getWidth() - 8);

		// Lista de ondas com suporte a animação de deslocamento
		syncAnimationOffsets();

		for (int i = 0; i < waves.size(); i++) {
			WaveData wave = waves.get(i);
			float targetOffset = 0.0F;

			if (isDragging && draggedIndex >= 0 && i != draggedIndex) {
				if (draggedIndex < targetIndex && i > draggedIndex && i <= targetIndex) {
					targetOffset = -ROW_HEIGHT;
				} else if (draggedIndex > targetIndex && i >= targetIndex && i < draggedIndex) {
					targetOffset = ROW_HEIGHT;
				}
			}

			float currentOffset = Mth.lerp(0.3F, animatedOffsets.get(i), targetOffset);
			animatedOffsets.set(i, currentOffset);

			// Se for a linha sendo arrastada, desenha no final para ficar por cima
			if (isDragging && i == draggedIndex) continue;

			renderWaveRow(graphics, font, wave, i, listTop + i * ROW_HEIGHT + (int) currentOffset, mouseX, mouseY);
		}

		// Renderiza a linha que está sendo arrastada com efeito flutuante elevado
		if (isDragging && draggedIndex >= 0 && draggedIndex < waves.size()) {
			WaveData wave = waves.get(draggedIndex);
			int dragY = (int) Math.round(currentMouseY - ROW_HEIGHT / 2.0);
			graphics.fill(getX() + 3, dragY - 1, getX() + getWidth() - 3, dragY + ROW_HEIGHT + 1, 0x88000000);
			renderWaveRow(graphics, font, wave, draggedIndex, dragY, mouseX, mouseY);
		}

		// Popups de menu
		renderPopups(graphics, font, mouseX, mouseY);
	}

	private void renderWaveRow(GuiGraphicsExtractor graphics, Font font, WaveData wave, int index, int y, int mouseX, int mouseY) {
		boolean hovered = mouseX >= getX() + 4 && mouseX < getX() + getWidth() - 4 && mouseY >= y && mouseY < y + ROW_HEIGHT;

		int fill = hovered ? 0xFF1C2738 : (index % 2 == 0 ? 0xFF121A26 : 0xFF151F2E);
		int border = hovered ? RpgUi.BORDER_HIGHLIGHT : RpgUi.BORDER_SOFT;

		RpgUi.panel(graphics, getX() + 4, y, getWidth() - 8, ROW_HEIGHT - 2, fill, border);

		// Coluna 1: Ordem
		RpgScreenText.draw(graphics, font, "#" + wave.getOrder(), getX() + 10, y + 5, RpgUi.GOLD, false, true);

		// Coluna 2: Nome + Modo de Spawn
		String modeBadge = " [" + wave.getSpawnMode().getTitle() + "]";
		RpgScreenText.draw(graphics, font, wave.getName(), getX() + 50, y + 5, RpgUi.TEXT, false, true);
		int nameWidth = font.width(wave.getName());
		int badgeX = getX() + 52 + nameWidth;
		int badgeW = font.width(modeBadge);
		boolean badgeHover = mouseX >= badgeX - 2 && mouseX < badgeX + badgeW + 4 && mouseY >= y && mouseY < y + ROW_HEIGHT;
		if (badgeHover) {
			graphics.fill(badgeX - 2, y + 3, badgeX + badgeW + 2, y + ROW_HEIGHT - 4, 0x4400E5FF);
		}
		RpgScreenText.draw(graphics, font, modeBadge, badgeX, y + 5, badgeHover ? RpgUi.GREEN : RpgUi.CYAN, false, true);

		// Coluna 3: Botão [⋮] cobrindo toda a área sob 'Ações'
		int dotsW = 28;
		int dotsH = 16;
		int dotsX = getX() + getWidth() - 34;
		int dotsY = y + 3;
		boolean dotsHover = mouseX >= dotsX && mouseX < dotsX + dotsW && mouseY >= dotsY && mouseY < dotsY + dotsH;
		RpgUi.panel(graphics, dotsX, dotsY, dotsW, dotsH, dotsHover ? RpgUi.PANEL_HOVER : fill, dotsHover ? RpgUi.BORDER_HIGHLIGHT : border);
		RpgScreenText.draw(graphics, font, "⋮", dotsX + (dotsW - font.width("⋮")) / 2, dotsY + 4, dotsHover ? RpgUi.TEXT : RpgUi.TEXT_MUTED, false, true);
	}

	private void renderPopups(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (showHeaderMenu) {
			RpgUi.panel(graphics, popupX, popupY, 110, 20, 0xF9151E2D, 0xFF3D5575);
			boolean h = mouseX >= popupX && mouseX < popupX + 110 && mouseY >= popupY && mouseY < popupY + 20;
			if (h) graphics.fill(popupX + 2, popupY + 2, popupX + 108, popupY + 18, 0xFF2A3A52);
			RpgScreenText.draw(graphics, font, "Deletar todas as ondas", popupX + 6, popupY + 6, RpgUi.RED, false, true);
		}

		if (activeMenuWaveIndex >= 0 && activeMenuWaveIndex < waves.size()) {
			WaveData wave = waves.get(activeMenuWaveIndex);
			RpgUi.panel(graphics, popupX, popupY, 105, 54, 0xF9151E2D, 0xFF3D5575);

			// Item 1: Deletar onda
			boolean h1 = mouseX >= popupX && mouseX < popupX + 105 && mouseY >= popupY && mouseY < popupY + 18;
			if (h1) graphics.fill(popupX + 2, popupY + 2, popupX + 103, popupY + 18, 0xFF2A3A52);
			RpgScreenText.draw(graphics, font, "Deletar onda", popupX + 6, popupY + 5, RpgUi.RED, false, true);

			// Item 2: Editar onda
			boolean h2 = mouseX >= popupX && mouseX < popupX + 105 && mouseY >= popupY + 18 && mouseY < popupY + 36;
			if (h2) graphics.fill(popupX + 2, popupY + 18, popupX + 103, popupY + 36, 0xFF2A3A52);
			RpgScreenText.draw(graphics, font, "Editar onda", popupX + 6, popupY + 23, RpgUi.GREEN, false, true);

			// Item 3: Modo de spawn >
			boolean h3 = mouseX >= popupX && mouseX < popupX + 105 && mouseY >= popupY + 36 && mouseY < popupY + 54;
			if (h3) graphics.fill(popupX + 2, popupY + 36, popupX + 103, popupY + 52, 0xFF2A3A52);
			RpgScreenText.draw(graphics, font, "Modo de spawn >", popupX + 6, popupY + 41, RpgUi.TEXT, false, true);

			// Flyout Submenu com indicação visual de qual modo está selecionado
			if (showSpawnSubmenu) {
				int subX = getSubmenuX();
				int subY = popupY + 36;
				int subW = 85;
				int subH = 36;
				RpgUi.panel(graphics, subX, subY, subW, subH, 0xF9151E2D, 0xFF3D5575);

				boolean isGradual = wave.getSpawnMode() == WaveData.SpawnMode.GRADUAL;
				boolean isTotal = wave.getSpawnMode() == WaveData.SpawnMode.TOTAL;

				boolean sh1 = mouseX >= subX && mouseX < subX + subW && mouseY >= subY && mouseY < subY + 18;
				if (sh1) graphics.fill(subX + 2, subY + 2, subX + subW - 2, subY + 18, 0xFF2A3A52);
				String gradText = (isGradual ? "✔ " : "   ") + "Gradual";
				RpgScreenText.draw(graphics, font, gradText, subX + 6, subY + 5, isGradual ? RpgUi.GREEN : RpgUi.TEXT, false, true);

				boolean sh2 = mouseX >= subX && mouseX < subX + subW && mouseY >= subY + 18 && mouseY < subY + 36;
				if (sh2) graphics.fill(subX + 2, subY + 18, subX + subW - 2, subY + 34, 0xFF2A3A52);
				String totalText = (isTotal ? "✔ " : "   ") + "Total";
				RpgScreenText.draw(graphics, font, totalText, subX + 6, subY + 23, isTotal ? RpgUi.GREEN : RpgUi.TEXT, false, true);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
