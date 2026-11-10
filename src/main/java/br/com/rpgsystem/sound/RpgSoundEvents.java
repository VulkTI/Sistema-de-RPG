package br.com.rpgsystem.sound;

import br.com.rpgsystem.RpgMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class RpgSoundEvents {
	public static final SoundEvent UI_CLICK = register("ui.click");
	public static final SoundEvent UI_SKILL_LEARN = register("ui.skill_learn");
	public static final SoundEvent UI_TAB_SWITCH = register("ui.tab_switch");
	public static final SoundEvent UI_OPEN = register("ui.open");
	public static final SoundEvent UI_CLOSE = register("ui.close");

	private RpgSoundEvents() {}

	private static SoundEvent register(String path) {
		Identifier id = RpgMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {
		// Inicializa a classe e registra os SoundEvents
	}
}
