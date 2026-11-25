package br.com.rpgsystem.dungeon.block;

import br.com.rpgsystem.RpgMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class DungeonBlocks {
	public static final ResourceKey<Block> WHITE_PORTAL_KEY = ResourceKey.create(Registries.BLOCK, RpgMod.id("dungeon_white_portal"));
	public static final ResourceKey<Item> WHITE_PORTAL_ITEM_KEY = ResourceKey.create(Registries.ITEM, RpgMod.id("dungeon_white_portal"));

	public static final ResourceKey<Block> BLUE_PORTAL_KEY = ResourceKey.create(Registries.BLOCK, RpgMod.id("dungeon_blue_portal"));
	public static final ResourceKey<Item> BLUE_PORTAL_ITEM_KEY = ResourceKey.create(Registries.ITEM, RpgMod.id("dungeon_blue_portal"));

	public static final ResourceKey<Block> PORTAL_OVERLAY_KEY = ResourceKey.create(Registries.BLOCK, RpgMod.id("dungeon_portal_overlay"));
	public static final ResourceKey<Item> PORTAL_OVERLAY_ITEM_KEY = ResourceKey.create(Registries.ITEM, RpgMod.id("dungeon_portal_overlay"));

	public static final ResourceKey<Block> AREA_OVERLAY_KEY = ResourceKey.create(Registries.BLOCK, RpgMod.id("dungeon_area_overlay"));
	public static final ResourceKey<Item> AREA_OVERLAY_ITEM_KEY = ResourceKey.create(Registries.ITEM, RpgMod.id("dungeon_area_overlay"));

	public static final Block WHITE_PORTAL = new WhitePortalBlock(WHITE_PORTAL_KEY);
	public static final Block BLUE_PORTAL = new BluePortalBlock(BLUE_PORTAL_KEY);
	public static final Block PORTAL_OVERLAY = new DungeonOverlayBlock(PORTAL_OVERLAY_KEY);
	public static final Block AREA_OVERLAY = new DungeonOverlayBlock(AREA_OVERLAY_KEY);

	public static final BlockItem WHITE_PORTAL_ITEM = new BlockItem(WHITE_PORTAL, new Item.Properties().setId(WHITE_PORTAL_ITEM_KEY));
	public static final BlockItem BLUE_PORTAL_ITEM = new BlockItem(BLUE_PORTAL, new Item.Properties().setId(BLUE_PORTAL_ITEM_KEY));
	public static final BlockItem PORTAL_OVERLAY_ITEM = new BlockItem(PORTAL_OVERLAY, new Item.Properties().setId(PORTAL_OVERLAY_ITEM_KEY));
	public static final BlockItem AREA_OVERLAY_ITEM = new BlockItem(AREA_OVERLAY, new Item.Properties().setId(AREA_OVERLAY_ITEM_KEY));

	private DungeonBlocks() {}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, WHITE_PORTAL_KEY, WHITE_PORTAL);
		Registry.register(BuiltInRegistries.ITEM, WHITE_PORTAL_ITEM_KEY, WHITE_PORTAL_ITEM);

		Registry.register(BuiltInRegistries.BLOCK, BLUE_PORTAL_KEY, BLUE_PORTAL);
		Registry.register(BuiltInRegistries.ITEM, BLUE_PORTAL_ITEM_KEY, BLUE_PORTAL_ITEM);

		Registry.register(BuiltInRegistries.BLOCK, PORTAL_OVERLAY_KEY, PORTAL_OVERLAY);
		Registry.register(BuiltInRegistries.ITEM, PORTAL_OVERLAY_ITEM_KEY, PORTAL_OVERLAY_ITEM);

		Registry.register(BuiltInRegistries.BLOCK, AREA_OVERLAY_KEY, AREA_OVERLAY);
		Registry.register(BuiltInRegistries.ITEM, AREA_OVERLAY_ITEM_KEY, AREA_OVERLAY_ITEM);
	}
}
