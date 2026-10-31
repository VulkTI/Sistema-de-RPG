package br.com.rpgsystem.skills;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.mobs.MobLevelAccess;
import br.com.rpgsystem.network.RpgNetworking;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Eventos server-side das seis skills. Cada recompensa passa por este serviço para evitar XP acima do teto. */
public final class UniversalSkillEvents {
	private static int tickCounter;
	private static final List<String> RELEVANT_STRUCTURES = List.of(
			"minecraft:village", "minecraft:desert_pyramid", "minecraft:jungle_temple", "minecraft:pillager_outpost",
			"minecraft:mansion", "minecraft:monument", "minecraft:ancient_city", "minecraft:trial_chambers",
			"minecraft:stronghold", "minecraft:bastion_remnant", "minecraft:fortress", "minecraft:end_city");

	private UniversalSkillEvents() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			UniversalSkillService.refreshAttributes(handler.player);
			RpgNetworking.sendSnapshot(handler.player, false);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player) || entity == player) return;
			MobCategory category = entity.getType().getCategory();
			if (category == MobCategory.MISC) return;
			int mobLevel = entity instanceof MobLevelAccess access && access.rpgsystem$hasMobLevel() ? access.rpgsystem$getMobLevel() : 1;
			double base = UniversalSkillConfig.activityXp(UniversalSkill.COMBATE,
					category == MobCategory.MONSTER ? "hostile_kill" : "peaceful_kill", category == MobCategory.MONSTER ? 35 : 9);
			if (category != MobCategory.MONSTER) base *= UniversalSkillConfig.peacefulCombatMultiplier();
			UniversalSkillService.addXp(player, UniversalSkill.COMBATE, base * (1D + Math.max(0, mobLevel - 1) / 20D));
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return;
			awardMining(serverPlayer, state);
			awardHarvest(serverPlayer, level, pos, state, blockEntity);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) return InteractionResult.PASS;
			ItemStack held = player.getItemInHand(hand);
			if (entity instanceof Piglin && !held.isEmpty() && held.is(Items.GOLD_INGOT)) UniversalSkillService.addXp(serverPlayer,
						UniversalSkill.COMERCIO, UniversalSkillConfig.activityXp(UniversalSkill.COMERCIO, "piglin_trade", 10));
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null && chest.canOpen(player)) {
				String key = ExplorationKeys.chest(dimension(serverPlayer), pos.asLong());
				RpgSavedData.Profile profile = RpgSavedData.get(serverPlayer.level().getServer()).profile(serverPlayer.getUUID());
				if (!profile.hasExplored("chests", key)) {
					RpgSavedData.get(serverPlayer.level().getServer()).update(serverPlayer.getUUID(), current -> current.markExplored("chests", key));
					UniversalSkillService.addXp(serverPlayer, UniversalSkill.EXPLORACAO,
							UniversalSkillConfig.activityXp(UniversalSkill.EXPLORACAO, "chest", 35));
				}
			}
			return InteractionResult.PASS;
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) UniversalSkillService.addXp(player, UniversalSkill.EXPLORACAO,
					UniversalSkillConfig.activityXp(UniversalSkill.EXPLORACAO, "sleep", 20));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if ((tickCounter++ % 20) != 0) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				UniversalSkillService.refreshAttributes(player);
				discoverStructure(player);
			}
		});
	}

	private static void awardMining(ServerPlayer player, BlockState state) {
		if (hasSilkTouch(player, player.getMainHandItem())) return;
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
		int ore = oreXp(path);
		int common = path.equals("stone") || path.equals("deepslate") ? 1 : 0;
		RpgSavedData data = RpgSavedData.get(player.level().getServer());
		RpgSavedData.Profile profile = data.profile(player.getUUID());
		int prior = profile.actionCounter("universal_mining_common_blocks");
		int blocksPerReward = Math.max(1, br.com.rpgsystem.config.XpConfig.mineradorBlocksPerReward());
		int rewards = (prior + common) / blocksPerReward;
		int remainder = (prior + common) % blocksPerReward;
		int stoneXp = br.com.rpgsystem.config.XpConfig.mineradorStoneXp();
		data.update(player.getUUID(), current -> current.withActionCounter("universal_mining_common_blocks", remainder));
		if (ore > 0 || rewards > 0) UniversalSkillService.addXp(player, UniversalSkill.MINERACAO,
				(ore + rewards * stoneXp) * UniversalSkillConfig.activityMultiplier(UniversalSkill.MINERACAO, "class_xp", 1.2D));
	}

	private static void awardHarvest(ServerPlayer player, net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		boolean mature = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
				|| state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3;
		if (!mature) return;
		UniversalSkillService.addXp(player, UniversalSkill.AGRICULTURA,
				UniversalSkillConfig.activityXp(UniversalSkill.AGRICULTURA, "harvest", 12));
		if (player.getRandom().nextDouble() >= UniversalSkillService.harvestChance(player)) return;
		ItemStack extra = harvestItem(state);
		if (!extra.isEmpty()) Block.popResource(level, pos, extra);
	}

	private static ItemStack harvestItem(BlockState state) {
		return switch (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()) {
			case "wheat" -> new ItemStack(Items.WHEAT);
			case "carrots" -> new ItemStack(Items.CARROT);
			case "potatoes" -> new ItemStack(Items.POTATO);
			case "beetroots" -> new ItemStack(Items.BEETROOT);
			case "nether_wart" -> new ItemStack(Items.NETHER_WART);
			case "cocoa" -> new ItemStack(Items.COCOA_BEANS);
			case "sweet_berry_bush" -> new ItemStack(Items.SWEET_BERRIES);
			default -> ItemStack.EMPTY;
		};
	}

	private static int oreXp(String path) { return br.com.rpgsystem.config.XpConfig.mineradorOreXp(path); }
	private static boolean hasSilkTouch(ServerPlayer player, ItemStack stack) {
		var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return stack.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH)) > 0;
	}

	private static void discoverStructure(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		for (String idString : RELEVANT_STRUCTURES) {
			Identifier id = Identifier.tryParse(idString);
			if (id == null) continue;
			Optional<net.minecraft.world.level.levelgen.structure.Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOptional(id);
			if (structure.isEmpty()) continue;
			var start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(id));
			if (!start.isValid()) continue;
			String key = ExplorationKeys.structure(dimension(player), idString, start.getBoundingBox().minX(), start.getBoundingBox().minZ());
			RpgSavedData.Profile profile = RpgSavedData.get(level.getServer()).profile(player.getUUID());
			if (profile.hasExplored("structures", key)) return;
			boolean firstType = profile.exploredStructures().getOrDefault("structure_type:" + idString, List.of()).isEmpty();
			RpgSavedData.get(level.getServer()).update(player.getUUID(), current -> current.markExplored("structures", key).markExplored("structure_type:" + idString, "visited"));
			double base = UniversalSkillConfig.structureXp(idString, UniversalSkillConfig.genericStructureXp());
			UniversalSkillService.addXp(player, UniversalSkill.EXPLORACAO, base * (firstType ? 1D + UniversalSkillConfig.firstStructureMultiplier() : 1D));
			return;
		}
		RpgSavedData.Profile profile = RpgSavedData.get(level.getServer()).profile(player.getUUID());
		if (!profile.hasExplored("structures", "__generic_structure_discovery__")
				&& !level.structureManager().getAllStructuresAt(player.blockPosition()).isEmpty()) {
			RpgSavedData.get(level.getServer()).update(player.getUUID(), current -> current.markExplored("structures", "__generic_structure_discovery__"));
			UniversalSkillService.addXp(player, UniversalSkill.EXPLORACAO, UniversalSkillConfig.genericStructureXp());
		}
	}

	private static String dimension(ServerPlayer player) { return player.level().dimension().toString(); }
}
