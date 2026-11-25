package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class OfficialDungeonPromptScreen extends RpgBaseScreen {
	private final String officialName;

	public OfficialDungeonPromptScreen(String officialName) {
		super(Component.literal("Atenção: Dungeon Oficial"), "Modificação de conteúdo oficial");
		this.officialName = officialName;
		this.windowAccentColor = RpgUi.GOLD;
		this.showCloseButton = false;
	}

	@Override
	protected void layoutWindow() {
		panelWidth = 280;
		panelHeight = 150;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	protected void init() {
		super.init();

		int btnW = 90;
		int btnH = 20;
		int btnY = panelY + panelHeight - btnH - 12;

		// Botão Confirmar
		addRenderableWidget(new RpgButton(panelX + 30, btnY, btnW, btnH,
				Component.literal("Confirmar"), RpgUi.GREEN, () -> {
			ClientPlayNetworking.send(new DungeonPayloads.ConfirmOfficialDuplicate(officialName));
			onClose();
		}));

		// Botão Cancelar
		addRenderableWidget(new RpgButton(panelX + panelWidth - 30 - btnW, btnY, btnW, btnH,
				Component.literal("Cancelar"), RpgUi.RED, this::onClose));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int textX = panelX + 16;
		int textY = panelY + headerHeight + 12;

		RpgScreenText.draw(graphics, font, "§eEsta é uma dungeon oficial embutida no mod.", textX, textY, 0xFFFFFFFF, false, true);
		RpgScreenText.draw(graphics, font, "§7Ao salvar, uma cópia será duplicada para o", textX, textY + font.lineHeight + 3, 0xFFAAAAAA, false, true);
		RpgScreenText.draw(graphics, font, "§7mundo e a oficial original será desabilitada.", textX, textY + (font.lineHeight + 3) * 2, 0xFFAAAAAA, false, true);
		RpgScreenText.draw(graphics, font, "§fDeseja continuar?", textX, textY + (font.lineHeight + 3) * 3 + 4, 0xFFFFFFFF, false, true);
	}
}
