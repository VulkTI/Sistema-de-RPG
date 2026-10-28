package br.com.rpgsystem.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Cópia somente de leitura do estado do servidor usado pela HUD e pelas telas. */
public final class RpgClientState {
	private static volatile JsonObject snapshot = new JsonObject();
	private static volatile boolean received;

	private RpgClientState() {}

	public static void update(String json) {
		snapshot = JsonParser.parseString(json).getAsJsonObject();
		received = true;
	}

	public static JsonObject snapshot() { return snapshot; }
	public static boolean hasSnapshot() { return received; }
}
