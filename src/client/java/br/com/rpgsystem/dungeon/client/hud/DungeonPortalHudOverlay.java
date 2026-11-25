package br.com.rpgsystem.dungeon.client.hud;

import br.com.rpgsystem.client.screen.RpgScreenText;
import br.com.rpgsystem.client.screen.RpgUi;
import br.com.rpgsystem.dungeon.block.DungeonBlocks;
import br.com.rpgsystem.dungeon.client.DungeonClientEditorState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class DungeonPortalHudOverlay implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (mc.gui.screen() != null) return;

		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = mc.level.getBlockState(hit.getBlockPos());
			if (state.is(DungeonBlocks.WHITE_PORTAL)) {
				renderPortalTooltip(graphics, mc);
			}
		}
	}

	private void renderPortalTooltip(GuiGraphicsExtractor graphics, Minecraft mc) {
		Font font = mc.font;
		int centerX = mc.getWindow().getGuiScaledWidth() / 2;
		int centerY = mc.getWindow().getGuiScaledHeight() / 2;

		int tooltipX = centerX + 14;
		int tooltipY = centerY - 14;

		boolean isEditor = DungeonClientEditorState.isInSession()
				|| (mc.level != null && mc.level.dimension().identifier().getPath().equals("dungeon_editor"));

		String dungeonName;
		String levelStr;
		String timeStr = "";
		String statusStr;
		int statusColor;
		int accentColor;

		if (isEditor) {
			dungeonName = !DungeonClientEditorState.getDungeonName().isBlank()
					? DungeonClientEditorState.getDungeonName()
					: (DungeonClientAimState.isFresh() && !DungeonClientAimState.getDungeonName().isBlank()
					? DungeonClientAimState.getDungeonName()
					: "Dungeon");
			int minLvl = DungeonClientEditorState.getMinLevel();
			int maxLvl = DungeonClientEditorState.getMaxLevel();
			levelStr = minLvl == maxLvl ? String.valueOf(minLvl) : minLvl + "-" + maxLvl;
			statusStr = "Status: Edição";
			statusColor = 0xFFFFAA00;
			accentColor = RpgUi.GOLD;
		} else {
			dungeonName = DungeonClientAimState.isFresh() && !DungeonClientAimState.getDungeonName().isBlank()
					? DungeonClientAimState.getDungeonName()
					: "Dungeon";
			int lvl = DungeonClientAimState.isFresh() ? DungeonClientAimState.getDungeonLevel() : 1;
			levelStr = String.valueOf(lvl);

			int seconds = DungeonClientAimState.getTimeLeftSeconds();
			int mm = seconds / 60;
			int ss = seconds % 60;
			timeStr = String.format("%02d:%02d", mm, ss);

			boolean completed = DungeonClientAimState.isCompleted();
			if (completed) {
				statusStr = "Status: Concluída ✅";
				statusColor = 0xFF55FF55;
				accentColor = RpgUi.GREEN;
			} else {
				statusStr = "Status: Não concluída ❌";
				statusColor = 0xFFFF5555;
				accentColor = RpgUi.RED;
			}
		}

		String line1 = "Dungeon: " + dungeonName;
		String line2 = "Nível: " + levelStr;
		String line3 = isEditor ? statusStr : ("Tempo: " + timeStr);
		String line4 = isEditor ? "" : statusStr;

		int line1Width = font.width(line1);
		int line2Width = font.width(line2);
		int line3Width = font.width(line3);
		int line4Width = isEditor ? 0 : font.width(line4);

		int boxWidth = Math.max(Math.max(line1Width, line2Width), Math.max(line3Width, line4Width)) + 14;
		int linesCount = isEditor ? 3 : 4;
		int boxHeight = (font.lineHeight + 2) * linesCount + 8;

		RpgUi.panel(graphics, tooltipX, tooltipY, boxWidth, boxHeight, 0xEE101622, RpgUi.BORDER);
		RpgUi.accent(graphics, tooltipX, tooltipY, boxWidth, accentColor);

		int textX = tooltipX + 7;
		int textY = tooltipY + 5;
		RpgScreenText.draw(graphics, font, line1, textX, textY, 0xFFFFFFFF, false, true);
		RpgScreenText.draw(graphics, font, line2, textX, textY + font.lineHeight + 2, 0xFF55FFFF, false, true);
		if (isEditor) {
			RpgScreenText.draw(graphics, font, line3, textX, textY + (font.lineHeight + 2) * 2, statusColor, false, true);
		} else {
			RpgScreenText.draw(graphics, font, line3, textX, textY + (font.lineHeight + 2) * 2, 0xFFFFAA00, false, true);
			RpgScreenText.draw(graphics, font, line4, textX, textY + (font.lineHeight + 2) * 3, statusColor, false, true);
		}
	}
}
