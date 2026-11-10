package br.com.rpgsystem.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Botão sem o sprite vanilla, desenhado como um componente da UI do mod. */
public final class RpgButton extends AbstractWidget {
	private final Runnable action;
	private final int accentColor;

	public RpgButton(int x, int y, int width, int height, Component message, int accentColor, Runnable action) {
		super(x, y, width, height, message);
		this.action = action;
		this.accentColor = accentColor;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean hovered = isHoveredOrFocused();
		int border = !active ? 0xFF273247 : hovered ? accentColor : RpgUi.BORDER;
		int fill = !active ? 0xFF111724 : hovered ? RpgUi.PANEL_HOVER : RpgUi.PANEL_RAISED;
		RpgUi.panel(graphics, getX(), getY(), getWidth(), getHeight(), fill, border);
		RpgUi.accent(graphics, getX(), getY(), getWidth(), !active ? RpgUi.TEXT_DIM : accentColor);
		int color = !active ? RpgUi.TEXT_DIM : hovered ? RpgUi.TEXT : RpgUi.TEXT_MUTED;
		RpgScreenText.draw(graphics, Minecraft.getInstance().font, getMessage().getString(), getX() + getWidth() / 2,
				getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2 + 2, color, true, true);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && event.button() == 1) action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
