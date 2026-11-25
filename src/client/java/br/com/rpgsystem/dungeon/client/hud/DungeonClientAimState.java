package br.com.rpgsystem.dungeon.client.hud;

public final class DungeonClientAimState {
	private static String dungeonName = "";
	private static int dungeonLevel = 1;
	private static int timeLeftSeconds = 0;
	private static boolean completed = false;
	private static long lastUpdateTimeMs = 0;

	private DungeonClientAimState() {}

	public static void update(String name, int level, int timeLeft, boolean isCompleted) {
		dungeonName = name;
		dungeonLevel = Math.max(1, level);
		timeLeftSeconds = timeLeft;
		completed = isCompleted;
		lastUpdateTimeMs = System.currentTimeMillis();
	}

	public static String getDungeonName() {
		return dungeonName;
	}

	public static int getDungeonLevel() {
		return dungeonLevel;
	}

	public static int getTimeLeftSeconds() {
		return timeLeftSeconds;
	}

	public static boolean isCompleted() {
		return completed;
	}

	public static boolean isFresh() {
		return (System.currentTimeMillis() - lastUpdateTimeMs) < 3000;
	}
}
