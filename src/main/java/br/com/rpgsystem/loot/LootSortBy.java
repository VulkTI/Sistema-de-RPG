package br.com.rpgsystem.loot;

import java.util.Locale;

/** Critérios de ordenação para a listagem da tabela de loot. */
public enum LootSortBy {
	ID("id", "ID"),
	NAME("name", "A-Z"),
	CHANCE("chance", "Chance");

	private final String commandKey;
	private final String displayName;

	LootSortBy(String commandKey, String displayName) {
		this.commandKey = commandKey;
		this.displayName = displayName;
	}

	public String commandKey() {
		return commandKey;
	}

	public String displayName() {
		return displayName;
	}

	public static LootSortBy fromString(String value) {
		if (value == null) return ID;
		String lower = value.toLowerCase(Locale.ROOT).trim();
		return switch (lower) {
			case "name", "a-z", "az", "nome", "alfabetica" -> NAME;
			case "chance", "peso", "weight", "porcentagem" -> CHANCE;
			default -> ID;
		};
	}
}
