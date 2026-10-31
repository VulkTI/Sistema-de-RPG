package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {
	@Shadow private net.minecraft.world.item.trading.Merchant trader;
	@Unique private static final Map<MerchantOffer, Integer> rpgsystem$originalPrices = new WeakHashMap<>();

	@Inject(method = "slotsChanged", at = @At("TAIL"))
	private void rpgsystem$discountTrade(Container container, CallbackInfo ci) {
		Player tradingPlayer = trader.getTradingPlayer();
		if (!(tradingPlayer instanceof ServerPlayer player)) return;
		MerchantOffers offers = trader.getOffers();
		if (offers == null) return;
		for (MerchantOffer offer : offers) {
			int original = rpgsystem$originalPrices.computeIfAbsent(offer, ignored -> offer.getSpecialPriceDiff());
			int discount = (int) Math.floor(offer.getBaseCostA().getCount() * (1D - UniversalSkillService.villagerTradeMultiplier(player)));
			offer.setSpecialPriceDiff(Math.max(-Math.max(0, offer.getBaseCostA().getCount() - 1), original - discount));
		}
	}
}
