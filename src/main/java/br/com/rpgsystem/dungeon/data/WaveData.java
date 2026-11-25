package br.com.rpgsystem.dungeon.data;

import java.util.ArrayList;
import java.util.List;

public class WaveData {
	public enum SpawnMode {
		GRADUAL("Gradual", "Spawna 1 mob a cada 0.5s"),
		TOTAL("Total", "Spawna todos os mobs de uma vez");

		private final String title;
		private final String description;

		SpawnMode(String title, String description) {
			this.title = title;
			this.description = description;
		}

		public String getTitle() { return title; }
		public String getDescription() { return description; }
	}

	private int order = 1;
	private String name = "Onda 1";
	private SpawnMode spawnMode = SpawnMode.GRADUAL;
	private String activationAreaName = "";
	private final List<MobSpawnData> mobs = new ArrayList<>();

	public WaveData() {}

	public WaveData(int order, String name) {
		this.order = order;
		this.name = name;
	}

	public int getOrder() { return order; }
	public void setOrder(int order) { this.order = order; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public SpawnMode getSpawnMode() { return spawnMode; }
	public void setSpawnMode(SpawnMode spawnMode) { this.spawnMode = spawnMode; }

	public String getActivationAreaName() { return activationAreaName; }
	public void setActivationAreaName(String activationAreaName) { this.activationAreaName = activationAreaName; }

	public List<MobSpawnData> getMobs() { return mobs; }

	public void addMob(MobSpawnData mob) {
		this.mobs.add(mob);
	}

	public boolean removeMob(MobSpawnData mob) {
		return this.mobs.remove(mob);
	}
}
