package br.com.rpgsystem.dungeon.client.screen;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgScrollArea;
import br.com.rpgsystem.dungeon.data.ChestSpawnData;
import br.com.rpgsystem.dungeon.network.DungeonPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ChestToolScreen extends RpgBaseScreen {
	private final String initialJson;
	private String chestName = "Baú";
	private String selectedLootTable = "";
	private ChestSpawnData.SpawnCondition condition = ChestSpawnData.SpawnCondition.AFTER_WAVE;
	private int waveOrder = 1;
	private String targetAreaName = "";
	private boolean hasPos = false;
	private int posX, posY, posZ;

	private final List<String> availableLootTables = new ArrayList<>();
	private final Map<String, List<LootItemDisplay>> lootTablesData = new HashMap<>();

	// Widgets
	private EditBox nameInput;
	private RpgScrollArea tablesScrollArea;
	private RpgScrollArea itemsScrollArea;

	// Tooltip hovered
	private ItemStack hoveredItem = ItemStack.EMPTY;
	private String hoveredItemText = "";

	public record LootItemDisplay(ItemStack stack, String name, double weight) {}

	public ChestToolScreen(String json) {
		super(Component.literal("Configuração de Baú"), "Configure o nome e a tabela de looting do baú");
		this.initialJson = json;
		this.showCloseButton = true;
		this.windowAccentColor = RpgUi.GOLD;
		parseJson();
	}

	private void parseJson() {
		try {
			JsonObject obj = JsonParser.parseString(initialJson).getAsJsonObject();
			if (obj.has("chestName")) chestName = obj.get("chestName").getAsString();
			if (obj.has("lootTableName")) selectedLootTable = obj.get("lootTableName").getAsString();
			if (obj.has("condition")) condition = ChestSpawnData.SpawnCondition.valueOf(obj.get("condition").getAsString());
			if (obj.has("waveOrder")) waveOrder = obj.get("waveOrder").getAsInt();
			if (obj.has("targetAreaName")) targetAreaName = obj.get("targetAreaName").getAsString();

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
					List<LootItemDisplay> list = new ArrayList<>();
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
						list.add(new LootItemDisplay(stack, name, weight));
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
		panelWidth = 330;
		panelHeight = 185;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
	}

	@Override
	protected void init() {
		super.init();

		int startY = panelY + headerHeight + 6;

		// Nome do baú
		nameInput = new EditBox(font, panelX + 54, startY, 150, 16, Component.literal("Nome"));
		nameInput.setMaxLength(64);
		nameInput.setValue(chestName);
		nameInput.setResponder(val -> this.chestName = val);
		addRenderableWidget(nameInput);

		// Áreas roláveis de Loot: Lista de tabelas à esquerda, Grid 3 colunas à direita
		int lootY = startY + 24;
		int scrollH = 80;
		int tableListW = 125;
		int itemsGridW = panelWidth - tableListW - 32;

		tablesScrollArea = new RpgScrollArea(panelX + 12, lootY, tableListW, scrollH);
		tablesScrollArea.setContentHeight(availableLootTables.size() * 18);

		itemsScrollArea = new RpgScrollArea(panelX + 16 + tableListW, lootY, itemsGridW, scrollH);
		updateItemsScrollHeight();

		// Botão OK centralizado
		int btnW = 90;
		int btnH = 18;
		addRenderableWidget(new RpgButton(panelX + (panelWidth - btnW) / 2, panelY + panelHeight - btnH - 6,
				btnW, btnH, Component.literal("OK"), RpgUi.GREEN, this::saveAndClose));
	}

	private void updateItemsScrollHeight() {
		if (itemsScrollArea == null) return;
		List<LootItemDisplay> items = lootTablesData.getOrDefault(selectedLootTable, List.of());
		int rows = (int) Math.ceil(items.size() / 3.0);
		itemsScrollArea.setContentHeight(rows * 24 + 4);
	}

	private void saveAndClose() {
		JsonObject obj = new JsonObject();
		obj.addProperty("chestName", nameInput.getValue().trim().isEmpty() ? "Baú" : nameInput.getValue().trim());
		obj.addProperty("lootTableName", selectedLootTable);
		obj.addProperty("condition", condition.name());
		obj.addProperty("waveOrder", waveOrder);
		obj.addProperty("targetAreaName", targetAreaName.trim());
		obj.addProperty("hasPos", hasPos);
		if (hasPos) {
			obj.addProperty("posX", posX);
			obj.addProperty("posY", posY);
			obj.addProperty("posZ", posZ);
		}

		ClientPlayNetworking.send(new DungeonPayloads.SaveChestTool(obj.toString()));
		onClose();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hoveredItem = ItemStack.EMPTY;
		hoveredItemText = "";

		int startY = panelY + headerHeight + 6;
		RpgScreenText.draw(graphics, font, "Nome:", panelX + 14, startY + 4, 0xFFCCCCCC, false, true);

		int lootY = startY + 24;
		int tableListW = 125;
		int itemsGridW = panelWidth - tableListW - 32;

		// Borda da lista de tabelas
		RpgUi.panel(graphics, panelX + 12, lootY, tableListW, 80, 0xFF101622, RpgUi.BORDER_SOFT);

		// Desenhar lista de tabelas
		tablesScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
			int itemY = lootY;
			for (String tName : availableLootTables) {
				boolean isSelected = tName.equalsIgnoreCase(selectedLootTable);
				boolean hovered = mouseX >= panelX + 12 && mouseX <= panelX + 12 + tableListW - 10
						&& mouseY >= itemY - tablesScrollArea.getScroll()
						&& mouseY < itemY + 16 - tablesScrollArea.getScroll();

				int color = isSelected ? 0xFFFFAA00 : hovered ? 0xFFFFFFFF : 0xFFAAAAAA;
				int bg = isSelected ? 0x44FFAA00 : hovered ? 0x22FFFFFF : 0x00000000;
				if (bg != 0) {
					g.fill(panelX + 13, itemY, panelX + 11 + tableListW, itemY + 16, bg);
				}
				RpgScreenText.draw(g, font, tName, panelX + 16, itemY + 4, color, false, true);
				itemY += 18;
			}
		});

		// Borda da grid de itens
		RpgUi.panel(graphics, panelX + 16 + tableListW, lootY, itemsGridW, 80, 0xFF101622, RpgUi.BORDER_SOFT);

		// Desenhar 3 colunas de itens com porcentagem calculada a partir do peso
		itemsScrollArea.render(graphics, mouseX, mouseY, delta, g -> {
			List<LootItemDisplay> items = lootTablesData.getOrDefault(selectedLootTable, List.of());
			if (items.isEmpty()) {
				RpgScreenText.draw(g, font, "Tabela sem itens", panelX + 24 + tableListW, lootY + 32, 0xFF777777, false, false);
				return;
			}

			double totalWeight = items.stream().mapToDouble(LootItemDisplay::weight).sum();
			int cellW = (itemsGridW - 16) / 3;
			int cellH = 22;

			for (int i = 0; i < items.size(); i++) {
				int col = i % 3;
				int row = i / 3;
				int cellX = panelX + 20 + tableListW + col * cellW;
				int cellY = lootY + 4 + row * cellH;

				LootItemDisplay itemDisp = items.get(i);
				ItemStack st = itemDisp.stack();
				double pct = totalWeight > 0 ? (itemDisp.weight() / totalWeight) * 100.0 : 0.0;
				String pctStr = String.format(Locale.ROOT, "%.1f%%", pct);

				boolean hovered = mouseX >= cellX && mouseX < cellX + cellW - 2
						&& mouseY >= cellY - itemsScrollArea.getScroll()
						&& mouseY < cellY + cellH - itemsScrollArea.getScroll();

				int fill = hovered ? 0x33FFAA00 : 0xFF141E2E;
				int border = hovered ? RpgUi.GOLD : RpgUi.BORDER;
				RpgUi.panel(g, cellX, cellY, cellW - 2, cellH - 2, fill, border);

				if (!st.isEmpty()) {
					g.item(st, cellX + 2, cellY + 2);
				}
				RpgScreenText.draw(g, font, pctStr, cellX + 19, cellY + 7, RpgUi.GOLD, false, false);

				if (hovered) {
					hoveredItem = st;
					hoveredItemText = itemDisp.name() + " - " + pctStr + " (" + String.format(Locale.ROOT, "%.1f", itemDisp.weight()) + "x)";
				}
			}
		});

		// Tooltip se houver item sob o mouse
		if (!hoveredItemText.isBlank()) {
			graphics.setTooltipForNextFrame(Component.literal("§6" + hoveredItemText), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int lootY = panelY + headerHeight + 6 + 24;
		int tableListW = 125;
		if (tablesScrollArea != null && tablesScrollArea.isMouseOver(event.x(), event.y())) {
			if (tablesScrollArea.mouseClicked(event)) {
				return true;
			}
			if (event.x() >= panelX + 12 && event.x() <= panelX + 12 + tableListW) {
				double relativeY = event.y() - lootY + tablesScrollArea.getScroll();
				int index = (int) (relativeY / 18);
				if (index >= 0 && index < availableLootTables.size()) {
					String clicked = availableLootTables.get(index);
					if (clicked.equalsIgnoreCase(selectedLootTable)) {
						selectedLootTable = "";
					} else {
						selectedLootTable = clicked;
					}
					updateItemsScrollHeight();
					br.com.rpgsystem.client.ui.RpgUiSounds.playClick();
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
		if (tablesScrollArea != null && tablesScrollArea.mouseReleased(event)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseReleased(event)) return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (tablesScrollArea != null && tablesScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseDragged(event, deltaX, deltaY)) return true;
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (tablesScrollArea != null && tablesScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		if (itemsScrollArea != null && itemsScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
