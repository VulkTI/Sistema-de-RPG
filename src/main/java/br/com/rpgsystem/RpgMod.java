package br.com.rpgsystem;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.FerreiroClass;
import br.com.rpgsystem.classes.MineradorClass;
import br.com.rpgsystem.affixes.AffixComponents;
import br.com.rpgsystem.affixes.AffixRepository;
import br.com.rpgsystem.affixes.AffixItemGroups;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.commands.RpgCommands;
import br.com.rpgsystem.events.RpgEvents;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.config.XpConfig;
import br.com.rpgsystem.config.MobLevelConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RpgMod implements ModInitializer {
	public static final String MOD_ID = "rpgsystem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		XpConfig.load();
		MobLevelConfig.load();
		RpgAttributes.register();
		AffixComponents.register();
		AffixRepository.register();
		AffixItemGroups.register();
		ClassRegistry.register(new FerreiroClass());
		ClassRegistry.register(new MineradorClass());
		RpgNetworking.register();
		RpgCommands.register();
		RpgEvents.register();
		LOGGER.info("Sistema RPG carregado com {} classe(s).", ClassRegistry.all().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
