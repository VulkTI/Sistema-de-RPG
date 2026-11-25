package br.com.rpgsystem.dungeon.block;

import br.com.rpgsystem.dungeon.world.DungeonWorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WhitePortalBlock extends Block {
	protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

	public WhitePortalBlock(ResourceKey<Block> key) {
		super(BlockBehaviour.Properties.of()
				.setId(key)
				.noOcclusion()
				.noCollision()
				.noLootTable()
				.strength(-1.0F, 3600000.0F)
				.lightLevel(state -> 12)
				.sound(SoundType.AMETHYST));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isInside) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) {
			if (level.dimension().equals(br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.EDITOR_DIMENSION)
					|| br.com.rpgsystem.dungeon.editor.DungeonEditorSessionManager.isInEditorSession(player.getUUID())) {
				return;
			}
			DungeonWorldManager.onPlayerTouchEntrancePortal(player, pos);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 4; i++) {
			double px = pos.getX() + random.nextDouble();
			double py = pos.getY() + random.nextDouble();
			double pz = pos.getZ() + random.nextDouble();
			double vx = (random.nextDouble() - 0.5) * 0.1;
			double vy = random.nextDouble() * 0.1;
			double vz = (random.nextDouble() - 0.5) * 0.1;
			level.addParticle(ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
		}
	}
}
