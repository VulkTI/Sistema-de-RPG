package br.com.rpgsystem.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RpgLootTableTest {

	@Test
	void testLowestAvailableIdAllocation() {
		RpgLootTable table = new RpgLootTable("dungeon_chest");

		RpgLootEntry e0 = table.addItem(null, 10.0, "Ferro");
		RpgLootEntry e1 = table.addItem(null, 20.0, "Ouro");
		RpgLootEntry e2 = table.addItem(null, 30.0, "Diamante");

		assertEquals(0, e0.id());
		assertEquals(1, e1.id());
		assertEquals(2, e2.id());
		assertEquals(3, table.size());

		// Deletar o item 1
		assertTrue(table.removeItem(1));
		assertEquals(2, table.size());
		assertFalse(table.getEntry(1).isPresent());

		// Adicionar novo item: deve reutilizar o ID 1 porque é o menor disponível
		RpgLootEntry eNew1 = table.addItem(null, 5.0, "Esmeralda");
		assertEquals(1, eNew1.id(), "O menor ID disponível (1) deveria ter sido alocado.");

		// Próximo item deve ser o ID 3
		RpgLootEntry e3 = table.addItem(null, 1.0, "Netherita");
		assertEquals(3, e3.id(), "O próximo menor ID disponível deveria ser 3.");
	}

	@Test
	void testWeightAndChances() {
		RpgLootTable table = new RpgLootTable("test_weights");
		table.addItem(null, 10.0, "Terra");
		table.addItem(null, 40.0, "Pedregulho");
		table.addItem(null, 50.0, "Diamante");

		assertEquals(100.0, table.totalWeight(), 0.0001);
		assertEquals(10.0, table.getChancePercentage(0), 0.0001);
		assertEquals(40.0, table.getChancePercentage(1), 0.0001);
		assertEquals(50.0, table.getChancePercentage(2), 0.0001);

		// Atualizar peso do ID 0 de 10 para 60
		assertTrue(table.setWeight(0, 60.0));
		assertEquals(150.0, table.totalWeight(), 0.0001);
		assertEquals(40.0, table.getChancePercentage(0), 0.0001); // 60/150 = 40%
	}

	@Test
	void testAmountValidation() {
		RpgLootTable table = new RpgLootTable("amount_test");
		table.setAmount(2, 5);
		assertEquals(2, table.minAmount());
		assertEquals(5, table.maxAmount());

		// Min == Max é permitido
		table.setAmount(3, 3);
		assertEquals(3, table.minAmount());
		assertEquals(3, table.maxAmount());

		// Max < Min deve lançar exceção
		assertThrows(IllegalArgumentException.class, () -> table.setAmount(5, 2));
		// Min < 0 deve lançar exceção
		assertThrows(IllegalArgumentException.class, () -> table.setAmount(-1, 2));
	}

	@Test
	void testSorting() {
		RpgLootTable table = new RpgLootTable("sort_test");
		table.addItem(null, 80.0, "Graveto");       // ID 0, "Graveto", peso 80
		table.addItem(null, 10.0, "Maçã");          // ID 1, "Maçã", peso 10
		table.addItem(null, 5.0, "Arco");           // ID 2, "Arco", peso 5

		// Ordenação por ID Crescente
		List<RpgLootEntry> byIdAsc = table.getSortedEntries(LootSortBy.ID, SortDirection.ASCENDING);
		assertEquals(0, byIdAsc.get(0).id());
		assertEquals(1, byIdAsc.get(1).id());
		assertEquals(2, byIdAsc.get(2).id());

		// Ordenação por ID Decrescente
		List<RpgLootEntry> byIdDesc = table.getSortedEntries(LootSortBy.ID, SortDirection.DESCENDING);
		assertEquals(2, byIdDesc.get(0).id());
		assertEquals(1, byIdDesc.get(1).id());
		assertEquals(0, byIdDesc.get(2).id());

		// Ordenação por Nome (A-Z) Crescente
		List<RpgLootEntry> byNameAsc = table.getSortedEntries(LootSortBy.NAME, SortDirection.ASCENDING);
		assertEquals("Arco", byNameAsc.get(0).itemNameOnly());
		assertEquals("Graveto", byNameAsc.get(1).itemNameOnly());
		assertEquals("Maçã", byNameAsc.get(2).itemNameOnly());

		// Ordenação por Nome (A-Z) Decrescente
		List<RpgLootEntry> byNameDesc = table.getSortedEntries(LootSortBy.NAME, SortDirection.DESCENDING);
		assertEquals("Maçã", byNameDesc.get(0).itemNameOnly());
		assertEquals("Graveto", byNameDesc.get(1).itemNameOnly());
		assertEquals("Arco", byNameDesc.get(2).itemNameOnly());

		// Ordenação por Chance Crescente (menor para maior)
		List<RpgLootEntry> byChanceAsc = table.getSortedEntries(LootSortBy.CHANCE, SortDirection.ASCENDING);
		assertEquals(2, byChanceAsc.get(0).id()); // Arco (5)
		assertEquals(1, byChanceAsc.get(1).id()); // Maçã (10)
		assertEquals(0, byChanceAsc.get(2).id()); // Graveto (80)

		// Ordenação por Chance Decrescente (maior para menor)
		List<RpgLootEntry> byChanceDesc = table.getSortedEntries(LootSortBy.CHANCE, SortDirection.DESCENDING);
		assertEquals(0, byChanceDesc.get(0).id()); // Graveto (80)
		assertEquals(1, byChanceDesc.get(1).id()); // Maçã (10)
		assertEquals(2, byChanceDesc.get(2).id()); // Arco (5)
	}

	@Test
	void testEnumsFromString() {
		assertEquals(LootSortBy.ID, LootSortBy.fromString("id"));
		assertEquals(LootSortBy.NAME, LootSortBy.fromString("a-z"));
		assertEquals(LootSortBy.NAME, LootSortBy.fromString("name"));
		assertEquals(LootSortBy.NAME, LootSortBy.fromString("nome"));
		assertEquals(LootSortBy.CHANCE, LootSortBy.fromString("chance"));
		assertEquals(LootSortBy.CHANCE, LootSortBy.fromString("peso"));

		assertEquals(SortDirection.ASCENDING, SortDirection.fromString("crescente"));
		assertEquals(SortDirection.ASCENDING, SortDirection.fromString("asc"));
		assertEquals(SortDirection.DESCENDING, SortDirection.fromString("decrescente"));
		assertEquals(SortDirection.DESCENDING, SortDirection.fromString("desc"));
	}

	@Test
	void testMatchingEntryAndDeduplication() {
		RpgLootTable table = new RpgLootTable("dedup_test", 2, 4);
		assertEquals(2, table.minAmount());
		assertEquals(4, table.maxAmount());

		RpgLootEntry e0 = table.addItem(null, 10.0, "Espada de Ferro");
		assertEquals(0, e0.id());

		// Procura por item equivalente
		var match = table.findMatchingEntry(null, "Espada de Ferro");
		assertTrue(match.isPresent());
		assertEquals(0, match.get().id());

		// Atualiza peso ao invés de duplicar
		assertTrue(table.setWeight(match.get().id(), 25.0));
		assertEquals(25.0, table.getEntry(0).orElseThrow().weight());
		assertEquals(1, table.size());

		// Item diferente não deve dar match
		var noMatch = table.findMatchingEntry(null, "Espada de Diamante");
		assertFalse(noMatch.isPresent());
	}
}
