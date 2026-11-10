package br.com.rpgsystem.skills;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.network.RpgNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Autoridade do servidor para XP, níveis e efeitos passivos universais. */
public final class UniversalSkillService {
	private static final Identifier COMBAT_DAMAGE = RpgMod.id("universal_combat_damage");
	private static final Identifier MINING_SPEED = RpgMod.id("universal_mining_speed");
	private static final Identifier KNOWLEDGE_LUCK = RpgMod.id("universal_knowledge_luck");
	private static final Identifier EXPLORATION_SPEED = RpgMod.id("universal_exploration_speed");
	private static final Identifier FARMING_HARVEST = RpgMod.id("universal_farming_harvest");

	private UniversalSkillService() {}

	public static void addXp(ServerPlayer player, UniversalSkill skill, double amount) {
		if (amount <= 0D) return;
		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile before = data.profile(player.getUUID());
		long oldXp = before.xpForUniversal(skill.id());
		long gained = Math.max(0L, Math.round(amount));
		long next = Math.min(UniversalProgression.maxXp(skill), oldXp > Long.MAX_VALUE - gained ? Long.MAX_VALUE : oldXp + gained);
		if (next == oldXp) return;
		int oldLevel = UniversalProgression.levelForXp(skill, oldXp);
		int newLevel = UniversalProgression.levelForXp(skill, next);
		data.update(player.getUUID(), profile -> profile.withUniversalXp(skill.id(), next));
		if (newLevel != oldLevel) refreshAttributes(player);
		RpgNetworking.sendSnapshot(player, false);
	}

	public static int level(ServerPlayer player, UniversalSkill skill) {
		return UniversalProgression.levelForXp(skill,
				RpgSavedData.get(player.level().getServer()).profile(player.getUUID()).xpForUniversal(skill.id()));
	}

	public static double bonus(ServerPlayer player, UniversalSkill skill) { return level(player, skill); }

	public static void refreshAttributes(ServerPlayer player) {
		set(player.getAttribute(Attributes.ATTACK_DAMAGE), COMBAT_DAMAGE,
				UniversalSkillConfig.bonus(UniversalSkill.COMBATE, "attack_damage_per_level", 0.5D) * level(player, UniversalSkill.COMBATE), AttributeModifier.Operation.ADD_VALUE);
		set(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), MINING_SPEED,
				UniversalSkillConfig.bonus(UniversalSkill.MINERACAO, "mining_speed_per_level", 0.01D) * level(player, UniversalSkill.MINERACAO), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player.getAttribute(Attributes.MOVEMENT_SPEED), EXPLORATION_SPEED,
				UniversalSkillConfig.bonus(UniversalSkill.EXPLORACAO, "movement_speed_per_level", 0.01D) * level(player, UniversalSkill.EXPLORACAO), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player.getAttribute(RpgAttributes.AFFIX_LUCK), KNOWLEDGE_LUCK,
				UniversalSkillConfig.bonus(UniversalSkill.CONHECIMENTO, "affix_luck_per_level", 0.005D) * level(player, UniversalSkill.CONHECIMENTO), AttributeModifier.Operation.ADD_VALUE);
		set(player.getAttribute(RpgAttributes.AFFIX_LUCK), FARMING_HARVEST,
				0.0D, AttributeModifier.Operation.ADD_VALUE);
	}

	public static double damageTakenMultiplier(ServerPlayer player) {
		return Math.max(0D, 1D - level(player, UniversalSkill.EXPLORACAO) * UniversalSkillConfig.bonus(UniversalSkill.EXPLORACAO, "damage_taken_reduction_per_level", 0.005D));
	}

	public static double harvestChance(ServerPlayer player) { return level(player, UniversalSkill.AGRICULTURA) * UniversalSkillConfig.bonus(UniversalSkill.AGRICULTURA, "harvest_extra_chance_per_level", 0.025D); }
	public static double anvilCostMultiplier(ServerPlayer player) { return Math.max(0D, 1D - level(player, UniversalSkill.CONHECIMENTO) * UniversalSkillConfig.bonus(UniversalSkill.CONHECIMENTO, "anvil_cost_reduction_per_level", 0.01D)); }
	public static double villagerTradeMultiplier(ServerPlayer player) { return Math.max(0D, 1D - level(player, UniversalSkill.COMERCIO) * UniversalSkillConfig.bonus(UniversalSkill.COMERCIO, "trade_cost_reduction_per_level", 0.005D)); }
	public static double breedingCooldownMultiplier(ServerPlayer player) { return Math.max(0D, 1D - level(player, UniversalSkill.AGRICULTURA) * UniversalSkillConfig.bonus(UniversalSkill.AGRICULTURA, "breeding_cooldown_reduction_per_level", 0.005D)); }

	private static void set(AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		if (attribute == null) return;
		attribute.removeModifier(id);
		if (amount != 0D) attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
	}
}
