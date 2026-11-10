package br.com.rpgsystem.client.ui;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * Barra de progresso estilizada para XP, vida, mana ou progresso de habilidades.
 * Suporta atualização em tempo real via Suppliers, texto centralizado e efeito pulsante.
 */
public class RpgProgressBar extends AbstractWidget {
	private final Supplier<Long> currentSupplier;
	private final Supplier<Long> maxSupplier;
	private int fillColor;
	private boolean showText = true;
	private Supplier<String> customTextSupplier = null;
	private boolean pulseWhenFull = false;

	public RpgProgressBar(int x, int y, int width, int height,
			Supplier<Long> currentSupplier, Supplier<Long> maxSupplier, int fillColor) {
		super(x, y, width, height, Component.empty());
		this.currentSupplier = currentSupplier;
		this.maxSupplier = maxSupplier;
		this.fillColor = fillColor;
	}

	public RpgProgressBar(int x, int y, int width, int height, long current, long max, int fillColor) {
		this(x, y, width, height, () -> current, () -> max, fillColor);
	}

	public RpgProgressBar setShowText(boolean show) {
		this.showText = show;
		return this;
	}

	public RpgProgressBar setCustomText(Supplier<String> textSupplier) {
		this.customTextSupplier = textSupplier;
		return this;
	}

	public RpgProgressBar setPulseWhenFull(boolean pulse) {
		this.pulseWhenFull = pulse;
		return this;
	}

	public RpgProgressBar setFillColor(int color) {
		this.fillColor = color;
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long current = currentSupplier != null ? Math.max(0L, currentSupplier.get()) : 0L;
		long max = maxSupplier != null ? Math.max(1L, maxSupplier.get()) : 1L;
		float fraction = Mth.clamp((float) current / (float) max, 0.0F, 1.0F);

		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		// Moldura e fundo da barra
		graphics.fill(x, y, x + w, y + h, 0xFF0A0F1A);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1C273B);

		int filledWidth = (int) ((w - 2) * fraction);
		if (filledWidth > 0) {
			int barColor = fillColor;
			if (pulseWhenFull && fraction >= 1.0F) {
				float pulse = RpgUi.pulse(1.5F);
				barColor = RpgUi.blend(fillColor, 0xFFFFFFFF, pulse * 0.4F);
			}

			graphics.fill(x + 1, y + 1, x + 1 + filledWidth, y + h - 1, barColor);
			// Linha de brilho superior no preenchimento
			if (h > 4) {
				graphics.fill(x + 1, y + 1, x + 1 + filledWidth, y + 2, RpgUi.withAlpha(0xFFFFFFFF, 0.35F));
			}
		}

		// Texto centralizado
		if (showText && h >= 9) {
			Font font = Minecraft.getInstance().font;
			String text;
			if (customTextSupplier != null) {
				text = customTextSupplier.get();
			} else {
				text = RpgUi.number(current) + " / " + RpgUi.number(max);
			}

			if (text != null && !text.isBlank()) {
				int textY = y + (h - font.lineHeight) / 2 + 1;
				RpgScreenText.draw(graphics, font, text, x + w / 2, textY, RpgUi.TEXT, true, true);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {}
}
