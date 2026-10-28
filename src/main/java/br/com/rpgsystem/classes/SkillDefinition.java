package br.com.rpgsystem.classes;

import java.util.List;

/** Um nó da árvore da classe. Dependencies são IDs de outras habilidades da mesma árvore. */
public record SkillDefinition(String id, String name, String description, int cost,
		SkillType type, List<String> dependencies, String effect, double amount) {
	public SkillDefinition {
		dependencies = List.copyOf(dependencies);
		if (cost < 0) throw new IllegalArgumentException("Custo de habilidade não pode ser negativo: " + id);
	}
}
