package br.com.rpgsystem.client.ui;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Botão temático de RPG com suporte a:
 * - Troca do som do clique
 * - Efeito hover estático ou animado (pulso suave)
 * - Cores de acento e bordas estilizadas
 * - Tooltips dinâmicos
 */
public class RpgButton extends AbstractWidget {

	public enum HoverEffect {
		STATIC,
		PULSE,
		NONE
	}

	private final Runnable action;
	private int accentColor;
	private HoverEffect hoverEffect = HoverEffect.STATIC;
	private SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();
	private float clickSoundPitch = 1.0F;
	private Supplier<List<Component>> tooltipSupplier = null;

	public RpgButton(int x, int y, int width, int height, Component message, int accentColor, Runnable action) {
		super(x, y, width, height, message);
		this.action = action;
		this.accentColor = accentColor;
	}

	public RpgButton setHoverEffect(HoverEffect effect) {
		this.hoverEffect = effect;
		return this;
	}

	public RpgButton setClickSound(SoundEvent sound) {
		this.clickSound = sound;
		return this;
	}

	public RpgButton setClickSound(SoundEvent sound, float pitch) {
		this.clickSound = sound;
		this.clickSoundPitch = pitch;
		return this;
	}

	public RpgButton setAccentColor(int color) {
		this.accentColor = color;
		return this;
	}

	public RpgButton setTooltip(Supplier<List<Component>> tooltipSupplier) {
		this.tooltipSupplier = tooltipSupplier;
		return this;
	}

	public RpgButton setTooltip(String title, String... lines) {
		this.tooltipSupplier = () -> RpgUi.tooltip(title, lines);
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean hovered = isHoveredOrFocused();

		int border = !active ? 0xFF273247 : hovered ? accentColor : RpgUi.BORDER;
		int fill = !active ? 0xFF111724 : hovered ? RpgUi.PANEL_HOVER : RpgUi.PANEL_RAISED;

		if (hovered && active && hoverEffect == HoverEffect.PULSE) {
			float pulse = RpgUi.pulse(2.0F);
			border = RpgUi.blend(accentColor, 0xFFFFFFFF, pulse * 0.45F);
			fill = RpgUi.blend(fill, RpgUi.PANEL_HOVER, 0.8F);
		}

		RpgUi.panel(graphics, getX(), getY(), getWidth(), getHeight(), fill, border);
		RpgUi.accent(graphics, getX(), getY(), getWidth(), !active ? RpgUi.TEXT_DIM : accentColor);

		int color = !active ? RpgUi.TEXT_DIM : hovered ? RpgUi.TEXT : RpgUi.TEXT_MUTED;
		Font font = Minecraft.getInstance().font;
		RpgScreenText.draw(graphics, font, getMessage().getString(), getX() + getWidth() / 2,
				getY() + (getHeight() - font.lineHeight) / 2 + 2, color, true, true);

		// Exibir tooltip
		if (hovered && tooltipSupplier != null) {
			List<Component> lines = tooltipSupplier.get();
			if (lines != null && !lines.isEmpty()) {
				List<FormattedCharSequence> wrapped = new ArrayList<>();
				for (Component line : lines) {
					wrapped.addAll(font.split(line, 240));
				}
				graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
			}
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && event.button() == 1) {
			if (clickSound != null) {
				RpgUiSounds.play(clickSound, clickSoundPitch);
			}
			if (action != null) {
				action.run();
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {
		defaultButtonNarrationText(narration);
	}
}
