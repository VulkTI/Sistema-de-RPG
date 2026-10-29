package br.com.rpgsystem.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Configuração de XP editável em config/rpgsystem_xp.json. */
public final class XpConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rpgsystem_xp.json");
	private static JsonObject root;

	private XpConfig() {}

	public static void load() {
		try {
			if (Files.exists(FILE)) root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			else { root = defaults(); save(); }
		} catch (Exception exception) {
			root = defaults();
			System.err.println("Não foi possível ler " + FILE + "; usando valores padrão: " + exception.getMessage());
		}
	}

	private static void save() throws IOException {
		Files.createDirectories(FILE.getParent());
		Files.writeString(FILE, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	private static JsonObject defaults() {
		JsonObject json = new JsonObject();
		json.addProperty("player_hostile_kill", 8);
		JsonObject minerador = new JsonObject();
		minerador.addProperty("stone_blocks_per_reward", 64);
		minerador.addProperty("stone_reward_xp", 4);
		JsonObject ores = new JsonObject();
		Map.ofEntries(Map.entry("coal_ore", 2), Map.entry("copper_ore", 3), Map.entry("nether_quartz_ore", 3), Map.entry("redstone_ore", 4),
				Map.entry("lapis_ore", 4), Map.entry("iron_ore", 5), Map.entry("gold_ore", 6), Map.entry("nether_gold_ore", 6),
				Map.entry("emerald_ore", 12), Map.entry("diamond_ore", 16), Map.entry("ancient_debris", 40)).forEach(ores::addProperty);
		minerador.add("ores", ores);
		json.add("minerador", minerador);
		JsonObject ferreiro = new JsonObject();
		ferreiro.addProperty("smelted_copper_ingot", 4);
		ferreiro.addProperty("smelted_iron_ingot", 10);
		ferreiro.addProperty("smelted_gold_ingot", 15);
		ferreiro.addProperty("netherite_ingot_crafted", 500);
		ferreiro.addProperty("metal_item_crafted_base", 10);
		ferreiro.addProperty("material_bonus_iron", 10);
		ferreiro.addProperty("material_bonus_copper", 5);
		ferreiro.addProperty("material_bonus_gold", 20);
		ferreiro.addProperty("material_bonus_diamond", 35);
		ferreiro.addProperty("material_bonus_netherite", 50);
		ferreiro.addProperty("bars_multiplier", 0.30);
		json.add("ferreiro", ferreiro);
		return json;
	}

	private static JsonObject section(String name) { return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : new JsonObject(); }
	public static int playerHostileKill() { return root.get("player_hostile_kill").getAsInt(); }
	public static int mineradorBlocksPerReward() { return Math.max(1, section("minerador").get("stone_blocks_per_reward").getAsInt()); }
	public static int mineradorStoneXp() { return section("minerador").get("stone_reward_xp").getAsInt(); }
	public static int mineradorOreXp(String path) {
		JsonObject ores = section("minerador").getAsJsonObject("ores");
		return ores.has(path) ? Math.max(0, ores.get(path).getAsInt()) : 0;
	}
	public static int ferreiro(String key, int fallback) {
		JsonObject section = section("ferreiro");
		return section.has(key) ? section.get(key).getAsInt() : fallback;
	}
	public static double ferreiroDouble(String key, double fallback) {
		JsonObject values = section("ferreiro");
		return values.has(key) ? values.get(key).getAsDouble() : fallback;
	}
}
