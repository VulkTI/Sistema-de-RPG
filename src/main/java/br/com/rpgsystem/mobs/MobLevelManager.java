package br.com.rpgsystem.mobs;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.config.MobLevelConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;
import java.util.Optional;

/** Inicialização única, resolução de zona e aplicação idempotente de atributos. */
public final class MobLevelManager {
	private static final Identifier HEALTH_MODIFIER = RpgMod.id("mob_level_health");
	private static final Identifier DAMAGE_MODIFIER = RpgMod.id("mob_level_damage");
	private static final Identifier ARMOR_MODIFIER = RpgMod.id("mob_level_armor");

	private MobLevelManager() {}

	public static void initialize(Mob mob, ServerLevel level) {
		if (!(mob instanceof MobLevelAccess data)) return;
		Integer fixed = fixedLevel(mob.getType());
		boolean firstSpawn = !data.rpgsystem$hasMobLevel();
		boolean levelChanged = false;
		if (fixed != null && data.rpgsystem$getMobLevel() != fixed) {
			data.rpgsystem$setMobLevel(fixed);
			levelChanged = true;
		} else if (fixed == null && firstSpawn) {
			data.rpgsystem$setMobLevel(resolveInitialLevel(mob, level));
			levelChanged = true;
		}
		// O evento de carga e o tick podem chegar em ordens diferentes. O modificador
		// de vida é o marcador barato de que esta entidade já recebeu o escalonamento.
		if (levelChanged || !hasRpgModifiers(mob)) applyAttributes(mob, data.rpgsystem$getMobLevel(), firstSpawn);
	}

	private static boolean hasRpgModifiers(Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		return health != null && health.hasModifier(HEALTH_MODIFIER);
	}

	private static int resolveInitialLevel(Mob mob, ServerLevel level) {
		LevelRange override = entityRange(mob.getType());
		if (override != null) return override.random(mob.getRandom());
		BlockPos origin = mob.blockPosition();
		LevelRange structure = structureRange(level, origin);
		if (structure != null) return structure.random(mob.getRandom());
		Identifier biome = level.getBiome(origin).unwrapKey().map(key -> key.identifier()).orElse(null);
		LevelRange range = biome == null ? null : MobLevelConfig.biomeRanges().get(biome.toString());
		return (range == null ? MobLevelConfig.fallbackRange() : range).random(mob.getRandom());
	}

	private static LevelRange entityRange(EntityType<?> type) {
		return MobLevelConfig.entityOverride(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	public static Integer fixedLevel(EntityType<?> type) {
		LevelRange range = entityRange(type);
		return (range != null && range.min() == range.max()) ? range.min() : null;
	}

	public static Integer fixedLevelForId(Identifier id) {
		LevelRange range = MobLevelConfig.entityOverride(id);
		return (range != null && range.min() == range.max()) ? range.min() : null;
	}

	/** Consulta somente a peça que contém o ponto, sem busca de estruturas nas proximidades. */
	private static LevelRange structureRange(ServerLevel level, BlockPos pos) {
		for (MobLevelRanges.StructureRange candidate : MobLevelConfig.structureRanges()) {
			Optional<net.minecraft.world.level.levelgen.structure.Structure> structure = level.registryAccess()
					.lookupOrThrow(Registries.STRUCTURE).getOptional(candidate.id());
			if (structure.isEmpty()) {
				RpgMod.LOGGER.debug("Estrutura {} não existe neste registro; regra ignorada.", candidate.id());
				continue;
			}
			StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(candidate.id()));
			if (start.isValid()) return candidate.range();
		}
		return null;
	}

	public static void applyAttributes(Mob mob, int level) {
		applyAttributes(mob, level, false);
	}

	public static void applyAttributes(Mob mob, int level, boolean fullHealth) {
		level = Math.clamp(level, 1, 100);
		float previousHealth = mob.getHealth();
		apply(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER,
				MobLevelConfig.healthPerLevel() * (level - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER,
				MobLevelConfig.damagePerLevel() * (level - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		double armor = Math.min(MobLevelConfig.armorBonusCap(), MobLevelConfig.armorPerLevel() * (level - 1));
		apply(mob.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER, armor, AttributeModifier.Operation.ADD_VALUE);

		if (fullHealth) {
			mob.setHealth(mob.getMaxHealth());
		} else {
			// Nunca cura em uma reinicialização: somente impede vida atual acima do novo máximo.
			mob.setHealth(Math.min(previousHealth, mob.getMaxHealth()));
		}
	}

	private static void apply(AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		if (attribute == null) return;
		attribute.addOrUpdateTransientModifier(new AttributeModifier(id, Math.max(0D, amount), operation));
	}
}
