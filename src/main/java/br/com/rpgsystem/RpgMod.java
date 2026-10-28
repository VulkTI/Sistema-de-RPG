package br.com.rpgsystem;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.AventureiroClass;
import br.com.rpgsystem.affixes.AffixComponents;
import br.com.rpgsystem.affixes.AffixRepository;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.commands.RpgCommands;
import br.com.rpgsystem.events.RpgEvents;
import br.com.rpgsystem.network.RpgNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RpgMod implements ModInitializer {
	public static final String MOD_ID = "rpgsystem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RpgAttributes.register();
		AffixComponents.register();
		AffixRepository.register();
		ClassRegistry.register(new AventureiroClass());
		RpgNetworking.register();
		RpgCommands.register();
		RpgEvents.register();
		LOGGER.info("Sistema RPG carregado com {} classe(s).", ClassRegistry.all().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
