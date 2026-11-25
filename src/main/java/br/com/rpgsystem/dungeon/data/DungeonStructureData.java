package br.com.rpgsystem.dungeon.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DungeonStructureData {
	private String structureName = "";
	private String schematicPath = "";
	private long timeTicks = 6000L;
	private boolean daylightCycle = false;
	private PortalData entrancePortal = null;
	private PortalData exitPortal = null;
	private BlockPos playerSpawnPos = null;
	private float playerSpawnYaw = 0.0F;
	private final List<WaveData> waves = new ArrayList<>();
	private final List<ChestSpawnData> chests = new ArrayList<>();
	private final List<AreaTriggerData> areas = new ArrayList<>();

	public DungeonStructureData() {}

	public String getStructureName() { return structureName; }
	public void setStructureName(String structureName) { this.structureName = structureName; }

	public String getSchematicPath() { return schematicPath; }
	public void setSchematicPath(String schematicPath) { this.schematicPath = schematicPath; }

	public long getTimeTicks() { return timeTicks; }
	public void setTimeTicks(long timeTicks) { this.timeTicks = timeTicks; }

	public boolean isDaylightCycle() { return daylightCycle; }
	public void setDaylightCycle(boolean daylightCycle) { this.daylightCycle = daylightCycle; }

	public PortalData getEntrancePortal() { return entrancePortal; }
	public void setEntrancePortal(PortalData entrancePortal) { this.entrancePortal = entrancePortal; }

	public PortalData getExitPortal() { return exitPortal; }
	public void setExitPortal(PortalData exitPortal) { this.exitPortal = exitPortal; }

	public BlockPos getPlayerSpawnPos() { return playerSpawnPos; }
	public void setPlayerSpawnPos(BlockPos playerSpawnPos) { this.playerSpawnPos = playerSpawnPos; }

	public float getPlayerSpawnYaw() { return playerSpawnYaw; }
	public void setPlayerSpawnYaw(float playerSpawnYaw) { this.playerSpawnYaw = playerSpawnYaw; }

	public List<WaveData> getWaves() { return waves; }
	public List<ChestSpawnData> getChests() { return chests; }
	public List<AreaTriggerData> getAreas() { return areas; }
}
