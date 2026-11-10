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
 * Widget interativo de ícone de item/habilidade com suporte a:
 * - Atualização de dados em tempo real (Item, Tooltip, Badge e Estado via Suppliers)
 * - Efeitos de hover estáticos ou animados (Pulso suave, Elevação/Float)
 * - Som de clique totalmente customizável
 * - Moldura com indicação de status (Normal, Desbloqueado, Pronto para Aprender, Bloqueado, Selecionado)
 */
public class RpgIconWidget extends AbstractWidget {

	public enum HoverEffect {
		NONE,
		STATIC,
		PULSE,
		FLOAT
	}

	public enum Status {
		NORMAL(RpgUi.BORDER, 0xFF141F2E),
		UNLOCKED(RpgUi.CYAN, 0xFF122C33),
		READY_TO_LEARN(RpgUi.RED, 0xFF2A1620),
		LOCKED(RpgUi.BORDER_SOFT, 0xFF0D131C),
		SELECTED(RpgUi.GOLD, 0xFF2B2616);

		public final int borderColor;
		public final int fillColor;

		Status(int borderColor, int fillColor) {
			this.borderColor = borderColor;
			this.fillColor = fillColor;
		}
	}

	private Supplier<ItemStack> itemSupplier;
	private Supplier<Status> statusSupplier = () -> Status.NORMAL;
	private Supplier<List<Component>> tooltipSupplier = null;
	private Supplier<Integer> badgeSupplier = null;
	private Supplier<Boolean> selectedSupplier = null;

	private HoverEffect hoverEffect = HoverEffect.STATIC;
	private SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();
	private float clickSoundPitch = 1.0F;

	private Consumer<RpgIconWidget> clickAction = null;
	private int customBorderColor = -1;
	private int customFillColor = -1;

	public RpgIconWidget(int x, int y, int size, Supplier<ItemStack> itemSupplier) {
		super(x, y, size, size, Component.empty());
		this.itemSupplier = itemSupplier;
	}

	public RpgIconWidget(int x, int y, int size, ItemStack staticItem) {
		this(x, y, size, () -> staticItem);
	}

	public RpgIconWidget setItemSupplier(Supplier<ItemStack> supplier) {
		this.itemSupplier = supplier;
		return this;
	}

	public RpgIconWidget setStatusSupplier(Supplier<Status> statusSupplier) {
		this.statusSupplier = statusSupplier;
		return this;
	}

	public RpgIconWidget setTooltip(Supplier<List<Component>> tooltipSupplier) {
		this.tooltipSupplier = tooltipSupplier;
		return this;
	}

	public RpgIconWidget setTooltip(String title, String... lines) {
		this.tooltipSupplier = () -> RpgUi.tooltip(title, lines);
		return this;
	}

	public RpgIconWidget setBadge(Supplier<Integer> badgeSupplier) {
		this.badgeSupplier = badgeSupplier;
		return this;
	}

	public RpgIconWidget setSelected(Supplier<Boolean> selectedSupplier) {
		this.selectedSupplier = selectedSupplier;
		return this;
	}

	public RpgIconWidget setHoverEffect(HoverEffect effect) {
		this.hoverEffect = effect;
		return this;
	}

	public RpgIconWidget setClickSound(SoundEvent sound) {
		this.clickSound = sound;
		return this;
	}

	public RpgIconWidget setClickSound(SoundEvent sound, float pitch) {
		this.clickSound = sound;
		this.clickSoundPitch = pitch;
		return this;
	}

	public RpgIconWidget onClick(Consumer<RpgIconWidget> action) {
		this.clickAction = action;
		return this;
	}

	public RpgIconWidget onClick(Runnable action) {
		this.clickAction = widget -> action.run();
		return this;
	}

	public RpgIconWidget setCustomColors(int border, int fill) {
		this.customBorderColor = border;
		this.customFillColor = fill;
		return this;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		boolean hovered = isHoveredOrFocused();
		boolean isSelected = selectedSupplier != null && selectedSupplier.get();
		Status status = statusSupplier != null ? statusSupplier.get() : Status.NORMAL;
		if (isSelected) status = Status.SELECTED;

		int border = customBorderColor != -1 ? customBorderColor : status.borderColor;
		int fill = customFillColor != -1 ? customFillColor : status.fillColor;

		int drawY = y;
		if (hovered && hoverEffect == HoverEffect.FLOAT) {
			drawY -= 1;
		}

		// Efeito Hover / Pulso
		if (hovered) {
			if (hoverEffect == HoverEffect.PULSE) {
				float pulse = RpgUi.pulse(2.0F);
				border = RpgUi.blend(border, 0xFFFFFFFF, pulse * 0.6F);
				fill = RpgUi.blend(fill, RpgUi.PANEL_HOVER, 0.7F);
			} else if (hoverEffect == HoverEffect.STATIC || hoverEffect == HoverEffect.FLOAT) {
				border = RpgUi.blend(border, RpgUi.BORDER_HIGHLIGHT, 0.8F);
				fill = RpgUi.PANEL_HOVER;
			}
		} else if (status == Status.READY_TO_LEARN) {
			// Pulso suave chamativo quando pronto para aprender
			float pulse = RpgUi.pulse(1.4F);
			border = RpgUi.blend(RpgUi.RED, RpgUi.GOLD, pulse);
		}

		// Desenhar painel da moldura
		RpgUi.panel(graphics, x, drawY, w, h, fill, border);

		// Renderizar item centralizado
		ItemStack stack = itemSupplier != null ? itemSupplier.get() : null;
		if (stack != null && !stack.isEmpty()) {
			int itemX = x + (w - 16) / 2;
			int itemY = drawY + (h - 16) / 2;
			graphics.item(stack, itemX, itemY);
		}

		// Badge / Contador no canto superior direito
		if (badgeSupplier != null) {
			int count = badgeSupplier.get();
			if (count > 0) {
				Font font = Minecraft.getInstance().font;
				String badgeStr = String.valueOf(count);
				int badgeW = font.width(badgeStr) + 4;
				int badgeX = x + w - badgeW;
				int badgeY = drawY - 2;
				graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 9, 0xEECC1122);
				graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFF7788);
				RpgScreenText.draw(graphics, font, badgeStr, badgeX + 2, badgeY + 1, RpgUi.TEXT, false, true);
			}
		}

		// Tooltip se hovered
		if (hovered && tooltipSupplier != null) {
			List<Component> lines = tooltipSupplier.get();
			if (lines != null && !lines.isEmpty()) {
				Font font = Minecraft.getInstance().font;
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
			if (clickAction != null) {
				clickAction.accept(this);
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narration) {}
}
