package br.com.rpgsystem.mixin;

import br.com.rpgsystem.crafting.CraftingAffixHelper;
import br.com.rpgsystem.affixes.AffixDefinition;
import br.com.rpgsystem.affixes.AffixService;
import br.com.rpgsystem.affixes.RpgAttributes;
import br.com.rpgsystem.classes.ClassRegistry;
import br.com.rpgsystem.data.RpgSavedData;
import br.com.rpgsystem.progression.ProgressionMath;
import br.com.rpgsystem.network.RpgNetworking;
import br.com.rpgsystem.classes.FerreiroClass;
import br.com.rpgsystem.classes.RpgAction;
import br.com.rpgsystem.affixes.AffixItemGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Shadow private Player player;

	@Inject(method = "remove", at = @At("HEAD"))
	private void rpgsystem$prepareBeforeTransfer(int amount, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ItemStack> cir) {
		if (player instanceof ServerPlayer server) CraftingAffixHelper.prepareAffix(server, ((Slot) (Object) this).getItem());
	}
	@Inject(method = "onTake", at = @At("HEAD"))
	private void rpgsystem$rollAffixOnCraft(Player entity, ItemStack craftedStack, CallbackInfo ci) {
		if (CraftingAffixHelper.isShiftCraftInProgress()) return;
		if (!(entity instanceof ServerPlayer player) || craftedStack.isEmpty()) return;
		RpgSavedData.Profile profile = RpgSavedData.get(player.level().getServer()).profile(player.getUUID());
		final FerreiroClass blacksmith = profile.selectedClassId().equals("rpgsystem:ferreiro")
				? ClassRegistry.byId(br.com.rpgsystem.RpgMod.id("ferreiro")).filter(FerreiroClass.class::isInstance)
				.map(FerreiroClass.class::cast).orElse(null) : null;
		if (blacksmith != null && player.containerMenu instanceof AnvilMenu && player.containerMenu.slots.size() > 0) {
			ItemStack repaired = player.containerMenu.getSlot(0).getItem();
			if (!repaired.isEmpty() && repaired.getMaxDamage() > 0) {
				int recovered = repaired.getDamageValue() - craftedStack.getDamageValue();
				if (recovered > 0) craftedStack.setDamageValue(Math.max(0,
						repaired.getDamageValue() - (int) Math.ceil(recovered * 1.10)));
			}
			return;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(craftedStack.getItem());
		if (blacksmith != null && itemId.equals(Identifier.fromNamespaceAndPath("minecraft", "netherite_ingot"))) {
			RpgSavedData data = RpgSavedData.get(player.level().getServer());
			long next = ProgressionMath.change(profile.xpForClass(blacksmith.id().toString()), blacksmith.xpFor(RpgAction.NETHERITE_INGOT_CRAFTED) * craftedStack.getCount(),
					ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
			data.update(player.getUUID(), current -> current.withClassXp(blacksmith.id().toString(), next));
			RpgNetworking.sendSnapshot(player, false);
		}
		if (craftedStack.get(br.com.rpgsystem.affixes.AffixComponents.AFFIX) == null) {
			double luck = player.getAttributeValue(RpgAttributes.AFFIX_LUCK);
			double reduction = blacksmith == null ? 0.0 : 0.5;
			AffixDefinition definition = AffixService.rollFor(craftedStack, luck, player.getRandom(), reduction).orElse(null);
			if (definition != null) AffixService.apply(craftedStack, definition);
		}
		if (blacksmith != null && AffixItemGroups.matches("metal", craftedStack.getItem())) {
			int maxDamage = craftedStack.getMaxDamage();
			if (maxDamage > 0) craftedStack.set(DataComponents.MAX_DAMAGE, (int) Math.ceil(maxDamage * 1.10));
			int bars = 0;
			for (int slot = 1; slot < player.containerMenu.slots.size(); slot++) {
				ItemStack ingredient = player.containerMenu.getSlot(slot).getItem();
				String ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).getPath();
				if (ingredientId.endsWith("_ingot") && !ingredient.isEmpty()) bars += ingredient.getCount();
			}
			final int xp = blacksmith.craftedItemXp(itemId, craftedStack.getCount(), bars);
			RpgSavedData data = RpgSavedData.get(player.level().getServer());
			long next = ProgressionMath.change(profile.xpForClass(blacksmith.id().toString()), xp,
					ProgressionMath.Change.ADD, ProgressionMath.Unit.POINTS);
			data.update(player.getUUID(), current -> current.withClassXp(blacksmith.id().toString(), next));
			RpgNetworking.sendSnapshot(player, false);
		}
	}
}
