package br.com.rpgsystem.network;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.RpgClass;
import br.com.rpgsystem.classes.SkillDefinition;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.progression.ProgressionMath;
import br.com.rpgsystem.mining.MineradorEffects;
import br.com.rpgsystem.skills.UniversalProgression;
import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillTrees;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class RpgNetworking {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private RpgNetworking() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(RpgPayloads.Snapshot.TYPE, RpgPayloads.Snapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RpgPayloads.SelectClass.TYPE, RpgPayloads.SelectClass.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RpgPayloads.LearnSkill.TYPE, RpgPayloads.LearnSkill.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RpgPayloads.LearnUniversalSkill.TYPE, RpgPayloads.LearnUniversalSkill.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RpgPayloads.SelectClass.TYPE, (payload, context) ->
				context.server().execute(() -> selectClass(context.player(), payload.classId())));
		ServerPlayNetworking.registerGlobalReceiver(RpgPayloads.LearnSkill.TYPE, (payload, context) ->
				context.server().execute(() -> learnSkill(context.player(), payload.skillId())));
		ServerPlayNetworking.registerGlobalReceiver(RpgPayloads.LearnUniversalSkill.TYPE, (payload, context) ->
				context.server().execute(() -> learnUniversalSkill(context.player(), payload.skillId(), payload.nodeId())));
	}

	public static void sendSnapshot(ServerPlayer player, boolean openMenu) {
		JsonObject snapshot = makeSnapshot(player, openMenu);
		ServerPlayNetworking.send(player, new RpgPayloads.Snapshot(GSON.toJson(snapshot)));
	}

	private static JsonObject makeSnapshot(ServerPlayer player, boolean openMenu) {
		RpgSavedData.Profile profile = RpgSavedData.get(player.level().getServer()).profile(player.getUUID());
		String selectedId = profile.selectedClassId();
		RpgClass selected = parseClass(selectedId);
		long classXp = selected == null ? 0L : profile.xpForClass(selectedId);
		int playerLevel = ProgressionMath.levelForXp(profile.playerXp());
		int classLevel = ProgressionMath.levelForXp(classXp);

		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("openMenu", openMenu);
		snapshot.addProperty("selectedClassId", selectedId);
		snapshot.addProperty("selectedClassName", selected == null ? "Sem classe" : selected.name());
		snapshot.addProperty("playerLevel", playerLevel);
		snapshot.addProperty("playerXpTotal", profile.playerXp());
		snapshot.addProperty("playerXpCurrent", ProgressionMath.xpIntoLevel(profile.playerXp()));
		snapshot.addProperty("playerXpNext", ProgressionMath.xpForNextLevel(playerLevel));
		snapshot.addProperty("classLevel", classLevel);
		snapshot.addProperty("classXpTotal", classXp);
		snapshot.addProperty("classXpCurrent", ProgressionMath.xpIntoLevel(classXp));
		snapshot.addProperty("classXpNext", ProgressionMath.xpForNextLevel(classLevel));
		JsonArray classes = new JsonArray();
		for (RpgClass rpgClass : ClassRegistry.all()) {
			JsonObject option = new JsonObject();
			option.addProperty("id", rpgClass.id().toString());
			option.addProperty("name", rpgClass.name());
			option.addProperty("description", rpgClass.description());
			JsonArray perks = new JsonArray();
			rpgClass.perks().forEach(perk -> {
				JsonObject entry = new JsonObject();
				entry.addProperty("name", perk.name());
				entry.addProperty("description", perk.description());
				perks.add(entry);
			});
			option.add("perks", perks);
			classes.add(option);
		}
		snapshot.add("classes", classes);
		snapshot.addProperty("skillPoints", selected == null ? 0 : profile.availableSkillPoints(selectedId));
		snapshot.addProperty("classHasLearnableSkill", selected != null && selected.skillTree().stream().anyMatch(skill ->
				!profile.hasSkill(selectedId, skill.id()) && profile.availableSkillPoints(selectedId) >= skill.cost()
						&& skill.dependencies().stream().allMatch(dependency -> profile.hasSkill(selectedId, dependency))));
		JsonArray skillTree = new JsonArray();
		if (selected != null) for (SkillDefinition skill : selected.skillTree()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("id", skill.id());
			entry.addProperty("name", skill.name());
			entry.addProperty("description", skill.description());
			entry.addProperty("cost", skill.cost());
			entry.addProperty("type", skill.type().name());
			entry.addProperty("effect", skill.effect());
			entry.addProperty("amount", skill.amount());
			JsonArray dependencies = new JsonArray();
			skill.dependencies().forEach(dependencies::add);
			entry.add("dependencies", dependencies);
			entry.addProperty("unlocked", profile.hasSkill(selectedId, skill.id()));
			skillTree.add(entry);
		}
		snapshot.add("skillTree", skillTree);
		JsonArray universalSkills = new JsonArray();
		JsonObject universalTrees = new JsonObject();
		for (UniversalSkill skill : UniversalSkill.values()) {
			long xp = profile.xpForUniversal(skill.id());
			int level = UniversalProgression.levelForXp(skill, xp);
			JsonObject entry = new JsonObject();
			String treeId = "universal:" + skill.id();
			List<SkillDefinition> tree = UniversalSkillTrees.tree(skill);
			int availablePoints = profile.availableSkillPoints(treeId);
			entry.addProperty("id", skill.id());
			entry.addProperty("name", skill.displayName());
			entry.addProperty("description", universalDescription(skill));
			entry.addProperty("level", level);
			entry.addProperty("xpTotal", xp);
			entry.addProperty("xpCurrent", UniversalProgression.xpIntoLevel(skill, xp));
			entry.addProperty("xpNext", UniversalProgression.xpForNextLevel(skill, level));
			entry.addProperty("skillPoints", availablePoints);
			entry.addProperty("bonusText", universalBonusText(skill, level));
			entry.addProperty("hasLearnableSkill", tree.stream().anyMatch(node ->
					!profile.hasSkill(treeId, node.id()) && availablePoints >= node.cost()
							&& node.dependencies().stream().allMatch(dependency -> profile.hasSkill(treeId, dependency))));
			universalSkills.add(entry);
			JsonArray nodes = new JsonArray();
			tree.forEach(node -> {
				JsonObject nodeJson = new JsonObject();
				nodeJson.addProperty("id", node.id());
				nodeJson.addProperty("name", node.name());
				nodeJson.addProperty("description", node.description());
				nodeJson.addProperty("cost", node.cost());
				nodeJson.addProperty("type", node.type().name());
				nodeJson.addProperty("effect", node.effect());
				nodeJson.addProperty("amount", node.amount());
				JsonArray deps = new JsonArray(); node.dependencies().forEach(deps::add); nodeJson.add("dependencies", deps);
				nodeJson.addProperty("unlocked", profile.hasSkill(treeId, node.id()));
				nodes.add(nodeJson);
			});
			universalTrees.add(skill.id(), nodes);
		}
		snapshot.add("universalSkills", universalSkills);
		snapshot.add("universalSkillTrees", universalTrees);
		return snapshot;
	}

	private static String universalBonusText(UniversalSkill skill, int level) {
		if (level <= 0) return "Nenhum bônus ativo.";
		return switch (skill) {
			case COMBATE -> String.format(java.util.Locale.ROOT, "+%.1f dano de ataque por nível", UniversalSkillConfig.bonus(skill, "attack_damage_per_level", 0.5D) * level);
			case MINERACAO -> String.format(java.util.Locale.ROOT, "+%.1f%% velocidade de mineração", UniversalSkillConfig.bonus(skill, "mining_speed_per_level", 0.01D) * level * 100.0D);
			case AGRICULTURA -> String.format(java.util.Locale.ROOT, "+%.1f%% chance de colheita extra  •  -%.1f%% cooldown de reprodução",
					UniversalSkillConfig.bonus(skill, "harvest_extra_chance_per_level", 0.025D) * level * 100.0D,
					UniversalSkillConfig.bonus(skill, "breeding_cooldown_reduction_per_level", 0.005D) * level * 100.0D);
			case CONHECIMENTO -> String.format(java.util.Locale.ROOT, "-%.1f%% custo de bigorna  •  +%.1f%% sorte de afixos",
					UniversalSkillConfig.bonus(skill, "anvil_cost_reduction_per_level", 0.01D) * level * 100.0D,
					UniversalSkillConfig.bonus(skill, "affix_luck_per_level", 0.005D) * level * 100.0D);
			case EXPLORACAO -> String.format(java.util.Locale.ROOT, "+%.1f%% velocidade  •  -%.1f%% dano recebido",
					UniversalSkillConfig.bonus(skill, "movement_speed_per_level", 0.01D) * level * 100.0D,
					UniversalSkillConfig.bonus(skill, "damage_taken_reduction_per_level", 0.005D) * level * 100.0D);
			case COMERCIO -> String.format(java.util.Locale.ROOT, "-%.1f%% custo nas trocas", UniversalSkillConfig.bonus(skill, "trade_cost_reduction_per_level", 0.005D) * level * 100.0D);
		};
	}

	private static String universalDescription(UniversalSkill skill) {
		return switch (skill) {
			case COMBATE -> "Aprimora seu desempenho em combate e aumenta o dano causado conforme você derrota criaturas.";
			case MINERACAO -> "Melhora a velocidade e o rendimento da mineração. Minérios mais valiosos concedem mais experiência.";
			case AGRICULTURA -> "Aprimora a produção rural: colheitas maduras, alimentação de filhotes e reprodução de animais geram experiência.";
			case CONHECIMENTO -> "Representa seu domínio das técnicas de criação. Fabricar itens, usar bigornas, encantar e preparar poções gera experiência.";
			case EXPLORACAO -> "Recompensa quem descobre o mundo. Explorar estruturas, abrir baús inéditos e dormir em novas jornadas aumenta esta habilidade.";
			case COMERCIO -> "Aprimora suas negociações. Trocar com aldeões, evoluí-los e negociar com piglins aumenta sua experiência comercial.";
		};
	}

	private static void selectClass(ServerPlayer player, String classId) {
		Identifier id = Identifier.tryParse(classId);
		RpgClass rpgClass = id == null ? null : ClassRegistry.byId(id).orElse(null);
		if (rpgClass == null) {
			player.sendSystemMessage(Component.literal("Essa classe não existe."));
			return;
		}
		RpgSavedData.get(player.level().getServer()).update(player.getUUID(), profile -> profile.withClass(id.toString()));
		MineradorEffects.refreshMiningSpeed(player);
		player.sendSystemMessage(Component.literal("Classe definida: " + rpgClass.name() + "."));
		sendSnapshot(player, false);
	}

	private static RpgClass parseClass(String id) {
		if (id == null || id.isBlank()) return null;
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : ClassRegistry.byId(identifier).orElse(null);
	}

	private static void learnSkill(ServerPlayer player, String skillId) {
		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		RpgClass selected = parseClass(profile.selectedClassId());
		if (selected == null) return;
		SkillDefinition skill = selected.skillTree().stream().filter(candidate -> candidate.id().equals(skillId)).findFirst().orElse(null);
		if (skill == null || profile.hasSkill(selected.id().toString(), skillId)
				|| profile.availableSkillPoints(selected.id().toString()) < skill.cost()
				|| !skill.dependencies().stream().allMatch(dependency -> profile.hasSkill(selected.id().toString(), dependency))) {
			player.sendSystemMessage(Component.literal("Ainda não é possível desbloquear essa habilidade."));
			return;
		}
		data.update(player.getUUID(), current -> current.unlockSkill(selected.id().toString(), skillId, skill.cost()));
		player.sendSystemMessage(Component.literal("Habilidade desbloqueada: " + skill.name() + "."));
		sendSnapshot(player, false);
	}

	private static void learnUniversalSkill(ServerPlayer player, String skillId, String nodeId) {
		UniversalSkill skill = UniversalSkill.byId(skillId);
		if (skill == null) return;
		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		SkillDefinition definition = UniversalSkillTrees.tree(skill).stream().filter(node -> node.id().equals(nodeId)).findFirst().orElse(null);
		String treeId = "universal:" + skill.id();
		if (definition == null || profile.hasSkill(treeId, nodeId)
				|| profile.availableSkillPoints(treeId) < definition.cost()
				|| !definition.dependencies().stream().allMatch(dependency -> profile.hasSkill(treeId, dependency))) return;
		data.update(player.getUUID(), current -> current.unlockSkill(treeId, nodeId, definition.cost()));
		sendSnapshot(player, false);
	}
}
