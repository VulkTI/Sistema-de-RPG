package br.com.rpgsystem.dungeon.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class ChestSpawnData {
	public enum SpawnCondition {
		ALWAYS("Sempre"),
		AFTER_WAVE("Após onda"),
		ENTER_AREA("Entrar na área");

		private final String displayName;

		SpawnCondition(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() { return displayName; }
	}

	private String chestName = "Baú 1";
	private String lootTableName = "";
	private SpawnCondition condition = SpawnCondition.ALWAYS;
	private int waveOrder = 1;
	private String targetAreaName = "";
	private BlockPos relativePos = BlockPos.ZERO;
	private Direction facing = Direction.NORTH;

	public ChestSpawnData() {}

	public ChestSpawnData(String chestName, BlockPos relativePos) {
		this.chestName = chestName;
		this.relativePos = relativePos;
	}

	public String getChestName() { return chestName; }
	public void setChestName(String chestName) { this.chestName = chestName; }

	public String getLootTableName() { return lootTableName; }
	public void setLootTableName(String lootTableName) { this.lootTableName = lootTableName; }

	public SpawnCondition getCondition() { return condition; }
	public void setCondition(SpawnCondition condition) { this.condition = condition; }

	public int getWaveOrder() { return waveOrder; }
	public void setWaveOrder(int waveOrder) { this.waveOrder = waveOrder; }

	public String getTargetAreaName() { return targetAreaName; }
	public void setTargetAreaName(String targetAreaName) { this.targetAreaName = targetAreaName; }

	public BlockPos getRelativePos() { return relativePos; }
	public void setRelativePos(BlockPos relativePos) { this.relativePos = relativePos; }

	public Direction getFacing() { return facing != null ? facing : Direction.NORTH; }
	public void setFacing(Direction facing) { this.facing = facing != null ? facing : Direction.NORTH; }
}
