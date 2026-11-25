package br.com.rpgsystem.mining;

import br.com.rpgsystem.RpgMod;
import br.com.rpgsystem.data.RpgSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Efeitos persistentes e condicionais exclusivos da classe Minerador. */
public final class MineradorEffects {
	private static final String MINERADOR_ID = "rpgsystem:minerador";
	private static final Identifier MINING_SPEED_ID = RpgMod.id("minerador_mining_speed");
	private static final AttributeModifier MINING_SPEED = new AttributeModifier(
			MINING_SPEED_ID, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private MineradorEffects() {}

	public static boolean isMinerador(ServerPlayer player) {
		if (player == null || player.level() == null || player.level().getServer() == null) return false;
		return RpgSavedData.get(player.level().getServer()).profile(player.getUUID()).selectedClassId().equals(MINERADOR_ID);
	}

	/** Mantém o atributo sincronizado para servidor e cliente ao entrar ou trocar de classe. */
	public static void refreshMiningSpeed(ServerPlayer player) {
		if (player == null) return;
		AttributeInstance attribute = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (attribute == null) return;
		attribute.removeModifier(MINING_SPEED_ID);
		if (isMinerador(player)) attribute.addOrUpdateTransientModifier(MINING_SPEED);
	}

	/** A rolagem ocorre antes da durabilidade vanilla, portanto combina naturalmente com Inquebrável. */
	public static boolean shouldPreventPickaxeDamage(ItemStack stack, ServerPlayer player) {
		if (player == null || !stack.is(ItemTags.PICKAXES)) return false;
		return isMinerador(player) && player.getRandom().nextDouble() < 0.10;
	}
}
