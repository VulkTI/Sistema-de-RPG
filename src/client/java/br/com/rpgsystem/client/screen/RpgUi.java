package br.com.rpgsystem.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Vocabulário visual compartilhado pelas telas do sistema RPG.
 * Mantém a interface legível, consistente e responsiva com suporte a animações sutis.
 */
public final class RpgUi {
	public static final int BACKDROP = 0xB90B1020;
	public static final int PANEL = 0xF0151D2E;
	public static final int PANEL_RAISED = 0xF91C273B;
	public static final int PANEL_MUTED = 0xE50F1625;
	public static final int PANEL_HOVER = 0xF928354B;
	public static final int BORDER = 0xFF33445D;
	public static final int BORDER_SOFT = 0xFF25334A;
	public static final int BORDER_HIGHLIGHT = 0xFF5A7CA6;
	public static final int TEXT = 0xFFF3F6FF;
	public static final int TEXT_MUTED = 0xFF9EABC2;
	public static final int TEXT_DIM = 0xFF6C7890;
	public static final int CYAN = 0xFF5DE0D0;
	public static final int BLUE = 0xFF71A7FF;
	public static final int PURPLE = 0xFFC28BFF;
	public static final int GOLD = 0xFFFFC857;
	public static final int GREEN = 0xFF62D99B;
	public static final int RED = 0xFFFF7185;

	private RpgUi() {}

	/** Retorna uma oscilação suave de 0.0 a 1.0 baseada no relógio do sistema. */
	public static float pulse(float speed) {
		double time = (System.currentTimeMillis() % 100000L) / 1000.0;
		return (float) ((Math.sin(time * speed * Math.PI * 2.0) + 1.0) * 0.5);
	}

	/** Interpola linearmente entre duas cores ARGB. */
	public static int blend(int c1, int c2, float ratio) {
		float r = Mth.clamp(ratio, 0.0F, 1.0F);
		float inv = 1.0F - r;
		int a = (int) (((c1 >> 24) & 0xFF) * inv + ((c2 >> 24) & 0xFF) * r);
		int red = (int) (((c1 >> 16) & 0xFF) * inv + ((c2 >> 16) & 0xFF) * r);
		int g = (int) (((c1 >> 8) & 0xFF) * inv + ((c2 >> 8) & 0xFF) * r);
		int b = (int) ((c1 & 0xFF) * inv + (c2 & 0xFF) * r);
		return (a << 24) | (red << 16) | (g << 8) | b;
	}

	public static int withAlpha(int color, int alpha) {
		return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
	}

	public static int withAlpha(int color, float alphaRatio) {
		int alpha = Mth.clamp((int) (alphaRatio * 255.0F), 0, 255);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	public static void dim(GuiGraphicsExtractor graphics, int width, int height) {
		graphics.fill(0, 0, width, height, BACKDROP);
		graphics.fill(0, 0, width, 3, 0xFF19263B);
		graphics.fill(0, height - 2, width, height, 0xFF0A0E18);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		panel(graphics, x, y, width, height, PANEL, BORDER);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
		if (width <= 0 || height <= 0) return;
		graphics.fill(x, y, x + width, y + height, border);
		if (width > 2 && height > 2) graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
	}

	public static void panelGlow(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			int fill, int border, int glowColor, float glowAlpha) {
		if (width <= 0 || height <= 0) return;
		int effectiveBorder = blend(border, glowColor, glowAlpha);
		graphics.fill(x, y, x + width, y + height, effectiveBorder);
		if (width > 2 && height > 2) {
			graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
		}
	}

	public static void accent(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
		graphics.fill(x, y, x + width, y + 2, color);
	}

	public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
		graphics.fill(x, y, x + width, y + 1, BORDER_SOFT);
	}

	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			long current, long required, int fillColor) {
		if (width <= 2 || height <= 1) return;
		graphics.fill(x, y, x + width, y + height, 0xFF0A0F1A);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF29354A);
		float fraction = required <= 0L ? 0.0F : Mth.clamp((float) current / (float) required, 0.0F, 1.0F);
		int filled = Mth.clamp((int) ((width - 2) * fraction), 0, width - 2);
		if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
	}

	public static void badge(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		int width = font.width(text) + 12;
		graphics.fill(x, y, x + width, y + 15, 0xAA0A101C);
		graphics.fill(x, y, x + 2, y + 15, color);
		graphics.text(font, text, x + 7, y + 3, color, true);
	}

	public static void title(GuiGraphicsExtractor graphics, Font font, String title, String subtitle,
			int x, int y) {
		graphics.text(font, title, x, y, TEXT, true);
		if (subtitle != null && !subtitle.isBlank()) graphics.text(font, subtitle, x, y + 13, TEXT_MUTED, true);
	}

	public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, text.toUpperCase(java.util.Locale.ROOT), x, y, color, true);
	}

	public static List<Component> tooltip(String title, String... body) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(title));
		for (String line : body) if (line != null && !line.isBlank()) lines.add(Component.literal(line));
		return lines;
	}

	public static void tooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
			String title, String... body) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component line : tooltip(title, body)) lines.addAll(font.split(line, 240));
		graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
	}

	public static int centerX(int width, int panelWidth) {
		return Math.max(8, (width - panelWidth) / 2);
	}

	public static String number(long value) {
		if (value >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0D);
		if (value >= 1_000L) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0D);
		return Long.toString(value);
	}
}
