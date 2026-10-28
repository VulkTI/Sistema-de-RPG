package br.com.rpgsystem.client.screen;

import br.com.rpgsystem.client.RpgClientState;
import br.com.rpgsystem.network.RpgPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Árvore com painel lateral fixo e uma área independente, navegável para o grafo. */
public final class SkillTreeScreen extends Screen {
	private static final int SIDEBAR_WIDTH = 220;
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
	private boolean movedDuringDrag;
	private Button learnButton;

	public SkillTreeScreen() {
		super(Component.literal("Árvore de habilidades"));
		JsonArray tree = RpgClientState.snapshot().getAsJsonArray("skillTree");
		skills = tree == null ? new JsonArray() : tree;
		if (!skills.isEmpty()) selectedSkillId = skills.get(0).getAsJsonObject().get("id").getAsString();
		for (int i = 0; i < skills.size(); i++) depth(skills.get(i).getAsJsonObject(), new java.util.HashSet<>());
		layoutGraph();
	}

	private int depth(JsonObject skill, java.util.Set<String> visiting) {
		String id = skill.get("id").getAsString();
		if (depths.containsKey(id)) return depths.get(id);
		if (!visiting.add(id)) return 0;
		JsonArray deps = skill.getAsJsonArray("dependencies");
		int result = 0;
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency != null) result = Math.max(result, depth(dependency, visiting) + 1);
		}
		visiting.remove(id);
		depths.put(id, result);
		return result;
	}

	private void layoutGraph() {
		Map<Integer, List<JsonObject>> levels = new java.util.TreeMap<>();
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			levels.computeIfAbsent(depths.getOrDefault(skill.get("id").getAsString(), 0), unused -> new ArrayList<>()).add(skill);
		}
		horizontalOffsets.clear();
		for (List<JsonObject> nodes : levels.values()) {
			for (int i = 0; i < nodes.size(); i++) {
				horizontalOffsets.put(nodes.get(i).get("id").getAsString(), (i - (nodes.size() - 1) / 2.0) * 175.0);
			}
		}
	}

	private JsonObject find(String id) {
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			if (skill.get("id").getAsString().equals(id)) return skill;
		}
		return null;
	}

	private boolean requirementsMet(JsonObject skill) {
		JsonArray deps = skill.getAsJsonArray("dependencies");
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency == null || !dependency.get("unlocked").getAsBoolean()) return false;
		}
		return true;
	}

	@Override
	protected void init() {
		learnButton = addRenderableWidget(Button.builder(Component.literal("Aprender habilidade"), button -> {
			if (!selectedSkillId.isBlank()) ClientPlayNetworking.send(new RpgPayloads.LearnSkill(selectedSkillId));
		}).bounds(width - SIDEBAR_WIDTH + 15, height - 62, SIDEBAR_WIDTH - 30, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width - SIDEBAR_WIDTH + 15, height - 36, SIDEBAR_WIDTH - 30, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// O servidor publica um snapshot novo após cada desbloqueio; manter a tela sincronizada
		// também atualiza cores, dependências e disponibilidade do botão sem reabrir a interface.
		JsonArray latestTree = RpgClientState.snapshot().getAsJsonArray("skillTree");
		if (latestTree != null) skills = latestTree;
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawGraph(graphics);
		drawSidebar(graphics);
		drawDependencyTooltip(graphics, mouseX, mouseY);
		JsonObject selected = find(selectedSkillId);
		learnButton.active = selected != null && !selected.get("unlocked").getAsBoolean()
				&& requirementsMet(selected) && RpgClientState.snapshot().get("skillPoints").getAsInt() >= selected.get("cost").getAsInt();
	}

	private void drawGraph(GuiGraphicsExtractor graphics) {
		int canvasWidth = width - SIDEBAR_WIDTH;
		double centerX = canvasWidth / 2.0;
		double baseY = height - 88.0;
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			JsonArray deps = skill.getAsJsonArray("dependencies");
			for (int j = 0; deps != null && j < deps.size(); j++) {
				JsonObject parent = find(deps.get(j).getAsString());
				if (parent == null) continue;
				drawConnection(graphics, centerX, baseY, parent, skill);
			}
		}
		for (int i = 0; i < skills.size(); i++) drawNode(graphics, centerX, baseY, skills.get(i).getAsJsonObject());
	}

	private void drawConnection(GuiGraphicsExtractor graphics, double centerX, double baseY, JsonObject parent, JsonObject child) {
		double px = centerX + panX + horizontalOffsets.getOrDefault(parent.get("id").getAsString(), 0.0) * zoom;
		double py = baseY + panY - depths.getOrDefault(parent.get("id").getAsString(), 0) * 150.0 * zoom;
		double cx = centerX + panX + horizontalOffsets.getOrDefault(child.get("id").getAsString(), 0.0) * zoom;
		double cy = baseY + panY - depths.getOrDefault(child.get("id").getAsString(), 0) * 150.0 * zoom;
		int x1 = (int) px, x2 = (int) cx, y1 = (int) (py - 20 * zoom), y2 = (int) (cy + 20 * zoom);
		int middle = (y1 + y2) / 2;
		fillGraphClipped(graphics, Math.min(x1, x2), middle, Math.max(x1, x2) + 1, middle + 2);
		fillGraphClipped(graphics, x1, middle, x1 + 2, y1 + 2);
		fillGraphClipped(graphics, x2, y2 - 2, x2 + 2, middle + 2);
	}

	private void fillGraphClipped(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
		int left = Math.max(0, Math.min(x1, x2));
		int right = Math.min(width - SIDEBAR_WIDTH, Math.max(x1, x2));
		int top = Math.max(0, Math.min(y1, y2));
		int bottom = Math.min(height, Math.max(y1, y2));
		if (right > left && bottom > top) graphics.fill(left, top, right, bottom, 0xFF777777);
	}

	private void drawNode(GuiGraphicsExtractor graphics, double centerX, double baseY, JsonObject skill) {
		String id = skill.get("id").getAsString();
		double centerNodeX = centerX + panX + horizontalOffsets.getOrDefault(id, 0.0) * zoom;
		double centerNodeY = baseY + panY - depths.getOrDefault(id, 0) * 150.0 * zoom;
		int nodeWidth = Math.max(52, (int) (140 * zoom));
		int nodeHeight = Math.max(26, (int) (42 * zoom));
		if (centerNodeX < 0 || centerNodeX > width - SIDEBAR_WIDTH) return;
		int x = (int) centerNodeX - nodeWidth / 2;
		int y = (int) centerNodeY - nodeHeight / 2;
		boolean unlocked = skill.get("unlocked").getAsBoolean();
		boolean available = requirementsMet(skill);
		int fill = id.equals(selectedSkillId) ? 0xFF707070 : unlocked ? 0xFF555555 : available ? 0xFF3F3F3F : 0xFF242424;
		graphics.fill(x, y, x + nodeWidth, y + nodeHeight, 0xFF101010);
		graphics.fill(x + 1, y + 1, x + nodeWidth - 1, y + nodeHeight - 1, fill);
		String label = (unlocked ? "✓ " : "") + skill.get("name").getAsString();
		RpgScreenText.draw(graphics, font, label, (int) centerNodeX, (int) centerNodeY - font.lineHeight / 2, 0xFFFFFFFF, true, true);
	}

	private void drawDependencyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (mouseX >= width - SIDEBAR_WIDTH) return;
		JsonObject hovered = hoverNode(mouseX, mouseY);
		if (hovered == null) return;
		JsonArray dependencies = hovered.getAsJsonArray("dependencies");
		if (dependencies == null || dependencies.isEmpty()) return;
		boolean available = requirementsMet(hovered);
		String text = available
				? "Pré-requisitos: " + dependencyNames(hovered)
				: "Depende de desbloquear: " + missingRequirements(hovered);
		int maxWidth = Math.max(1, Math.min(190, width - SIDEBAR_WIDTH - 12));
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(1, maxWidth - 12));
		int boxWidth = Math.min(maxWidth, lines.stream().mapToInt(font::width).max().orElse(0) + 12);
		int boxHeight = lines.size() * font.lineHeight + 8;
		int x = mouseX + 12;
		if (x + boxWidth > width - SIDEBAR_WIDTH) x = mouseX - boxWidth - 8;
		int y = mouseY + 12;
		if (y + boxHeight > height) y = mouseY - boxHeight - 8;
		x = Math.max(4, x);
		y = Math.max(4, y);
		graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0101010);
		graphics.fill(x, y, x + boxWidth, y + 1, 0xFFAAAAAA);
		graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF555555);
		graphics.fill(x, y, x + 1, y + boxHeight, 0xFFAAAAAA);
		graphics.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF555555);
		RpgScreenText.drawWrapped(graphics, font, text, x + 6, y + 4, boxWidth - 12, 0xFFFFFFFF);
	}

	private String missingRequirements(JsonObject skill) {
		JsonArray deps = skill.getAsJsonArray("dependencies");
		List<String> missing = new ArrayList<>();
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency != null && !dependency.get("unlocked").getAsBoolean()) missing.add(dependency.get("name").getAsString());
		}
		return String.join(", ", missing);
	}

	private String dependencyNames(JsonObject skill) {
		JsonArray deps = skill.getAsJsonArray("dependencies");
		List<String> names = new ArrayList<>();
		for (int i = 0; deps != null && i < deps.size(); i++) {
			JsonObject dependency = find(deps.get(i).getAsString());
			if (dependency != null) names.add(dependency.get("name").getAsString());
		}
		return String.join(", ", names);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics) {
		int left = width - SIDEBAR_WIDTH;
		JsonObject data = RpgClientState.snapshot();
		RpgScreenText.drawWrapped(graphics, font, "Árvore de " + data.get("selectedClassName").getAsString(),
				left + 15, 22, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		RpgScreenText.draw(graphics, font, "Pontos: " + data.get("skillPoints").getAsInt(),
				left + 15, 60, 0xFFFFFFFF, false, true);
		JsonObject selected = find(selectedSkillId);
		if (selected == null) return;
		int detailX = left + 15;
		RpgScreenText.drawWrapped(graphics, font, selected.get("name").getAsString(), detailX, 92, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		RpgScreenText.drawWrapped(graphics, font, selected.get("description").getAsString(), detailX, 116, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		RpgScreenText.draw(graphics, font, "Custo: " + selected.get("cost").getAsInt() + " pontos",
				detailX, 178, 0xFFFFFFFF, false, true);
		RpgScreenText.draw(graphics, font, "Tipo: " + selected.get("type").getAsString(), detailX, 197, 0xFFFFFFFF, false, true);
		if (!requirementsMet(selected)) {
			RpgScreenText.drawWrapped(graphics, font, "Passe o cursor sobre a habilidade para ver os pré-requisitos.",
					detailX, 222, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		} else if (!selected.get("unlocked").getAsBoolean()) {
			RpgScreenText.drawWrapped(graphics, font, "Pré-requisitos atendidos.", detailX, 222, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		} else {
			RpgScreenText.drawWrapped(graphics, font, "Habilidade desbloqueada.", detailX, 222, SIDEBAR_WIDTH - 30, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		// Deixa o Screen tratar primeiro os botões da sidebar; o grafo captura apenas o canvas.
		if (mouseX >= width - SIDEBAR_WIDTH) return super.mouseClicked(event, doubleClick);
		// Minecraft 26.3 usa input 1 para o botão esquerdo (0 é botão direito).
		if (event.button() == 1 && mouseX < width - SIDEBAR_WIDTH) {
			dragging = true;
			setDragging(true);
			movedDuringDrag = false;
			dragX = mouseX;
			dragY = mouseY;
			pressedSkillId = hitNode(mouseX, mouseY);
			if (pressedSkillId != null) {
				selectedSkillId = pressedSkillId;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double mouseX = event.x(), mouseY = event.y();
		if (dragging) {
			double dx = mouseX - this.dragX;
			double dy = mouseY - this.dragY;
			if (Math.abs(dx) + Math.abs(dy) > 2.0) movedDuringDrag = true;
			panX += dx;
			panY += dy;
			this.dragX = mouseX;
			this.dragY = mouseY;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			setDragging(false);
			pressedSkillId = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= width - SIDEBAR_WIDTH) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		double oldZoom = zoom;
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (verticalAmount > 0 ? 1.12 : 1.0 / 1.12)));
		double centerX = (width - SIDEBAR_WIDTH) / 2.0;
		double baseY = height - 88.0;
		double graphX = (mouseX - centerX - panX) / oldZoom;
		double graphY = (mouseY - baseY - panY) / oldZoom;
		panX = mouseX - centerX - graphX * zoom;
		panY = mouseY - baseY - graphY * zoom;
		return true;
	}

	private String hitNode(double mouseX, double mouseY) {
		JsonObject skill = hoverNode(mouseX, mouseY);
		if (skill == null || (!skill.get("unlocked").getAsBoolean() && !requirementsMet(skill))) return null;
		return skill.get("id").getAsString();
	}

	private JsonObject hoverNode(double mouseX, double mouseY) {
		double centerX = (width - SIDEBAR_WIDTH) / 2.0;
		double baseY = height - 88.0;
		for (int i = 0; i < skills.size(); i++) {
			JsonObject skill = skills.get(i).getAsJsonObject();
			double x = centerX + panX + horizontalOffsets.getOrDefault(skill.get("id").getAsString(), 0.0) * zoom;
			double y = baseY + panY - depths.getOrDefault(skill.get("id").getAsString(), 0) * 150.0 * zoom;
			double halfWidth = Math.max(52, 140 * zoom) / 2.0;
			double halfHeight = Math.max(26, 42 * zoom) / 2.0;
			if (mouseX >= x - halfWidth && mouseX <= x + halfWidth && mouseY >= y - halfHeight && mouseY <= y + halfHeight) {
				return skill;
			}
		}
		return null;
	}
}
