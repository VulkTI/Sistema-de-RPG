package br.com.rpgsystem.client.screen;

import br.com.rpgsystem.client.RpgClientState;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgProgressBar;
import br.com.rpgsystem.client.ui.RpgScrollArea;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import br.com.rpgsystem.network.RpgPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grafo navegável de uma árvore de habilidades da classe ou universal.
 * Atualizado com a nova base de UI:
 * - Área de rolagem lateral usando RpgScrollArea
 * - Barra de progresso reativa RpgProgressBar
 * - Animações de pulso em nós prontos para aprender
 * - Conexões com realce luminoso e sons imersivos
 */
public final class SkillTreeScreen extends Screen {
	private static final int SIDEBAR_WIDTH = 230;
	private static final int FOOTER_HEIGHT = 38;
	private static final int SCREEN_MARGIN = 12;
	private static final double MIN_ZOOM = 0.45;
	private static final double MAX_ZOOM = 2.5;

	private JsonArray skills;
	private final Map<String, Integer> depths = new HashMap<>();
	private final Map<String, Double> horizontalOffsets = new HashMap<>();
	private String selectedSkillId = "";
	private String pressedSkillId;
	private double zoom = 1.0;
	private double panX;
	private double panY;
	private double dragX;
	private double dragY;
	private boolean dragging;

	private RpgScrollArea sidebarScrollArea;
	private RpgProgressBar sidebarProgressBar;
	private RpgButton confirmButton;
	private final String universalSkillId;

	public SkillTreeScreen() {
		this(null);
	}

	public SkillTreeScreen(String universalSkillId) {
		super(Component.literal("Árvore de Habilidades"));
		this.universalSkillId = universalSkillId;
		JsonArray tree = treeFromSnapshot(RpgClientState.snapshot());
		skills = tree == null ? new JsonArray() : tree;
		rebuildLayout();
	}

	private JsonArray treeFromSnapshot(JsonObject snapshot) {
		if (universalSkillId == null) return snapshot.getAsJsonArray("skillTree");
		JsonObject trees = snapshot.getAsJsonObject("universalSkillTrees");
		return trees == null || !trees.has(universalSkillId) ? new JsonArray() : trees.getAsJsonArray(universalSkillId);
	}

	private void rebuildLayout() {
		depths.clear();
		for (int i = 0; i < skills.size(); i++) depth(skills.get(i).getAsJsonObject(), new java.util.HashSet<>());
		Map<Integer, List<JsonObject>> levels = new java.util.TreeMap<>();
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			levels.computeIfAbsent(depths.getOrDefault(skill.get("id").getAsString(), 0), unused -> new ArrayList<>()).add(skill);
		}
		horizontalOffsets.clear();
		for (List<JsonObject> nodes : levels.values()) {
			for (int i = 0; i < nodes.size(); i++) {
				horizontalOffsets.put(nodes.get(i).get("id").getAsString(), (i - (nodes.size() - 1) / 2.0) * 180.0);
			}
		}
	}

	private int depth(JsonObject skill, java.util.Set<String> visiting) {
		String id = stringValue(skill, "id", "");
		if (depths.containsKey(id)) return depths.get(id);
		if (!visiting.add(id)) return 0;
		int result = 0;
		JsonArray deps = skill.getAsJsonArray("dependencies");
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency != null) result = Math.max(result, depth(dependency, visiting) + 1);
		}
		visiting.remove(id);
		depths.put(id, result);
		return result;
	}

	private JsonObject find(String id) {
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			if (id.equals(stringValue(skill, "id", ""))) return skill;
		}
		return null;
	}

	private boolean requirementsMet(JsonObject skill) {
		JsonArray deps = skill.getAsJsonArray("dependencies");
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency == null || !boolValue(dependency, "unlocked")) return false;
		}
		return true;
	}

	@Override
	protected void init() {
		super.init();
		RpgUiSounds.playOpen();

		int sidebarX = sidebarX();
		int bodyHeight = height - SCREEN_MARGIN - FOOTER_HEIGHT;

		// Botão CONFIRMAR
		confirmButton = addRenderableWidget(new RpgButton(
				sidebarX + 16,
				height - SCREEN_MARGIN - FOOTER_HEIGHT - 27,
				SIDEBAR_WIDTH - 32,
				22,
				Component.literal("APRENDER"),
				RpgUi.CYAN,
				this::confirmTree
		).setHoverEffect(RpgButton.HoverEffect.PULSE));

		// Botão VOLTAR
		addRenderableWidget(new RpgButton(
				SCREEN_MARGIN + 10,
				SCREEN_MARGIN + 10,
				60,
				16,
				Component.literal("← VOLTAR"),
				RpgUi.BLUE,
				this::returnToCharacter
		).setHoverEffect(RpgButton.HoverEffect.STATIC));

		// Barra de progresso de XP na sidebar
		sidebarProgressBar = addRenderableWidget(new RpgProgressBar(
				sidebarX + 26,
				SCREEN_MARGIN + 55,
				SIDEBAR_WIDTH - 52,
				8,
				() -> getXpCurrent(),
				() -> getXpNext(),
				RpgUi.CYAN
		).setPulseWhenFull(true));

		// Área de rolagem para o corpo da sidebar
		int viewportTop = SCREEN_MARGIN + 72;
		int viewportBottom = bodyHeight - 32;
		sidebarScrollArea = new RpgScrollArea(sidebarX + 14, viewportTop, SIDEBAR_WIDTH - 28, viewportBottom - viewportTop);
	}

	private long getXpCurrent() {
		JsonObject snap = RpgClientState.snapshot();
		if (universalSkillId == null) return longValue(snap, "classXpCurrent");
		JsonObject u = universalSkill(snap);
		return u != null ? longValue(u, "xpCurrent") : 0L;
	}

	private long getXpNext() {
		JsonObject snap = RpgClientState.snapshot();
		if (universalSkillId == null) return Math.max(1L, longValue(snap, "classXpNext"));
		JsonObject u = universalSkill(snap);
		return u != null ? Math.max(1L, longValue(u, "xpNext")) : 1L;
	}

	private void confirmTree() {
		JsonObject selected = find(selectedSkillId);
		if (selected != null && !boolValue(selected, "unlocked") && requirementsMet(selected) && availablePoints() >= intValue(selected, "cost")) {
			RpgUiSounds.playSkillLearn();
			if (universalSkillId == null) {
				ClientPlayNetworking.send(new RpgPayloads.LearnSkill(selectedSkillId));
			} else {
				ClientPlayNetworking.send(new RpgPayloads.LearnUniversalSkill(universalSkillId, selectedSkillId));
			}
		} else {
			RpgUiSounds.playClick();
		}
		returnToCharacter();
	}

	private void returnToCharacter() {
		RpgUiSounds.playClose();
		Minecraft.getInstance().gui.setScreen(new PlayerStatusScreen());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		JsonArray latestTree = treeFromSnapshot(RpgClientState.snapshot());
		if (latestTree != null && latestTree != skills) {
			skills = latestTree;
			if (!selectedSkillId.isBlank() && find(selectedSkillId) == null) selectedSkillId = "";
			rebuildLayout();
		}

		JsonObject selected = find(selectedSkillId);
		boolean canLearn = selected != null && !boolValue(selected, "unlocked") && requirementsMet(selected) && availablePoints() >= intValue(selected, "cost");
		confirmButton.active = selected == null || canLearn;
		confirmButton.setMessage(Component.literal(selected == null ? "RETORNAR" : canLearn ? "APRENDER" : boolValue(selected, "unlocked") ? "DESBLOQUEADO" : "BLOQUEADO"));

		RpgUi.dim(graphics, width, height);
		int sidebarX = sidebarX();

		// Moldura do Canvas e Divisor
		RpgUi.panel(graphics, SCREEN_MARGIN, SCREEN_MARGIN, width - SCREEN_MARGIN * 2, height - SCREEN_MARGIN * 2, 0xEE111A2A, 0xFF3A526D);
		graphics.fill(sidebarX - 1, SCREEN_MARGIN, sidebarX, height - SCREEN_MARGIN, 0xFF0A0F1A);

		drawCanvas(graphics, mouseX, mouseY);
		drawSidebar(graphics, sidebarX, selected, mouseX, mouseY, delta);

		JsonObject hovered = hoverNode(mouseX, mouseY);
		if (hovered != null) drawNodeTooltip(graphics, mouseX, mouseY, hovered);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int canvasWidth = canvasWidth();
		int headerHeight = SCREEN_MARGIN + 36;

		// Faixa do topo do canvas
		graphics.fill(SCREEN_MARGIN, SCREEN_MARGIN, canvasWidth, headerHeight, 0xCC0A111E);
		RpgScreenText.draw(graphics, font, "ÁRVORE DE HABILIDADES", canvasWidth / 2, SCREEN_MARGIN + 14, RpgUi.TEXT, true, true);

		// Grid sutil decorativo
		for (int x = SCREEN_MARGIN; x < canvasWidth; x += 32) graphics.fill(x, headerHeight, x + 1, canvasBottom(), 0x301D3045);
		for (int y = headerHeight; y < canvasBottom(); y += 32) graphics.fill(SCREEN_MARGIN, y, canvasWidth, y + 1, 0x301D3045);

		int centerX = canvasWidth / 2;
		double baseY = baseY();

		// Conexões
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			JsonArray deps = skill.getAsJsonArray("dependencies");
			for (int j = 0; deps != null && j < deps.size(); j++) {
				JsonObject parent = find(deps.get(j).getAsString());
				if (parent != null) drawConnection(graphics, centerX, baseY, parent, skill);
			}
		}

		// Nós da árvore
		for (int i = 0; i < skills.size(); i++) {
			drawNode(graphics, centerX, baseY, skills.get(i).getAsJsonObject(), mouseX, mouseY);
		}

		if (skills.isEmpty()) {
			RpgScreenText.draw(graphics, font, "Sem habilidades para mostrar", canvasWidth / 2, (SCREEN_MARGIN + canvasBottom()) / 2 - 6,
					RpgUi.TEXT, true, true);
		}

		// Badge de Zoom no canto
		RpgUi.badge(graphics, font, String.format(java.util.Locale.ROOT, "ZOOM %d%%", Math.round(zoom * 100.0)),
				canvasWidth - 92, SCREEN_MARGIN + 10, RpgUi.BLUE);
	}

	private void drawConnection(GuiGraphicsExtractor graphics, double centerX, double baseY, JsonObject parent, JsonObject child) {
		double px = centerX + panX + horizontalOffsets.getOrDefault(stringValue(parent, "id", ""), 0.0) * zoom;
		double py = baseY + panY - depths.getOrDefault(stringValue(parent, "id", ""), 0) * 150.0 * zoom;
		double cx = centerX + panX + horizontalOffsets.getOrDefault(stringValue(child, "id", ""), 0.0) * zoom;
		double cy = baseY + panY - depths.getOrDefault(stringValue(child, "id", ""), 0) * 150.0 * zoom;

		int x1 = (int) px, x2 = (int) cx;
		int y1 = (int) (py - 24 * zoom);
		int y2 = (int) (cy + 24 * zoom);
		int middle = (y1 + y2) / 2;

		int color = boolValue(parent, "unlocked") && boolValue(child, "unlocked") ? RpgUi.CYAN : 0xFF34455B;
		fillGraphClipped(graphics, Math.min(x1, x2), middle, Math.max(x1, x2) + 1, middle + 2, color);
		fillGraphClipped(graphics, x1, middle, x1 + 2, y1 + 2, color);
		fillGraphClipped(graphics, x2, y2 - 2, x2 + 2, middle + 2, color);
	}

	private void fillGraphClipped(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
		int canvasWidth = canvasWidth();
		int left = Math.max(0, Math.min(x1, x2));
		int right = Math.min(canvasWidth, Math.max(x1, x2));
		int top = Math.max(SCREEN_MARGIN + 36, Math.min(y1, y2));
		int bottom = Math.min(canvasBottom(), Math.max(y1, y2));
		if (right > left && bottom > top) graphics.fill(left, top, right, bottom, color);
	}

	private void drawNode(GuiGraphicsExtractor graphics, double centerX, double baseY, JsonObject skill, int mouseX, int mouseY) {
		String id = stringValue(skill, "id", "");
		double nodeX = centerX + panX + horizontalOffsets.getOrDefault(id, 0.0) * zoom;
		double nodeY = baseY + panY - depths.getOrDefault(id, 0) * 150.0 * zoom;
		int nodeWidth = Math.max(44, (int) (58 * zoom));
		int nodeHeight = nodeWidth;

		if (nodeX < SCREEN_MARGIN - nodeWidth || nodeX > canvasWidth() + nodeWidth) return;
		int x = (int) nodeX - nodeWidth / 2;
		int y = (int) nodeY - nodeHeight / 2;

		boolean unlocked = boolValue(skill, "unlocked");
		boolean available = requirementsMet(skill);
		boolean readyToLearn = !unlocked && available && availablePoints() >= intValue(skill, "cost");
		boolean hovered = mouseX >= x && mouseX < x + nodeWidth && mouseY >= y && mouseY < y + nodeHeight;
		boolean isSelected = id.equals(selectedSkillId);

		int color = unlocked ? RpgUi.CYAN : readyToLearn ? RpgUi.GOLD : available ? RpgUi.PURPLE : RpgUi.TEXT_DIM;
		if (readyToLearn) {
			float pulse = RpgUi.pulse(2.0F);
			color = RpgUi.blend(RpgUi.RED, RpgUi.GOLD, pulse);
		}

		int border = isSelected ? 0xFFFFFFFF : hovered ? RpgUi.BORDER_HIGHLIGHT : color;
		int fill = isSelected ? 0xF02B374A : hovered ? RpgUi.PANEL_HOVER : unlocked ? 0xFF172B34 : available ? 0xFF211F35 : 0xFF111824;

		RpgUi.panel(graphics, x, y, nodeWidth, nodeHeight, fill, border);
		RpgUi.accent(graphics, x, y, nodeWidth, color);

		ItemStack icon = new ItemStack(universalSkillId == null ? Items.ENCHANTED_BOOK : Items.NETHER_STAR);
		graphics.item(icon, x + nodeWidth / 2 - 8, y + nodeHeight / 2 - 8);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int left, JsonObject selected, int mouseX, int mouseY, float delta) {
		int bodyHeight = height - SCREEN_MARGIN - FOOTER_HEIGHT;
		RpgUi.panel(graphics, left + 10, SCREEN_MARGIN, SIDEBAR_WIDTH - 20, bodyHeight - SCREEN_MARGIN - 4, RpgUi.PANEL, RpgUi.BORDER);

		// Cabeçalho da Sidebar
		String treeName = universalSkillId == null ? stringValue(RpgClientState.snapshot(), "selectedClassName", "Classe") : universalSkillName(RpgClientState.snapshot());
		RpgScreenText.draw(graphics, font, treeName, left + SIDEBAR_WIDTH / 2, SCREEN_MARGIN + 12, RpgUi.TEXT, true, true);

		int level = 0;
		int points = availablePoints();
		JsonObject snapshot = RpgClientState.snapshot();
		if (universalSkillId == null) {
			level = intValue(snapshot, "classLevel");
		} else {
			JsonObject progress = universalSkill(snapshot);
			if (progress != null) level = intValue(progress, "level");
		}

		int x = left + 26;
		RpgScreenText.draw(graphics, font, "NÍVEL " + level, x, SCREEN_MARGIN + 40, RpgUi.CYAN, false, true);
		String pointsText = "PONTOS: " + points;
		RpgScreenText.draw(graphics, font, pointsText, left + SIDEBAR_WIDTH - font.width(pointsText) - 26, SCREEN_MARGIN + 40, RpgUi.GOLD, false, true);

		// Renderizar conteúdo rolável da sidebar com RpgScrollArea
		int textWidth = sidebarScrollArea.getVisibleContentWidth() - 4;
		int textX = left + 24;

		sidebarScrollArea.render(graphics, mouseX, mouseY, delta, innerGraphics -> {
			int curY = SCREEN_MARGIN + 74;

			RpgUi.divider(innerGraphics, textX, curY, textWidth);
			curY += 10;

			RpgScreenText.draw(innerGraphics, font, "DESCRIÇÃO", textX, curY, RpgUi.TEXT, false, true);
			curY += 14;
			curY = RpgScreenText.drawWrapped(innerGraphics, font, treeDescription(snapshot), textX, curY, textWidth, RpgUi.TEXT_MUTED) + 10;

			RpgScreenText.draw(innerGraphics, font, "COMO GANHAR XP", textX, curY, RpgUi.TEXT, false, true);
			curY += 14;
			curY = RpgScreenText.drawWrapped(innerGraphics, font, treeXpGuide(), textX, curY, textWidth, RpgUi.TEXT_MUTED) + 12;

			if (selected == null) {
				RpgUi.label(innerGraphics, font, "Habilidades Ativas", textX, curY, RpgUi.TEXT_MUTED);
				curY += 16;
				boolean any = false;
				for (int i = 0; i < skills.size(); i++) {
					JsonObject node = skills.get(i).getAsJsonObject();
					if (boolValue(node, "unlocked")) {
						any = true;
						curY = RpgScreenText.drawWrapped(innerGraphics, font, "●  " + stringValue(node, "name", "Habilidade"), textX, curY, textWidth, RpgUi.GREEN) + 4;
					}
				}
				if (!any) {
					curY = RpgScreenText.drawWrapped(innerGraphics, font, "Nenhuma habilidade aprendida ainda.", textX, curY, textWidth, RpgUi.TEXT_DIM) + 4;
				}
			} else {
				RpgUi.divider(innerGraphics, textX, curY, textWidth);
				curY += 10;

				RpgScreenText.draw(innerGraphics, font, stringValue(selected, "name", "Habilidade"), textX, curY, RpgUi.TEXT, false, true);
				curY += 14;
				curY = RpgScreenText.drawWrapped(innerGraphics, font, stringValue(selected, "description", "Sem descrição."), textX, curY, textWidth, RpgUi.TEXT_MUTED) + 10;

				String effect = stringValue(selected, "effect", "");
				if (!effect.isBlank()) {
					RpgScreenText.draw(innerGraphics, font, "EFEITO ATIVO:", textX, curY, RpgUi.CYAN, false, true);
					curY += 14;
					curY = RpgScreenText.drawWrapped(innerGraphics, font, effect + " (" + selected.get("amount").getAsString() + ")", textX, curY, textWidth, RpgUi.TEXT) + 10;
				}

				int cost = intValue(selected, "cost");
				RpgScreenText.draw(innerGraphics, font, "Custo: " + cost + " ponto(s)", textX, curY, RpgUi.GOLD, false, true);
				curY += 14;
			}

			int totalH = curY - (SCREEN_MARGIN + 74);
			sidebarScrollArea.setContentHeight(totalH);
		});
	}

	private String treeDescription(JsonObject snapshot) {
		if (universalSkillId != null) {
			JsonObject skill = universalSkill(snapshot);
			return stringValue(skill, "description", "Habilidade universal e seus bônus progressivos.");
		}
		String selectedId = stringValue(snapshot, "selectedClassId", "");
		JsonArray classes = snapshot.getAsJsonArray("classes");
		for (int i = 0; classes != null && i < classes.size(); i++) {
			JsonObject value = classes.get(i).getAsJsonObject();
			if (selectedId.equals(stringValue(value, "id", ""))) return stringValue(value, "description", "Sem descrição.");
		}
		return "Sem descrição.";
	}

	private String treeXpGuide() {
		if (universalSkillId != null) return switch (universalSkillId) {
			case "combate" -> "Derrote criaturas. Inimigos hostis dão mais XP, criaturas pacíficas dão menos, e mobs de nível alto aumentam a recompensa.";
			case "mineracao" -> "Quebre minérios e blocos comuns. Pedra gera XP por quantidade; minérios raros dão XP individualmente. Toque Suave não concede XP.";
			case "agricultura" -> "Colha plantações maduras, alimente filhotes e reproduza animais.";
			case "conhecimento" -> "Fabrique itens, use a bigorna, encante equipamentos e prepare poções.";
			case "exploracao" -> "Abra baús de estruturas inéditas, descubra estruturas e durma durante suas jornadas.";
			case "comercio" -> "Faça trocas com aldeões, evolua aldeões e negocie com piglins usando barras de ouro.";
			default -> "Realize atividades relacionadas a esta habilidade.";
		};
		String id = stringValue(RpgClientState.snapshot(), "selectedClassId", "");
		return id.endsWith("minerador") ? "Minere pedra comum e minérios." : id.endsWith("ferreiro") ? "Colete barras fundidas e fabrique itens metálicos." : "Realize as atividades da classe.";
	}

	private void drawNodeTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, JsonObject skill) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("§6" + stringValue(skill, "name", "Habilidade")));
		lines.add(Component.literal("§7" + stringValue(skill, "description", "Sem descrição.")));
		lines.add(Component.literal("§bTipo: §f" + stringValue(skill, "type", "PASSIVO") + "  •  §bCusto: §e" + intValue(skill, "cost") + " pontos"));
		String effect = stringValue(skill, "effect", "");
		if (!effect.isBlank()) lines.add(Component.literal("§aEfeito: §f" + effect + " (" + skill.get("amount").getAsString() + ")"));
		JsonArray deps = skill.getAsJsonArray("dependencies");
		if (deps != null && !deps.isEmpty()) {
			lines.add(Component.literal(requirementsMet(skill) ? "§a✔ Pré-requisitos atendidos." : "§cFalta: " + missingRequirements(skill)));
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) wrapped.addAll(font.split(line, 260));
		graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
	}

	private String missingRequirements(JsonObject skill) {
		List<String> missing = new ArrayList<>();
		JsonArray deps = skill.getAsJsonArray("dependencies");
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency != null && !boolValue(dependency, "unlocked")) missing.add(stringValue(dependency, "name", "outra habilidade"));
		}
		return String.join(", ", missing);
	}

	private JsonObject universalSkill(JsonObject snapshot) {
		JsonArray values = snapshot.getAsJsonArray("universalSkills");
		for (int i = 0; values != null && i < values.size(); i++) {
			if (universalSkillId.equals(stringValue(values.get(i).getAsJsonObject(), "id", ""))) return values.get(i).getAsJsonObject();
		}
		return null;
	}

	private String universalSkillName(JsonObject snapshot) {
		JsonObject value = universalSkill(snapshot);
		return value == null ? universalSkillId : stringValue(value, "name", universalSkillId);
	}

	private int availablePoints() {
		if (universalSkillId == null) return intValue(RpgClientState.snapshot(), "skillPoints");
		JsonObject progress = universalSkill(RpgClientState.snapshot());
		return intValue(progress, "skillPoints");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (sidebarScrollArea != null && sidebarScrollArea.mouseClicked(event)) {
			return true;
		}
		if (event.x() >= sidebarX()) return super.mouseClicked(event, doubleClick);
		if (event.button() == 1) {
			dragging = true;
			dragX = event.x();
			dragY = event.y();
			pressedSkillId = hitNode(event.x(), event.y());
			if (pressedSkillId != null) {
				RpgUiSounds.playClick();
				selectedSkillId = pressedSkillId;
			} else {
				selectedSkillId = "";
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (sidebarScrollArea != null && sidebarScrollArea.mouseDragged(event, dragX, dragY)) {
			return true;
		}
		if (dragging) {
			panX += event.x() - this.dragX;
			panY += event.y() - this.dragY;
			this.dragX = event.x();
			this.dragY = event.y();
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (sidebarScrollArea != null && sidebarScrollArea.mouseReleased(event)) {
			return true;
		}
		if (dragging) {
			dragging = false;
			pressedSkillId = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (sidebarScrollArea != null && sidebarScrollArea.mouseScrolled(mouseX, mouseY, verticalAmount)) {
			return true;
		}
		double oldZoom = zoom;
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (verticalAmount > 0 ? 1.12 : 1.0 / 1.12)));
		double centerX = canvasWidth() / 2.0;
		double baseY = baseY();
		double graphX = (mouseX - centerX - panX) / oldZoom;
		double graphY = (mouseY - baseY - panY) / oldZoom;
		panX = mouseX - centerX - graphX * zoom;
		panY = mouseY - baseY - graphY * zoom;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 80) { // Tecla P
			returnToCharacter();
			return true;
		}
		return super.keyPressed(event);
	}

	private String hitNode(double mouseX, double mouseY) {
		JsonObject skill = hoverNode(mouseX, mouseY);
		return skill == null ? null : stringValue(skill, "id", "");
	}

	private JsonObject hoverNode(double mouseX, double mouseY) {
		double centerX = canvasWidth() / 2.0;
		double baseY = baseY();
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			double x = centerX + panX + horizontalOffsets.getOrDefault(stringValue(skill, "id", ""), 0.0) * zoom;
			double y = baseY + panY - depths.getOrDefault(stringValue(skill, "id", ""), 0) * 150.0 * zoom;
			double halfWidth = Math.max(44, 58 * zoom) / 2.0;
			double halfHeight = halfWidth;
			if (mouseX >= x - halfWidth && mouseX <= x + halfWidth && mouseY >= y - halfHeight && mouseY <= y + halfHeight) return skill;
		}
		return null;
	}

	private int sidebarX() { return width - SCREEN_MARGIN - SIDEBAR_WIDTH; }
	private int canvasWidth() { return sidebarX(); }
	private int canvasBottom() { return height - SCREEN_MARGIN - FOOTER_HEIGHT; }
	private double baseY() { return canvasBottom() - 42.0; }

	private static String stringValue(JsonObject value, String key, String fallback) {
		return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback;
	}

	private static int intValue(JsonObject value, String key) { return value != null && value.has(key) ? value.get(key).getAsInt() : 0; }
	private static long longValue(JsonObject value, String key) { return value != null && value.has(key) ? value.get(key).getAsLong() : 0L; }
	private static boolean boolValue(JsonObject value, String key) { return value != null && value.has(key) && value.get(key).getAsBoolean(); }
}
