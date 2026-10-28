package br.com.rpgsystem.affixes;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/** Atributos extras do sistema RPG. */
public final class RpgAttributes {
	public static final net.minecraft.core.Holder.Reference<Attribute> AFFIX_LUCK = Registry.registerForHolder(
			BuiltInRegistries.ATTRIBUTE,
			Identifier.fromNamespaceAndPath("rpgsystem", "affix_luck"),
			new RangedAttribute("attribute.rpgsystem.affix_luck", 1.0, 0.0, 1024.0).setSyncable(true));

	private RpgAttributes() {}

	public static void register() {
		FabricDefaultAttributeRegistry.MODIFY.register(context -> context.modify(
				type -> EntityType.getKey(type).equals(Identifier.fromNamespaceAndPath("minecraft", "player")),
				(type, builder) -> builder.add(AFFIX_LUCK, 1.0)));
	}
}
