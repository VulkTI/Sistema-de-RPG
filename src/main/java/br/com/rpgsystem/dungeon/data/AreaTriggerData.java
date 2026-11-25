package br.com.rpgsystem.dungeon.data;

import net.minecraft.core.BlockPos;

public class AreaTriggerData {
	public enum Mode {
		START_WAVE("Iniciar onda"),
		SPAWN_CHEST("Spawnar baú"),
		PLAY_MUSIC("Tocar música");

		private final String displayName;

		Mode(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() { return displayName; }
	}

	private String name = "Área 1";
	private Mode mode = Mode.START_WAVE;
	private int waveOrder = 1;
	private BlockPos minPos = BlockPos.ZERO;
	private BlockPos maxPos = BlockPos.ZERO;
	private String targetWaveName = "";
	private String targetChestName = "";
	private String musicId = "minecraft:music_disc.pigstep";
	private boolean replaceMusic = true;

	public AreaTriggerData() {}

	public AreaTriggerData(String name, Mode mode, BlockPos minPos, BlockPos maxPos) {
		this.name = name;
		this.mode = mode;
		this.minPos = minPos;
		this.maxPos = maxPos;
	}

	public int getWaveOrder() { return waveOrder; }
	public void setWaveOrder(int waveOrder) { this.waveOrder = waveOrder; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public Mode getMode() { return mode; }
	public void setMode(Mode mode) { this.mode = mode; }

	public BlockPos getMinPos() { return minPos; }
	public void setMinPos(BlockPos minPos) { this.minPos = minPos; }

	public BlockPos getMaxPos() { return maxPos; }
	public void setMaxPos(BlockPos maxPos) { this.maxPos = maxPos; }

	public String getTargetWaveName() { return targetWaveName; }
	public void setTargetWaveName(String targetWaveName) { this.targetWaveName = targetWaveName; }

	public String getTargetChestName() { return targetChestName; }
	public void setTargetChestName(String targetChestName) { this.targetChestName = targetChestName; }

	public String getMusicId() { return musicId; }
	public void setMusicId(String musicId) { this.musicId = musicId; }

	public boolean isReplaceMusic() { return replaceMusic; }
	public void setReplaceMusic(boolean replaceMusic) { this.replaceMusic = replaceMusic; }

	public boolean contains(BlockPos pos) {
		return pos.getX() >= minPos.getX() && pos.getX() <= maxPos.getX()
				&& pos.getY() >= minPos.getY() && pos.getY() <= maxPos.getY()
				&& pos.getZ() >= minPos.getZ() && pos.getZ() <= maxPos.getZ();
	}
}
