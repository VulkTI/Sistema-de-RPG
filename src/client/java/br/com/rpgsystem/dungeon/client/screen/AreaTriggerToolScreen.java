package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgScrollArea;
import br.com.rpgsystem.dungeon.client.ui.RpgToggleSwitch;
import br.com.rpgsystem.dungeon.data.AreaTriggerData;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class AreaTriggerToolScreen extends RpgBaseScreen {
	private final String initialJson;
	private String areaName = "Área 1";
	private AreaTriggerData.Mode mode = AreaTriggerData.Mode.START_WAVE;
	private int minX, minY, minZ, maxX, maxY, maxZ;
	private int currentWaveOrder = 1;
	private String currentWaveName = "";
	private String targetWaveName = "";
	private String targetChestName = "";
	private String musicId = "minecraft:music.nether.crimson_forest";
	private boolean replaceMusic = true;
	private boolean saved = false;

	public record AreaEntry(String name, AreaTriggerData.Mode mode, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String targetWave, String targetChest, String music, boolean replace) {}
	private final List<AreaEntry> areasList = new ArrayList<>();
	private final List<String> availableChests = new ArrayList<>();

	// Widgets - Formulário da Área
	private EditBox nameInput;
	private EditBox p1XInput, p1YInput, p1ZInput;
	private EditBox p2XInput, p2YInput, p2ZInput;

	// Baú selector (Dropdown em vez de texto)
	private RpgButton btnSelectChest;
	private boolean chestDropdownOpen = false;

	// Música
	private EditBox musicIdInput;
	private RpgToggleSwitch replaceMusicSwitch;
	private ItemStack musicIcon = ItemStack.EMPTY;
	private String musicTooltip = "";
	private SimpleSoundInstance playingSoundInstance = null;

	private RpgButton btnModeWave;
	private RpgButton btnModeChest;
	private RpgButton btnModeMusic;

	private RpgScrollArea listScrollArea;

	public AreaTriggerToolScreen(String json) {
		super(Component.literal("Configuração de Áreas"), "Gerencie e configure as áreas de gatilho da onda");
		this.initialJson = json;
		this.showCloseButton = true;
		this.windowAccentColor = RpgUi.CYAN;
		parseJson();
	}

	private void parseJson() {
		try {
			JsonObject obj = JsonParser.parseString(initialJson).getAsJsonObject();
			if (obj.has("name")) areaName = obj.get("name").getAsString();
			if (obj.has("mode")) mode = AreaTriggerData.Mode.valueOf(obj.get("mode").getAsString());
			if (obj.has("minX")) minX = obj.get("minX").getAsInt();
			if (obj.has("minY")) minY = obj.get("minY").getAsInt();
			if (obj.has("minZ")) minZ = obj.get("minZ").getAsInt();
			if (obj.has("maxX")) maxX = obj.get("maxX").getAsInt();
			if (obj.has("maxY")) maxY = obj.get("maxY").getAsInt();
			if (obj.has("maxZ")) maxZ = obj.get("maxZ").getAsInt();
			if (obj.has("currentWaveOrder")) currentWaveOrder = obj.get("currentWaveOrder").getAsInt();
			if (obj.has("currentWaveName")) currentWaveName = obj.get("currentWaveName").getAsString();
			if (obj.has("targetWaveName")) targetWaveName = obj.get("targetWaveName").getAsString();
			if (obj.has("targetChestName")) targetChestName = obj.get("targetChestName").getAsString();
			if (obj.has("musicId")) musicId = obj.get("musicId").getAsString();
			if (obj.has("replaceMusic")) replaceMusic = obj.get("replaceMusic").getAsBoolean();

			availableChests.clear();
			if (obj.has("availableChests")) {
				for (JsonElement el : obj.getAsJsonArray("availableChests")) {
					availableChests.add(el.getAsString());
				}
			}

			areasList.clear();
			if (obj.has("areasList")) {
				for (JsonElement el : obj.getAsJsonArray("areasList")) {
					JsonObject aObj = el.getAsJsonObject();
					String nm = aObj.has("name") ? aObj.get("name").getAsString() : "Área";
					AreaTriggerData.Mode m = aObj.has("mode") ? AreaTriggerData.Mode.valueOf(aObj.get("mode").getAsString()) : AreaTriggerData.Mode.START_WAVE;
					int x1 = aObj.has("minX") ? aObj.get("minX").getAsInt() : 0;
					int y1 = aObj.has("minY") ? aObj.get("minY").getAsInt() : 0;
					int z1 = aObj.has("minZ") ? aObj.get("minZ").getAsInt() : 0;
					int x2 = aObj.has("maxX") ? aObj.get("maxX").getAsInt() : 0;
					int y2 = aObj.has("maxY") ? aObj.get("maxY").getAsInt() : 0;
					int z2 = aObj.has("maxZ") ? aObj.get("maxZ").getAsInt() : 0;
					String tw = aObj.has("targetWaveName") ? aObj.get("targetWaveName").getAsString() : "";
					String tc = aObj.has("targetChestName") ? aObj.get("targetChestName").getAsString() : "";
					String mu = aObj.has("musicId") ? aObj.get("musicId").getAsString() : "";
					boolean rm = aObj.has("replaceMusic") && aObj.get("replaceMusic").getAsBoolean();
					areasList.add(new AreaEntry(nm, m, x1, y1, z1, x2, y2, z2, tw, tc, mu, rm));
				}
			}
		} catch (Throwable ignored) {}
	}

	@Override
	protected void layoutWindow() {
		panelWidth = 360;
		panelHeight = 240;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	protected void init() {
		super.init();

		int leftW = 185;
		int rightX = panelX + leftW + 12;
		int rightW = panelWidth - leftW - 20;

		int startY = panelY + headerHeight + 4;

		// Nome da área
		nameInput = new EditBox(font, panelX + 48, startY, leftW - 48, 14, Component.literal("Nome"));
		nameInput.setMaxLength(64);
		nameInput.setValue(areaName);
		nameInput.setResponder(val -> this.areaName = val);
		addRenderableWidget(nameInput);

		// Botões de modo da ferramenta
		int modeBtnY = startY + 18;
		int modeBtnW = (leftW - 4) / 3;

		btnModeWave = new RpgButton(panelX + 10, modeBtnY, modeBtnW, 16, Component.literal("Onda"),
				mode == AreaTriggerData.Mode.START_WAVE ? RpgUi.CYAN : RpgUi.BORDER, () -> setMode(AreaTriggerData.Mode.START_WAVE));
		btnModeChest = new RpgButton(panelX + 12 + modeBtnW, modeBtnY, modeBtnW, 16, Component.literal("Baú"),
				mode == AreaTriggerData.Mode.SPAWN_CHEST ? RpgUi.CYAN : RpgUi.BORDER, () -> setMode(AreaTriggerData.Mode.SPAWN_CHEST));
		btnModeMusic = new RpgButton(panelX + 14 + modeBtnW * 2, modeBtnY, modeBtnW, 16, Component.literal("Música"),
				mode == AreaTriggerData.Mode.PLAY_MUSIC ? RpgUi.CYAN : RpgUi.BORDER, () -> setMode(AreaTriggerData.Mode.PLAY_MUSIC));

		addRenderableWidget(btnModeWave);
		addRenderableWidget(btnModeChest);
		addRenderableWidget(btnModeMusic);

		// Coordenadas Ponto 1 (X, Y, Z)
		int coordY1 = modeBtnY + 20;
		int coordBoxW = 38;
		p1XInput = new EditBox(font, panelX + 44, coordY1, coordBoxW, 14, Component.literal("X1"));
		p1XInput.setValue(String.valueOf(minX));
		p1XInput.setResponder(val -> { try { this.minX = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p1XInput);

		p1YInput = new EditBox(font, panelX + 46 + coordBoxW, coordY1, coordBoxW, 14, Component.literal("Y1"));
		p1YInput.setValue(String.valueOf(minY));
		p1YInput.setResponder(val -> { try { this.minY = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p1YInput);

		p1ZInput = new EditBox(font, panelX + 48 + coordBoxW * 2, coordY1, coordBoxW, 14, Component.literal("Z1"));
		p1ZInput.setValue(String.valueOf(minZ));
		p1ZInput.setResponder(val -> { try { this.minZ = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p1ZInput);

		// Coordenadas Ponto 2 (X, Y, Z)
		int coordY2 = coordY1 + 18;
		p2XInput = new EditBox(font, panelX + 44, coordY2, coordBoxW, 14, Component.literal("X2"));
		p2XInput.setValue(String.valueOf(maxX));
		p2XInput.setResponder(val -> { try { this.maxX = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p2XInput);

		p2YInput = new EditBox(font, panelX + 46 + coordBoxW, coordY2, coordBoxW, 14, Component.literal("Y2"));
		p2YInput.setValue(String.valueOf(maxY));
		p2YInput.setResponder(val -> { try { this.maxY = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p2YInput);

		p2ZInput = new EditBox(font, panelX + 48 + coordBoxW * 2, coordY2, coordBoxW, 14, Component.literal("Z2"));
		p2ZInput.setValue(String.valueOf(maxZ));
		p2ZInput.setResponder(val -> { try { this.maxZ = Integer.parseInt(val); } catch (NumberFormatException ignored) {} });
		addRenderableWidget(p2ZInput);

		// Configurações específicas de cada modo
		int configY = coordY2 + 20;

		// Modo Baú: Dropdown em vez de campo de texto
		btnSelectChest = new RpgButton(panelX + 44, configY, leftW - 44, 16, Component.literal(getChestButtonLabel()),
				RpgUi.GOLD, () -> {
			this.chestDropdownOpen = !this.chestDropdownOpen;
			br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
		});
		addRenderableWidget(btnSelectChest);

		// Modo Música: Ícone do mob ao lado do ID da música
		int musicInputX = panelX + 32;
		int musicInputW = leftW - 32;
		musicIdInput = new EditBox(font, musicInputX, configY + 1, musicInputW, 14, Component.literal("Música"));
		musicIdInput.setMaxLength(256);
		musicIdInput.setValue(musicId);
		musicIdInput.setResponder(val -> {
			this.musicId = val.trim();
			if (isMusicPlaying()) {
				stopMusicPreview();
			}
			updateMusicIcon();
		});
		addRenderableWidget(musicIdInput);

		replaceMusicSwitch = new RpgToggleSwitch(panelX + 70, configY + 20, 26, 12, replaceMusic, val -> this.replaceMusic = val);
		addRenderableWidget(replaceMusicSwitch);

		updateMusicIcon();
		updateModeVisibility();

		// Botão Salvar à esquerda
		int btnW = 80;
		int btnH = 18;
		addRenderableWidget(new RpgButton(panelX + (leftW - btnW) / 2 + 10, panelY + panelHeight - btnH - 8,
				btnW, btnH, Component.literal("Salvar"), RpgUi.GREEN, this::saveAndClose));

		// Painel rolável à direita para lista de áreas
		int listY = startY + 14;
		int listH = panelHeight - headerHeight - 26;
		listScrollArea = new RpgScrollArea(rightX, listY, rightW, listH);
		listScrollArea.setContentHeight(areasList.size() * 32);
	}

	private String getChestButtonLabel() {
		if (targetChestName.isBlank()) {
			return availableChests.isEmpty() ? "Nenhum baú" : "Selecionar Baú ▼";
		}
		return targetChestName + " ▼";
	}

	private void setMode(AreaTriggerData.Mode newMode) {
		this.mode = newMode;
		btnModeWave.setAccentColor(mode == AreaTriggerData.Mode.START_WAVE ? RpgUi.CYAN : RpgUi.BORDER);
		btnModeChest.setAccentColor(mode == AreaTriggerData.Mode.SPAWN_CHEST ? RpgUi.CYAN : RpgUi.BORDER);
		btnModeMusic.setAccentColor(mode == AreaTriggerData.Mode.PLAY_MUSIC ? RpgUi.CYAN : RpgUi.BORDER);
		chestDropdownOpen = false;
		updateModeVisibility();
	}

	private void updateModeVisibility() {
		if (btnSelectChest != null) {
			btnSelectChest.visible = mode == AreaTriggerData.Mode.SPAWN_CHEST;
		}
		if (musicIdInput != null) {
			musicIdInput.visible = mode == AreaTriggerData.Mode.PLAY_MUSIC;
			replaceMusicSwitch.visible = mode == AreaTriggerData.Mode.PLAY_MUSIC;
		}
	}

	private void updateMusicIcon() {
		Identifier loc = Identifier.tryParse(musicId);
		if (loc != null) {
			if (BuiltInRegistries.ITEM.containsKey(loc)) {
				var item = BuiltInRegistries.ITEM.getValue(loc);
				musicIcon = new ItemStack(item);
				musicTooltip = item.getName(musicIcon).getString();
			} else {
				String converted = loc.getPath().replace('.', '_');
				Identifier itemLoc = Identifier.fromNamespaceAndPath(loc.getNamespace(), converted);
				if (BuiltInRegistries.ITEM.containsKey(itemLoc)) {
					var item = BuiltInRegistries.ITEM.getValue(itemLoc);
					musicIcon = new ItemStack(item);
					musicTooltip = item.getName(musicIcon).getString();
				} else if (BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
					if (loc.getPath().contains("disc")) {
						musicIcon = new ItemStack(Items.MUSIC_DISC_CAT);
						musicTooltip = "Disco: " + loc;
					} else {
						musicIcon = new ItemStack(Items.JUKEBOX);
						musicTooltip = "Música: " + loc;
					}
				} else {
					musicIcon = ItemStack.EMPTY;
					musicTooltip = "";
				}
			}
		} else {
			musicIcon = ItemStack.EMPTY;
			musicTooltip = "";
		}
	}

	private boolean isMusicPlaying() {
		return playingSoundInstance != null && Minecraft.getInstance().getSoundManager().isActive(playingSoundInstance);
	}

	private void toggleMusicPreview() {
		if (isMusicPlaying()) {
			stopMusicPreview();
			br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
		} else {
			startMusicPreview();
		}
	}

	private void startMusicPreview() {
		stopMusicPreview();
		Identifier loc = Identifier.tryParse(musicId);
		if (loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
			SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(loc);
			playingSoundInstance = SimpleSoundInstance.forUI(event, 1.0F);
			Minecraft.getInstance().getSoundManager().play(playingSoundInstance);
		}
	}

	private void stopMusicPreview() {
		if (playingSoundInstance != null) {
			Minecraft.getInstance().getSoundManager().stop(playingSoundInstance);
			playingSoundInstance = null;
		}
	}

	private void loadAreaIntoForm(AreaEntry a) {
		if (isMusicPlaying()) {
			stopMusicPreview();
		}
		this.areaName = a.name();
		this.mode = a.mode();
		this.minX = a.minX();
		this.minY = a.minY();
		this.minZ = a.minZ();
		this.maxX = a.maxX();
		this.maxY = a.maxY();
		this.maxZ = a.maxZ();
		this.targetWaveName = a.targetWave();
		this.targetChestName = a.targetChest();
		this.musicId = a.music();
		this.replaceMusic = a.replace();

		nameInput.setValue(areaName);
		p1XInput.setValue(String.valueOf(minX));
		p1YInput.setValue(String.valueOf(minY));
		p1ZInput.setValue(String.valueOf(minZ));
		p2XInput.setValue(String.valueOf(maxX));
		p2YInput.setValue(String.valueOf(maxY));
		p2ZInput.setValue(String.valueOf(maxZ));
		if (btnSelectChest != null) {
			btnSelectChest.setMessage(Component.literal(getChestButtonLabel()));
		}
		if (musicIdInput != null) {
			musicIdInput.setValue(musicId);
		}
		setMode(mode);
		updateMusicIcon();
		br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
	}

	private void deleteArea(AreaEntry a) {
		ClientPlayNetworking.send(new DungeonPayloads.DeleteAreaTool(a.name()));
		areasList.remove(a);
		listScrollArea.setContentHeight(areasList.size() * 32);
		br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
	}

	private void saveAndClose() {
		int actualMinX = Math.min(minX, maxX);
		int actualMinY = Math.min(minY, maxY);
		int actualMinZ = Math.min(minZ, maxZ);
		int actualMaxX = Math.max(minX, maxX);
		int actualMaxY = Math.max(minY, maxY);
		int actualMaxZ = Math.max(minZ, maxZ);

		JsonObject obj = new JsonObject();
		obj.addProperty("name", nameInput.getValue().trim().isEmpty() ? "Área" : nameInput.getValue().trim());
		obj.addProperty("mode", mode.name());
		obj.addProperty("minX", actualMinX);
		obj.addProperty("minY", actualMinY);
		obj.addProperty("minZ", actualMinZ);
		obj.addProperty("maxX", actualMaxX);
		obj.addProperty("maxY", actualMaxY);
		obj.addProperty("maxZ", actualMaxZ);
		obj.addProperty("targetWaveName", currentWaveName);
		obj.addProperty("targetChestName", targetChestName.trim());
		obj.addProperty("musicId", musicIdInput.getValue().trim());
		obj.addProperty("replaceMusic", replaceMusicSwitch.isChecked());

		saved = true;
		ClientPlayNetworking.send(new DungeonPayloads.SaveAreaTool(obj.toString()));
		onClose();
	}

	@Override
	public void tick() {
		super.tick();
		if (playingSoundInstance != null && !Minecraft.getInstance().getSoundManager().isActive(playingSoundInstance)) {
			playingSoundInstance = null;
		}
	}

	@Override
	public void onClose() {
		stopMusicPreview();
		if (!saved) {
			ClientPlayNetworking.send(new DungeonPayloads.CancelAreaTool());
		}
		super.onClose();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int leftW = 185;
		int rightX = panelX + leftW + 12;
		int rightW = panelWidth - leftW - 20;

		int startY = panelY + headerHeight + 4;
		RpgScreenText.draw(graphics, font, "Nome:", panelX + 10, startY + 3, 0xFFCCCCCC, false, true);

		int coordY1 = startY + 38;
		RpgScreenText.draw(graphics, font, "P1 (X,Y,Z):", panelX + 10, coordY1 + 3, 0xFFCCCCCC, false, false);

		int coordY2 = coordY1 + 18;
		RpgScreenText.draw(graphics, font, "P2 (X,Y,Z):", panelX + 10, coordY2 + 3, 0xFFCCCCCC, false, false);

		int configY = coordY2 + 20;
		if (mode == AreaTriggerData.Mode.START_WAVE) {
			RpgScreenText.draw(graphics, font, "§aInicia a onda atual", panelX + 10, configY + 3, RpgUi.GREEN, false, true);
			String waveInfo = !currentWaveName.isBlank() ? currentWaveName : ("Onda " + currentWaveOrder);
			RpgScreenText.draw(graphics, font, "§7(" + waveInfo + ")", panelX + 10, configY + 15, RpgUi.TEXT_MUTED, false, false);
		} else if (mode == AreaTriggerData.Mode.SPAWN_CHEST) {
			RpgScreenText.draw(graphics, font, "Baú:", panelX + 10, configY + 4, 0xFFCCCCCC, false, true);
		} else if (mode == AreaTriggerData.Mode.PLAY_MUSIC) {
			// Ícone de música: selecionado com destaque enquanto a música toca
			boolean isPlaying = isMusicPlaying();
			int iconX = panelX + 10;
			int iconY = configY - 1;
			int border = isPlaying ? 0xFF55FF55 : (!musicIcon.isEmpty() ? RpgUi.CYAN : RpgUi.BORDER);
			int bg = isPlaying ? 0xFF1C382B : 0xFF141E2E;
			RpgUi.panel(graphics, iconX, iconY, 18, 18, bg, border);
			if (isPlaying) {
				RpgUi.accent(graphics, iconX, iconY, 18, RpgUi.GREEN);
			}
			if (!musicIcon.isEmpty()) {
				graphics.item(musicIcon, iconX + 1, iconY + 1);
			}
			RpgScreenText.draw(graphics, font, "Substituir:", panelX + 10, configY + 22, 0xFFCCCCCC, false, true);
		}

		// Linha divisória vertical
		graphics.fill(panelX + leftW + 6, panelY + headerHeight + 4, panelX + leftW + 7, panelY + panelHeight - 8, 0xFF2A3A52);

		// Cabeçalho da lista de áreas
		RpgScreenText.draw(graphics, font, "Áreas desta Onda (" + areasList.size() + ")", rightX, startY + 2, RpgUi.CYAN, false, true);

		// Renderizar lista de áreas
		int listY = startY + 14;
		int listH = panelHeight - headerHeight - 26;
		RpgUi.panel(graphics, rightX, listY, rightW, listH, 0xFF101622, RpgUi.BORDER_SOFT);

		listScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
			if (areasList.isEmpty()) {
				RpgScreenText.draw(g, font, "Nenhuma área criada", rightX + 8, listY + 16, RpgUi.TEXT_MUTED, false, false);
				return;
			}

			int itemY = listY + 2;
			for (int i = 0; i < areasList.size(); i++) {
				AreaEntry a = areasList.get(i);
				int rowH = 30;

				boolean hovered = mouseX >= rightX && mouseX < rightX + rightW
						&& mouseY >= itemY - listScrollArea.getScroll()
						&& mouseY < itemY + rowH - listScrollArea.getScroll();

				int bg = hovered ? 0x2200D4FF : 0xFF141E2E;
				RpgUi.panel(g, rightX + 2, itemY, rightW - 4, rowH - 2, bg, RpgUi.BORDER);

				String modeTag = a.mode() == AreaTriggerData.Mode.START_WAVE ? "§b[Onda]" : a.mode() == AreaTriggerData.Mode.SPAWN_CHEST ? "§6[Baú]" : "§d[Música]";
				RpgScreenText.draw(g, font, a.name() + " " + modeTag, rightX + 5, itemY + 3, RpgUi.TEXT, false, true);

				String boundsStr = String.format("§8(%d,%d,%d) a (%d,%d,%d)", a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ());
				RpgScreenText.draw(g, font, boundsStr, rightX + 5, itemY + 14, RpgUi.TEXT_DIM, false, false);

				// Botão Editar (E)
				int editBtnX = rightX + rightW - 38;
				int editBtnY = itemY + 6;
				boolean editHover = mouseX >= editBtnX && mouseX < editBtnX + 16 && mouseY >= editBtnY - listScrollArea.getScroll() && mouseY < editBtnY + 14 - listScrollArea.getScroll();
				RpgUi.panel(g, editBtnX, editBtnY, 14, 14, editHover ? 0xFF2A3A52 : 0xFF182333, editHover ? RpgUi.CYAN : RpgUi.BORDER);
				RpgScreenText.draw(g, font, "✎", editBtnX + 3, editBtnY + 3, editHover ? RpgUi.CYAN : RpgUi.TEXT, false, false);

				// Botão Deletar (✕)
				int delBtnX = rightX + rightW - 20;
				int delBtnY = itemY + 6;
				boolean delHover = mouseX >= delBtnX && mouseX < delBtnX + 16 && mouseY >= delBtnY - listScrollArea.getScroll() && mouseY < delBtnY + 14 - listScrollArea.getScroll();
				RpgUi.panel(g, delBtnX, delBtnY, 14, 14, delHover ? 0x44FF4444 : 0xFF182333, delHover ? RpgUi.RED : RpgUi.BORDER);
				RpgScreenText.draw(g, font, "✕", delBtnX + 3, delBtnY + 3, delHover ? RpgUi.RED : 0xFF888888, false, false);

				itemY += 32;
			}
		});

		// Renderizar Dropdown flutuante de Baús
		if (chestDropdownOpen && mode == AreaTriggerData.Mode.SPAWN_CHEST) {
			int dropX = panelX + 44;
			int dropY = configY + 18;
			int dropW = leftW - 44;
			int dropH = Math.min(80, Math.max(1, availableChests.size()) * 16 + 4);
			RpgUi.panel(graphics, dropX, dropY, dropW, dropH, 0xF5151E2D, RpgUi.GOLD);

			if (availableChests.isEmpty()) {
				RpgScreenText.draw(graphics, font, "Nenhum baú", dropX + 6, dropY + 4, RpgUi.RED, false, false);
			} else {
				for (int i = 0; i < availableChests.size(); i++) {
					String cName = availableChests.get(i);
					int itemY = dropY + 2 + i * 16;
					boolean h = mouseX >= dropX && mouseX < dropX + dropW && mouseY >= itemY && mouseY < itemY + 16;
					if (h) graphics.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + 16, 0xFF2A3A52);
					RpgScreenText.draw(graphics, font, cName, dropX + 6, itemY + 4, cName.equalsIgnoreCase(targetChestName) ? RpgUi.GOLD : RpgUi.TEXT, false, true);
				}
			}
		}

		// Tooltip se passar o mouse sobre o ícone de música
		if (mode == AreaTriggerData.Mode.PLAY_MUSIC && !musicIcon.isEmpty()) {
			int iconX = panelX + 10;
			int iconY = configY - 1;
			if (mouseX >= iconX && mouseX < iconX + 18 && mouseY >= iconY && mouseY < iconY + 18) {
				boolean isPlaying = isMusicPlaying();
				String actionHint = isPlaying ? "§c(Clique para parar)" : "§e(Clique para ouvir)";
				graphics.setTooltipForNextFrame(Component.literal("§b" + musicTooltip + " " + actionHint), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int leftW = 185;
		int rightX = panelX + leftW + 12;
		int rightW = panelWidth - leftW - 20;
		int startY = panelY + headerHeight + 4;
		int coordY1 = startY + 18 + 20;
		int coordY2 = coordY1 + 18;
		int configY = coordY2 + 20;

		// Clique no ícone de música para prévia (tocar / parar e alternar seleção)
		if (mode == AreaTriggerData.Mode.PLAY_MUSIC && !musicIcon.isEmpty()) {
			int iconX = panelX + 10;
			int iconY = configY - 1;
			if (event.x() >= iconX && event.x() < iconX + 18 && event.y() >= iconY && event.y() < iconY + 18) {
				toggleMusicPreview();
				return true;
			}
		}

		// Clique no Dropdown de Baús
		if (chestDropdownOpen && mode == AreaTriggerData.Mode.SPAWN_CHEST) {
			int dropX = panelX + 44;
			int dropY = configY + 18;
			int dropW = leftW - 44;
			int dropH = Math.min(80, Math.max(1, availableChests.size()) * 16 + 4);
			if (event.x() >= dropX && event.x() < dropX + dropW && event.y() >= dropY && event.y() < dropY + dropH) {
				int idx = (int) ((event.y() - dropY - 2) / 16);
				if (idx >= 0 && idx < availableChests.size()) {
					this.targetChestName = availableChests.get(idx);
					btnSelectChest.setMessage(Component.literal(getChestButtonLabel()));
					br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
				}
				chestDropdownOpen = false;
				return true;
			} else {
				chestDropdownOpen = false;
			}
		}

		int listY = startY + 14;

		if (listScrollArea != null && listScrollArea.isMouseOver(event.x(), event.y())) {
			if (listScrollArea.mouseClicked(event)) return true;

			// Verifica cliques nos botões Editar e Deletar da lista
			double relY = event.y() - listY + listScrollArea.getScroll();
			int idx = (int) (relY / 32);
			if (idx >= 0 && idx < areasList.size()) {
				AreaEntry a = areasList.get(idx);
				int itemY = listY + 2 + idx * 32 - (int) listScrollArea.getScroll();

				// Editar
				int editBtnX = rightX + rightW - 38;
				int editBtnY = itemY + 6;
				if (event.x() >= editBtnX && event.x() < editBtnX + 16 && event.y() >= editBtnY && event.y() < editBtnY + 14) {
					loadAreaIntoForm(a);
					return true;
				}

				// Deletar
				int delBtnX = rightX + rightW - 20;
				int delBtnY = itemY + 6;
				if (event.x() >= delBtnX && event.x() < delBtnX + 16 && event.y() >= delBtnY && event.y() < delBtnY + 14) {
					deleteArea(a);
					return true;
				}
			}
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (listScrollArea != null && listScrollArea.mouseReleased(event)) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (listScrollArea != null && listScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (listScrollArea != null && listScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
