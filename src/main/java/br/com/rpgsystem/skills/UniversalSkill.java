package br.com.rpgsystem.skills;

/** As seis habilidades universais, independentes da classe ativa. */
public enum UniversalSkill {
	COMBATE("combate", "Combate"),
	MINERACAO("mineracao", "Mineração"),
	AGRICULTURA("agricultura", "Agricultura e Pecuária"),
	CONHECIMENTO("conhecimento", "Conhecimento"),
	EXPLORACAO("exploracao", "Exploração"),
	COMERCIO("comercio", "Comércio");

	private final String id;
	private final String displayName;

	UniversalSkill(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() { return id; }
	public String displayName() { return displayName; }

	public static UniversalSkill byId(String id) {
		for (UniversalSkill skill : values()) if (skill.id.equals(id)) return skill;
		return null;
	}
}
