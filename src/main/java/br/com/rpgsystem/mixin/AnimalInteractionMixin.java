package br.com.rpgsystem.mixin;

import br.com.rpgsystem.skills.UniversalSkill;
import br.com.rpgsystem.skills.UniversalSkillConfig;
import br.com.rpgsystem.skills.UniversalSkillService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public abstract class AnimalInteractionMixin {
	@Unique private boolean rpgsystem$wasBaby;
	@Unique private boolean rpgsystem$hadFood;

	@Inject(method = "mobInteract", at = @At("HEAD"))
	private void rpgsystem$rememberFeed(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Animal animal = (Animal) (Object) this;
		rpgsystem$wasBaby = animal.isBaby();
		rpgsystem$hadFood = player.getItemInHand(hand).has(DataComponents.FOOD);
	}

	@Inject(method = "mobInteract", at = @At("RETURN"))
	private void rpgsystem$awardFeed(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (rpgsystem$wasBaby && rpgsystem$hadFood && cir.getReturnValue().consumesAction() && player instanceof ServerPlayer serverPlayer)
			UniversalSkillService.addXp(serverPlayer, UniversalSkill.AGRICULTURA,
					UniversalSkillConfig.activityXp(UniversalSkill.AGRICULTURA, "feed_baby", 18));
	}

	@Inject(method = "spawnChildFromBreeding", at = @At("HEAD"))
	private void rpgsystem$awardBreeding(ServerLevel level, Animal partner, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		ServerPlayer player = ((Animal) (Object) this).getLoveCause();
		if (player == null) player = partner.getLoveCause();
		if (player != null) {
			UniversalSkillService.addXp(player, UniversalSkill.AGRICULTURA,
					UniversalSkillConfig.activityXp(UniversalSkill.AGRICULTURA, "breed", 35));
			double cooldownMultiplier = UniversalSkillService.breedingCooldownMultiplier(player);
			Animal animal = (Animal) (Object) this;
			if (animal.getAge() > 0) animal.setAge((int) Math.ceil(animal.getAge() * cooldownMultiplier));
			if (partner.getAge() > 0) partner.setAge((int) Math.ceil(partner.getAge() * cooldownMultiplier));
		}
	}
}
