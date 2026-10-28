package br.com.rpgsystem.classes;

import br.com.rpgsystem.perks.PerkDefinition;
import br.com.rpgsystem.perks.PerkRepository;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Classe-mãe: dados, perks e recompensas de XP são definidos por cada classe. */
public abstract class RpgClass {
	public abstract Identifier id();
	public abstract String name();
	public abstract String description();
	public abstract List<Identifier> perkIds();
	public abstract int xpFor(RpgAction action);
	public List<SkillDefinition> skillTree() { return List.of(); }

	public final List<PerkDefinition> perks() {
		return perkIds().stream().map(PerkRepository::load).toList();
	}

	public final boolean hasPerk(String perkPath) {
		return perkIds().stream().anyMatch(perk -> perk.getPath().equals(perkPath));
	}

	public final String commandName() {
		return id().getPath();
	}
}
