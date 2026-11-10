package br.com.rpgsystem.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Auxiliar para a Ferramenta de Looting usada para testar tabelas em containers. */
public final class LootToolHelper {
	public static final String TAG_LOOT_TABLE = "RpgLootTable";

	private LootToolHelper() {}

	/**
	 * Cria a ferramenta de teste vinculada a uma tabela específica.
	 */
	public static ItemStack createTool(String tableName) {
		ItemStack stack = new ItemStack(Items.BLAZE_ROD);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6§lFerramenta de Loot: §e" + tableName));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("§7Tabela vinculada: §f" + tableName),
				Component.literal("§8Clique com o botão direito em um baú, baú com armadilha"),
				Component.literal("§8ou barril para limpar e sortear o loot desta tabela.")
		)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_LOOT_TABLE, tableName));
		return stack;
	}

	/**
	 * Verifica se o item é uma ferramenta de loot.
	 */
	public static boolean isTool(ItemStack stack) {
		if (stack.isEmpty()) return false;
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return false;
		CompoundTag tag = customData.copyTag();
		return tag.contains(TAG_LOOT_TABLE);
	}

	/**
	 * Retorna o nome da tabela vinculada à ferramenta, ou vazio se não houver.
	 */
	public static String getTableName(ItemStack stack) {
		if (!isTool(stack)) return "";
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return "";
		return customData.copyTag().getString(TAG_LOOT_TABLE).orElse("");
	}

	/**
	 * Executa a lógica de preenchimento quando o jogador usa a ferramenta em um container.
	 */
	public static InteractionResult handleUseOnBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		String tableName = getTableName(held);
		if (tableName.isBlank()) {
			return InteractionResult.PASS;
		}

		BlockState state = level.getBlockState(pos);
		Container container = resolveContainer(level, pos, state);

		if (container == null) {
			player.sendSystemMessage(Component.literal("§cEste bloco não é um container suportado (baú, baú com armadilha ou barril)!"));
			return InteractionResult.SUCCESS;
		}

		RpgLootTableSavedData savedData = RpgLootTableSavedData.get(level.getServer());
		Optional<RpgLootTable> tableOpt = savedData.getTable(tableName);
		if (tableOpt.isEmpty()) {
			player.sendSystemMessage(Component.literal("§cA tabela de loot '" + tableName + "' não foi encontrada no mundo!"));
			return InteractionResult.SUCCESS;
		}

		RpgLootTable table = tableOpt.get();
		if (table.isEmpty()) {
			player.sendSystemMessage(Component.literal("§cA tabela de loot '" + tableName + "' está vazia! Adicione itens com §e/rpg table insert§c."));
			return InteractionResult.SUCCESS;
		}

		List<ItemStack> rolled = table.rollLoot(player.getRandom());
		container.clearContent();

		int containerSize = container.getContainerSize();
		if (containerSize > 0) {
			List<Integer> slots = new ArrayList<>(containerSize);
			for (int i = 0; i < containerSize; i++) {
				slots.add(i);
			}
			Collections.shuffle(slots, new Random(player.getRandom().nextLong()));

			int limit = Math.min(rolled.size(), slots.size());
			for (int i = 0; i < limit; i++) {
				container.setItem(slots.get(i), rolled.get(i));
			}
		}

		container.setChanged();
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			be.setChanged();
		}
		level.sendBlockUpdated(pos, state, state, 3);

		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7f, 1.2f);
		player.sendSystemMessage(Component.literal("§a[RPG Loot] Baú atualizado com " + rolled.size()
				+ " item(ns) da tabela '" + table.name() + "' (faixa de quantidade: "
				+ table.minAmount() + "-" + table.maxAmount() + ")!"));

		return InteractionResult.SUCCESS;
	}

	private static Container resolveContainer(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof ChestBlock chestBlock) {
			return ChestBlock.getContainer(chestBlock, state, level, pos, true);
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c) {
			return c;
		}
		return null;
	}
}
