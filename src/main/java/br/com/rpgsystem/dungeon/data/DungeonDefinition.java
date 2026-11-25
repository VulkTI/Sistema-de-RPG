package br.com.rpgsystem.dungeon.data;

import java.util.ArrayList;
import java.util.List;

public class DungeonDefinition {
	private String name;
	private int minLevel = 1;
	private int maxLevel = 1;
	private int weight = 1;
	private boolean active = false;
	private boolean official = false;
	private String source = "WORLD"; // "MOD" or "WORLD"

	private EntranceStructureData entranceStructure = new EntranceStructureData();
	private DungeonStructureData dungeonStructure = new DungeonStructureData();

	public DungeonDefinition() {}

	public DungeonDefinition(String name) {
		this(name, 1, 1, 1);
	}

	public DungeonDefinition(String name, int minLevel, int maxLevel, int weight) {
		this.name = name;
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.weight = weight;
		String safeName = (name != null ? name.trim() : "dungeon")
				.replaceAll("[^a-zA-Z0-9_\\-]", "_")
				.toLowerCase(java.util.Locale.ROOT);
		this.entranceStructure = new EntranceStructureData();
		this.entranceStructure.setStructureName(safeName + "_entrada");
		this.dungeonStructure = new DungeonStructureData();
		this.dungeonStructure.setStructureName(safeName + "_dungeon");
	}

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public int getMinLevel() { return minLevel; }
	public void setMinLevel(int minLevel) { this.minLevel = minLevel; }

	public int getMaxLevel() { return maxLevel; }
	public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

	public int getWeight() { return weight; }
	public void setWeight(int weight) { this.weight = weight; }

	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }

	public boolean isOfficial() { return official; }
	public void setOfficial(boolean official) { this.official = official; }

	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }

	public EntranceStructureData getEntranceStructure() { return entranceStructure; }
	public void setEntranceStructure(EntranceStructureData entranceStructure) { this.entranceStructure = entranceStructure; }

	public DungeonStructureData getDungeonStructure() { return dungeonStructure; }
	public void setDungeonStructure(DungeonStructureData dungeonStructure) { this.dungeonStructure = dungeonStructure; }

	/**
	 * Valida se a dungeon atende a todos os requisitos para ser ativada.
	 * Retorna lista vazia se for válida, ou mensagens de erro detalhadas.
	 */
	public List<String> validateForActivation() {
		List<String> errors = new ArrayList<>();
		if (dungeonStructure == null || dungeonStructure.getPlayerSpawnPos() == null) {
			errors.add("A dungeon precisa ter o ponto de spawn do jogador configurado.");
		}
		return errors;
	}
}
