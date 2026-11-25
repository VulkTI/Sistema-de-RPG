package br.com.rpgsystem.dungeon.data;

import java.util.HashSet;
import java.util.Set;

public class EntranceStructureData {
	private String structureName = "";
	private String schematicPath = "";
	private boolean subsolo = false;
	private boolean ceu = false;
	private final Set<String> dimensions = new HashSet<>(Set.of("minecraft:overworld"));
	private PortalData entrancePortal = null;

	public EntranceStructureData() {}

	public String getStructureName() { return structureName; }
	public void setStructureName(String structureName) { this.structureName = structureName; }

	public String getSchematicPath() { return schematicPath; }
	public void setSchematicPath(String schematicPath) { this.schematicPath = schematicPath; }

	public boolean isSubsolo() { return subsolo; }
	public void setSubsolo(boolean subsolo) { this.subsolo = subsolo; }

	public boolean isCeu() { return ceu; }
	public void setCeu(boolean ceu) { this.ceu = ceu; }

	public Set<String> getDimensions() { return dimensions; }

	public PortalData getEntrancePortal() { return entrancePortal; }
	public void setEntrancePortal(PortalData entrancePortal) { this.entrancePortal = entrancePortal; }
}
