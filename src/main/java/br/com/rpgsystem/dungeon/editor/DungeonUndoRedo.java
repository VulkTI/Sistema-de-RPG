package br.com.rpgsystem.dungeon.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;

public class DungeonUndoRedo {
	public enum ActionType {
		PLACE_MARKER,
		REMOVE_MARKER,
		CONFIGURE_MARKER,
		PLACE_AREA,
		REMOVE_AREA
	}

	public record ActionRecord(
			ActionType type,
			BlockPos pos,
			java.util.List<BlockPos> positions,
			BlockState previousState,
			BlockState newState,
			Object previousData,
			Object newData,
			java.util.Map<BlockPos, BlockState> previousBlockStates
	) {
		public ActionRecord(ActionType type, BlockPos pos, java.util.List<BlockPos> positions, BlockState previousState, BlockState newState, Object previousData, Object newData) {
			this(type, pos, positions, previousState, newState, previousData, newData, java.util.Map.of());
		}

		public ActionRecord(ActionType type, BlockPos pos, BlockState previousState, BlockState newState, Object previousData, Object newData) {
			this(type, pos, java.util.List.of(pos), previousState, newState, previousData, newData, java.util.Map.of());
		}
	}

	private final Deque<ActionRecord> undoStack = new ArrayDeque<>();
	private final Deque<ActionRecord> redoStack = new ArrayDeque<>();

	public void push(ActionRecord action) {
		undoStack.push(action);
		redoStack.clear();
	}

	public boolean canUndo() {
		return !undoStack.isEmpty();
	}

	public boolean canRedo() {
		return !redoStack.isEmpty();
	}

	public ActionRecord popUndo() {
		if (undoStack.isEmpty()) return null;
		ActionRecord action = undoStack.pop();
		redoStack.push(action);
		return action;
	}

	public ActionRecord popRedo() {
		if (redoStack.isEmpty()) return null;
		ActionRecord action = redoStack.pop();
		undoStack.push(action);
		return action;
	}

	public void clear() {
		undoStack.clear();
		redoStack.clear();
	}
}
