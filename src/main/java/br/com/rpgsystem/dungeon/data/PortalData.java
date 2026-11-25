package br.com.rpgsystem.dungeon.data;

import net.minecraft.core.BlockPos;

public class PortalData {
	public enum Type {
		ENTRANCE_WHITE("Portal de Entrada (Branco)"),
		EXIT_LIGHT_BLUE("Portal de Saída (Azul Claro)");

		private final String title;

		Type(String title) {
			this.title = title;
		}

		public String getTitle() { return title; }
	}

	private Type type = Type.ENTRANCE_WHITE;
	private BlockPos minPos = BlockPos.ZERO;
	private BlockPos maxPos = BlockPos.ZERO;
	private transient java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> replacedStates = new java.util.HashMap<>();

	public PortalData() {}

	public PortalData(Type type, BlockPos minPos, BlockPos maxPos) {
		this.type = type;
		this.minPos = minPos;
		this.maxPos = maxPos;
	}

	public java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> getReplacedStates() {
		return replacedStates;
	}

	public void setReplacedStates(java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> states) {
		this.replacedStates = states != null ? states : new java.util.HashMap<>();
	}

	public Type getType() { return type; }
	public void setType(Type type) { this.type = type; }

	public BlockPos getMinPos() { return minPos; }
	public void setMinPos(BlockPos minPos) { this.minPos = minPos; }

	public BlockPos getMaxPos() { return maxPos; }
	public void setMaxPos(BlockPos maxPos) { this.maxPos = maxPos; }

	public boolean contains(BlockPos pos) {
		return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
				&& pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
				&& pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
	}
}
