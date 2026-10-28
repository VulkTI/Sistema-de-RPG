package br.com.rpgsystem.events;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.RpgAction;
import br.com.rpgsystem.classes.RpgClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.perks.PerkDefinition;
import br.com.rpgsystem.progression.ProgressionMath;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

public final class RpgEvents {
	private static final int PLAYER_XP_PER_HOSTILE_KILL = 8;

	private RpgEvents() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			RpgSavedData.Profile profile = RpgSavedData.get(server).profile(player.getUUID());
			RpgNetworking.sendSnapshot(player, profile.selectedClassId().isBlank());
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity.getType().getCategory() != MobCategory.MONSTER) return;
			if (!(damageSource.getEntity() instanceof ServerPlayer player)) return;

			RpgSavedData data = RpgSavedData.get(player.level().getServer());
			RpgSavedData.Profile before = data.profile(player.getUUID());
			long playerXp = ProgressionMath.change(before.playerXp(), PLAYER_XP_PER_HOSTILE_KILL,
					ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
			String selectedId = before.selectedClassId();
			net.minecraft.resources.Identifier activeId = net.minecraft.resources.Identifier.tryParse(selectedId);
			RpgClass activeClass = activeId == null ? null : ClassRegistry.byId(activeId).orElse(null);
			long classXp = activeClass == null ? 0L : before.xpForClass(selectedId);
			if (activeClass != null) {
				int reward = Math.max(0, activeClass.xpFor(RpgAction.HOSTILE_MOB_KILL));
				classXp = ProgressionMath.change(classXp, reward, ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
			}

			long finalClassXp = classXp;
			data.update(player.getUUID(), profile -> {
				RpgSavedData.Profile changed = profile.withPlayerXp(playerXp);
				return activeClass == null ? changed : changed.withClassXp(selectedId, finalClassXp);
			});

			if (activeClass != null) {
				for (PerkDefinition perk : activeClass.perks()) {
					if (perk.effect().equals("heal_on_hostile_kill")) {
						player.heal((float) perk.amount());
					}
				}
			}
			RpgNetworking.sendSnapshot(player, false);
		});
	}
}
