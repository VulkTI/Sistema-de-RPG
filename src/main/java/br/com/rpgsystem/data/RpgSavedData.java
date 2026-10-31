package br.com.rpgsystem.data;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.progression.ProgressionMath;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Um único SavedData por mundo armazena os perfis RPG, indexados pelo UUID do jogador. */
public final class RpgSavedData extends SavedData {
	private static final Codec<Map<String, Profile>> PROFILES_CODEC = Codec.unboundedMap(Codec.STRING, Profile.CODEC);
	private static final Codec<RpgSavedData> CODEC = PROFILES_CODEC.xmap(RpgSavedData::new, data -> data.profiles);
	private static final SavedDataType<RpgSavedData> TYPE = new SavedDataType<>(
			RpgMod.id("player_profiles"), RpgSavedData::new, CODEC, null);

	private final Map<String, Profile> profiles;

	public RpgSavedData() {
		this(new HashMap<>());
	}

	private RpgSavedData(Map<String, Profile> profiles) {
		this.profiles = new HashMap<>(profiles);
	}

	public static RpgSavedData get(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
		if (overworld == null) throw new IllegalStateException("O mundo principal ainda não foi carregado.");
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public Profile profile(UUID playerId) {
		return profiles.getOrDefault(playerId.toString(), Profile.DEFAULT);
	}

	public Profile update(UUID playerId, UnaryOperator<Profile> change) {
		Profile updated = change.apply(profile(playerId));
		profiles.put(playerId.toString(), updated);
		setDirty();
		return updated;
	}

	public record Profile(String selectedClassId, long playerXp, Map<String, Long> classXp,
			Map<String, Integer> skillPoints, Map<String, java.util.List<String>> unlockedSkills,
			Map<String, Integer> classHighestLevels, Map<String, Integer> actionCounters,
			Map<String, Long> universalSkillXp, Map<String, java.util.List<String>> exploredStructures) {
		private static final Profile DEFAULT = new Profile("", 0L, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		private static final Codec<Profile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.optionalFieldOf("selected_class", "").forGetter(Profile::selectedClassId),
				Codec.LONG.optionalFieldOf("player_xp", 0L).forGetter(Profile::playerXp),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("class_xp", Map.of()).forGetter(Profile::classXp),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("skill_points", Map.of()).forGetter(Profile::skillPoints),
				Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("unlocked_skills", Map.of()).forGetter(Profile::unlockedSkills),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("class_highest_levels", Map.of()).forGetter(Profile::classHighestLevels),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("action_counters", Map.of()).forGetter(Profile::actionCounters),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("universal_skill_xp", Map.of()).forGetter(Profile::universalSkillXp),
				Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("explored_structures", Map.of()).forGetter(Profile::exploredStructures)
		).apply(instance, Profile::new));

		public Profile {
			selectedClassId = selectedClassId == null ? "" : selectedClassId;
			playerXp = Math.max(0L, playerXp);
			classXp = Map.copyOf(classXp);
			skillPoints = Map.copyOf(skillPoints);
			unlockedSkills = Map.copyOf(unlockedSkills);
			classHighestLevels = Map.copyOf(classHighestLevels);
			actionCounters = Map.copyOf(actionCounters);
			universalSkillXp = Map.copyOf(universalSkillXp);
			exploredStructures = Map.copyOf(exploredStructures);
		}

		public long xpForClass(String classId) {
			return Math.max(0L, classXp.getOrDefault(classId, 0L));
		}

		public Profile withClass(String classId) {
			return copy(classId, playerXp, classXp, skillPoints, unlockedSkills, classHighestLevels, actionCounters);
		}

		public Profile withoutClass() {
			return copy("", playerXp, classXp, skillPoints, unlockedSkills, classHighestLevels, actionCounters);
		}

		public Profile withPlayerXp(long xp) {
			return copy(selectedClassId, xp, classXp, skillPoints, unlockedSkills, classHighestLevels, actionCounters);
		}

		public Profile withClassXp(String classId, long xp) {
			Map<String, Long> changed = new HashMap<>(classXp);
			changed.put(classId, Math.max(0L, xp));
			Map<String, Integer> highs = new HashMap<>(classHighestLevels);
			int oldHigh = highs.getOrDefault(classId, 1);
			int newHigh = Math.max(oldHigh, ProgressionMath.levelForXp(xp));
			highs.put(classId, newHigh);
			Map<String, Integer> points = new HashMap<>(skillPoints);
			if (newHigh > oldHigh) points.merge(classId, (newHigh - oldHigh) * 2, Integer::sum);
			return copy(selectedClassId, playerXp, changed, points, unlockedSkills, highs, actionCounters);
		}

		public int availableSkillPoints(String classId) { return Math.max(0, skillPoints.getOrDefault(classId, 0)); }
		public boolean hasSkill(String classId, String skillId) {
			return unlockedSkills.getOrDefault(classId, java.util.List.of()).contains(skillId);
		}
		public Profile unlockSkill(String classId, String skillId, int cost) {
			Map<String, Integer> points = new HashMap<>(skillPoints);
			points.put(classId, Math.max(0, availableSkillPoints(classId) - cost));
			Map<String, java.util.List<String>> skills = new HashMap<>(unlockedSkills);
			java.util.ArrayList<String> unlocked = new java.util.ArrayList<>(skills.getOrDefault(classId, java.util.List.of()));
			if (!unlocked.contains(skillId)) unlocked.add(skillId);
			skills.put(classId, java.util.List.copyOf(unlocked));
			return copy(selectedClassId, playerXp, classXp, points, skills, classHighestLevels, actionCounters);
		}

		public int actionCounter(String key) { return Math.max(0, actionCounters.getOrDefault(key, 0)); }
		public Profile withActionCounter(String key, int value) {
			Map<String, Integer> changed = new HashMap<>(actionCounters);
			changed.put(key, Math.max(0, value));
			return copy(selectedClassId, playerXp, classXp, skillPoints, unlockedSkills, classHighestLevels, changed);
		}

		private Profile copy(String selected, long xp, Map<String, Long> classes, Map<String, Integer> points,
				Map<String, java.util.List<String>> skills, Map<String, Integer> highs, Map<String, Integer> counters) {
			return new Profile(selected, xp, classes, points, skills, highs, counters, universalSkillXp, exploredStructures);
		}

		public long xpForUniversal(String skillId) { return Math.max(0L, universalSkillXp.getOrDefault(skillId, 0L)); }
		public Profile withUniversalXp(String skillId, long xp) {
			Map<String, Long> changed = new HashMap<>(universalSkillXp);
			changed.put(skillId, Math.max(0L, xp));
			return new Profile(selectedClassId, playerXp, classXp, skillPoints, unlockedSkills, classHighestLevels, actionCounters, changed, exploredStructures);
		}
		public boolean hasExplored(String skillId, String key) { return exploredStructures.getOrDefault(skillId, java.util.List.of()).contains(key); }
		public Profile markExplored(String skillId, String key) {
			Map<String, java.util.List<String>> changed = new HashMap<>(exploredStructures);
			java.util.ArrayList<String> values = new java.util.ArrayList<>(changed.getOrDefault(skillId, java.util.List.of()));
			if (!values.contains(key)) values.add(key);
			changed.put(skillId, java.util.List.copyOf(values));
			return new Profile(selectedClassId, playerXp, classXp, skillPoints, unlockedSkills, classHighestLevels, actionCounters, universalSkillXp, changed);
		}

		public Profile reset() {
			return DEFAULT;
		}
	}
}
