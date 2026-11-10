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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Card temático para exibir entidades de RPG (Classes, Habilidades Universais, Status).
 * Suporta ícone integrado, barra de progresso, tags, tooltips e clique interativo.
 */
public class RpgCardWidget extends AbstractWidget {

	private Supplier<String> titleSupplier;
	private Supplier<String> subtitleSupplier;
	private Supplier<ItemStack> iconSupplier;
	private Supplier<List<Component>> tooltipSupplier;
	private Supplier<Boolean> highlightedSupplier;
	private Supplier<Integer> pointsSupplier;

	private int accentColor = RpgUi.BLUE;
	private int borderColor = RpgUi.BORDER;
	private int fillColor = RpgUi.PANEL_MUTED;
	private SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();
	private Consumer<RpgCardWidget> clickAction = null;

	// Barra de progresso opcional embutida no card
	private Supplier<Long> currentXpSupplier = null;
	private Supplier<Long> nextXpSupplier = null;

	public RpgCardWidget(int x, int y, int width, int height, String title) {
		super(x, y, width, height, Component.literal(title));
		this.titleSupplier = () -> title;
	}

	public RpgCardWidget setTitle(Supplier<String> titleSupplier) {
		this.titleSupplier = titleSupplier;
		return this;
	}

	public RpgCardWidget setSubtitle(Supplier<String> subtitleSupplier) {
		this.subtitleSupplier = subtitleSupplier;
		return this;
	}

	public RpgCardWidget setIcon(Supplier<ItemStack> iconSupplier) {
		this.iconSupplier = iconSupplier;
		return this;
	}

	public RpgCardWidget setIcon(ItemStack staticStack) {
		this.iconSupplier = () -> staticStack;
		return this;
	}

	public RpgCardWidget setXpProgress(Supplier<Long> currentXp, Supplier<Long> nextXp) {
		this.currentXpSupplier = currentXp;
		this.nextXpSupplier = nextXp;
		return this;
	}

	public RpgCardWidget setAccentColor(int color) {
		this.accentColor = color;
		return this;
	}

	public RpgCardWidget setBorderColor(int color) {
		this.borderColor = color;
		return this;
	}

	public RpgCardWidget setFillColor(int color) {
		this.fillColor = color;
		return this;
	}

	public RpgCardWidget setHighlighted(Supplier<Boolean> highlightedSupplier) {
		this.highlightedSupplier = highlightedSupplier;
		return this;
	}

	public RpgCardWidget setPoints(Supplier<Integer> pointsSupplier) {
		this.pointsSupplier = pointsSupplier;
		return this;
	}

	public RpgCardWidget setTooltip(Supplier<List<Component>> tooltipSupplier) {
		this.tooltipSupplier = tooltipSupplier;
		return this;
	}

	public RpgCardWidget setTooltip(String title, String... lines) {
		this.tooltipSupplier = () -> RpgUi.tooltip(title, lines);
		return this;
	}

	public RpgCardWidget setClickSound(SoundEvent sound) {
		this.clickSound = sound;
		return this;
	}

	public RpgCardWidget onClick(Consumer<RpgCardWidget> action) {
		this.clickAction = action;
		return this;
	}

	public RpgCardWidget onClick(Runnable action) {
		this.clickAction = card -> action.run();
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		boolean hovered = isHoveredOrFocused();
		boolean highlighted = highlightedSupplier != null && highlightedSupplier.get();
		int points = pointsSupplier != null ? pointsSupplier.get() : 0;

		int currentBorder = borderColor;
		if (highlighted) {
			float pulse = RpgUi.pulse(1.8F);
			currentBorder = RpgUi.blend(RpgUi.RED, RpgUi.GOLD, pulse);
		} else if (points > 0) {
			currentBorder = RpgUi.GOLD;
		} else if (hovered && clickAction != null) {
			currentBorder = RpgUi.blend(borderColor, RpgUi.BORDER_HIGHLIGHT, 0.7F);
		}

		int currentFill = hovered && clickAction != null ? RpgUi.PANEL_HOVER : fillColor;

		// Moldura do card e acento
		RpgUi.panel(graphics, x, y, w, h, currentFill, currentBorder);
		RpgUi.accent(graphics, x, y, w, accentColor);

		Font font = Minecraft.getInstance().font;
		int contentLeft = x + 6;

		// Ícone se configurado
		if (iconSupplier != null) {
			int iconBox = Math.min(22, h - 8);
			int iconY = y + (h - iconBox) / 2;
			RpgUi.panel(graphics, x + 4, iconY, iconBox, iconBox, 0xFF141F2E, RpgUi.BORDER_SOFT);
			ItemStack stack = iconSupplier.get();
			if (stack != null && !stack.isEmpty()) {
				graphics.item(stack, x + 4 + (iconBox - 16) / 2, iconY + (iconBox - 16) / 2);
			}
			contentLeft = x + 4 + iconBox + 5;
		}

		int rightBound = x + w - 6;

		// Título (Linha 1)
		String title = titleSupplier != null ? titleSupplier.get() : "";
		if (title != null && !title.isBlank()) {
			RpgScreenText.draw(graphics, font, title, contentLeft, y + 4, RpgUi.TEXT, false, true);
		}

		// Subtítulo / Nível e XP (Linha 2)
		int line2Y = y + 4 + font.lineHeight + 2;
		String subtitle = subtitleSupplier != null ? subtitleSupplier.get() : null;
		if (subtitle != null && !subtitle.isBlank()) {
			RpgScreenText.draw(graphics, font, subtitle, contentLeft, line2Y, accentColor, false, true);
		}

		// Barra de XP se configurada (Linha 3)
		if (currentXpSupplier != null && nextXpSupplier != null) {
			long cur = currentXpSupplier.get();
			long next = nextXpSupplier.get();
			String xpText = RpgUi.number(cur) + "/" + RpgUi.number(next);
			RpgScreenText.draw(graphics, font, xpText, rightBound - font.width(xpText), line2Y, RpgUi.TEXT_MUTED, false, true);

			int barY = line2Y + font.lineHeight + 2;
			int barW = rightBound - contentLeft;
			if (barW > 10 && barY + 4 <= y + h) {
				RpgUi.bar(graphics, contentLeft, barY, barW, Math.min(5, y + h - barY - 2), cur, next, accentColor);
			}
		}

		// Tooltip se hovered
		if (hovered && tooltipSupplier != null) {
			List<Component> lines = tooltipSupplier.get();
			if (lines != null && !lines.isEmpty()) {
				List<FormattedCharSequence> wrapped = new ArrayList<>();
				for (Component line : lines) {
					wrapped.addAll(font.split(line, 250));
				}
				graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
			}
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && event.button() == 1 && clickAction != null) {
			if (clickSound != null) {
				RpgUiSounds.play(clickSound, 1.0F);
			}
			clickAction.accept(this);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {}
}
