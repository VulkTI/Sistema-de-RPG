package br.com.rpgsystem.client.ui;

import br.com.rpgsystem.client.screen.RpgUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Consumer;

/**
 * Área rolável reutilizável com recorte (scissor), barra de rolagem estilizada,
 * arrasto com mouse e suporte à roda do scroll.
 */
public class RpgScrollArea {
	private int x;
	private int y;
	private int width;
	private int height;
	private int scrollBarWidth = 8;
	private int contentHeight;
	private int scroll;
	private boolean isDragging;
	private double dragStartY;
	private int dragStartScroll;

	public RpgScrollArea(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setContentHeight(int contentHeight) {
		this.contentHeight = contentHeight;
		clampScroll();
	}

	public int getScroll() {
		return scroll;
	}

	public void setScroll(int scroll) {
		this.scroll = scroll;
		clampScroll();
	}

	public int getMaxScroll() {
		return Math.max(0, contentHeight - height);
	}

	private void clampScroll() {
		this.scroll = Math.max(0, Math.min(scroll, getMaxScroll()));
	}

	public int getVisibleContentWidth() {
		return getMaxScroll() > 0 ? width - scrollBarWidth - 4 : width;
	}

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
			Consumer<GuiGraphicsExtractor> contentRenderer) {
		clampScroll();

		int scissorX1 = x;
		int scissorY1 = y;
		int scissorX2 = x + getVisibleContentWidth();
		int scissorY2 = y + height;

		if (scissorX2 > scissorX1 && scissorY2 > scissorY1) {
			graphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
			graphics.pose().pushMatrix();
			graphics.pose().translate(0, -scroll);

			contentRenderer.accept(graphics);

			graphics.pose().popMatrix();
			graphics.disableScissor();
		}

		// Desenhar barra de rolagem se houver conteúdo além da visualização
		int maxScroll = getMaxScroll();
		if (maxScroll > 0) {
			int trackX = x + width - scrollBarWidth;
			int trackY = y;
			int trackH = height;

			// Trilha
			graphics.fill(trackX, trackY, trackX + scrollBarWidth, trackY + trackH, 0xFF141D2B);
			graphics.fill(trackX - 1, trackY, trackX, trackY + trackH, RpgUi.BORDER_SOFT);

			int thumbH = Math.max(16, trackH * trackH / (trackH + maxScroll));
			int thumbY = trackY + (trackH - thumbH) * scroll / maxScroll;

			boolean hoveringThumb = mouseX >= trackX && mouseX < trackX + scrollBarWidth
					&& mouseY >= thumbY && mouseY < thumbY + thumbH;
			int thumbColor = isDragging ? RpgUi.CYAN : (hoveringThumb ? 0xFF769DC9 : 0xFF476487);

			graphics.fill(trackX + 1, thumbY, trackX + scrollBarWidth - 1, thumbY + thumbH, thumbColor);
		}
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (isMouseOver(mouseX, mouseY) && getMaxScroll() > 0) {
			scroll = Math.max(0, Math.min(getMaxScroll(), scroll - (int) (verticalAmount * 16)));
			return true;
		}
		return false;
	}

	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() == 1 && getMaxScroll() > 0) {
			double mx = event.x();
			double my = event.y();
			int trackX = x + width - scrollBarWidth;

			if (mx >= trackX && mx < trackX + scrollBarWidth && my >= y && my < y + height) {
				int trackH = height;
				int maxScroll = getMaxScroll();
				int thumbH = Math.max(16, trackH * trackH / (trackH + maxScroll));
				int thumbY = y + (trackH - thumbH) * scroll / maxScroll;

				if (my >= thumbY && my < thumbY + thumbH) {
					isDragging = true;
					dragStartY = my;
					dragStartScroll = scroll;
				} else {
					float fraction = (float) (my - y - thumbH / 2) / Math.max(1, trackH - thumbH);
					scroll = Math.max(0, Math.min(maxScroll, Math.round(fraction * maxScroll)));
					isDragging = true;
					dragStartY = my;
					dragStartScroll = scroll;
				}
				return true;
			}
		}
		return false;
	}

	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (isDragging && getMaxScroll() > 0) {
			int trackH = height;
			int maxScroll = getMaxScroll();
			int thumbH = Math.max(16, trackH * trackH / (trackH + maxScroll));
			int scrollableTrack = trackH - thumbH;

			if (scrollableTrack > 0) {
				double deltaY = event.y() - dragStartY;
				scroll = Math.max(0, Math.min(maxScroll, dragStartScroll + (int) Math.round(deltaY * maxScroll / scrollableTrack)));
			}
			return true;
		}
		return false;
	}

	public boolean mouseReleased(MouseButtonEvent event) {
		if (isDragging) {
			isDragging = false;
			return true;
		}
		return false;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
