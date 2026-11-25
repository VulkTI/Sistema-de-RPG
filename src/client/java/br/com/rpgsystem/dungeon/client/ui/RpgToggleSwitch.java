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

public class RpgToggleSwitch extends AbstractWidget {
	private boolean checked;
	private final Consumer<Boolean> onToggle;
	private float animationProgress;

	public RpgToggleSwitch(int x, int y, int width, int height, boolean initialChecked, Consumer<Boolean> onToggle) {
		super(x, y, width, height, Component.empty());
		this.checked = initialChecked;
		this.onToggle = onToggle;
		this.animationProgress = initialChecked ? 1.0F : 0.0F;
	}

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean checked) {
		this.checked = checked;
		this.animationProgress = checked ? 1.0F : 0.0F;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && event.button() == 1) {
			checked = !checked;
			RpgUiSounds.playClick();
			if (onToggle != null) {
				onToggle.accept(checked);
			}
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float target = checked ? 1.0F : 0.0F;
		animationProgress = Mth.lerp(0.25F, animationProgress, target);

		int trackFill = RpgUi.blend(0xFF1B2232, 0xFF1E3A2E, animationProgress);
		int trackBorder = RpgUi.blend(RpgUi.BORDER, RpgUi.GREEN, animationProgress);

		// Trilho do Switch
		RpgUi.panel(graphics, getX(), getY(), getWidth(), getHeight(), trackFill, trackBorder);

		// Thumb móvel
		int thumbW = getHeight() - 4;
		int thumbH = getHeight() - 4;
		int thumbX = getX() + 2 + (int) Math.round(animationProgress * (getWidth() - thumbW - 4));
		int thumbY = getY() + 2;

		int thumbFill = RpgUi.blend(0xFF7A8B9E, RpgUi.GREEN, animationProgress);
		graphics.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbFill);

		// Texto ON / OFF ao lado
		Font font = Minecraft.getInstance().font;
		String label = checked ? "ON" : "OFF";
		int textColor = checked ? RpgUi.GREEN : RpgUi.TEXT_DIM;
		RpgScreenText.draw(graphics, font, label, getX() + getWidth() + 6, getY() + (getHeight() - font.lineHeight) / 2 + 1,
				textColor, false, true);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
