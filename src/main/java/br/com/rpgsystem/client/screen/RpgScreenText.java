package br.com.rpgsystem.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class RpgScreenText {
	private RpgScreenText() {}

	public static void draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
			int color, boolean centered, boolean shadow) {
		int drawX = centered ? x - font.width(text) / 2 : x;
		graphics.text(font, text, drawX, y, color, shadow);
	}

	public static void drawWrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
			int maxWidth, int color) {
		int lineY = y;
		for (FormattedCharSequence line : font.split(Component.literal(text), Math.max(1, maxWidth))) {
			graphics.text(font, line, x, lineY, color, true);
			lineY += font.lineHeight;
		}
	}
}
