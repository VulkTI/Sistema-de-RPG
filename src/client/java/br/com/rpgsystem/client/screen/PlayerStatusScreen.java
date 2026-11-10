package br.com.rpgsystem.client.screen;

import br.com.rpgsystem.client.RpgClientState;
import br.com.rpgsystem.client.ui.RpgBaseScreen;
import br.com.rpgsystem.client.ui.RpgButton;
import br.com.rpgsystem.client.ui.RpgCardWidget;
import br.com.rpgsystem.client.ui.RpgProgressBar;
import br.com.rpgsystem.client.ui.RpgUiSounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Painel central de status do personagem sobre a base de UI:
 * - Card da classe com a exata mesma altura e proporção das habilidades de jogador
 * - Layout limpo sem sobreposição de textos
 * - Cards interativos reativos em tempo real via RpgClientState
 */
public final class PlayerStatusScreen extends RpgBaseScreen {

	private static final int CARD_HEIGHT = 34;
	private static final int CARD_GAP_Y = 4;
	private static final int PAD_X = 8;
	private static final int PAD_Y = 8;
	private static final int GAP_X = 8;

	private final List<RpgCardWidget> universalCardWidgets = new ArrayList<>();
	private RpgCardWidget classCardWidget;
	private RpgProgressBar playerXpBar;

	public PlayerStatusScreen() {
		super(Component.literal("Personagem"));
		this.showCloseButton = false;
		this.renderWindowHeader = false; // Não sobrepõe textos no topo do painel
	}

	@Override
	protected void layoutWindow() {
		panelWidth = Math.min(270, Math.max(220, width - 20));
		int totalCardsHeight = 3 * CARD_HEIGHT + 2 * CARD_GAP_Y;
		int btnHeight = 16;
		int spacingAboveBtn = 6;
		int spacingBelowBtn = 7;
		int sectionTitleHeight = 16;

		panelHeight = PAD_Y + CARD_HEIGHT + 6 + sectionTitleHeight + totalCardsHeight + spacingAboveBtn + btnHeight + spacingBelowBtn;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		headerHeight = 0;
		windowAccentColor = RpgUi.BLUE;
	}

	@Override
	protected void init() {
		super.init();
		universalCardWidgets.clear();

		int colWidth = (panelWidth - PAD_X * 2 - GAP_X) / 2;
		int topCardsY = panelY + PAD_Y;

		// 1. Barra de XP do Jogador (Card esquerdo: texto desenhado em drawContent, barra é widget)
		int playerCardX = panelX + PAD_X;
		playerXpBar = addRenderableWidget(new RpgProgressBar(
				playerCardX + 2,
				topCardsY + 25,
				colWidth - 4,
				5,
				() -> longValue(RpgClientState.snapshot(), "playerXpCurrent"),
				() -> longValue(RpgClientState.snapshot(), "playerXpNext"),
				RpgUi.BLUE
		).setShowText(false));

		// 2. Card da Classe Selecionada (Topo direito - mesma altura CARD_HEIGHT das habilidades)
		int classCardX = playerCardX + colWidth + GAP_X;
		classCardWidget = addRenderableWidget(new RpgCardWidget(classCardX, topCardsY, colWidth, CARD_HEIGHT, "")
				.setTitle(() -> stringValue(RpgClientState.snapshot(), "selectedClassName", "Sem classe"))
				.setSubtitle(() -> "Nível " + intValue(RpgClientState.snapshot(), "classLevel"))
				.setIcon(() -> new ItemStack(classIcon(stringValue(RpgClientState.snapshot(), "selectedClassId", ""))))
				.setAccentColor(RpgUi.PURPLE)
				.setXpProgress(
						() -> longValue(RpgClientState.snapshot(), "classXpCurrent"),
						() -> longValue(RpgClientState.snapshot(), "classXpNext")
				)
				.setPoints(() -> intValue(RpgClientState.snapshot(), "skillPoints"))
				.setHighlighted(() -> boolValue(RpgClientState.snapshot(), "classHasLearnableSkill"))
				.setTooltip(() -> {
					JsonObject data = RpgClientState.snapshot();
					String name = stringValue(data, "selectedClassName", "Classe");
					int pts = intValue(data, "skillPoints");
					boolean learnable = boolValue(data, "classHasLearnableSkill");
					String guide = "Como ganhar XP: " + classXpGuide(stringValue(data, "selectedClassId", ""));
					return makeTooltip(name, guide, pts, learnable, "Clique para abrir a árvore de habilidades da classe.");
				})
				.onClick(() -> {
					RpgUiSounds.playClick();
					minecraft.gui.setScreen(new SkillTreeScreen());
				}));

		// 3. Grid de Habilidades Universais (6 cards em 2 colunas x 3 linhas)
		int sectionTitleY = topCardsY + CARD_HEIGHT + 6;
		int cardsY = sectionTitleY + 11;

		for (int i = 0; i < 6; i++) {
			final int index = i;
			int col = index % 2;
			int row = index / 2;
			int cx = panelX + PAD_X + col * (colWidth + GAP_X);
			int cy = cardsY + row * (CARD_HEIGHT + CARD_GAP_Y);
			int color = universalColor(index);

			RpgCardWidget card = addRenderableWidget(new RpgCardWidget(cx, cy, colWidth, CARD_HEIGHT, "")
					.setAccentColor(color)
					.setTitle(() -> {
						JsonObject skill = getSkill(index);
						return skill != null ? stringValue(skill, "name", "Skill") : "Habilidade";
					})
					.setSubtitle(() -> {
						JsonObject skill = getSkill(index);
						return skill != null ? "Nível " + intValue(skill, "level") : "Nível 0";
					})
					.setIcon(() -> {
						JsonObject skill = getSkill(index);
						return new ItemStack(universalIcon(skill != null ? stringValue(skill, "id", "") : ""));
					})
					.setXpProgress(
							() -> {
								JsonObject skill = getSkill(index);
								return skill != null ? longValue(skill, "xpCurrent") : 0L;
							},
							() -> {
								JsonObject skill = getSkill(index);
								return skill != null ? longValue(skill, "xpNext") : 1L;
							}
					)
					.setPoints(() -> {
						JsonObject skill = getSkill(index);
						return skill != null ? intValue(skill, "skillPoints") : 0;
					})
					.setHighlighted(() -> {
						JsonObject skill = getSkill(index);
						return skill != null && boolValue(skill, "hasLearnableSkill");
					})
					.setTooltip(() -> {
						JsonObject skill = getSkill(index);
						if (skill == null) return List.of();
						String name = stringValue(skill, "name", "Habilidade");
						String bonus = "Buffs atuais: " + stringValue(skill, "bonusText", "Nenhum bônus informado.");
						int pts = intValue(skill, "skillPoints");
						boolean learnable = boolValue(skill, "hasLearnableSkill");
						return makeTooltip(name, bonus, pts, learnable, "Clique para abrir a árvore desta habilidade.");
					})
					.onClick(() -> {
						JsonObject skill = getSkill(index);
						if (skill != null) {
							RpgUiSounds.playClick();
							minecraft.gui.setScreen(new SkillTreeScreen(stringValue(skill, "id", "")));
						}
					}));

			universalCardWidgets.add(card);
		}

		// 4. Botão FECHAR no rodapé
		int totalCardsHeight = 3 * CARD_HEIGHT + 2 * CARD_GAP_Y;
		int btnWidth = 92;
		int btnHeight = 16;
		int btnY = cardsY + totalCardsHeight + 6;

		addRenderableWidget(new RpgButton(
				(width - btnWidth) / 2,
				btnY,
				btnWidth,
				btnHeight,
				Component.literal("FECHAR"),
				RpgUi.CYAN,
				this::onClose
		).setHoverEffect(RpgButton.HoverEffect.PULSE));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		JsonObject data = RpgClientState.snapshot();
		int colWidth = (panelWidth - PAD_X * 2 - GAP_X) / 2;
		int topCardsY = panelY + PAD_Y;
		int playerCardX = panelX + PAD_X;

		// Bloco do jogador (Painel com nome, nível e XP textual na mesma altura de 34px)
		String playerName = Minecraft.getInstance().player == null ? "Jogador" : Minecraft.getInstance().player.getName().getString();
		long curXp = longValue(data, "playerXpCurrent");
		long nextXp = longValue(data, "playerXpNext");
		String xpStr = RpgUi.number(curXp) + "/" + RpgUi.number(nextXp);

		// Linha 1: Nome do jogador centralizado
		RpgScreenText.draw(graphics, font, playerName, playerCardX + colWidth / 2, topCardsY + 3, RpgUi.TEXT, true, true);

		// Linha 2: Nível à esquerda e XP à direita
		int line2Y = topCardsY + 14;
		RpgScreenText.draw(graphics, font, "Nível " + intValue(data, "playerLevel"), playerCardX + 2, line2Y, RpgUi.CYAN, false, true);
		RpgScreenText.draw(graphics, font, xpStr, playerCardX + colWidth - font.width(xpStr) - 2, line2Y, RpgUi.TEXT_MUTED, false, true);

		// Título da seção central de habilidades
		int sectionTitleY = topCardsY + CARD_HEIGHT + 6;
		RpgScreenText.draw(graphics, font, "HABILIDADES DE JOGADOR", width / 2, sectionTitleY, RpgUi.TEXT, true, true);
	}

	private JsonObject getSkill(int index) {
		JsonArray skills = RpgClientState.snapshot().getAsJsonArray("universalSkills");
		if (skills != null && index >= 0 && index < skills.size()) {
			return skills.get(index).getAsJsonObject();
		}
		return null;
	}

	private static List<Component> makeTooltip(String title, String mainInfo, int points, boolean learnable, String hint) {
		List<String> lines = new ArrayList<>();
		lines.add(mainInfo);
		if (points > 0) {
			lines.add("§6Pontos não atribuídos: " + points);
		}
		if (learnable) {
			lines.add("§a✦ Há habilidades prontas para aprender!");
		}
		if (hint != null) {
			lines.add("§e" + hint);
		}
		return RpgUi.tooltip(title, lines.toArray(new String[0]));
	}

	private static Item classIcon(String id) {
		return id.endsWith("minerador") ? Items.IRON_PICKAXE : id.endsWith("ferreiro") ? Items.ANVIL : Items.PLAYER_HEAD;
	}

	private static Item universalIcon(String id) {
		return switch (id) {
			case "combate" -> Items.DIAMOND_SWORD;
			case "mineracao" -> Items.IRON_PICKAXE;
			case "agricultura" -> Items.WHEAT;
			case "conhecimento" -> Items.ENCHANTING_TABLE;
			case "exploracao" -> Items.COMPASS;
			case "comercio" -> Items.EMERALD;
			default -> Items.NETHER_STAR;
		};
	}

	private static int universalColor(int index) {
		return switch (index % 6) {
			case 0 -> RpgUi.RED;
			case 1 -> RpgUi.CYAN;
			case 2 -> RpgUi.GREEN;
			case 3 -> RpgUi.BLUE;
			case 4 -> RpgUi.PURPLE;
			default -> 0xFFE486B5;
		};
	}

	private static String classXpGuide(String id) {
		return id.endsWith("minerador") ? "minere pedra comum e minérios."
				: id.endsWith("ferreiro") ? "colete barras fundidas e fabrique itens metálicos."
				: "realize as atividades da classe.";
	}

	private static String stringValue(JsonObject value, String key, String fallback) {
		return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback;
	}

	private static int intValue(JsonObject value, String key) {
		return value != null && value.has(key) ? value.get(key).getAsInt() : 0;
	}

	private static long longValue(JsonObject value, String key) {
		return value != null && value.has(key) ? value.get(key).getAsLong() : 0L;
	}

	private static boolean boolValue(JsonObject value, String key) {
		return value != null && value.has(key) && value.get(key).getAsBoolean();
	}
}
