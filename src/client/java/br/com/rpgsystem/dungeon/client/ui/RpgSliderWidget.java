package br.com.rpgsystem.dungeon.client.ui;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Function;

public class RpgSliderWidget extends AbstractWidget {
	private final double min;
	private final double max;
	private double value;
	private final Consumer<Double> onValueChanged;
	private Function<Double, String> textFormatter = v -> String.format("%.1f", v);
	private boolean dragging = false;
	private int accentColor = RpgUi.BLUE;

	public RpgSliderWidget(int x, int y, int width, int height, double min, double max, double initialValue,
	                       Consumer<Double> onValueChanged) {
		super(x, y, width, height, Component.empty());
		this.min = min;
		this.max = max;
		this.value = Mth.clamp(initialValue, min, max);
		this.onValueChanged = onValueChanged;
	}

	public RpgSliderWidget setTextFormatter(Function<Double, String> formatter) {
		this.textFormatter = formatter;
		return this;
	}

	public RpgSliderWidget setAccentColor(int color) {
		this.accentColor = color;
		return this;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double val) {
		this.value = Mth.clamp(val, min, max);
	}

	private void updateFromMouse(double mouseX) {
		double ratio = (mouseX - (getX() + 4)) / (double) (getWidth() - 8);
		ratio = Mth.clamp(ratio, 0.0, 1.0);
		double newValue = min + ratio * (max - min);
		if (Math.abs(newValue - this.value) > 1e-6) {
			this.value = newValue;
			if (onValueChanged != null) {
				onValueChanged.accept(this.value);
			}
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && event.button() == 1) {
			dragging = true;
			updateFromMouse(event.x());
			RpgUiSounds.playClick();
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		if (active && dragging) {
			updateFromMouse(event.x());
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		dragging = false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean hovered = isHoveredOrFocused();

		// Fundo do trilho
		int trackY = getY() + (getHeight() - 6) / 2;
		RpgUi.panel(graphics, getX(), trackY, getWidth(), 6, 0xFF101622, RpgUi.BORDER);

		// Barra preenchida
		double ratio = (value - min) / (max - min);
		int fillW = (int) Math.round(ratio * (getWidth() - 8));
		if (fillW > 0) {
			graphics.fill(getX() + 2, trackY + 1, getX() + 2 + fillW, trackY + 5, accentColor);
		}

		// Cursor / Thumb móvel
		int thumbW = 8;
		int thumbH = getHeight();
		int thumbX = getX() + (int) Math.round(ratio * (getWidth() - thumbW));
		int thumbBorder = hovered || dragging ? 0xFFFFFFFF : accentColor;
		int thumbFill = hovered || dragging ? RpgUi.blend(accentColor, 0xFFFFFFFF, 0.3F) : accentColor;

		RpgUi.panel(graphics, thumbX, getY(), thumbW, thumbH, thumbFill, thumbBorder);

		// Texto formatado à direita ou centralizado
		Font font = Minecraft.getInstance().font;
		String text = textFormatter.apply(value);
		RpgScreenText.draw(graphics, font, text, getX() + getWidth() + 6, getY() + (getHeight() - font.lineHeight) / 2 + 1,
				RpgUi.TEXT, false, true);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
