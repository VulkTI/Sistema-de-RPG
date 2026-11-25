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

import java.util.function.Consumer;

public class RpgCheckbox extends AbstractWidget {
	private boolean checked;
	private final String label;
	private final Consumer<Boolean> onToggle;

	public RpgCheckbox(int x, int y, String label, boolean initialChecked, Consumer<Boolean> onToggle) {
		super(x, y, 14, 14, Component.literal(label));
		this.label = label;
		this.checked = initialChecked;
		this.onToggle = onToggle;
	}

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean checked) {
		this.checked = checked;
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
		boolean hovered = isHoveredOrFocused();

		int fill = checked ? 0xFF1B3D2B : hovered ? RpgUi.PANEL_HOVER : 0xFF141D2B;
		int border = checked ? RpgUi.GREEN : hovered ? RpgUi.BORDER_HIGHLIGHT : RpgUi.BORDER;

		// Caixa de seleção
		RpgUi.panel(graphics, getX(), getY(), 14, 14, fill, border);

		Font font = Minecraft.getInstance().font;
		if (checked) {
			RpgScreenText.draw(graphics, font, "✔", getX() + 3, getY() + 2, RpgUi.GREEN, false, true);
		}

		// Texto do rótulo
		RpgScreenText.draw(graphics, font, label, getX() + 18, getY() + 3, hovered ? RpgUi.TEXT : RpgUi.TEXT_MUTED, false, true);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
