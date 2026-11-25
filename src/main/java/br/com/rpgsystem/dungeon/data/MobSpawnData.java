package br.com.rpgsystem.dungeon.data;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

public class MobSpawnData {
	public static class EquippedItemData {
		private String itemId = "";
		private int durability = 0; // Se 0, item intacto ou usa dano padrão
		private int maxDamage = 0;
		private double dropChance = 100.0; // Chance individual de drop (0 a 100%)
		private final Map<String, Integer> enchantments = new LinkedHashMap<>();

		public EquippedItemData() {}

		public EquippedItemData(String itemId) {
			this.itemId = itemId != null ? itemId : "";
		}

		public String getItemId() { return itemId; }
		public void setItemId(String itemId) { this.itemId = itemId != null ? itemId : ""; }

		public int getDurability() { return durability; }
		public void setDurability(int durability) { this.durability = durability; }

		public int getMaxDamage() { return maxDamage; }
		public void setMaxDamage(int maxDamage) { this.maxDamage = maxDamage; }

		public double getDropChance() { return dropChance; }
		public void setDropChance(double dropChance) { this.dropChance = Math.max(0.0, Math.min(100.0, dropChance)); }

		public Map<String, Integer> getEnchantments() { return enchantments; }

		public EquippedItemData copy() {
			EquippedItemData c = new EquippedItemData(this.itemId);
			c.durability = this.durability;
			c.maxDamage = this.maxDamage;
			c.dropChance = this.dropChance;
			c.enchantments.putAll(this.enchantments);
			return c;
		}
	}

	public enum Category {
		MOB("Mob", 0xFF62D99B),
		MINI_BOSS("Mini-chefe", 0xFFFF9933),
		BOSS("Chefe", 0xFFFF4444);

		private final String displayName;
		private final int color;

		Category(String displayName, int color) {
			this.displayName = displayName;
			this.color = color;
		}

		public String displayName() { return displayName; }
		public int color() { return color; }
	}

	private Category category = Category.MOB;
	private String entityId = "minecraft:zombie";
	private String customName = "";
	private String particleId = "minecraft:smoke";
	private float particleIntensity = 1.0F;
	private String soundId = "minecraft:entity.zombie.ambient";
	private double dropChance = 100.0;
	private String lootTableName = "";
	private float scale = 1.0F;
	private float healthMultiplier = 1.0F;
	private float attackMultiplier = 1.0F;
	private float speedMultiplier = 1.0F;
	private boolean constantParticles = false;
	private boolean dropEquipment = false;
	private final Map<String, EquippedItemData> equipment = new LinkedHashMap<>();
	private BlockPos relativePos = BlockPos.ZERO;

	public MobSpawnData() {}

	public MobSpawnData(Category category, BlockPos pos) {
		this.category = category;
		this.relativePos = pos;
		if (category == Category.MINI_BOSS) {
			this.scale = 1.5F;
		} else if (category == Category.BOSS) {
			this.scale = 2.5F;
		}
	}

	public Category getCategory() { return category; }
	public void setCategory(Category category) { this.category = category; }

	public String getEntityId() { return entityId; }
	public void setEntityId(String entityId) { this.entityId = entityId; }

	public String getCustomName() { return customName; }
	public void setCustomName(String customName) { this.customName = customName; }

	public String getParticleId() { return particleId; }
	public void setParticleId(String particleId) { this.particleId = particleId; }

	public float getParticleIntensity() { return particleIntensity; }
	public void setParticleIntensity(float particleIntensity) { this.particleIntensity = particleIntensity; }

	public String getSoundId() { return soundId; }
	public void setSoundId(String soundId) { this.soundId = soundId; }

	public double getDropChance() { return dropChance; }
	public void setDropChance(double dropChance) { this.dropChance = dropChance; }

	public String getLootTableName() { return lootTableName; }
	public void setLootTableName(String lootTableName) { this.lootTableName = lootTableName; }

	public float getScale() { return scale; }
	public void setScale(float scale) { this.scale = scale; }

	public float getHealthMultiplier() { return healthMultiplier; }
	public void setHealthMultiplier(float healthMultiplier) { this.healthMultiplier = Math.max(0.1F, healthMultiplier); }

	public float getAttackMultiplier() { return attackMultiplier; }
	public void setAttackMultiplier(float attackMultiplier) { this.attackMultiplier = Math.max(0.1F, attackMultiplier); }

	public float getSpeedMultiplier() { return speedMultiplier; }
	public void setSpeedMultiplier(float speedMultiplier) { this.speedMultiplier = Math.max(0.1F, speedMultiplier); }

	public boolean isConstantParticles() { return constantParticles; }
	public void setConstantParticles(boolean constantParticles) { this.constantParticles = constantParticles; }

	public boolean isDropEquipment() { return dropEquipment; }
	public void setDropEquipment(boolean dropEquipment) { this.dropEquipment = dropEquipment; }

	public Map<String, EquippedItemData> getEquipment() { return equipment; }

	public BlockPos getRelativePos() { return relativePos; }
	public void setRelativePos(BlockPos relativePos) { this.relativePos = relativePos; }

	public MobSpawnData copy() {
		MobSpawnData c = new MobSpawnData(this.category, this.relativePos);
		c.entityId = this.entityId;
		c.customName = this.customName;
		c.particleId = this.particleId;
		c.particleIntensity = this.particleIntensity;
		c.soundId = this.soundId;
		c.dropChance = this.dropChance;
		c.lootTableName = this.lootTableName;
		c.scale = this.scale;
		c.healthMultiplier = this.healthMultiplier;
		c.attackMultiplier = this.attackMultiplier;
		c.speedMultiplier = this.speedMultiplier;
		c.constantParticles = this.constantParticles;
		c.dropEquipment = this.dropEquipment;
		for (Map.Entry<String, EquippedItemData> entry : this.equipment.entrySet()) {
			c.equipment.put(entry.getKey(), entry.getValue().copy());
		}
		return c;
	}
}
