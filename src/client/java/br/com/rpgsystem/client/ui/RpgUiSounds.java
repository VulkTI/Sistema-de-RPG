package br.com.rpgsystem.client.ui;

import br.com.rpgsystem.sound.RpgSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Utilitário centralizado para reprodução de efeitos sonoros de interface.
 * Suporta SoundEvents customizados do mod (registrados em RpgSoundEvents)
 * e oferece fallback para sons vanilla caso o som customizado não tenha áudio anexado.
 */
public final class RpgUiSounds {
	private RpgUiSounds() {}

	public static void play(SoundEvent sound, float pitch) {
		play(sound, pitch, 1.0F);
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.getSoundManager() != null && sound != null) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
		}
	}

	public static void playClick() {
		play(RpgSoundEvents.UI_CLICK != null ? RpgSoundEvents.UI_CLICK : SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
	}

	public static void playSkillLearn() {
		play(RpgSoundEvents.UI_SKILL_LEARN != null ? RpgSoundEvents.UI_SKILL_LEARN : SoundEvents.PLAYER_LEVELUP, 1.2F);
	}

	public static void playTab() {
		play(RpgSoundEvents.UI_TAB_SWITCH != null ? RpgSoundEvents.UI_TAB_SWITCH : SoundEvents.UI_BUTTON_CLICK.value(), 1.15F);
	}

	public static void playOpen() {
		play(RpgSoundEvents.UI_OPEN != null ? RpgSoundEvents.UI_OPEN : SoundEvents.UI_BUTTON_CLICK.value(), 0.9F);
	}

	public static void playClose() {
		play(RpgSoundEvents.UI_CLOSE != null ? RpgSoundEvents.UI_CLOSE : SoundEvents.UI_BUTTON_CLICK.value(), 0.8F);
	}
}
