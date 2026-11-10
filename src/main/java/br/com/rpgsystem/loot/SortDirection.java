package br.com.rpgsystem.loot;

import java.util.Locale;

/** Direção de ordenação para a listagem da tabela de loot. */
public enum SortDirection {
	ASCENDING("crescente", "Crescente"),
	DESCENDING("decrescente", "Decrescente");

	private final String commandKey;
	private final String displayName;

	SortDirection(String commandKey, String displayName) {
		this.commandKey = commandKey;
		this.displayName = displayName;
	}

	public String commandKey() {
		return commandKey;
	}

	public String displayName() {
		return displayName;
	}

	public static SortDirection fromString(String value) {
		if (value == null) return ASCENDING;
		String lower = value.toLowerCase(Locale.ROOT).trim();
		return switch (lower) {
			case "desc", "decrescente", "descending", "baixo" -> DESCENDING;
			default -> ASCENDING;
		};
	}
}
