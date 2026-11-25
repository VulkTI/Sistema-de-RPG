package br.com.rpgsystem.dungeon.editor;

import com.google.gson.JsonObject;
import br.com.rpgsystem.dungeon.data.AreaTriggerData;
import br.com.rpgsystem.dungeon.data.ChestSpawnData;
import br.com.rpgsystem.dungeon.data.MobSpawnData;
import br.com.rpgsystem.dungeon.data.PortalData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class DungeonEditorSession {
	public enum SessionState {
		SETUP_SCREEN,
		FREE_BUILD,
		TOOL_WAVE_EDIT,
		TOOL_PORTAL_EDIT
	}

	private final UUID playerId;
	private String dungeonName;
	private String selectedStructure = ""; // "ENTRADA" or "DUNGEON"
	private SessionState state = SessionState.SETUP_SCREEN;

	// Schematics
	private String entranceSchematicName = "";
	private String entranceSchematicPath = "";
	private String dungeonSchematicName = "";
	private String dungeonSchematicPath = "";

	// Player restore data
	private ResourceKey<Level> previousDimension = Level.OVERWORLD;
	private Vec3 previousPos = Vec3.ZERO;
	private float previousYaw = 0.0F;
	private float previousPitch = 0.0F;
	private GameType previousGameMode = GameType.SURVIVAL;
	private final Map<Integer, ItemStack> savedInventory = new HashMap<>();
	private final List<MobEffectInstance> savedEffects = new ArrayList<>();

	// Placed blocks in void world for bounding box
	private final Set<BlockPos> placedBlocks = new HashSet<>();

	// Wave edit tools state
	private int editingWaveIndex = 0;
	private MobSpawnData.Category currentMobCategory = MobSpawnData.Category.MOB;
	private MobSpawnData defaultMobData = new MobSpawnData();

	private ChestSpawnData.SpawnCondition currentChestCondition = ChestSpawnData.SpawnCondition.ALWAYS;
	private ChestSpawnData defaultChestData = new ChestSpawnData();

	private AreaTriggerData.Mode currentAreaMode = AreaTriggerData.Mode.START_WAVE;
	private AreaTriggerData defaultAreaData = new AreaTriggerData();

	private PortalData.Type currentPortalType = PortalData.Type.ENTRANCE_WHITE;

	private BlockPos areaPoint1 = null;
	private BlockPos portalPoint1 = null;

	// Spawn do jogador na dungeon (definido na Onda 1)
	private BlockPos playerSpawnPos = null;
	private float playerSpawnYaw = 0.0F;

	// Daylight and Weather settings during session
	private long timeTicks = 6000L;
	private boolean daylightCycle = false;

	// Configuração temporária da estrutura em edição (mantida na memória da sessão até o clique em SALVAR)
	private JsonObject tempStructureConfig = null;

	// Markers placed during tool mode
	private final Map<BlockPos, Object> placedMarkers = new HashMap<>();
	private final Set<BlockPos> activeOverlayBlocks = new HashSet<>();
	private final DungeonUndoRedo undoRedo = new DungeonUndoRedo();

	public DungeonEditorSession(UUID playerId, String dungeonName) {
		this.playerId = playerId;
		this.dungeonName = dungeonName;
	}

	public UUID getPlayerId() { return playerId; }

	public String getDungeonName() { return dungeonName; }
	public void setDungeonName(String dungeonName) { this.dungeonName = dungeonName; }

	public String getSelectedStructure() { return selectedStructure; }
	public void setSelectedStructure(String selectedStructure) { this.selectedStructure = selectedStructure; }

	public SessionState getState() { return state; }
	public void setState(SessionState state) { this.state = state; }

	public String getEntranceSchematicName() { return entranceSchematicName; }
	public void setEntranceSchematicName(String name) { this.entranceSchematicName = name; }

	public String getEntranceSchematicPath() { return entranceSchematicPath; }
	public void setEntranceSchematicPath(String path) { this.entranceSchematicPath = path; }

	public String getDungeonSchematicName() { return dungeonSchematicName; }
	public void setDungeonSchematicName(String name) { this.dungeonSchematicName = name; }

	public String getDungeonSchematicPath() { return dungeonSchematicPath; }
	public void setDungeonSchematicPath(String path) { this.dungeonSchematicPath = path; }

	// Restore data
	public ResourceKey<Level> getPreviousDimension() { return previousDimension; }
	public void setPreviousDimension(ResourceKey<Level> dim) { this.previousDimension = dim; }

	public Vec3 getPreviousPos() { return previousPos; }
	public void setPreviousPos(Vec3 pos) { this.previousPos = pos; }

	public float getPreviousYaw() { return previousYaw; }
	public void setPreviousYaw(float yaw) { this.previousYaw = yaw; }

	public float getPreviousPitch() { return previousPitch; }
	public void setPreviousPitch(float pitch) { this.previousPitch = pitch; }

	public GameType getPreviousGameMode() { return previousGameMode; }
	public void setPreviousGameMode(GameType gm) { this.previousGameMode = gm; }

	public Map<Integer, ItemStack> getSavedInventory() { return savedInventory; }
	public List<MobEffectInstance> getSavedEffects() { return savedEffects; }

	public Set<BlockPos> getPlacedBlocks() { return placedBlocks; }

	public int getEditingWaveIndex() { return editingWaveIndex; }
	public void setEditingWaveIndex(int index) { this.editingWaveIndex = index; }

	public MobSpawnData.Category getCurrentMobCategory() { return currentMobCategory; }
	public void setCurrentMobCategory(MobSpawnData.Category cat) { this.currentMobCategory = cat; }

	public MobSpawnData getDefaultMobData() { return defaultMobData; }
	public void setDefaultMobData(MobSpawnData data) { this.defaultMobData = data; }

	public ChestSpawnData.SpawnCondition getCurrentChestCondition() { return currentChestCondition; }
	public void setCurrentChestCondition(ChestSpawnData.SpawnCondition cond) { this.currentChestCondition = cond; }

	public ChestSpawnData getDefaultChestData() { return defaultChestData; }
	public void setDefaultChestData(ChestSpawnData data) { this.defaultChestData = data; }

	public AreaTriggerData.Mode getCurrentAreaMode() { return currentAreaMode; }
	public void setCurrentAreaMode(AreaTriggerData.Mode mode) { this.currentAreaMode = mode; }

	public AreaTriggerData getDefaultAreaData() { return defaultAreaData; }
	public void setDefaultAreaData(AreaTriggerData data) { this.defaultAreaData = data; }

	public PortalData.Type getCurrentPortalType() { return currentPortalType; }
	public void setCurrentPortalType(PortalData.Type type) { this.currentPortalType = type; }

	public BlockPos getAreaPoint1() { return areaPoint1; }
	public void setAreaPoint1(BlockPos p) { this.areaPoint1 = p; }

	public BlockPos getPortalPoint1() { return portalPoint1; }
	public void setPortalPoint1(BlockPos p) { this.portalPoint1 = p; }

	public BlockPos getPlayerSpawnPos() { return playerSpawnPos; }
	public void setPlayerSpawnPos(BlockPos pos) { this.playerSpawnPos = pos; }

	public float getPlayerSpawnYaw() { return playerSpawnYaw; }
	public void setPlayerSpawnYaw(float yaw) { this.playerSpawnYaw = yaw; }

	public Map<BlockPos, Object> getPlacedMarkers() { return placedMarkers; }
	public Set<BlockPos> getActiveOverlayBlocks() { return activeOverlayBlocks; }
	public DungeonUndoRedo getUndoRedo() { return undoRedo; }

	public JsonObject getTempStructureConfig() { return tempStructureConfig; }
	public void setTempStructureConfig(JsonObject config) { this.tempStructureConfig = config; }

	public long getTimeTicks() { return timeTicks; }
	public void setTimeTicks(long timeTicks) { this.timeTicks = timeTicks; }

	public boolean isDaylightCycle() { return daylightCycle; }
	public void setDaylightCycle(boolean daylightCycle) { this.daylightCycle = daylightCycle; }
}
