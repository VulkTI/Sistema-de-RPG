package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.dungeon.client.ui.RpgCheckbox;
import br.com.rpgsystem.dungeon.client.ui.RpgSliderWidget;
import br.com.rpgsystem.dungeon.client.ui.RpgToggleSwitch;
import br.com.rpgsystem.dungeon.client.ui.RpgWaveListWidget;
import br.com.rpgsystem.dungeon.data.WaveData;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructureConfigScreen extends RpgBaseScreen {
	private final String jsonConfig;
	private String selectedStructure = "DUNGEON";
	private String structureName = "";
	private boolean isOfficial = false;

	// Configurações da Dungeon
	private long timeTicks = 6000L;
	private boolean daylightCycle = false;
	private final List<WaveData> waves = new ArrayList<>();

	// Configurações da Entrada
	private boolean subsolo = false;
	private boolean ceu = false;
	private final Set<String> dimensions = new HashSet<>();

	// Widgets
	private EditBox nameInput;
	private RpgSliderWidget timeSlider;
	private RpgToggleSwitch cycleSwitch;
	private RpgWaveListWidget waveListWidget;

	private RpgCheckbox chkSubsolo;
	private RpgCheckbox chkCeu;
	private RpgCheckbox chkOverworld;
	private RpgCheckbox chkNether;
	private RpgCheckbox chkEnd;

	public StructureConfigScreen(String jsonConfig) {
		super(Component.literal("Configuração da Estrutura"));
		this.jsonConfig = jsonConfig;
		this.showCloseButton = true;
		parseConfig();
	}

	private void parseConfig() {
		try {
			JsonObject root = JsonParser.parseString(jsonConfig).getAsJsonObject();
			if (root.has("selectedStructure")) selectedStructure = root.get("selectedStructure").getAsString();
			if (root.has("isOfficial")) isOfficial = root.get("isOfficial").getAsBoolean();

			if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
				JsonObject ds = root.has("dungeonStructure") ? root.getAsJsonObject("dungeonStructure") : root;
				structureName = ds.has("structureName") ? ds.get("structureName").getAsString() : (root.has("structureName") ? root.get("structureName").getAsString() : "Dungeon");
				timeTicks = ds.has("timeTicks") ? ds.get("timeTicks").getAsLong() : (root.has("timeTicks") ? root.get("timeTicks").getAsLong() : 6000L);
				daylightCycle = ds.has("daylightCycle") ? ds.get("daylightCycle").getAsBoolean() : (root.has("daylightCycle") && root.get("daylightCycle").getAsBoolean());

				waves.clear();
				if (ds.has("waves")) {
					for (var wEl : ds.getAsJsonArray("waves")) {
						JsonObject wObj = wEl.getAsJsonObject();
						WaveData w = new WaveData(wObj.get("order").getAsInt(), wObj.get("name").getAsString());
						if (wObj.has("spawnMode")) w.setSpawnMode(WaveData.SpawnMode.valueOf(wObj.get("spawnMode").getAsString()));
						if (wObj.has("activationAreaName")) w.setActivationAreaName(wObj.get("activationAreaName").getAsString());
						waves.add(w);
					}
				}
			} else if (root.has("entranceStructure")) {
				JsonObject es = root.getAsJsonObject("entranceStructure");
				structureName = es.has("structureName") ? es.get("structureName").getAsString() : "Entrada";
				subsolo = es.has("subsolo") && es.get("subsolo").getAsBoolean();
				ceu = es.has("ceu") && es.get("ceu").getAsBoolean();
				dimensions.clear();
				if (es.has("dimensions")) {
					for (var el : es.getAsJsonArray("dimensions")) dimensions.add(el.getAsString());
				}
			}
		} catch (Exception ignored) {}
	}

	@Override
	protected void layoutWindow() {
		panelWidth = Math.min(360, width - 24);
		panelHeight = Math.min(270, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		headerHeight = 24;
		windowAccentColor = "DUNGEON".equalsIgnoreCase(selectedStructure) ? RpgUi.PURPLE : RpgUi.BLUE;
	}

	@Override
	protected void init() {
		super.init();

		int left = panelX + 16;
		int contentY = panelY + 28;

		// 1. Campo de texto do Nome da Estrutura
		nameInput = addRenderableWidget(new EditBox(font, left, contentY, panelWidth - 32, 16, Component.literal("Nome da Estrutura")));
		nameInput.setMaxLength(64);
		nameInput.setValue(structureName);
		contentY += 22;

		if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
			// 2. Horário: Ícone de Relógio (desenhado no drawContent) + Slider 0:00 a 23:59 (0 a 1439 minutos)
			int sliderW = 120;
			int minutes = ticksToMinutes(timeTicks);
			// O slider só armazena localmente — envia ao servidor em tempo real apenas para preview do relógio na dimensão
			timeSlider = addRenderableWidget(new RpgSliderWidget(left + 54, contentY, sliderW, 14, 0, 1439, minutes, val -> {
				long newTicks = minutesToTicks(val.intValue());
				this.timeTicks = newTicks;
				// Envia preview em tempo real para mover o sol/lua na dimensão do editor
				ClientPlayNetworking.send(new DungeonPayloads.SetEditorTime(newTicks));
				syncTempConfig();
			}).setTextFormatter(v -> {
				int m = v.intValue();
				return String.format("%02d:%02d", m / 60, m % 60);
			}));
			contentY += 20;

			// 3. Ciclo On/Off — apenas armazena localmente, sem enviar ao servidor
			cycleSwitch = addRenderableWidget(new RpgToggleSwitch(left + 44, contentY, 32, 14, daylightCycle, checked -> {
				this.daylightCycle = checked;
				// Envia preview em tempo real para o editor
				ClientPlayNetworking.send(new DungeonPayloads.SetEditorCycle(checked));
				syncTempConfig();
			}));
			contentY += 20;

			// 4. Portais: [Portais] (unificado)
			addRenderableWidget(new RpgButton(left + 54, contentY, 116, 16, Component.literal("Portais"), RpgUi.BLUE, () -> {
				ClientPlayNetworking.send(new DungeonPayloads.EditPortalAction("ALL"));
				Minecraft.getInstance().gui.setScreen(null);
			}).setTooltip("Editar Portais", "Abre as ferramentas para configurar os portais de entrada e saída"));
			contentY += 22;

			// 5. Widget de Ondas
			int wavesWidgetH = panelY + panelHeight - contentY - 32;
			waveListWidget = addRenderableWidget(new RpgWaveListWidget(left, contentY, panelWidth - 32, wavesWidgetH, waves, updated -> {
				waves.clear();
				waves.addAll(updated);
				syncTempConfig();
			}, () -> {
				syncTempConfig();
				Minecraft.getInstance().gui.setScreen(null);
			}));

		} else {
			// Estrutura de Entrada
			chkSubsolo = addRenderableWidget(new RpgCheckbox(left, contentY, "Subsolo", subsolo, val -> {
				this.subsolo = val;
				syncTempConfig();
			}));
			contentY += 20;

			chkCeu = addRenderableWidget(new RpgCheckbox(left, contentY, "Céu", ceu, val -> {
				this.ceu = val;
				syncTempConfig();
			}));
			contentY += 22;

			// Dimensões
			chkOverworld = addRenderableWidget(new RpgCheckbox(left, contentY, "Overworld", dimensions.contains("minecraft:overworld"), val -> toggleDim("minecraft:overworld", val)));
			chkNether = addRenderableWidget(new RpgCheckbox(left + 80, contentY, "Nether", dimensions.contains("minecraft:the_nether"), val -> toggleDim("minecraft:the_nether", val)));
			chkEnd = addRenderableWidget(new RpgCheckbox(left + 150, contentY, "End", dimensions.contains("minecraft:the_end"), val -> toggleDim("minecraft:the_end", val)));
			contentY += 26;

			// Portais: [Entrada]
			addRenderableWidget(new RpgButton(left + 54, contentY, 60, 18, Component.literal("Entrada"), RpgUi.BLUE, () -> {
				ClientPlayNetworking.send(new DungeonPayloads.EditPortalAction("ENTRADA"));
				Minecraft.getInstance().gui.setScreen(null);
			}).setTooltip("Editar Portal de Entrada", "Abre as ferramentas para configurar o portal branco da entrada"));
		}

		// Botões: [Salvar] e [Cancelar] no rodapé
		int btnW = 80;
		int footerY = panelY + panelHeight - 24;
		addRenderableWidget(new RpgButton(panelX + 16, footerY, btnW, 18, Component.literal("Salvar"), RpgUi.GREEN, this::handleSave));
		addRenderableWidget(new RpgButton(panelX + panelWidth - 16 - btnW, footerY, btnW, 18, Component.literal("Cancelar"), RpgUi.RED, this::handleCancel));
	}

	private void handleCancel() {
		ClientPlayNetworking.send(new DungeonPayloads.CancelStructureConfig());
		Minecraft.getInstance().gui.setScreen(null);
	}

	private void toggleDim(String id, boolean val) {
		if (val) dimensions.add(id);
		else dimensions.remove(id);
		syncTempConfig();
	}

	private void syncTempConfig() {
		try {
			JsonObject root = buildCurrentConfigJson();
			ClientPlayNetworking.send(new DungeonPayloads.UpdateTempStructureConfig(root.toString()));
		} catch (Exception ignored) {}
	}

	private JsonObject buildCurrentConfigJson() {
		JsonObject root = new JsonObject();
		root.addProperty("selectedStructure", selectedStructure);
		root.addProperty("isOfficial", isOfficial);
		String name = nameInput != null ? nameInput.getValue().trim() : structureName;

		if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
			JsonObject dung = new JsonObject();
			dung.addProperty("structureName", name);
			dung.addProperty("timeTicks", timeTicks);
			dung.addProperty("daylightCycle", daylightCycle);

			JsonArray wavesArr = new JsonArray();
			var currentWaves = waveListWidget != null ? waveListWidget.getWaves() : waves;
			for (WaveData w : currentWaves) {
				JsonObject wObj = new JsonObject();
				wObj.addProperty("order", w.getOrder());
				wObj.addProperty("name", w.getName());
				wObj.addProperty("spawnMode", w.getSpawnMode().name());
				wObj.addProperty("activationAreaName", w.getActivationAreaName());
				wavesArr.add(wObj);
			}
			dung.add("waves", wavesArr);
			root.add("dungeonStructure", dung);
		} else {
			JsonObject ent = new JsonObject();
			ent.addProperty("structureName", name);
			ent.addProperty("subsolo", subsolo);
			ent.addProperty("ceu", ceu);
			JsonArray dimsArr = new JsonArray();
			dimensions.forEach(dimsArr::add);
			ent.add("dimensions", dimsArr);
			root.add("entranceStructure", ent);
		}
		return root;
	}

	private void handleSave() {
		JsonObject root = new JsonObject();
		root.addProperty("structureName", nameInput.getValue().trim());

		if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
			root.addProperty("timeTicks", timeTicks);
			root.addProperty("daylightCycle", daylightCycle);

			JsonArray wavesArr = new JsonArray();
			for (WaveData w : waveListWidget.getWaves()) {
				JsonObject wObj = new JsonObject();
				wObj.addProperty("order", w.getOrder());
				wObj.addProperty("name", w.getName());
				wObj.addProperty("spawnMode", w.getSpawnMode().name());
				wObj.addProperty("activationAreaName", w.getActivationAreaName());
				wavesArr.add(wObj);
			}
			root.add("waves", wavesArr);
		} else {
			root.addProperty("subsolo", subsolo);
			root.addProperty("ceu", ceu);
			JsonArray dimsArr = new JsonArray();
			dimensions.forEach(dimsArr::add);
			root.add("dimensions", dimsArr);
		}

		// Envia ao servidor — o servidor vai salvar no arquivo e reabrir DungeonSetupScreen
		ClientPlayNetworking.send(new DungeonPayloads.SaveStructureConfig(root.toString()));
		RpgUiSounds.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
		Minecraft.getInstance().gui.setScreen(null);
	}

	@Override
	public void onClose() {
		syncTempConfig();
		super.onClose();
	}

	/** Tecla P durante sessão de edição deve fechar a tela de config da estrutura (voltando ao jogo),
	 *  pois pressionar P de novo (no jogo) reabrirá esta tela via RequestReopenSetup. */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		var focused = getFocused();
		if (focused instanceof net.minecraft.client.gui.components.EditBox editBox) {
			if (editBox.keyPressed(event)) {
				return true;
			}
		}
		if (isAnyTextInputFocused()) {
			return super.keyPressed(event);
		}
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_P) { // Tecla P — fecha a tela, voltando ao jogo de edição
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int left = panelX + 16;

		if ("DUNGEON".equalsIgnoreCase(selectedStructure)) {
			// Ícone de relógio estático: calcula o frame (0-63) com base em timeTicks e faz blit da textura
			int clockFrame = ticksToClockFrame(timeTicks);
			try {
				Identifier clockTex = Identifier.fromNamespaceAndPath("minecraft", "textures/item/" + String.format("clock_%02d", clockFrame) + ".png");
				// blit(Identifier, x, y, width, height, u0, v0, u1, v1)
				graphics.blit(clockTex, left, panelY + 49, 16, 16, 0.0f, 0.0f, 1.0f, 1.0f);
			} catch (Exception ignored) {
				// fallback: texto simples se a textura não carregar
				RpgScreenText.draw(graphics, font, "⏰", left, panelY + 53, RpgUi.GOLD, false, true);
			}
			RpgScreenText.draw(graphics, font, "Horário:", left + 18, panelY + 53, RpgUi.TEXT, false, true);

			// Texto Ciclo
			RpgScreenText.draw(graphics, font, "Ciclo:", left, panelY + 73, RpgUi.TEXT, false, true);

			// Texto Portais
			RpgScreenText.draw(graphics, font, "Portais:", left, panelY + 93, RpgUi.TEXT, false, true);
		} else {
			// Rótulo Dimensões
			RpgScreenText.draw(graphics, font, "Dimensões:", left, panelY + 92, RpgUi.TEXT_MUTED, false, true);
			RpgScreenText.draw(graphics, font, "Portais:", left, panelY + 118, RpgUi.TEXT, false, true);
		}
	}

	/**
	 * Converte ticks de jogo (0-23999) no frame do relógio (0-63).
	 * Frame 0 = meia-noite. Frame 16 ≈ meio-dia (ticks 6000).
	 * O relógio do Minecraft usa: frame = round(ticks * 64 / 24000) % 64
	 */
	private static int ticksToClockFrame(long ticks) {
		// Normaliza para 0-23999 e calcula o frame
		long normalized = ((ticks % 24000L) + 24000L) % 24000L;
		return (int) (normalized * 64L / 24000L) % 64;
	}

	private static int ticksToMinutes(long ticks) {
		long t = (ticks + 6000L) % 24000L;
		return (int) (t * 1440L / 24000L);
	}

	private static long minutesToTicks(int minutes) {
		long t = (long) minutes * 24000L / 1440L;
		return (t - 6000L + 24000L) % 24000L;
	}
}
