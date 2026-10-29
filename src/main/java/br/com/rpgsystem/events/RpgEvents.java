package br.com.rpgsystem.events;

import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.classes.RpgAction;
import br.com.rpgsystem.classes.RpgClass;
import br.com.rpgsystem.classes.MineradorClass;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.mining.MineradorEffects;
import br.com.rpgsystem.config.XpConfig;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.perks.PerkDefinition;
import br.com.rpgsystem.progression.ProgressionMath;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.MobCategory;

public final class RpgEvents {
	private static final int PLAYER_XP_PER_HOSTILE_KILL = 8;

	private RpgEvents() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			RpgSavedData.Profile profile = RpgSavedData.get(server).profile(player.getUUID());
			MineradorEffects.refreshMiningSpeed(player);
			RpgNetworking.sendSnapshot(player, profile.selectedClassId().isBlank());
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return;
			RpgSavedData data = RpgSavedData.get(serverPlayer.level().getServer());
			RpgSavedData.Profile profile = data.profile(serverPlayer.getUUID());
			if (!profile.selectedClassId().equals("rpgsystem:minerador")) return;
			MineradorClass minerador = ClassRegistry.byId(br.com.rpgsystem.RpgMod.id("minerador"))
					.filter(MineradorClass.class::isInstance).map(MineradorClass.class::cast).orElse(null);
			if (minerador == null) return;

			ItemStack tool = serverPlayer.getMainHandItem();
			boolean silkTouch = hasSilkTouch(serverPlayer, tool);
			// Toque Suave impede tanto o XP específico do minério quanto o bônus de rendimento.
			int oreXp = silkTouch ? 0 : minerador.oreXp(state);
			int commonBlocks = minerador.isCommonMiningBlock(state) && !silkTouch ? 1 : 0;
			int prior = profile.actionCounter("minerador_common_blocks");
			int rewards = (prior + commonBlocks) / minerador.commonBlocksPerReward();
			int remainder = (prior + commonBlocks) % minerador.commonBlocksPerReward();
			int xp = oreXp + rewards * minerador.xpFor(RpgAction.MINING_COMMON_BLOCKS);
			if (xp > 0 || commonBlocks > 0) {
				long nextXp = ProgressionMath.change(profile.xpForClass(minerador.id().toString()), xp,
						ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
				data.update(serverPlayer.getUUID(), current -> current
						.withClassXp(minerador.id().toString(), nextXp)
						.withActionCounter("minerador_common_blocks", remainder));
				if (xp > 0) RpgNetworking.sendSnapshot(serverPlayer, false);
			}
			if (oreXp > 0 && !silkTouch && serverPlayer.getRandom().nextDouble() < 0.01) {
				ItemStack bonus = bonusDrop(state);
				if (!bonus.isEmpty()) Block.popResource(level, pos, bonus);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity.getType().getCategory() != MobCategory.MONSTER) return;
			if (!(damageSource.getEntity() instanceof ServerPlayer player)) return;

			RpgSavedData data = RpgSavedData.get(player.level().getServer());
			RpgSavedData.Profile before = data.profile(player.getUUID());
			long playerXp = ProgressionMath.change(before.playerXp(), XpConfig.playerHostileKill(),
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

	private static boolean hasSilkTouch(ServerPlayer player, ItemStack stack) {
		var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return stack.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH)) > 0;
	}

	private static ItemStack bonusDrop(BlockState state) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
		return switch (path) {
			case "coal_ore" -> new ItemStack(Items.COAL);
			case "copper_ore" -> new ItemStack(Items.RAW_COPPER);
			case "iron_ore" -> new ItemStack(Items.RAW_IRON);
			case "gold_ore" -> new ItemStack(Items.RAW_GOLD);
			case "redstone_ore" -> new ItemStack(Items.REDSTONE);
			case "lapis_ore" -> new ItemStack(Items.LAPIS_LAZULI);
			case "diamond_ore" -> new ItemStack(Items.DIAMOND);
			case "emerald_ore" -> new ItemStack(Items.EMERALD);
			case "nether_quartz_ore" -> new ItemStack(Items.QUARTZ);
			case "nether_gold_ore" -> new ItemStack(Items.GOLD_NUGGET);
			case "ancient_debris" -> new ItemStack(Items.ANCIENT_DEBRIS);
			default -> ItemStack.EMPTY;
		};
	}
}
