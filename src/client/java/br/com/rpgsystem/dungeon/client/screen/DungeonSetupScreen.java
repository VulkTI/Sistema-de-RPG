package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DungeonSetupScreen extends RpgBaseScreen {
	private static final AtomicBoolean FILE_DIALOG_OPEN = new AtomicBoolean(false);

	private String dungeonName;
	private int minLevel;
	private int maxLevel;
	private int weight;

	private EditBox nameField;
	private EditBox minLevelField;
	private EditBox maxLevelField;
	private EditBox weightField;

	private RpgButton btnEntrada;
	private RpgButton btnEntradaArrow;
	private RpgButton btnDungeon;
	private RpgButton btnDungeonArrow;

	private RpgButton btnConstruir;
	private RpgButton btnCancelar;

	// Estado de seleção
	private String selectedStructure = ""; // "ENTRADA" or "DUNGEON"
	private String entranceSchematicName = "";
	private String entranceSchematicPath = "";
	private String dungeonSchematicName = "";
	private String dungeonSchematicPath = "";

	private String entranceStatus = "";
	private boolean entranceValid = false;
	private String dungeonStatus = "";
	private boolean dungeonValid = false;
	private String errorMessage = "";

	public DungeonSetupScreen(String json) {
		this(parseJsonField(json, "dungeonName", "Nova Dungeon"),
				parseJsonInt(json, "minLevel", 1),
				parseJsonInt(json, "maxLevel", 1),
				parseJsonInt(json, "weight", 1),
				parseJsonField(json, "entranceSchematic", ""),
				parseJsonField(json, "dungeonSchematic", ""));
	}

	private static String parseJsonField(String json, String key, String def) {
		try {
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			return obj.has(key) ? obj.get(key).getAsString() : def;
		} catch (Throwable ignored) {
			return def;
		}
	}

	private static int parseJsonInt(String json, String key, int def) {
		try {
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			return obj.has(key) ? obj.get(key).getAsInt() : def;
		} catch (Throwable ignored) {
			return def;
		}
	}

	public DungeonSetupScreen(String dungeonName, int minLevel, int maxLevel, int weight,
	                          String entranceSchem, String dungeonSchem) {
		super(Component.literal("Sessão de Edição de Dungeon"));
		this.dungeonName = dungeonName;
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.weight = weight;
		this.showCloseButton = false;

		if (entranceSchem != null && !entranceSchem.isBlank()) {
			this.entranceSchematicPath = entranceSchem;
			this.entranceSchematicName = new File(entranceSchem).getName();
			this.entranceStatus = entranceSchematicName + " ✅";
			this.entranceValid = true;
		}
		if (dungeonSchem != null && !dungeonSchem.isBlank()) {
			this.dungeonSchematicPath = dungeonSchem;
			this.dungeonSchematicName = new File(dungeonSchem).getName();
			this.dungeonStatus = dungeonSchematicName + " ✅";
			this.dungeonValid = true;
		}
	}

	@Override
	protected void layoutWindow() {
		panelWidth = Math.min(320, width - 24);
		panelHeight = 240;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		headerHeight = 26;
		windowAccentColor = RpgUi.GOLD;
	}

	@Override
	protected void init() {
		super.init();

		int left = panelX + 16;
		int rightCol = panelX + panelWidth / 2 + 8;
		int colW = panelWidth / 2 - 24;

		// 1. Campo de texto do Nome da Dungeon (topo)
		int nameY = panelY + 32;
		nameField = addRenderableWidget(new EditBox(font, left, nameY, panelWidth - 32, 18, Component.literal("Nome da Dungeon")));
		nameField.setValue(dungeonName);
		nameField.setMaxLength(64);

		// 2. Botões: Entrada (esquerda) e Dungeon (direita) com setinha [V]
		int btnY = nameY + 28;
		int mainBtnW = colW - 20;

		btnEntrada = addRenderableWidget(new RpgButton(left, btnY, mainBtnW, 20, Component.literal("Entrada"), RpgUi.BLUE, () -> {
			selectStructure("ENTRADA");
		}));

		btnEntradaArrow = addRenderableWidget(new RpgButton(left + mainBtnW + 2, btnY, 18, 20, Component.literal("v"), RpgUi.BLUE, () -> {
			ensureStructureSelected("ENTRADA");
			openSchematicFilePicker("ENTRADA");
		}).setTooltip("Selecionar esquemática", "Abre o explorador de arquivos para escolher um .nbt"));

		btnDungeon = addRenderableWidget(new RpgButton(rightCol, btnY, mainBtnW, 20, Component.literal("Dungeon"), RpgUi.PURPLE, () -> {
			selectStructure("DUNGEON");
		}));

		btnDungeonArrow = addRenderableWidget(new RpgButton(rightCol + mainBtnW + 2, btnY, 18, 20, Component.literal("v"), RpgUi.PURPLE, () -> {
			ensureStructureSelected("DUNGEON");
			openSchematicFilePicker("DUNGEON");
		}).setTooltip("Selecionar esquemática", "Abre o explorador de arquivos para escolher um .nbt"));

		// 3. Campos de Nível Mín e Nível Máx
		int lvlY = btnY + 42;
		minLevelField = addRenderableWidget(new EditBox(font, left + 30, lvlY, colW - 30, 16, Component.literal("Mín")));
		minLevelField.setValue(String.valueOf(minLevel));
		minLevelField.setMaxLength(3);

		maxLevelField = addRenderableWidget(new EditBox(font, rightCol + 30, lvlY, colW - 30, 16, Component.literal("Máx")));
		maxLevelField.setValue(String.valueOf(maxLevel));
		maxLevelField.setMaxLength(3);

		// 4. Campo de Peso
		int weightY = lvlY + 24;
		weightField = addRenderableWidget(new EditBox(font, left + 35, weightY, colW - 35, 16, Component.literal("Peso")));
		weightField.setValue(String.valueOf(weight));
		weightField.setMaxLength(4);

		// 5. Botões: Construir/Editar/Salvar e Cancelar
		int actionY = panelY + panelHeight - 28;
		btnConstruir = addRenderableWidget(new RpgButton(left, actionY, colW, 20, Component.literal("SALVAR"), RpgUi.GREEN, this::handleActionClick));
		btnCancelar = addRenderableWidget(new RpgButton(rightCol, actionY, colW, 20, Component.literal("Cancelar"), RpgUi.RED, this::handleCancel));

		updateButtonsState();
	}

	private void selectStructure(String type) {
		if (type != null && type.equalsIgnoreCase(this.selectedStructure)) {
			this.selectedStructure = "";
		} else {
			this.selectedStructure = type;
		}
		RpgUiSounds.playClick();
		updateButtonsState();
	}

	private void ensureStructureSelected(String type) {
		if (type != null && !type.equalsIgnoreCase(this.selectedStructure)) {
			this.selectedStructure = type;
			updateButtonsState();
		}
		RpgUiSounds.playClick();
	}

	private void updateButtonsState() {
		btnEntrada.setAccentColor("ENTRADA".equals(selectedStructure) ? RpgUi.GREEN : RpgUi.BLUE);
		btnDungeon.setAccentColor("DUNGEON".equals(selectedStructure) ? RpgUi.GREEN : RpgUi.PURPLE);

		boolean hasSelection = !selectedStructure.isBlank();
		if (hasSelection) {
			boolean hasSchem = ("ENTRADA".equals(selectedStructure) && entranceValid)
					|| ("DUNGEON".equals(selectedStructure) && dungeonValid);
			btnConstruir.active = true;
			btnConstruir.setMessage(Component.literal(hasSchem ? "Editar" : "Construir"));
			btnConstruir.setAccentColor(hasSchem ? RpgUi.CYAN : RpgUi.GREEN);
		} else {
			btnConstruir.active = true;
			btnConstruir.setMessage(Component.literal("SALVAR"));
			btnConstruir.setAccentColor(RpgUi.GREEN);
		}
	}

	private void openSchematicFilePicker(String targetStructure) {
		if (!FILE_DIALOG_OPEN.compareAndSet(false, true)) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Path initialDir;
		try {
			initialDir = getWorldStructuresDirectory(minecraft).toAbsolutePath().normalize();
			Files.createDirectories(initialDir);
		} catch (Exception ex) {
			FILE_DIALOG_OPEN.set(false);
			br.com.rpgsystem.RpgMod.LOGGER.error("Não foi possível abrir a pasta de esquemáticas do mundo", ex);
			setSchematicStatus(targetStructure, "esquemática falhou ❌", false);
			return;
		}

		// SDL3 exige separador final para abrir diretamente dentro da pasta (e não pré-preencher o nome da pasta).
		String initialLocation = initialDir.toString();
		if (!initialLocation.endsWith(File.separator)) {
			initialLocation = initialLocation + File.separator;
		}

		ByteBuffer filterName = null;
		ByteBuffer filterPattern = null;
		SDL_DialogFileFilter.Buffer filters = null;
		SDL_DialogFileCallback callback = null;
		int props = 0;

		try {
			filterName = MemoryUtil.memUTF8("Esquemáticas NBT (*.nbt)", true);
			filterPattern = MemoryUtil.memUTF8("nbt", true);
			filters = SDL_DialogFileFilter.calloc(1);
			filters.get(0).name(filterName).pattern(filterPattern);

			final ByteBuffer finalFilterName = filterName;
			final ByteBuffer finalFilterPattern = filterPattern;
			final SDL_DialogFileFilter.Buffer finalFilters = filters;
			final SDL_DialogFileCallback[] callbackHolder = new SDL_DialogFileCallback[1];

			callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
				String selectedPath = null;
				boolean sdlError = false;
				String sdlErrorMsg = null;

				if (filelist == MemoryUtil.NULL) {
					sdlError = true;
					sdlErrorMsg = SDLError.SDL_GetError();
				} else {
					long firstFilePtr = MemoryUtil.memGetAddress(filelist);
					if (firstFilePtr != MemoryUtil.NULL) {
						selectedPath = MemoryUtil.memUTF8(firstFilePtr);
					}
				}

				final String finalSelectedPath = selectedPath;
				final boolean finalSdlError = sdlError;
				final String finalSdlErrorMsg = sdlErrorMsg;

				minecraft.execute(() -> {
					try {
						if (callbackHolder[0] != null) {
							callbackHolder[0].free();
						}
						finalFilters.free();
						MemoryUtil.memFree(finalFilterName);
						MemoryUtil.memFree(finalFilterPattern);
					} finally {
						FILE_DIALOG_OPEN.set(false);
					}

					if (finalSdlError) {
						br.com.rpgsystem.RpgMod.LOGGER.error("Falha ao abrir o explorador de arquivos (SDL): {}", finalSdlErrorMsg);
						setSchematicStatus(targetStructure, "esquemática falhou ❌", false);
						return;
					}

					if (finalSelectedPath == null || finalSelectedPath.isBlank()) {
						return;
					}

					loadSelectedSchematic(minecraft, targetStructure, Path.of(finalSelectedPath));
				});
			});
			callbackHolder[0] = callback;

			long windowHandle = minecraft.getWindow() != null ? minecraft.getWindow().handle() : MemoryUtil.NULL;
			props = SDLProperties.SDL_CreateProperties();
			if (props == 0) {
				throw new IllegalStateException("SDL_CreateProperties falhou: " + SDLError.SDL_GetError());
			}
			SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
			SDLProperties.SDL_SetNumberProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1L);
			SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER, windowHandle);
			SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, initialLocation);
			SDLProperties.SDL_SetBooleanProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_MANY_BOOLEAN, false);
			SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, "Selecionar Esquemática (.nbt)");

			SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, callback, MemoryUtil.NULL, props);
		} catch (Throwable ex) {
			FILE_DIALOG_OPEN.set(false);
			if (callback != null) callback.free();
			if (filters != null) filters.free();
			if (filterName != null) MemoryUtil.memFree(filterName);
			if (filterPattern != null) MemoryUtil.memFree(filterPattern);
			br.com.rpgsystem.RpgMod.LOGGER.error("Falha ao abrir o explorador de arquivos para esquemática", ex);
			setSchematicStatus(targetStructure, "esquemática falhou ❌", false);
		} finally {
			if (props != 0) {
				SDLProperties.SDL_DestroyProperties(props);
			}
		}
	}

	private void loadSelectedSchematic(Minecraft minecraft, String targetStructure, Path selected) {
		CompletableFuture.runAsync(() -> {
			try {
				String file = selected.getFileName() != null ? selected.getFileName().toString() : "";
				if (!Files.isRegularFile(selected) || !file.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
					minecraft.execute(() -> setSchematicStatus(targetStructure, "esquemática falhou ❌", false));
					return;
				}
				CompoundTag tag = NbtIo.readCompressed(selected, NbtAccounter.unlimitedHeap());
				if (isStructureSchematic(tag)) {
					String absolutePath = selected.toAbsolutePath().normalize().toString();
					minecraft.execute(() -> {
						if ("ENTRADA".equals(targetStructure)) {
							entranceSchematicName = file;
							entranceSchematicPath = absolutePath;
						} else {
							dungeonSchematicName = file;
							dungeonSchematicPath = absolutePath;
						}
						setSchematicStatus(targetStructure, file + " ✅", true);
						RpgUiSounds.play(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F);
					});
				} else {
					minecraft.execute(() -> setSchematicStatus(targetStructure, "esquemática falhou ❌", false));
				}
			} catch (Exception ex) {
				br.com.rpgsystem.RpgMod.LOGGER.error("Falha ao abrir ou ler a esquemática da dungeon", ex);
				minecraft.execute(() -> setSchematicStatus(targetStructure, "esquemática falhou ❌", false));
			}
		});
	}

	private static Path getWorldStructuresDirectory(Minecraft minecraft) {
		MinecraftServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			return server.getWorldPath(LevelResource.GENERATED_DIR).resolve("minecraft").resolve("structures");
		}
		// Em servidores remotos não existe uma pasta de mundo local acessível ao cliente.
		return minecraft.gameDirectory.toPath().resolve("schematics");
	}

	private static boolean isStructureSchematic(CompoundTag tag) {
		return tag != null && tag.contains("size")
				&& (tag.contains("palette") || tag.contains("palettes"))
				&& tag.contains("blocks");
	}

	private void setSchematicStatus(String targetStructure, String status, boolean valid) {
		if ("ENTRADA".equals(targetStructure)) {
			entranceStatus = status;
			entranceValid = valid;
			if (!valid) {
				entranceSchematicName = "";
				entranceSchematicPath = "";
			}
		} else {
			dungeonStatus = status;
			dungeonValid = valid;
			if (!valid) {
				dungeonSchematicName = "";
				dungeonSchematicPath = "";
			}
		}
		updateButtonsState();
	}

	private void handleActionClick() {
		if (selectedStructure.isBlank()) {
			handleSaveDungeon();
		} else {
			handleBuildOrEdit();
		}
	}

	private void handleSaveDungeon() {
		errorMessage = "";
		String name = nameField.getValue().trim();
		if (name.isBlank()) {
			errorMessage = "Digite um nome para a dungeon!";
			return;
		}

		int minLvl, maxLvl, w;
		try {
			minLvl = Integer.parseInt(minLevelField.getValue().trim());
			maxLvl = Integer.parseInt(maxLevelField.getValue().trim());
			w = Integer.parseInt(weightField.getValue().trim());
		} catch (NumberFormatException e) {
			errorMessage = "Valores de nível e peso devem ser números válidos!";
			return;
		}

		if (maxLvl <= 0 || maxLvl > 100) {
			errorMessage = "O nível MÁX deve estar entre 1 e 100!";
			return;
		}
		if (maxLvl < minLvl) {
			errorMessage = "O nível MÁX deve ser maior ou igual ao MÍN!";
			return;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("dungeonName", name);
		payload.addProperty("minLevel", minLvl);
		payload.addProperty("maxLevel", maxLvl);
		payload.addProperty("weight", Math.max(1, w));
		payload.addProperty("entranceSchematicPath", entranceSchematicPath != null ? entranceSchematicPath : "");
		payload.addProperty("dungeonSchematicPath", dungeonSchematicPath != null ? dungeonSchematicPath : "");

		br.com.rpgsystem.dungeon.client.DungeonClientEditorState.leaveSession();
		ClientPlayNetworking.send(new DungeonPayloads.SaveDungeonSetup(payload.toString()));
		RpgUiSounds.play(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F);
		Minecraft.getInstance().gui.setScreen(null);
	}

	private void handleBuildOrEdit() {
		errorMessage = "";
		String name = nameField.getValue().trim();
		if (name.isBlank()) {
			errorMessage = "Digite um nome para a dungeon!";
			return;
		}

		int minLvl, maxLvl, w;
		try {
			minLvl = Integer.parseInt(minLevelField.getValue().trim());
			maxLvl = Integer.parseInt(maxLevelField.getValue().trim());
			w = Integer.parseInt(weightField.getValue().trim());
		} catch (NumberFormatException e) {
			errorMessage = "Valores de nível e peso devem ser números válidos!";
			return;
		}

		if (maxLvl <= 0 || maxLvl > 100) {
			errorMessage = "O nível MÁX deve estar entre 1 e 100!";
			return;
		}
		if (maxLvl < minLvl) {
			errorMessage = "O nível MÁX deve ser maior ou igual ao MÍN!";
			return;
		}
		if (selectedStructure.isBlank()) {
			errorMessage = "Selecione a estrutura: Entrada ou Dungeon!";
			return;
		}

		// Envia para o servidor
		JsonObject payload = new JsonObject();
		payload.addProperty("dungeonName", name);
		payload.addProperty("minLevel", minLvl);
		payload.addProperty("maxLevel", maxLvl);
		payload.addProperty("weight", Math.max(1, w));
		payload.addProperty("selectedStructure", selectedStructure);

		String path = "ENTRADA".equals(selectedStructure) ? entranceSchematicPath : dungeonSchematicPath;
		payload.addProperty("schematicPath", path);

		ClientPlayNetworking.send(new DungeonPayloads.StartBuild(payload.toString()));
		RpgUiSounds.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);

		// Fecha esta tela e libera o jogador para construir no void
		Minecraft.getInstance().gui.setScreen(null);
	}

	private void handleCancel() {
		br.com.rpgsystem.dungeon.client.DungeonClientEditorState.leaveSession();
		ClientPlayNetworking.send(new DungeonPayloads.CancelSetup());
		Minecraft.getInstance().gui.setScreen(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		boolean handled = super.mouseClicked(event, doubleClick);

		// Se clicar fora dos botões de estrutura e não estiver sobre outros controles interativos, deseleciona a estrutura
		boolean overStructButtons = (btnEntrada != null && btnEntrada.isMouseOver(mx, my))
				|| (btnEntradaArrow != null && btnEntradaArrow.isMouseOver(mx, my))
				|| (btnDungeon != null && btnDungeon.isMouseOver(mx, my))
				|| (btnDungeonArrow != null && btnDungeonArrow.isMouseOver(mx, my));

		boolean overOtherControls = (btnConstruir != null && btnConstruir.isMouseOver(mx, my))
				|| (btnCancelar != null && btnCancelar.isMouseOver(mx, my))
				|| (nameField != null && nameField.isMouseOver(mx, my))
				|| (minLevelField != null && minLevelField.isMouseOver(mx, my))
				|| (maxLevelField != null && maxLevelField.isMouseOver(mx, my))
				|| (weightField != null && weightField.isMouseOver(mx, my));

		if (!overStructButtons && !overOtherControls && !selectedStructure.isBlank()) {
			selectedStructure = "";
			updateButtonsState();
			return true;
		}

		return handled;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// BLOQUEIA ESC: O jogador NÃO pode sair sem escolher Construir/editar ou Cancelar!
		if (event.isEscape() || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || event.key() == 256) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int left = panelX + 16;
		int rightCol = panelX + panelWidth / 2 + 8;

		// Rótulos
		RpgScreenText.draw(graphics, font, "Mín:", left, panelY + 104, RpgUi.TEXT_MUTED, false, true);
		RpgScreenText.draw(graphics, font, "Máx:", rightCol, panelY + 104, RpgUi.TEXT_MUTED, false, true);
		RpgScreenText.draw(graphics, font, "Peso:", left, panelY + 128, RpgUi.TEXT_MUTED, false, true);

		// Textos de esquemática abaixo dos botões Entrada / Dungeon
		if (!entranceStatus.isBlank()) {
			int color = entranceValid ? RpgUi.GREEN : RpgUi.RED;
			RpgScreenText.draw(graphics, font, entranceStatus, left, panelY + 80, color, false, true);
		}
		if (!dungeonStatus.isBlank()) {
			int color = dungeonValid ? RpgUi.GREEN : RpgUi.RED;
			RpgScreenText.draw(graphics, font, dungeonStatus, rightCol, panelY + 80, color, false, true);
		}

		// Mensagem de erro de validação
		if (!errorMessage.isBlank()) {
			RpgScreenText.draw(graphics, font, errorMessage, panelX + panelWidth / 2, panelY + panelHeight - 44, RpgUi.RED, true, true);
		}
	}
}
