package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgScrollArea;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.dungeon.client.ui.RpgSliderWidget;
import br.com.rpgsystem.dungeon.data.MobSpawnData;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class MobSpawnerToolScreen extends RpgBaseScreen {
	private final String initialJson;
	private MobSpawnData.Category category = MobSpawnData.Category.MOB;
	private String entityId = "minecraft:zombie";
	private String customName = "";
	private String particleId = "minecraft:smoke";
	private float particleIntensity = 1.0F;
	private String soundId = "minecraft:entity.zombie.ambient";
	private double dropChance = 25.0;
	private String selectedLootTable = "";
	private float scale = 1.0F;
	private float healthMultiplier = 1.0F;
	private float attackMultiplier = 1.0F;
	private float speedMultiplier = 1.0F;
	private boolean constantParticles = false;
	private boolean dropEquipment = false;
	private double modalDropChance = 100.0;
	private float previewZoom = 1.0F;
	private final Map<String, MobSpawnData.EquippedItemData> configuredEquipment = new LinkedHashMap<>();

	private boolean hasPos = false;
	private int posX, posY, posZ;

	private final List<String> availableLootTables = new ArrayList<>();
	private final Map<String, List<ChestToolScreen.LootItemDisplay>> lootTablesData = new HashMap<>();

	// 3D Preview State
	private LivingEntity dummyEntity = null;
	private float previewYaw = 0.0F;
	private float previewPitch = 0.0F;
	private boolean isDraggingPreview = false;
	private double lastDragX, lastDragY;
	private double mouseDownX, mouseDownY;
	private EquipmentSlot clickedSlot = null;
	private boolean previewRenderSuccess = false;

	// 3D Overlay definitions
	public static class Box3D {
		public final Vector3f[] vertices = new Vector3f[8];
		public float avgZ = 0.0F;

		public Box3D(Vector3f[] pts) {
			for (int i = 0; i < 8; i++) {
				vertices[i] = pts[i];
				avgZ += pts[i].z;
			}
			avgZ /= 8.0F;
		}

		public boolean containsPoint(float px, float py) {
			return isQuadContains(px, py, 0, 1, 2, 3) || // Frente
			       isQuadContains(px, py, 5, 4, 7, 6) || // Trás
			       isQuadContains(px, py, 3, 2, 6, 7) || // Topo
			       isQuadContains(px, py, 0, 4, 5, 1) || // Base
			       isQuadContains(px, py, 4, 0, 3, 7) || // Esquerda
			       isQuadContains(px, py, 1, 5, 6, 2);   // Direita
		}

		private boolean isQuadContains(float px, float py, int i0, int i1, int i2, int i3) {
			return isPointInTriangle(px, py, vertices[i0], vertices[i1], vertices[i2])
					|| isPointInTriangle(px, py, vertices[i0], vertices[i2], vertices[i3]);
		}
	}

	public static class Slot3DOverlay {
		public final EquipmentSlot slot;
		public final String name;
		public final List<Box3D> boxes = new ArrayList<>();

		public Slot3DOverlay(EquipmentSlot slot, String name) {
			this.slot = slot;
			this.name = name;
		}

		public boolean isHovered(float px, float py) {
			for (Box3D box : boxes) {
				if (box.containsPoint(px, py)) return true;
			}
			return false;
		}

		public float getMinZ() {
			float min = Float.MAX_VALUE;
			for (Box3D box : boxes) {
				if (box.avgZ < min) min = box.avgZ;
			}
			return min;
		}
	}

	private final List<Slot3DOverlay> active3DOverlays = new ArrayList<>();
	private Slot3DOverlay hoveredSlot3D = null;

	// Main Screen Widgets
	private EditBox mobIdInput;
	private EditBox customNameInput;
	private RpgSliderWidget healthSlider;
	private RpgSliderWidget attackSlider;
	private RpgSliderWidget speedSlider;
	private RpgSliderWidget scaleSlider;
	private br.com.rpgsystem.dungeon.client.ui.RpgCheckbox chkConstantParticles;
	private br.com.rpgsystem.dungeon.client.ui.RpgCheckbox chkDropEquipment;
	private EditBox particleInput;
	private RpgSliderWidget intensitySlider;
	private EditBox soundInput;
	private RpgButton btnSoundPreview;
	private EditBox dropChanceInput;
	private RpgButton btnOk;

	private RpgScrollArea tablesScrollArea;
	private RpgScrollArea itemsScrollArea;

	// Equipment Modal State
	private boolean modalOpen = false;
	private boolean modalExpanded = false;
	private EquipmentSlot editingSlot = null;
	private EditBox modalItemInput;
	private ItemStack modalIdentifiedStack = ItemStack.EMPTY;
	private boolean isModalItemValid = true;
	private String modalValidationError = "";
	private int modalDurability = 0;
	private RpgSliderWidget modalDurabilitySlider;
	private RpgSliderWidget modalDropChanceSlider;
	private EditBox modalEnchantInput;
	private int modalEnchLevel = 1;
	private final Map<String, Integer> modalEnchantments = new LinkedHashMap<>();
	private RpgScrollArea modalEnchantsScrollArea;

	// Tooltip hovered
	private String hoveredHitboxTooltip = "";
	private ItemStack hoveredItem = ItemStack.EMPTY;
	private String hoveredItemText = "";

	public MobSpawnerToolScreen(String json) {
		super(Component.literal("Configuração de Mob"), "Defina atributos, equipamentos e drops do mob");
		this.initialJson = json;
		this.showCloseButton = true;
		parseJson();
		updateAccentColor();
	}

	private void updateAccentColor() {
		if (category == MobSpawnData.Category.BOSS) {
			this.windowAccentColor = RpgUi.RED;
		} else if (category == MobSpawnData.Category.MINI_BOSS) {
			this.windowAccentColor = RpgUi.GOLD;
		} else {
			this.windowAccentColor = RpgUi.GREEN;
		}
	}

	private void parseJson() {
		try {
			JsonObject obj = JsonParser.parseString(initialJson).getAsJsonObject();
			if (obj.has("category")) category = MobSpawnData.Category.valueOf(obj.get("category").getAsString());
			if (obj.has("entityId")) entityId = obj.get("entityId").getAsString();
			if (obj.has("customName")) customName = obj.get("customName").getAsString();
			if (obj.has("particleId")) particleId = obj.get("particleId").getAsString();
			if (obj.has("particleIntensity")) particleIntensity = obj.get("particleIntensity").getAsFloat();
			if (obj.has("soundId")) soundId = obj.get("soundId").getAsString();
			if (obj.has("dropChance")) dropChance = obj.get("dropChance").getAsDouble();
			if (obj.has("lootTableName")) selectedLootTable = obj.get("lootTableName").getAsString();
			if (obj.has("scale")) scale = obj.get("scale").getAsFloat();
			if (obj.has("healthMultiplier")) healthMultiplier = obj.get("healthMultiplier").getAsFloat();
			if (obj.has("attackMultiplier")) attackMultiplier = obj.get("attackMultiplier").getAsFloat();
			if (obj.has("speedMultiplier")) speedMultiplier = obj.get("speedMultiplier").getAsFloat();
			if (obj.has("constantParticles")) constantParticles = obj.get("constantParticles").getAsBoolean();
			if (obj.has("dropEquipment")) dropEquipment = obj.get("dropEquipment").getAsBoolean();

			configuredEquipment.clear();
			if (obj.has("equipment")) {
				JsonObject eqObj = obj.getAsJsonObject("equipment");
				for (String slotKey : eqObj.keySet()) {
					JsonObject itObj = eqObj.getAsJsonObject(slotKey);
					MobSpawnData.EquippedItemData eqItem = new MobSpawnData.EquippedItemData();
					if (itObj.has("itemId")) eqItem.setItemId(itObj.get("itemId").getAsString());
					if (itObj.has("durability")) eqItem.setDurability(itObj.get("durability").getAsInt());
					if (itObj.has("maxDamage")) eqItem.setMaxDamage(itObj.get("maxDamage").getAsInt());
					if (itObj.has("dropChance")) eqItem.setDropChance(itObj.get("dropChance").getAsDouble());
					if (itObj.has("enchantments")) {
						JsonObject enchObj = itObj.getAsJsonObject("enchantments");
						for (String eKey : enchObj.keySet()) {
							eqItem.getEnchantments().put(eKey, enchObj.get(eKey).getAsInt());
						}
					}
					configuredEquipment.put(slotKey, eqItem);
				}
			}

			if (obj.has("hasPos")) {
				hasPos = obj.get("hasPos").getAsBoolean();
				if (hasPos) {
					posX = obj.get("posX").getAsInt();
					posY = obj.get("posY").getAsInt();
					posZ = obj.get("posZ").getAsInt();
				}
			}

			availableLootTables.clear();
			if (obj.has("availableLootTables")) {
				for (JsonElement el : obj.getAsJsonArray("availableLootTables")) {
					availableLootTables.add(el.getAsString());
				}
			}

			lootTablesData.clear();
			if (obj.has("lootTablesData")) {
				JsonObject tablesObj = obj.getAsJsonObject("lootTablesData");
				for (String tName : tablesObj.keySet()) {
					List<ChestToolScreen.LootItemDisplay> list = new ArrayList<>();
					JsonArray arr = tablesObj.getAsJsonArray(tName);
					for (JsonElement itEl : arr) {
						JsonObject itObj = itEl.getAsJsonObject();
						String itemId = itObj.get("id").getAsString();
						int count = itObj.has("count") ? itObj.get("count").getAsInt() : 1;
						String name = itObj.has("name") ? itObj.get("name").getAsString() : "";
						double weight = itObj.has("weight") ? itObj.get("weight").getAsDouble() : 1.0;

						Identifier loc = Identifier.tryParse(itemId);
						ItemStack stack = ItemStack.EMPTY;
						if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
							stack = new ItemStack(BuiltInRegistries.ITEM.getValue(loc), count);
						}
						list.add(new ChestToolScreen.LootItemDisplay(stack, name, weight));
					}
					lootTablesData.put(tName, list);
				}
			}

			if (selectedLootTable.isBlank() && !availableLootTables.isEmpty() && !obj.has("lootTableName")) {
				selectedLootTable = availableLootTables.getFirst();
			}
		} catch (Throwable ignored) {}
	}

	@Override
	protected void layoutWindow() {
		panelWidth = 480;
		panelHeight = 310;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	protected void init() {
		super.init();

		updateDummyEntity();

		int previewW = 140;
		int previewH = 125;
		int previewX = panelX + 12;
		int previewY = panelY + headerHeight + 5;

		// Sliders da coluna esquerda (abaixo do preview 3D)
		int sliderY = previewY + previewH + 4;
		healthSlider = new RpgSliderWidget(previewX, sliderY, previewW, 15,
				0.5, 10.0, healthMultiplier,
				val -> this.healthMultiplier = val.floatValue())
				.setTextFormatter(val -> String.format(Locale.ROOT, "Vida: %.1fx", val));
		healthSlider.setAccentColor(0xFFFF4444);
		addRenderableWidget(healthSlider);

		sliderY += 17;
		attackSlider = new RpgSliderWidget(previewX, sliderY, previewW, 15,
				0.5, 10.0, attackMultiplier,
				val -> this.attackMultiplier = val.floatValue())
				.setTextFormatter(val -> String.format(Locale.ROOT, "Ataque: %.1fx", val));
		attackSlider.setAccentColor(0xFFFF9933);
		addRenderableWidget(attackSlider);

		sliderY += 17;
		speedSlider = new RpgSliderWidget(previewX, sliderY, previewW, 15,
				0.2, 3.0, speedMultiplier,
				val -> this.speedMultiplier = val.floatValue())
				.setTextFormatter(val -> String.format(Locale.ROOT, "Velocidade: %.1fx", val));
		speedSlider.setAccentColor(RpgUi.CYAN);
		addRenderableWidget(speedSlider);

		sliderY += 17;
		scaleSlider = new RpgSliderWidget(previewX, sliderY, previewW, 15,
				0.5, 4.0, scale,
				val -> this.scale = val.floatValue())
				.setTextFormatter(val -> String.format(Locale.ROOT, "Escala: %.1fx", val));
		scaleSlider.setAccentColor(category == MobSpawnData.Category.BOSS ? RpgUi.RED : RpgUi.GOLD);
		addRenderableWidget(scaleSlider);

		sliderY += 17;
		chkDropEquipment = addRenderableWidget(new br.com.rpgsystem.dungeon.client.ui.RpgCheckbox(
				previewX, sliderY, "Dropar Equip.", dropEquipment,
				val -> this.dropEquipment = val));

		sliderY += 16;
		chkConstantParticles = addRenderableWidget(new br.com.rpgsystem.dungeon.client.ui.RpgCheckbox(
				previewX, sliderY, "Partículas Const.", constantParticles,
				val -> this.constantParticles = val));

		// Coluna Direita (X = panelX + 162)
		int rightX = panelX + 162;
		int curY = previewY;

		// Linha 1: Mob ID e Nome
		mobIdInput = new EditBox(font, rightX + 46, curY, 100, 16, Component.literal("ID"));
		mobIdInput.setMaxLength(256);
		mobIdInput.setValue(entityId);
		mobIdInput.setResponder(val -> {
			this.entityId = val.trim();
			updateDummyEntity();
		});
		addRenderableWidget(mobIdInput);

		customNameInput = new EditBox(font, rightX + 188, curY, 106, 16, Component.literal("Nome"));
		customNameInput.setMaxLength(64);
		customNameInput.setValue(customName);
		customNameInput.setResponder(val -> this.customName = val);
		addRenderableWidget(customNameInput);

		curY += 22;

		// Linha 2: Partícula e Intensidade
		particleInput = new EditBox(font, rightX + 56, curY, 90, 16, Component.literal("Partícula"));
		particleInput.setMaxLength(256);
		particleInput.setValue(particleId);
		particleInput.setResponder(val -> this.particleId = val.trim());
		addRenderableWidget(particleInput);

		intensitySlider = new RpgSliderWidget(rightX + 194, curY, 100, 16,
				0.1, 2.0, particleIntensity,
				val -> this.particleIntensity = val.floatValue())
				.setTextFormatter(val -> String.format(Locale.ROOT, "%.1fx", val));
		intensitySlider.setAccentColor(RpgUi.BLUE);
		addRenderableWidget(intensitySlider);

		curY += 22;

		// Linha 3: Som ID, botão de preview e Chance de drop (%)
		soundInput = new EditBox(font, rightX + 46, curY, 146, 16, Component.literal("Som"));
		soundInput.setMaxLength(256);
		soundInput.setValue(soundId);
		soundInput.setResponder(val -> {
			this.soundId = val.trim();
			updateSoundPreviewVisibility();
		});
		addRenderableWidget(soundInput);

		btnSoundPreview = new RpgButton(rightX + 196, curY, 18, 16, Component.literal("🔊"), RpgUi.CYAN, this::playSoundPreview);
		addRenderableWidget(btnSoundPreview);
		updateSoundPreviewVisibility();

		dropChanceInput = new EditBox(font, rightX + 248, curY, 32, 16, Component.literal("Drop"));
		dropChanceInput.setMaxLength(16);
		dropChanceInput.setValue(String.format(Locale.ROOT, "%.0f", dropChance));
		dropChanceInput.setResponder(val -> {
			try { this.dropChance = Double.parseDouble(val.trim()); } catch (NumberFormatException ignored) {}
		});
		addRenderableWidget(dropChanceInput);

		// Linha 4 e 5: Tabelas de Looting e Grid de Drops
		int scrollH = 115;
		int tableListW = 120;
		int itemsGridW = 170;
		int panelsY = previewY + 78;

		tablesScrollArea = new RpgScrollArea(rightX, panelsY, tableListW, scrollH);
		tablesScrollArea.setContentHeight(availableLootTables.size() * 18);

		itemsScrollArea = new RpgScrollArea(rightX + tableListW + 4, panelsY, itemsGridW, scrollH);
		updateItemsScrollHeight();

		// Botão OK centralizado
		int btnW = 110;
		int btnH = 18;
		btnOk = new RpgButton(panelX + (panelWidth - btnW) / 2, panelY + panelHeight - btnH - 6,
				btnW, btnH, Component.literal("OK"), RpgUi.GREEN, this::saveAndClose);
		addRenderableWidget(btnOk);

		// Inicializa widgets do Modal de Equipamento
		initModalWidgets();
	}

	private void setMainWidgetsVisible(boolean visible) {
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget) {
				widget.visible = visible;
			}
		}
		if (visible) {
			updateSoundPreviewVisibility();
		}
	}

	private static PoseStack copyPoseStack(PoseStack base) {
		PoseStack ps = new PoseStack();
		ps.mulPose(base.last().pose());
		return ps;
	}

	private static Box3D createProjectedBox(PoseStack ps, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		Vector3f[] pts = new Vector3f[8];
		var mat = ps.last().pose();
		pts[0] = mat.transformPosition(minX, minY, minZ, new Vector3f());
		pts[1] = mat.transformPosition(maxX, minY, minZ, new Vector3f());
		pts[2] = mat.transformPosition(maxX, maxY, minZ, new Vector3f());
		pts[3] = mat.transformPosition(minX, maxY, minZ, new Vector3f());
		pts[4] = mat.transformPosition(minX, minY, maxZ, new Vector3f());
		pts[5] = mat.transformPosition(maxX, minY, maxZ, new Vector3f());
		pts[6] = mat.transformPosition(maxX, maxY, maxZ, new Vector3f());
		pts[7] = mat.transformPosition(minX, maxY, maxZ, new Vector3f());
		return new Box3D(pts);
	}

	private boolean canMobWearArmor(LivingEntity entity) {
		if (entity == null) return false;
		return entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| entity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
				|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
				|| entity instanceof net.minecraft.world.entity.player.Player
				|| entity instanceof net.minecraft.world.entity.decoration.ArmorStand;
	}

	private boolean canMobHoldItems(LivingEntity entity) {
		if (entity == null) return false;
		if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| entity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
				|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
				|| entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager
				|| entity instanceof net.minecraft.world.entity.monster.Witch
				|| entity instanceof net.minecraft.world.entity.monster.Enderman
				|| entity instanceof net.minecraft.world.entity.monster.Vex
				|| entity instanceof net.minecraft.world.entity.animal.fox.Fox
				|| entity instanceof net.minecraft.world.entity.animal.allay.Allay
				|| entity instanceof net.minecraft.world.entity.player.Player
				|| entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return true;
		}
		return false;
	}

	private void compute3DOverlays(LivingEntityRenderState state, EntityRenderer<LivingEntity, LivingEntityRenderState> renderer,
	                              float centerX, float centerY, int renderScale, float heightOffset,
	                              Quaternionf q1) {
		active3DOverlays.clear();

		PoseStack basePs = new PoseStack();
		basePs.translate(centerX, centerY, 0.0F);
		basePs.scale(renderScale, renderScale, -renderScale);
		basePs.translate(0.0F, heightOffset, 0.0F);
		basePs.rotate(q1);
		basePs.rotate(new Quaternionf().rotateY((float) Math.toRadians(-previewYaw)));

		boolean canArmor = canMobWearArmor(dummyEntity);
		boolean canHold = canMobHoldItems(dummyEntity);

		var model = (renderer instanceof LivingEntityRenderer livingRenderer) ? livingRenderer.getModel() : null;

		if (model instanceof HumanoidModel<?> humModel) {
			try {
				@SuppressWarnings("unchecked")
				HumanoidModel<HumanoidRenderState> hModel = (HumanoidModel<HumanoidRenderState>) humModel;
				hModel.setupAnim((HumanoidRenderState) state);
			} catch (Throwable ignored) {}

			if (canArmor) {
				// 1. Cabeça
				Slot3DOverlay headOverlay = new Slot3DOverlay(EquipmentSlot.HEAD, "Cabeça");
				PoseStack psHead = copyPoseStack(basePs);
				psHead.scale(-1.0F, -1.0F, 1.0F);
				psHead.translate(0.0F, -1.501F, 0.0F);
				humModel.head.translateAndRotate(psHead);
				headOverlay.boxes.add(createProjectedBox(psHead, -4.2F / 16F, -8.2F / 16F, -4.2F / 16F, 4.2F / 16F, 0.2F / 16F, 4.2F / 16F));
				active3DOverlays.add(headOverlay);

				// 2. Peito (Corpo)
				Slot3DOverlay chestOverlay = new Slot3DOverlay(EquipmentSlot.CHEST, "Peito");
				PoseStack psChest = copyPoseStack(basePs);
				psChest.scale(-1.0F, -1.0F, 1.0F);
				psChest.translate(0.0F, -1.501F, 0.0F);
				humModel.body.translateAndRotate(psChest);
				chestOverlay.boxes.add(createProjectedBox(psChest, -4.2F / 16F, -0.2F / 16F, -2.2F / 16F, 4.2F / 16F, 12.2F / 16F, 2.2F / 16F));
				active3DOverlays.add(chestOverlay);
			}

			// 3. Braços / Mãos
			if (canHold) {
				boolean isRightMain = dummyEntity == null || dummyEntity.getMainArm() == HumanoidArm.RIGHT;
				EquipmentSlot rightSlot = isRightMain ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
				String rightName = isRightMain ? "Mão Principal (Direita)" : "Mão Secundária (Direita)";
				EquipmentSlot leftSlot = isRightMain ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
				String leftName = isRightMain ? "Mão Secundária (Esquerda)" : "Mão Principal (Esquerda)";

				// Braço Direito
				Slot3DOverlay rightArmOverlay = new Slot3DOverlay(rightSlot, rightName);
				PoseStack psRightArm = copyPoseStack(basePs);
				psRightArm.scale(-1.0F, -1.0F, 1.0F);
				psRightArm.translate(0.0F, -1.501F, 0.0F);
				humModel.rightArm.translateAndRotate(psRightArm);
				rightArmOverlay.boxes.add(createProjectedBox(psRightArm, -3.2F / 16F, -2.2F / 16F, -2.2F / 16F, 1.2F / 16F, 10.2F / 16F, 2.2F / 16F));
				active3DOverlays.add(rightArmOverlay);

				// Braço Esquerdo
				Slot3DOverlay leftArmOverlay = new Slot3DOverlay(leftSlot, leftName);
				PoseStack psLeftArm = copyPoseStack(basePs);
				psLeftArm.scale(-1.0F, -1.0F, 1.0F);
				psLeftArm.translate(0.0F, -1.501F, 0.0F);
				humModel.leftArm.translateAndRotate(psLeftArm);
				leftArmOverlay.boxes.add(createProjectedBox(psLeftArm, -1.2F / 16F, -2.2F / 16F, -2.2F / 16F, 3.2F / 16F, 10.2F / 16F, 2.2F / 16F));
				active3DOverlays.add(leftArmOverlay);
			}

			if (canArmor) {
				// 4. Pernas (Calças)
				Slot3DOverlay legsOverlay = new Slot3DOverlay(EquipmentSlot.LEGS, "Pernas");
				PoseStack psRightLeg = copyPoseStack(basePs);
				psRightLeg.scale(-1.0F, -1.0F, 1.0F);
				psRightLeg.translate(0.0F, -1.501F, 0.0F);
				humModel.rightLeg.translateAndRotate(psRightLeg);
				legsOverlay.boxes.add(createProjectedBox(psRightLeg, -2.2F / 16F, -0.2F / 16F, -2.2F / 16F, 2.2F / 16F, 7.0F / 16F, 2.2F / 16F));

				PoseStack psLeftLeg = copyPoseStack(basePs);
				psLeftLeg.scale(-1.0F, -1.0F, 1.0F);
				psLeftLeg.translate(0.0F, -1.501F, 0.0F);
				humModel.leftLeg.translateAndRotate(psLeftLeg);
				legsOverlay.boxes.add(createProjectedBox(psLeftLeg, -2.2F / 16F, -0.2F / 16F, -2.2F / 16F, 2.2F / 16F, 7.0F / 16F, 2.2F / 16F));
				active3DOverlays.add(legsOverlay);

				// 5. Pés (Botas)
				Slot3DOverlay feetOverlay = new Slot3DOverlay(EquipmentSlot.FEET, "Pés");
				feetOverlay.boxes.add(createProjectedBox(psRightLeg, -2.2F / 16F, 7.0F / 16F, -2.2F / 16F, 2.2F / 16F, 12.2F / 16F, 2.2F / 16F));
				feetOverlay.boxes.add(createProjectedBox(psLeftLeg, -2.2F / 16F, 7.0F / 16F, -2.2F / 16F, 2.2F / 16F, 12.2F / 16F, 2.2F / 16F));
				active3DOverlays.add(feetOverlay);
			}

		} else {
			// Fallback para outros tipos de mob
			float w = dummyEntity != null ? dummyEntity.getBbWidth() : 0.6F;
			float h = dummyEntity != null ? dummyEntity.getBbHeight() : 1.8F;

			if (canArmor) {
				Slot3DOverlay headOverlay = new Slot3DOverlay(EquipmentSlot.HEAD, "Cabeça");
				headOverlay.boxes.add(createProjectedBox(basePs, -w * 0.35F, h * 0.70F, -w * 0.35F, w * 0.35F, h * 1.02F, w * 0.35F));
				active3DOverlays.add(headOverlay);

				Slot3DOverlay chestOverlay = new Slot3DOverlay(EquipmentSlot.CHEST, "Peito");
				chestOverlay.boxes.add(createProjectedBox(basePs, -w * 0.40F, h * 0.35F, -w * 0.35F, w * 0.40F, h * 0.70F, w * 0.35F));
				active3DOverlays.add(chestOverlay);

				Slot3DOverlay legsOverlay = new Slot3DOverlay(EquipmentSlot.LEGS, "Pernas");
				legsOverlay.boxes.add(createProjectedBox(basePs, -w * 0.35F, h * 0.15F, -w * 0.35F, w * 0.35F, h * 0.35F, w * 0.35F));
				active3DOverlays.add(legsOverlay);

				Slot3DOverlay feetOverlay = new Slot3DOverlay(EquipmentSlot.FEET, "Pés");
				feetOverlay.boxes.add(createProjectedBox(basePs, -w * 0.35F, -0.02F, -w * 0.35F, w * 0.35F, h * 0.15F, w * 0.35F));
				active3DOverlays.add(feetOverlay);
			}

			if (canHold) {
				Slot3DOverlay mainHand = new Slot3DOverlay(EquipmentSlot.MAINHAND, "Mão Principal (Direita)");
				mainHand.boxes.add(createProjectedBox(basePs, w * 0.35F, h * 0.25F, -w * 0.25F, w * 0.65F, h * 0.65F, w * 0.25F));
				active3DOverlays.add(mainHand);

				Slot3DOverlay offHand = new Slot3DOverlay(EquipmentSlot.OFFHAND, "Mão Secundária (Esquerda)");
				offHand.boxes.add(createProjectedBox(basePs, -w * 0.65F, h * 0.25F, -w * 0.25F, -w * 0.35F, h * 0.65F, w * 0.25F));
				active3DOverlays.add(offHand);
			}
		}
	}

	private void render3DBox(GuiGraphicsExtractor graphics, Box3D box, int fillColor, int edgeColor,
	                         boolean fill, int clipX0, int clipY0, int clipX1, int clipY1) {
		if (fill && (fillColor >>> 24) > 0) {
			renderQuad(graphics, box.vertices[0], box.vertices[1], box.vertices[2], box.vertices[3], fillColor, clipX0, clipY0, clipX1, clipY1);
			renderQuad(graphics, box.vertices[5], box.vertices[4], box.vertices[7], box.vertices[6], fillColor, clipX0, clipY0, clipX1, clipY1);
			renderQuad(graphics, box.vertices[3], box.vertices[2], box.vertices[6], box.vertices[7], fillColor, clipX0, clipY0, clipX1, clipY1);
			renderQuad(graphics, box.vertices[0], box.vertices[4], box.vertices[5], box.vertices[1], fillColor, clipX0, clipY0, clipX1, clipY1);
			renderQuad(graphics, box.vertices[4], box.vertices[0], box.vertices[3], box.vertices[7], fillColor, clipX0, clipY0, clipX1, clipY1);
			renderQuad(graphics, box.vertices[1], box.vertices[5], box.vertices[6], box.vertices[2], fillColor, clipX0, clipY0, clipX1, clipY1);
		}

		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		for (int[] e : edges) {
			drawLine(graphics, box.vertices[e[0]].x, box.vertices[e[0]].y,
					box.vertices[e[1]].x, box.vertices[e[1]].y, edgeColor, clipX0, clipY0, clipX1, clipY1);
		}
	}

	private void renderQuad(GuiGraphicsExtractor graphics, Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
	                        int color, int clipX0, int clipY0, int clipX1, int clipY1) {
		renderTriangle(graphics, v0, v1, v2, color, clipX0, clipY0, clipX1, clipY1);
		renderTriangle(graphics, v0, v2, v3, color, clipX0, clipY0, clipX1, clipY1);
	}

	private void renderTriangle(GuiGraphicsExtractor graphics, Vector3f a, Vector3f b, Vector3f c,
	                            int color, int clipX0, int clipY0, int clipX1, int clipY1) {
		Vector3f p0 = a, p1 = b, p2 = c;
		if (p0.y > p1.y) { var tmp = p0; p0 = p1; p1 = tmp; }
		if (p1.y > p2.y) { var tmp = p1; p1 = p2; p2 = tmp; }
		if (p0.y > p1.y) { var tmp = p0; p0 = p1; p1 = tmp; }

		float y0 = p0.y, y1 = p1.y, y2 = p2.y;
		if (Math.abs(y2 - y0) < 0.001F) return;

		int startY = Math.max(clipY0, (int) Math.ceil(y0));
		int endY = Math.min(clipY1 - 1, (int) Math.floor(y2));

		for (int y = startY; y <= endY; y++) {
			float tA = (y - y0) / (y2 - y0);
			float xA = p0.x + tA * (p2.x - p0.x);

			float xB;
			if (y < y1) {
				if (Math.abs(y1 - y0) < 0.001F) xB = p0.x;
				else {
					float tB = (y - y0) / (y1 - y0);
					xB = p0.x + tB * (p1.x - p0.x);
				}
			} else {
				if (Math.abs(y2 - y1) < 0.001F) xB = p1.x;
				else {
					float tB = (y - y1) / (y2 - y1);
					xB = p1.x + tB * (p2.x - p1.x);
				}
			}

			int minX = Math.max(clipX0, (int) Math.floor(Math.min(xA, xB)));
			int maxX = Math.min(clipX1, (int) Math.ceil(Math.max(xA, xB)));
			if (minX < maxX) {
				graphics.fill(minX, y, maxX, y + 1, color);
			}
		}
	}

	private void drawLine(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color,
	                      int clipX0, int clipY0, int clipX1, int clipY1) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dist = (float) Math.hypot(dx, dy);
		if (dist < 0.5F) return;
		int steps = (int) Math.ceil(dist);
		float stepX = dx / steps;
		float stepY = dy / steps;
		float cx = x0;
		float cy = y0;
		for (int i = 0; i <= steps; i++) {
			int ix = (int) Math.round(cx);
			int iy = (int) Math.round(cy);
			if (ix >= clipX0 && ix < clipX1 && iy >= clipY0 && iy < clipY1) {
				graphics.fill(ix, iy, ix + 1, iy + 1, color);
			}
			cx += stepX;
			cy += stepY;
		}
	}

	private static boolean isPointInTriangle(float px, float py, Vector3f a, Vector3f b, Vector3f c) {
		float cross1 = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x);
		float cross2 = (c.x - b.x) * (py - b.y) - (c.y - b.y) * (px - b.x);
		float cross3 = (a.x - c.x) * (py - c.y) - (a.y - c.y) * (px - c.x);
		boolean hasNeg = (cross1 < 0) || (cross2 < 0) || (cross3 < 0);
		boolean hasPos = (cross1 > 0) || (cross2 > 0) || (cross3 > 0);
		return !(hasNeg && hasPos);
	}

	private void initModalWidgets() {
		int modalW = 320;
		int modalH = 120;
		int modalX = (width - modalW) / 2;
		int modalY = (height - modalH) / 2;

		modalItemInput = new EditBox(font, modalX + 42, modalY + 27, modalW - 56, 18, Component.literal("Item"));
		modalItemInput.setMaxLength(256);
		modalItemInput.setResponder(val -> updateModalIdentifiedItem(val.trim()));

		modalEnchantInput = new EditBox(font, modalX + 14, modalY + 88, 130, 16, Component.literal("Encantamento"));
		modalEnchantInput.setMaxLength(128);

		modalEnchantsScrollArea = new RpgScrollArea(modalX + 14, modalY + 110, modalW - 28, 75);
	}

	private void updateDummyEntity() {
		Identifier loc = Identifier.tryParse(entityId);
		if (loc != null && BuiltInRegistries.ENTITY_TYPE.containsKey(loc) && minecraft != null && minecraft.level != null) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(loc);
			try {
				net.minecraft.world.entity.Entity e = type.create(minecraft.level, EntitySpawnReason.LOAD);
				if (e instanceof LivingEntity living) {
					dummyEntity = living;
					dummyEntity.setId(-1000); // Atribui ID para evitar IllegalStateException no ItemModelResolver
					if (minecraft.player != null) {
						dummyEntity.setPos(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
						dummyEntity.xo = minecraft.player.getX();
						dummyEntity.yo = minecraft.player.getY();
						dummyEntity.zo = minecraft.player.getZ();
						dummyEntity.tickCount = minecraft.player.tickCount;
					}
					if (dummyEntity instanceof Mob mob) {
						mob.setNoAi(true);
					}
					dummyEntity.setYRot(180.0F + previewYaw);
					dummyEntity.yBodyRot = 180.0F + previewYaw;
					dummyEntity.yHeadRot = 180.0F + previewYaw;
					dummyEntity.yHeadRotO = dummyEntity.yHeadRot;
					dummyEntity.yBodyRotO = dummyEntity.yBodyRot;
					dummyEntity.setXRot(0.0F);
					dummyEntity.xRotO = 0.0F;
					updateDummyEntityEquipment();
					return;
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		dummyEntity = null;
	}

	private void updateDummyEntityEquipment() {
		if (dummyEntity == null) return;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			dummyEntity.setItemSlot(slot, ItemStack.EMPTY);
		}
		boolean canArmor = canMobWearArmor(dummyEntity);
		boolean canHold = canMobHoldItems(dummyEntity);
		if (!canArmor) {
			configuredEquipment.remove("HEAD");
			configuredEquipment.remove("CHEST");
			configuredEquipment.remove("LEGS");
			configuredEquipment.remove("FEET");
		}
		if (!canHold) {
			configuredEquipment.remove("MAINHAND");
			configuredEquipment.remove("OFFHAND");
		}
		for (Map.Entry<String, MobSpawnData.EquippedItemData> entry : configuredEquipment.entrySet()) {
			EquipmentSlot slot = parseSlot(entry.getKey());
			if (slot != null && entry.getValue() != null) {
				ItemStack stack = buildStackFromData(entry.getValue());
				if (!stack.isEmpty()) {
					dummyEntity.setItemSlot(slot, stack);
				}
			}
		}
	}

	private EquipmentSlot parseSlot(String slotName) {
		if (slotName == null || slotName.isBlank()) return null;
		try {
			EquipmentSlot byName = EquipmentSlot.byName(slotName.toLowerCase(Locale.ROOT));
			if (byName != null) return byName;
		} catch (Throwable ignored) {}
		try {
			return EquipmentSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
		} catch (Throwable ignored) {}
		return null;
	}

	private String getSlotDisplayName(EquipmentSlot slot) {
		if (slot == null) return "";
		return switch (slot) {
			case HEAD -> "Cabeça";
			case CHEST -> "Peito";
			case LEGS -> "Pernas";
			case FEET -> "Pés";
			case MAINHAND -> "Mão Principal (Direita)";
			case OFFHAND -> "Mão Secundária (Esquerda)";
			default -> slot.getName();
		};
	}

	private ItemStack buildStackFromData(MobSpawnData.EquippedItemData data) {
		if (data == null || data.getItemId().isBlank()) return ItemStack.EMPTY;
		Identifier id = Identifier.tryParse(data.getItemId());
		if (id == null) return ItemStack.EMPTY;
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR) return ItemStack.EMPTY;

		ItemStack stack = new ItemStack(item);
		if (stack.isDamageableItem()) {
			int maxDmg = stack.getMaxDamage();
			if (data.getDurability() > 0 && data.getDurability() <= maxDmg) {
				stack.setDamageValue(Math.max(0, maxDmg - data.getDurability()));
			}
		}
		if (!data.getEnchantments().isEmpty() && minecraft != null && minecraft.level != null) {
			var enchLookup = minecraft.level.registryAccess().lookup(Registries.ENCHANTMENT);
			if (enchLookup.isPresent()) {
				for (Map.Entry<String, Integer> e : data.getEnchantments().entrySet()) {
					Identifier enchId = Identifier.tryParse(e.getKey());
					if (enchId != null) {
						var opt = enchLookup.get().get(ResourceKey.create(Registries.ENCHANTMENT, enchId));
						if (opt.isPresent()) {
							stack.enchant(opt.get(), Math.max(1, e.getValue()));
						}
					}
				}
			}
		}
		return stack;
	}

	private void openEquipmentModal(EquipmentSlot slot) {
		this.editingSlot = slot;
		this.modalOpen = true;
		this.modalExpanded = false;
		this.modalValidationError = "";
		this.modalEnchantments.clear();
		this.modalEnchLevel = 1;

		setMainWidgetsVisible(false);

		MobSpawnData.EquippedItemData existing = configuredEquipment.get(slot.name());
		if (existing != null && !existing.getItemId().isBlank()) {
			modalItemInput.setValue(existing.getItemId());
			modalDurability = existing.getDurability();
			modalDropChance = existing.getDropChance();
			modalEnchantments.putAll(existing.getEnchantments());
			updateModalIdentifiedItem(existing.getItemId());
		} else {
			modalItemInput.setValue("");
			modalIdentifiedStack = ItemStack.EMPTY;
			modalDurability = 0;
			modalDropChance = 100.0;
			isModalItemValid = true;
		}

		modalItemInput.setFocused(true);
		modalEnchantInput.setFocused(false);
		RpgUiSounds.playClick();
	}

	private void closeEquipmentModal() {
		this.modalOpen = false;
		setMainWidgetsVisible(true);
		RpgUiSounds.playClick();
	}

	private void updateModalIdentifiedItem(String query) {
		if (query.isBlank()) {
			modalIdentifiedStack = ItemStack.EMPTY;
			isModalItemValid = true;
			modalValidationError = "";
			return;
		}

		Item found = null;
		Identifier direct = Identifier.tryParse(query);
		if (direct != null && BuiltInRegistries.ITEM.containsKey(direct)) {
			found = BuiltInRegistries.ITEM.getValue(direct);
		} else {
			Identifier mc = Identifier.tryParse("minecraft:" + query.toLowerCase(Locale.ROOT).replace(' ', '_'));
			if (mc != null && BuiltInRegistries.ITEM.containsKey(mc)) {
				found = BuiltInRegistries.ITEM.getValue(mc);
			} else {
				for (Item it : BuiltInRegistries.ITEM) {
					if (it.getName(new ItemStack(it)).getString().equalsIgnoreCase(query)
							|| BuiltInRegistries.ITEM.getKey(it).getPath().equalsIgnoreCase(query)) {
						found = it;
						break;
					}
				}
			}
		}

		if (found == null || found == Items.AIR) {
			modalIdentifiedStack = ItemStack.EMPTY;
			isModalItemValid = false;
			modalValidationError = "Item não encontrado!";
			return;
		}

		ItemStack stack = new ItemStack(found);
		if (!isStackValidForSlot(stack, editingSlot)) {
			modalIdentifiedStack = stack;
			isModalItemValid = false;
			if (editingSlot == EquipmentSlot.MAINHAND || editingSlot == EquipmentSlot.OFFHAND) {
				modalValidationError = "Este mob não segura itens!";
			} else {
				modalValidationError = "Item não é vestível neste local do corpo!";
			}
			return;
		}

		modalIdentifiedStack = stack;
		isModalItemValid = true;
		modalValidationError = "";

		if (stack.isDamageableItem()) {
			int maxDmg = stack.getMaxDamage();
			if (modalDurability <= 0 || modalDurability > maxDmg) {
				modalDurability = maxDmg;
			}
			updateModalDurabilitySlider(maxDmg);
		}
	}

	private boolean isStackValidForSlot(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) return true;
		if (dummyEntity == null) return true;

		if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
			if (!canMobWearArmor(dummyEntity)) return false;
			return dummyEntity.isEquippableInSlot(stack, slot) || dummyEntity.getEquipmentSlotForItem(stack) == slot;
		}

		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
			return canMobHoldItems(dummyEntity);
		}

		return true;
	}

	private void updateModalDurabilitySlider(int maxDmg) {
		int modalW = 320;
		int modalH = modalExpanded ? 260 : 120;
		int modalX = (width - modalW) / 2;
		int modalY = (height - modalH) / 2;

		modalDurabilitySlider = new RpgSliderWidget(modalX + 14, modalY + 54, modalW - 28, 16,
				1, maxDmg, modalDurability,
				val -> this.modalDurability = val.intValue())
				.setTextFormatter(val -> "Durabilidade: " + val.intValue() + "/" + maxDmg);
		modalDurabilitySlider.setAccentColor(RpgUi.CYAN);
	}

	private void updateModalDropChanceSlider() {
		int modalW = 320;
		int modalH = modalExpanded ? 260 : 120;
		int modalX = (width - modalW) / 2;
		int modalY = (height - modalH) / 2;
		int expY = modalY + 54 + (modalIdentifiedStack.isDamageableItem() ? 20 : 18);

		modalDropChanceSlider = new RpgSliderWidget(modalX + 14, expY, modalW - 28, 16,
				0, 100, modalDropChance,
				val -> this.modalDropChance = Math.round(val * 10.0) / 10.0)
				.setTextFormatter(val -> String.format(Locale.ROOT, "Chance de Drop: %.1f%%", val));
		modalDropChanceSlider.setAccentColor(RpgUi.GOLD);
	}

	private void toggleModalExpanded() {
		RpgUiSounds.playClick();
		if (!modalIdentifiedStack.isEmpty() && isModalItemValid) {
			modalExpanded = !modalExpanded;
			if (modalExpanded) {
				if (modalIdentifiedStack.isDamageableItem()) {
					updateModalDurabilitySlider(modalIdentifiedStack.getMaxDamage());
				}
				updateModalDropChanceSlider();
				updateModalEnchantsScroll();
			}
		}
	}

	private void addModalEnchantment() {
		String query = modalEnchantInput.getValue().trim();
		if (query.isBlank() || minecraft == null || minecraft.level == null) return;

		var enchOpt = minecraft.level.registryAccess().lookup(Registries.ENCHANTMENT);
		if (enchOpt.isEmpty()) return;

		Registry<Enchantment> registry = enchOpt.get();
		Identifier foundId = null;

		Identifier direct = Identifier.tryParse(query);
		if (direct != null && registry.containsKey(direct)) {
			foundId = direct;
		} else {
			Identifier mc = Identifier.tryParse("minecraft:" + query.toLowerCase(Locale.ROOT).replace(' ', '_'));
			if (mc != null && registry.containsKey(mc)) {
				foundId = mc;
			} else {
				for (Identifier id : registry.keySet()) {
					if (id.getPath().equalsIgnoreCase(query) || id.toString().equalsIgnoreCase(query)) {
						foundId = id;
						break;
					}
				}
			}
		}

		if (foundId != null) {
			modalEnchantments.put(foundId.toString(), modalEnchLevel);
			modalEnchantInput.setValue("");
			updateModalEnchantsScroll();
			RpgUiSounds.playClick();
		}
	}

	private void updateModalEnchantsScroll() {
		if (modalEnchantsScrollArea != null) {
			modalEnchantsScrollArea.setContentHeight(modalEnchantments.size() * 18);
		}
	}

	private void saveModalEquipment() {
		if (!isModalItemValid) return;

		if (modalIdentifiedStack.isEmpty()) {
			configuredEquipment.remove(editingSlot.name());
		} else {
			MobSpawnData.EquippedItemData itemData = new MobSpawnData.EquippedItemData(
					BuiltInRegistries.ITEM.getKey(modalIdentifiedStack.getItem()).toString());
			if (modalIdentifiedStack.isDamageableItem()) {
				itemData.setDurability(modalDurability);
				itemData.setMaxDamage(modalIdentifiedStack.getMaxDamage());
			}
			itemData.setDropChance(modalDropChance);
			itemData.getEnchantments().putAll(modalEnchantments);
			configuredEquipment.put(editingSlot.name(), itemData);
		}

		updateDummyEntityEquipment();
		closeEquipmentModal();
	}

	private void clearModalEquipment() {
		configuredEquipment.remove(editingSlot.name());
		updateDummyEntityEquipment();
		closeEquipmentModal();
	}

	private void updateSoundPreviewVisibility() {
		if (btnSoundPreview == null) return;
		Identifier loc = Identifier.tryParse(soundId);
		boolean valid = loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc);
		btnSoundPreview.visible = valid;
	}

	private void playSoundPreview() {
		Identifier loc = Identifier.tryParse(soundId);
		if (loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
			SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(loc);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F));
		}
	}

	private void updateItemsScrollHeight() {
		if (itemsScrollArea == null) return;
		List<ChestToolScreen.LootItemDisplay> items = lootTablesData.getOrDefault(selectedLootTable, List.of());
		int rows = (int) Math.ceil(items.size() / 3.0);
		itemsScrollArea.setContentHeight(rows * 22 + 4);
	}

	private void saveAndClose() {
		JsonObject obj = new JsonObject();
		obj.addProperty("category", category.name());
		obj.addProperty("entityId", mobIdInput.getValue().trim().isEmpty() ? "minecraft:zombie" : mobIdInput.getValue().trim());
		obj.addProperty("customName", customNameInput.getValue().trim());
		obj.addProperty("particleId", particleInput.getValue().trim());
		obj.addProperty("particleIntensity", particleIntensity);
		obj.addProperty("soundId", soundInput.getValue().trim());
		obj.addProperty("dropChance", dropChance);
		obj.addProperty("lootTableName", selectedLootTable);
		obj.addProperty("scale", scale);
		obj.addProperty("healthMultiplier", healthMultiplier);
		obj.addProperty("attackMultiplier", attackMultiplier);
		obj.addProperty("speedMultiplier", speedMultiplier);
		obj.addProperty("constantParticles", constantParticles);
		obj.addProperty("dropEquipment", dropEquipment);

		JsonObject eqObj = new JsonObject();
		boolean canArmor = canMobWearArmor(dummyEntity);
		boolean canHold = canMobHoldItems(dummyEntity);
		for (Map.Entry<String, MobSpawnData.EquippedItemData> eq : configuredEquipment.entrySet()) {
			EquipmentSlot slot = parseSlot(eq.getKey());
			if (slot == null) continue;
			if ((slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) && !canArmor) {
				continue;
			}
			if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) && !canHold) {
				continue;
			}
			JsonObject itObj = new JsonObject();
			itObj.addProperty("itemId", eq.getValue().getItemId());
			itObj.addProperty("durability", eq.getValue().getDurability());
			itObj.addProperty("maxDamage", eq.getValue().getMaxDamage());
			itObj.addProperty("dropChance", eq.getValue().getDropChance());
			JsonObject enchs = new JsonObject();
			for (Map.Entry<String, Integer> e : eq.getValue().getEnchantments().entrySet()) {
				enchs.addProperty(e.getKey(), e.getValue());
			}
			itObj.add("enchantments", enchs);
			eqObj.add(eq.getKey(), itObj);
		}
		obj.add("equipment", eqObj);

		obj.addProperty("hasPos", hasPos);
		if (hasPos) {
			obj.addProperty("posX", posX);
			obj.addProperty("posY", posY);
			obj.addProperty("posZ", posZ);
		}

		ClientPlayNetworking.send(new DungeonPayloads.SaveMobTool(obj.toString()));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (modalOpen) {
			renderEquipmentModal(graphics, mouseX, mouseY, delta);
		} else {
			if (!hoveredHitboxTooltip.isBlank()) {
				graphics.setTooltipForNextFrame(Component.literal(hoveredHitboxTooltip), mouseX, mouseY);
			} else if (!hoveredItemText.isBlank()) {
				graphics.setTooltipForNextFrame(Component.literal("§a" + hoveredItemText), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hoveredHitboxTooltip = "";
		hoveredItem = ItemStack.EMPTY;
		hoveredItemText = "";

		int previewW = 140;
		int previewH = 125;
		int previewX = panelX + 12;
		int previewY = panelY + headerHeight + 5;

		// 1. Box do Preview 3D do Mob
		RpgUi.panel(graphics, previewX, previewY, previewW, previewH, 0xFF0B101B, RpgUi.BORDER);

		previewRenderSuccess = false;
		if (dummyEntity != null) {
			try {
				if (dummyEntity.getId() == 0) {
					dummyEntity.setId(-1000);
				}
				if (minecraft.player != null) {
					dummyEntity.setPos(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
					dummyEntity.tickCount = minecraft.player.tickCount;
				}
				dummyEntity.setYRot(180.0F + previewYaw);
				dummyEntity.yBodyRot = 180.0F + previewYaw;
				dummyEntity.yHeadRot = 180.0F + previewYaw;
				dummyEntity.yHeadRotO = dummyEntity.yHeadRot;
				dummyEntity.yBodyRotO = dummyEntity.yBodyRot;
				dummyEntity.setXRot(0.0F);
				dummyEntity.xRotO = 0.0F;
				EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
				@SuppressWarnings("unchecked")
				EntityRenderer<LivingEntity, LivingEntityRenderState> renderer =
						(EntityRenderer<LivingEntity, LivingEntityRenderState>) dispatcher.getRenderer(dummyEntity);
				LivingEntityRenderState state = (LivingEntityRenderState) renderer.createRenderState(dummyEntity, 1.0F);
				state.shadowPieces.clear();
				state.outlineColor = 0;

				state.bodyRot = 180.0F + previewYaw;
				state.yRot = 0.0F; // Cabeça estática alinhada ao corpo
				state.xRot = 0.0F;
				state.boundingBoxWidth = state.boundingBoxWidth / state.scale;
				state.boundingBoxHeight = state.boundingBoxHeight / state.scale;
				state.scale = 1.0F;

				Quaternionf q1 = new Quaternionf().rotateZ((float) Math.PI);
				Quaternionf q2 = new Quaternionf().rotateX(previewPitch * 0.017453292F);
				q1.mul(q2);

				float heightOffset = state.boundingBoxHeight / 2.0F + 0.0625F;
				Vector3f translation = new Vector3f(0.0F, heightOffset, 0.0F);

				int renderScale = (int) ((55.0F / Math.max(0.7F, dummyEntity.getBbHeight())) * scale * previewZoom);

				graphics.entity(state, renderScale, translation, q1, q2,
						previewX + 2, previewY + 2, previewX + previewW - 2, previewY + previewH - 2);
				previewRenderSuccess = true;

				float centerX = previewX + previewW / 2.0F;
				float centerY = previewY + previewH / 2.0F;
				compute3DOverlays(state, renderer, centerX, centerY, renderScale, heightOffset, q1);

				String displayName = (!customName.isBlank()) ? customName : (dummyEntity != null ? dummyEntity.getType().getDescription().getString() : "");
				if (!displayName.isBlank()) {
					int nameW = font.width(displayName);
					int tagX = (int) centerX - nameW / 2;
					int tagY = (int) (centerY - (dummyEntity.getBbHeight() * renderScale * 0.55F) - 12);
					tagY = Math.max(previewY + 4, Math.min(previewY + previewH - 16, tagY));
					graphics.fill(tagX - 3, tagY - 2, tagX + nameW + 3, tagY + font.lineHeight + 1, 0x88000000);
					RpgScreenText.draw(graphics, font, displayName, tagX, tagY, 0xFFFFFFFF, false, true);
				}
			} catch (Throwable t) {
				t.printStackTrace();
				RpgScreenText.draw(graphics, font, "Erro no Preview", previewX + 28, previewY + 65, 0xFFFF4444, false, false);
			}
		} else {
			RpgScreenText.draw(graphics, font, "Mob inválido", previewX + 35, previewY + 65, 0xFF777777, false, false);
		}

		// Hitboxes anatômicas 3D sobrepostas no preview (apenas se renderizado com sucesso)
		hoveredSlot3D = null;
		if (!modalOpen && previewRenderSuccess) {
			int clipX0 = previewX + 2;
			int clipY0 = previewY + 2;
			int clipX1 = previewX + previewW - 2;
			int clipY1 = previewY + previewH - 2;

			// Encontra o slot 3D sob o cursor (o mais próximo da câmera)
			if (mouseX >= clipX0 && mouseX < clipX1 && mouseY >= clipY0 && mouseY < clipY1) {
				float bestZ = Float.MAX_VALUE;
				for (Slot3DOverlay overlay : active3DOverlays) {
					if (overlay.isHovered(mouseX, mouseY)) {
						float z = overlay.getMinZ();
						if (z < bestZ) {
							bestZ = z;
							hoveredSlot3D = overlay;
						}
					}
				}
			}

			// Renderiza o slot 3D sob o mouse com wireframe translúcido suave apenas durante o hover
			if (hoveredSlot3D != null) {
				MobSpawnData.EquippedItemData eq = configuredEquipment.get(hoveredSlot3D.slot.name());
				boolean hasItem = eq != null && !eq.getItemId().isBlank();
				for (Box3D box : hoveredSlot3D.boxes) {
					render3DBox(graphics, box, 0x25FFFFFF, 0xCCFFFFFF, true, clipX0, clipY0, clipX1, clipY1);
				}
				String eqName = hasItem ? eq.getItemId() : "Vazio";
				hoveredHitboxTooltip = "§e" + hoveredSlot3D.name + ": §f" + eqName + " §7(Clique para equipar)";
			}
		}

		// Coluna Direita (Labels)
		int rightX = panelX + 162;
		int curY = previewY;

		RpgScreenText.draw(graphics, font, "Mob ID:", rightX, curY + 4, 0xFFCCCCCC, false, true);
		RpgScreenText.draw(graphics, font, "Nome:", rightX + 152, curY + 4, 0xFFCCCCCC, false, true);

		curY += 22;
		RpgScreenText.draw(graphics, font, "Partícula:", rightX, curY + 4, 0xFFCCCCCC, false, true);
		RpgScreenText.draw(graphics, font, "Intens.:", rightX + 152, curY + 4, 0xFFCCCCCC, false, true);

		curY += 22;
		RpgScreenText.draw(graphics, font, "Som ID:", rightX, curY + 4, 0xFFCCCCCC, false, true);
		RpgScreenText.draw(graphics, font, "Drop:", rightX + 218, curY + 4, 0xFFCCCCCC, false, true);
		RpgScreenText.draw(graphics, font, "%", rightX + 284, curY + 4, 0xFFFFAA00, false, true);

		// Título das seções de Tabelas e Itens
		curY += 24;
		int tableListW = 120;
		int itemsGridW = 170;
		int scrollH = 115;

		RpgScreenText.draw(graphics, font, "Tabelas de Loot", rightX + 2, curY, RpgUi.GOLD, false, true);
		RpgScreenText.draw(graphics, font, "Itens do Drop", rightX + tableListW + 6, curY, RpgUi.GOLD, false, true);

		curY += 10;
		int listTopY = curY;

		// Borda da lista de tabelas
		RpgUi.panel(graphics, rightX, curY, tableListW, scrollH, 0xFF101622, RpgUi.BORDER_SOFT);

		// Lista de tabelas
		tablesScrollArea.setBounds(rightX, curY, tableListW, scrollH);
		tablesScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
			int rowY = listTopY;
			for (String tName : availableLootTables) {
				boolean isSelected = tName.equalsIgnoreCase(selectedLootTable);
				boolean hovered = !modalOpen && mouseX >= rightX && mouseX <= rightX + tableListW - 10
						&& mouseY >= rowY - tablesScrollArea.getScroll()
						&& mouseY < rowY + 16 - tablesScrollArea.getScroll();

				int color = isSelected ? 0xFF55FF55 : hovered ? 0xFFFFFFFF : 0xFFAAAAAA;
				int bg = isSelected ? 0x4455FF55 : hovered ? 0x22FFFFFF : 0x00000000;
				if (bg != 0) {
					g.fill(rightX + 1, rowY, rightX + tableListW - 1, rowY + 16, bg);
				}
				RpgScreenText.draw(g, font, tName, rightX + 4, rowY + 4, color, false, true);
				rowY += 18;
			}
		});

		// Borda da grid de itens
		RpgUi.panel(graphics, rightX + tableListW + 4, curY, itemsGridW, scrollH, 0xFF101622, RpgUi.BORDER_SOFT);

		// Desenhar grid de itens
		itemsScrollArea.setBounds(rightX + tableListW + 4, curY, itemsGridW, scrollH);
		itemsScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
			List<ChestToolScreen.LootItemDisplay> items = lootTablesData.getOrDefault(selectedLootTable, List.of());
			if (items.isEmpty()) {
				RpgScreenText.draw(g, font, "Sem itens", rightX + tableListW + 45, listTopY + 48, 0xFF777777, false, false);
				return;
			}

			double totalWeight = items.stream().mapToDouble(ChestToolScreen.LootItemDisplay::weight).sum();
			int cellW = (itemsGridW - 14) / 3;
			int cellH = 20;

			for (int i = 0; i < items.size(); i++) {
				int col = i % 3;
				int row = i / 3;
				int cellX = rightX + tableListW + 8 + col * cellW;
				int cellY = listTopY + 3 + row * cellH;

				ChestToolScreen.LootItemDisplay itemDisp = items.get(i);
				ItemStack st = itemDisp.stack();
				double pct = totalWeight > 0 ? (itemDisp.weight() / totalWeight) * 100.0 : 0.0;
				String pctStr = String.format(Locale.ROOT, "%.1f%%", pct);

				boolean hovered = !modalOpen && mouseX >= cellX && mouseX < cellX + cellW - 2
						&& mouseY >= cellY - itemsScrollArea.getScroll()
						&& mouseY < cellY + cellH - itemsScrollArea.getScroll();

				int fill = hovered ? 0x3355FF55 : 0xFF141E2E;
				int border = hovered ? RpgUi.GREEN : RpgUi.BORDER;
				RpgUi.panel(g, cellX, cellY, cellW - 2, cellH - 2, fill, border);

				if (!st.isEmpty()) {
					g.item(st, cellX + 1, cellY + 1);
				}
				RpgScreenText.draw(g, font, pctStr, cellX + 17, cellY + 6, RpgUi.GREEN, false, false);

				if (hovered) {
					hoveredItem = st;
					hoveredItemText = itemDisp.name() + " - " + pctStr + " (" + String.format(Locale.ROOT, "%.1f", itemDisp.weight()) + "x)";
				}
			}
		});
	}

	private void renderEquipmentModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int modalW = 320;
		int modalH = modalExpanded ? 265 : 120;
		int modalX = (width - modalW) / 2;
		int modalY = (height - modalH) / 2;

		// Backdrop escuro cobrindo toda a tela
		graphics.fill(0, 0, width, height, 0xCC000000);

		// Painel do Modal
		RpgUi.panel(graphics, modalX, modalY, modalW, modalH, 0xF5141E2E, RpgUi.GOLD);

		// Cabeçalho
		String title = "Equipar: " + getSlotDisplayName(editingSlot);
		RpgScreenText.draw(graphics, font, title, modalX + 14, modalY + 8, RpgUi.GOLD, false, true);

		// Botão Fechar [X]
		boolean closeHovered = mouseX >= modalX + modalW - 20 && mouseX < modalX + modalW - 4 && mouseY >= modalY + 6 && mouseY < modalY + 20;
		RpgScreenText.draw(graphics, font, "✕", modalX + modalW - 16, modalY + 8, closeHovered ? 0xFFFF4444 : 0xFFAAAAAA, false, true);

		// Ícone do Item (à esquerda do campo de texto)
		int iconX = modalX + 14;
		int iconY = modalY + 25;
		int iconSize = 22;
		boolean iconHovered = mouseX >= iconX && mouseX < iconX + iconSize && mouseY >= iconY && mouseY < iconY + iconSize;
		int iconBorder = !isModalItemValid ? 0xFFFF4444 : (iconHovered ? RpgUi.GOLD : RpgUi.BORDER);
		RpgUi.panel(graphics, iconX, iconY, iconSize, iconSize, 0xFF0B101B, iconBorder);

		if (!modalIdentifiedStack.isEmpty()) {
			graphics.item(modalIdentifiedStack, iconX + 3, iconY + 3);
			if (iconHovered && isModalItemValid) {
				graphics.setTooltipForNextFrame(Component.literal("§eClique para expandir: Durabilidade, Drop e Encantamentos"), mouseX, mouseY);
			}
		}

		// Campo de texto do item
		modalItemInput.setX(modalX + 42);
		modalItemInput.setY(modalY + 27);
		modalItemInput.setWidth(modalW - 56);
		modalItemInput.extractRenderState(graphics, mouseX, mouseY, delta);

		// Erro de validação se houver
		if (!isModalItemValid && !modalValidationError.isBlank()) {
			RpgScreenText.draw(graphics, font, modalValidationError, modalX + 42, modalY + 48, 0xFFFF4444, false, false);
		}

		// Seção Expandida (Durabilidade, Drop e Encantamentos)
		if (modalExpanded) {
			int expY = modalY + 54;

			// 1. Durabilidade
			if (modalIdentifiedStack.isDamageableItem()) {
				if (modalDurabilitySlider != null) {
					modalDurabilitySlider.setX(modalX + 14);
					modalDurabilitySlider.setY(expY);
					modalDurabilitySlider.setWidth(modalW - 28);
					modalDurabilitySlider.extractRenderState(graphics, mouseX, mouseY, delta);
				}
			} else {
				RpgScreenText.draw(graphics, font, "§7Item sem durabilidade", modalX + 14, expY + 4, 0xFF888888, false, false);
			}

			expY += 20;

			// 2. Chance de Drop Individual
			if (modalDropChanceSlider != null) {
				modalDropChanceSlider.setX(modalX + 14);
				modalDropChanceSlider.setY(expY);
				modalDropChanceSlider.setWidth(modalW - 28);
				modalDropChanceSlider.extractRenderState(graphics, mouseX, mouseY, delta);
			}

			expY += 20;

			// 3. Encantamentos
			RpgScreenText.draw(graphics, font, "Encantamentos:", modalX + 14, expY, 0xFFCCCCCC, false, true);
			expY += 12;

			modalEnchantInput.setX(modalX + 14);
			modalEnchantInput.setY(expY);
			modalEnchantInput.setWidth(130);
			modalEnchantInput.extractRenderState(graphics, mouseX, mouseY, delta);

			// Botões de Nível do Encantamento [-] Lvl N [+]
			int lvlX = modalX + 150;
			boolean decHover = mouseX >= lvlX && mouseX < lvlX + 14 && mouseY >= expY && mouseY < expY + 16;
			RpgUi.panel(graphics, lvlX, expY, 14, 16, decHover ? 0xFF2A3A52 : 0xFF1A2636, RpgUi.BORDER);
			RpgScreenText.draw(graphics, font, "-", lvlX + 4, expY + 4, 0xFFFFFFFF, false, true);

			RpgScreenText.draw(graphics, font, "Nv " + modalEnchLevel, lvlX + 18, expY + 4, RpgUi.GOLD, false, true);

			int incX = modalX + 200;
			boolean incHover = mouseX >= incX && mouseX < incX + 14 && mouseY >= expY && mouseY < expY + 16;
			RpgUi.panel(graphics, incX, expY, 14, 16, incHover ? 0xFF2A3A52 : 0xFF1A2636, RpgUi.BORDER);
			RpgScreenText.draw(graphics, font, "+", incX + 4, expY + 4, 0xFFFFFFFF, false, true);

			// Botão [+] Add
			int addBtnX = modalX + 220;
			boolean addHover = mouseX >= addBtnX && mouseX < addBtnX + 44 && mouseY >= expY && mouseY < expY + 16;
			RpgUi.panel(graphics, addBtnX, expY, 44, 16, addHover ? 0xFF2A6636 : 0xFF1A4626, RpgUi.GREEN);
			RpgScreenText.draw(graphics, font, "+ Add", addBtnX + 6, expY + 4, 0xFFFFFFFF, false, true);

			expY += 20;

			// Lista de encantamentos adicionados
			int enchListH = 65;
			RpgUi.panel(graphics, modalX + 14, expY, modalW - 28, enchListH, 0xFF0B101B, RpgUi.BORDER_SOFT);

			int listTop = expY;
			modalEnchantsScrollArea.setBounds(modalX + 14, expY, modalW - 28, enchListH);
			modalEnchantsScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
				int rowY = listTop;
				for (Map.Entry<String, Integer> e : modalEnchantments.entrySet()) {
					String label = e.getKey() + " " + e.getValue();
					RpgScreenText.draw(g, font, label, modalX + 18, rowY + 4, 0xFF55FF55, false, false);

					// Botão [X] para remover
					int remX = modalX + modalW - 44;
					boolean remHover = mouseX >= remX && mouseX < remX + 14 && mouseY >= rowY - modalEnchantsScrollArea.getScroll() && mouseY < rowY + 14 - modalEnchantsScrollArea.getScroll();
					g.fill(remX, rowY + 2, remX + 12, rowY + 14, remHover ? 0x88FF4444 : 0x44FF4444);
					RpgScreenText.draw(g, font, "✕", remX + 2, rowY + 3, 0xFFFFFFFF, false, true);

					rowY += 16;
				}
			});
		}

		// Botões Inferiores do Modal: OK e Limpar
		int btnW = 85;
		int btnH = 18;
		int btnY = modalY + modalH - btnH - 6;

		int okX = modalX + (modalW - (btnW * 2 + 12)) / 2;
		boolean okHover = mouseX >= okX && mouseX < okX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
		RpgUi.panel(graphics, okX, btnY, btnW, btnH, okHover ? 0xFF2A6636 : 0xFF1A4626, isModalItemValid ? RpgUi.GREEN : RpgUi.BORDER);
		RpgScreenText.draw(graphics, font, "OK", okX + (btnW - font.width("OK")) / 2, btnY + 5, isModalItemValid ? 0xFFFFFFFF : 0xFF777777, false, true);

		int clrX = okX + btnW + 12;
		boolean clrHover = mouseX >= clrX && mouseX < clrX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
		RpgUi.panel(graphics, clrX, btnY, btnW, btnH, clrHover ? 0xFF662A2A : 0xFF461A1A, RpgUi.RED);
		RpgScreenText.draw(graphics, font, "Limpar", clrX + (btnW - font.width("Limpar")) / 2, btnY + 5, 0xFFFFFFFF, false, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Tratamento de cliques dentro do Modal de Equipamento
		if (modalOpen) {
			int modalW = 320;
			int modalH = modalExpanded ? 265 : 120;
			int modalX = (width - modalW) / 2;
			int modalY = (height - modalH) / 2;

			// Clique no botão fechar [X]
			if (event.x() >= modalX + modalW - 20 && event.x() < modalX + modalW - 4 && event.y() >= modalY + 6 && event.y() < modalY + 20) {
				closeEquipmentModal();
				return true;
			}

			// Clique no ícone do item para expandir
			if (event.x() >= modalX + 14 && event.x() < modalX + 36 && event.y() >= modalY + 25 && event.y() < modalY + 47) {
				toggleModalExpanded();
				return true;
			}

			// Foco no campo de texto do item
			if (modalItemInput.mouseClicked(event, doubleClick)) {
				modalItemInput.setFocused(true);
				modalEnchantInput.setFocused(false);
				return true;
			}

			if (modalExpanded) {
				int expY = modalY + 54;
				if (modalIdentifiedStack.isDamageableItem() && modalDurabilitySlider != null && modalDurabilitySlider.mouseClicked(event, doubleClick)) {
					return true;
				}

				if (modalDropChanceSlider != null && modalDropChanceSlider.mouseClicked(event, doubleClick)) {
					return true;
				}

				expY = modalY + 54 + (modalIdentifiedStack.isDamageableItem() ? 20 : 18) + 20 + 12;
				if (modalEnchantInput.mouseClicked(event, doubleClick)) {
					modalEnchantInput.setFocused(true);
					modalItemInput.setFocused(false);
					return true;
				}

				// Nível [-] e [+]
				int lvlX = modalX + 150;
				if (event.x() >= lvlX && event.x() < lvlX + 14 && event.y() >= expY && event.y() < expY + 16) {
					modalEnchLevel = Math.max(1, modalEnchLevel - 1);
					RpgUiSounds.playClick();
					return true;
				}
				int incX = modalX + 200;
				if (event.x() >= incX && event.x() < incX + 14 && event.y() >= expY && event.y() < expY + 16) {
					modalEnchLevel = Math.min(10, modalEnchLevel + 1);
					RpgUiSounds.playClick();
					return true;
				}

				// Botão [+ Add]
				int addBtnX = modalX + 220;
				if (event.x() >= addBtnX && event.x() < addBtnX + 44 && event.y() >= expY && event.y() < expY + 16) {
					addModalEnchantment();
					return true;
				}

				// Remoção de encantamentos na lista
				int enchListTop = expY + 20;
				if (modalEnchantsScrollArea.isMouseOver(event.x(), event.y())) {
					if (modalEnchantsScrollArea.mouseClicked(event)) return true;
					int remX = modalX + modalW - 44;
					if (event.x() >= remX && event.x() < remX + 14) {
						double relY = event.y() - enchListTop + modalEnchantsScrollArea.getScroll();
						int idx = (int) (relY / 16);
						if (idx >= 0 && idx < modalEnchantments.size()) {
							List<String> keys = new ArrayList<>(modalEnchantments.keySet());
							modalEnchantments.remove(keys.get(idx));
							updateModalEnchantsScroll();
							RpgUiSounds.playClick();
							return true;
						}
					}
				}
			}

			// Botões Inferiores: OK e Limpar
			int btnW = 85;
			int btnH = 18;
			int btnY = modalY + modalH - btnH - 6;
			int okX = modalX + (modalW - (btnW * 2 + 12)) / 2;
			int clrX = okX + btnW + 12;

			if (event.x() >= okX && event.x() < okX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
				saveModalEquipment();
				return true;
			}
			if (event.x() >= clrX && event.x() < clrX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
				clearModalEquipment();
				return true;
			}

			return true;
		}

		// Clique no Preview 3D: Checa Hitboxes 3D ou inicia Arraste
		int previewW = 140;
		int previewH = 125;
		int previewX = panelX + 12;
		int previewY = panelY + headerHeight + 5;

		if (event.x() >= previewX && event.x() < previewX + previewW && event.y() >= previewY && event.y() < previewY + previewH) {
			mouseDownX = event.x();
			mouseDownY = event.y();
			clickedSlot = (hoveredSlot3D != null) ? hoveredSlot3D.slot : null;
			isDraggingPreview = true;
			lastDragX = event.x();
			lastDragY = event.y();
			return true;
		}

		// Clique na lista de tabelas de drop
		int rightX = panelX + 162;
		int listY = previewY + 78;
		int tableListW = 120;
		if (tablesScrollArea != null && tablesScrollArea.isMouseOver(event.x(), event.y())) {
			if (tablesScrollArea.mouseClicked(event)) return true;
			if (event.x() >= rightX && event.x() <= rightX + tableListW) {
				double relY = event.y() - listY + tablesScrollArea.getScroll();
				int index = (int) (relY / 18);
				if (index >= 0 && index < availableLootTables.size()) {
					String clickedTable = availableLootTables.get(index);
					if (clickedTable.equalsIgnoreCase(selectedLootTable)) {
						selectedLootTable = "";
					} else {
						selectedLootTable = clickedTable;
					}
					updateItemsScrollHeight();
					RpgUiSounds.playClick();
					return true;
				}
			}
			return true;
		}

		if (itemsScrollArea != null && itemsScrollArea.isMouseOver(event.x(), event.y())) {
			if (itemsScrollArea.mouseClicked(event)) return true;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (modalOpen) {
			if (modalDurabilitySlider != null && modalDurabilitySlider.mouseReleased(event)) return true;
			if (modalDropChanceSlider != null && modalDropChanceSlider.mouseReleased(event)) return true;
			if (modalEnchantsScrollArea != null && modalEnchantsScrollArea.mouseReleased(event)) return true;
			return true;
		}
		if (isDraggingPreview) {
			isDraggingPreview = false;
			double distSq = (event.x() - mouseDownX) * (event.x() - mouseDownX) + (event.y() - mouseDownY) * (event.y() - mouseDownY);
			if (distSq < 16 && clickedSlot != null && previewRenderSuccess) {
				openEquipmentModal(clickedSlot);
				clickedSlot = null;
				return true;
			}
			clickedSlot = null;
			return true;
		}
		if (tablesScrollArea != null && tablesScrollArea.mouseReleased(event)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseReleased(event)) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (modalOpen) {
			if (modalDurabilitySlider != null && modalDurabilitySlider.mouseDragged(event, deltaX, deltaY)) return true;
			if (modalDropChanceSlider != null && modalDropChanceSlider.mouseDragged(event, deltaX, deltaY)) return true;
			if (modalEnchantsScrollArea != null && modalEnchantsScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
			return true;
		}
		if (isDraggingPreview) {
			previewYaw -= (float) (event.x() - lastDragX) * 1.5F;
			previewPitch = Math.clamp(previewPitch - (float) (event.y() - lastDragY) * 1.0F, -45.0F, 45.0F);
			if (dummyEntity != null) {
				dummyEntity.setYRot(180.0F + previewYaw);
				dummyEntity.yBodyRot = 180.0F + previewYaw;
				dummyEntity.yHeadRot = 180.0F + previewYaw;
				dummyEntity.yHeadRotO = dummyEntity.yHeadRot;
				dummyEntity.yBodyRotO = dummyEntity.yBodyRot;
				dummyEntity.setXRot(0.0F);
				dummyEntity.xRotO = 0.0F;
			}
			lastDragX = event.x();
			lastDragY = event.y();
			return true;
		}
		if (tablesScrollArea != null && tablesScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (modalOpen) {
			if (modalEnchantsScrollArea != null && modalEnchantsScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
			return true;
		}

		// Zoom in / zoom out no preview 3D do mob quando o mouse está sobre o preview
		int previewW = 140;
		int previewH = 125;
		int previewX = panelX + 12;
		int previewY = panelY + headerHeight + 5;
		if (mouseX >= previewX && mouseX <= previewX + previewW && mouseY >= previewY && mouseY <= previewY + previewH) {
			if (verticalAmount > 0) {
				previewZoom = Math.min(3.0F, previewZoom * 1.15F);
			} else if (verticalAmount < 0) {
				previewZoom = Math.max(0.4F, previewZoom / 1.15F);
			}
			return true;
		}

		if (tablesScrollArea != null && tablesScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (modalOpen) {
			if (event.isEscape() || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || event.key() == 256) { // ESC
				closeEquipmentModal();
				return true;
			}
			if (event.isConfirmation() || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER || event.key() == 257 || event.key() == 335) { // Enter / Numpad Enter
				if (modalExpanded && modalEnchantInput != null && modalEnchantInput.isFocused()) {
					addModalEnchantment();
					return true;
				}
			}
			if (modalItemInput.isFocused() && modalItemInput.keyPressed(event)) return true;
			if (modalExpanded && modalEnchantInput != null && modalEnchantInput.isFocused() && modalEnchantInput.keyPressed(event)) return true;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (modalOpen) {
			if (modalItemInput.isFocused() && modalItemInput.charTyped(event)) return true;
			if (modalExpanded && modalEnchantInput != null && modalEnchantInput.isFocused() && modalEnchantInput.charTyped(event)) return true;
			return true;
		}
		return super.charTyped(event);
	}
}
