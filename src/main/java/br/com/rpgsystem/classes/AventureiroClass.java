package br.com.rpgsystem.classes;

import br.com.rpgsystem.RpgMod;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/** Classe de demonstração: ganha XP ao derrotar criaturas hostis e possui Segundo Fôlego. */
public final class AventureiroClass extends RpgClass {
	private static final Identifier ID = RpgMod.id("aventureiro");
	private static final Identifier SECOND_WIND = RpgMod.id("segundo_folego");

	@Override public Identifier id() { return ID; }
	@Override public String name() { return "Aventureiro"; }
	@Override public String description() {
		return "Uma classe versátil que aprende enfrentando os perigos do mundo.";
	}
	@Override public List<Identifier> perkIds() { return List.of(SECOND_WIND); }
	@Override public int xpFor(RpgAction action) {
		return action == RpgAction.HOSTILE_MOB_KILL ? 10 : 0;
	}
	@Override public List<SkillDefinition> skillTree() {
		return List.of(
				new SkillDefinition("vigor", "Vigor", "Aumenta a vitalidade do aventureiro.", 1, SkillType.PASSIVE, List.of(), "bonus_health", 2.0),
				new SkillDefinition("golpe_firme", "Golpe Firme", "Fortalece seus ataques corpo a corpo.", 2, SkillType.PASSIVE, List.of("vigor"), "melee_damage", 1.0),
				new SkillDefinition("recuperacao", "Recuperação", "Recupera vida após derrotar uma criatura hostil.", 2, SkillType.PASSIVE, List.of("vigor"), "heal_on_hostile_kill", 4.0),
				new SkillDefinition("lenda_viva", "Lenda Viva", "O ápice da árvore: mais vida e força em combate.", 5, SkillType.PASSIVE, List.of("golpe_firme", "recuperacao"), "legendary_vitality", 1.0)
		);
	}
}
