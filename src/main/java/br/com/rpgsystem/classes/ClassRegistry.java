package br.com.rpgsystem.classes;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registro único consumido automaticamente pelos comandos, menu e HUD. */
public final class ClassRegistry {
	private static final Map<Identifier, RpgClass> CLASSES = new LinkedHashMap<>();

	private ClassRegistry() {}

	public static void register(RpgClass rpgClass) {
		if (CLASSES.putIfAbsent(rpgClass.id(), rpgClass) != null) {
			throw new IllegalArgumentException("Classe duplicada: " + rpgClass.id());
		}
	}

	public static Collection<RpgClass> all() {
		return List.copyOf(CLASSES.values());
	}

	public static Optional<RpgClass> byId(Identifier id) {
		return Optional.ofNullable(CLASSES.get(id));
	}

	public static Optional<RpgClass> byCommandName(String name) {
		return CLASSES.values().stream().filter(rpgClass -> rpgClass.commandName().equalsIgnoreCase(name)).findFirst();
	}
}
