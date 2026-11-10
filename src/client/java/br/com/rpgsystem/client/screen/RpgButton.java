package br.com.rpgsystem.client.screen;

import net.minecraft.network.chat.Component;

/**
 * Compatibilidade legada para RpgButton. Redireciona para br.com.rpgsystem.client.ui.RpgButton.
 */
public class RpgButton extends br.com.rpgsystem.client.ui.RpgButton {
	public RpgButton(int x, int y, int width, int height, Component message, int accentColor, Runnable action) {
		super(x, y, width, height, message, accentColor, action);
	}
}
