package br.com.rpgsystem.affixes;

import br.com.rpgsystem.RpgMod;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Component persistente que carrega todos os dados do afixo no próprio ItemStack. */
public final class AffixComponents {
	public static final DataComponentType<AffixDefinition> AFFIX = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			RpgMod.id("affix"),
			DataComponentType.<AffixDefinition>builder()
					.persistent(AffixDefinition.COMPONENT_CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(AffixDefinition.COMPONENT_CODEC))
					.build());

	private AffixComponents() {}

	public static void register() {
		ItemComponentTooltipProviderRegistry.addLast(AFFIX);
	}
}
