package br.com.rpgsystem.mobs;

/** Dados de nível anexados a entidades vivas pelo mixin. O valor zero significa não inicializado. */
public interface MobLevelAccess {
	int rpgsystem$getMobLevel();
	void rpgsystem$setMobLevel(int level);

	default boolean rpgsystem$hasMobLevel() {
		return rpgsystem$getMobLevel() >= 1;
	}

	default float rpgsystem$getAttackMultiplier() {
		return 1.0F;
	}

	default void rpgsystem$setAttackMultiplier(float multiplier) {}
}
