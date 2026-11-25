package br.com.rpgsystem.dungeon.client;

/**
 * Estado do cliente que indica se o jogador está numa sessão de edição de dungeon.
 * Quando ativo, a tecla P deve abrir a tela de DungeonSetupScreen ao invés da PlayerStatusScreen.
 */
public final class DungeonClientEditorState {
	private static boolean inSession = false;
	private static String dungeonName = "";
	private static int minLevel = 1;
	private static int maxLevel = 1;

	private DungeonClientEditorState() {}

	public static boolean isInSession() {
		return inSession;
	}

	public static void setInSession(boolean value) {
		inSession = value;
	}

	public static void enterSession() {
		inSession = true;
	}

	public static void enterSession(String name, int min, int max) {
		inSession = true;
		if (name != null && !name.isBlank()) dungeonName = name;
		minLevel = Math.max(1, min);
		maxLevel = Math.max(minLevel, max);
	}

	public static void leaveSession() {
		inSession = false;
	}

	public static String getDungeonName() {
		return dungeonName;
	}

	public static void setDungeonName(String name) {
		dungeonName = name != null ? name : "";
	}

	public static int getMinLevel() {
		return minLevel;
	}

	public static int getMaxLevel() {
		return maxLevel;
	}

	public static void setLevels(int min, int max) {
		minLevel = Math.max(1, min);
		maxLevel = Math.max(minLevel, max);
	}
}
