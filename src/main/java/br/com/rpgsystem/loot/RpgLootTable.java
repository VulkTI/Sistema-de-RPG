package br.com.rpgsystem.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Representa uma tabela de looting configurável com suporte a pesos e ordenação. */
public final class RpgLootTable {
	public static final Codec<RpgLootTable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(RpgLootTable::name),
			Codec.INT.optionalFieldOf("min_amount", 1).forGetter(RpgLootTable::minAmount),
			Codec.INT.optionalFieldOf("max_amount", 3).forGetter(RpgLootTable::maxAmount),
			RpgLootEntry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(RpgLootTable::itemsList)
	).apply(instance, RpgLootTable::fromList));

	private final String name;
	private int minAmount;
	private int maxAmount;
	private final Map<Integer, RpgLootEntry> entries;

	public RpgLootTable(String name) {
		this(name, 1, 3, new LinkedHashMap<>());
	}

	public RpgLootTable(String name, int minAmount, int maxAmount) {
		this(name, minAmount, maxAmount, new LinkedHashMap<>());
	}

	public RpgLootTable(String name, int minAmount, int maxAmount, Map<Integer, RpgLootEntry> entries) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("O nome da tabela não pode ser vazio.");
		}
		if (minAmount < 0) {
			throw new IllegalArgumentException("A quantidade mínima não pode ser negativa: " + minAmount);
		}
		if (maxAmount < minAmount) {
			throw new IllegalArgumentException("A quantidade máxima (" + maxAmount + ") não pode ser menor que a mínima (" + minAmount + ").");
		}
		this.name = name;
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.entries = new LinkedHashMap<>(entries);
	}

	public static RpgLootTable fromList(String name, int minAmount, int maxAmount, List<RpgLootEntry> items) {
		Map<Integer, RpgLootEntry> map = new LinkedHashMap<>();
		for (RpgLootEntry entry : items) {
			map.put(entry.id(), entry);
		}
		return new RpgLootTable(name, minAmount, maxAmount, map);
	}

	public String name() {
		return name;
	}

	public int minAmount() {
		return minAmount;
	}

	public int maxAmount() {
		return maxAmount;
	}

	public Map<Integer, RpgLootEntry> entries() {
		return Collections.unmodifiableMap(entries);
	}

	public List<RpgLootEntry> itemsList() {
		return new ArrayList<>(entries.values());
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public Optional<RpgLootEntry> getEntry(int id) {
		return Optional.ofNullable(entries.get(id));
	}

	public Optional<RpgLootEntry> findMatchingEntry(ItemStack candidate) {
		return findMatchingEntry(candidate, "");
	}

	public Optional<RpgLootEntry> findMatchingEntry(ItemStack candidate, String label) {
		for (RpgLootEntry entry : entries.values()) {
			if (entry.matches(candidate, label)) {
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	/**
	 * Aloca o menor número inteiro não negativo livre (0, 1, 2, ...).
	 */
	public int allocateLowestAvailableId() {
		int candidate = 0;
		while (entries.containsKey(candidate)) {
			candidate++;
		}
		return candidate;
	}

	/**
	 * Adiciona um item na tabela com o menor ID disponível.
	 */
	public RpgLootEntry addItem(ItemStack stack, double weight) {
		return addItem(stack, weight, "");
	}

	public RpgLootEntry addItem(ItemStack stack, double weight, String customLabel) {
		int id = allocateLowestAvailableId();
		RpgLootEntry entry = new RpgLootEntry(id, stack != null ? stack.copy() : null, weight, customLabel);
		entries.put(id, entry);
		return entry;
	}

	/**
	 * Adiciona ou substitui com ID explícito (usado em deserializações ou operações controladas).
	 */
	public void putEntry(RpgLootEntry entry) {
		entries.put(entry.id(), entry);
	}

	/**
	 * Remove um item pelo ID.
	 */
	public boolean removeItem(int id) {
		return entries.remove(id) != null;
	}

	/**
	 * Atualiza o peso de um item existente.
	 */
	public boolean setWeight(int id, double weight) {
		RpgLootEntry existing = entries.get(id);
		if (existing == null) {
			return false;
		}
		entries.put(id, existing.withWeight(weight));
		return true;
	}

	/**
	 * Define o intervalo mínimo e máximo de itens gerados no baú.
	 */
	public void setAmount(int min, int max) {
		if (min < 0) {
			throw new IllegalArgumentException("A quantidade mínima não pode ser negativa.");
		}
		if (max < min) {
			throw new IllegalArgumentException("O máximo não pode ser menor que o mínimo.");
		}
		this.minAmount = min;
		this.maxAmount = max;
	}

	/**
	 * Soma total de pesos de todos os itens cadastrados.
	 */
	public double totalWeight() {
		double sum = 0.0;
		for (RpgLootEntry entry : entries.values()) {
			sum += entry.weight();
		}
		return sum;
	}

	/**
	 * Percentual de chance (0.0 a 100.0) de o item de ID especificado vir em um sorteio individual.
	 */
	public double getChancePercentage(int id) {
		RpgLootEntry entry = entries.get(id);
		if (entry == null) return 0.0;
		double total = totalWeight();
		if (total <= 0.0) return 0.0;
		return (entry.weight() / total) * 100.0;
	}

	/**
	 * Retorna as entradas ordenadas pelo critério e direção selecionados.
	 */
	public List<RpgLootEntry> getSortedEntries(LootSortBy sortBy, SortDirection direction) {
		List<RpgLootEntry> list = new ArrayList<>(entries.values());
		Comparator<RpgLootEntry> comparator = switch (sortBy) {
			case ID -> Comparator.comparingInt(RpgLootEntry::id);
			case NAME -> Comparator.comparing(
					(RpgLootEntry e) -> e.itemNameOnly().toLowerCase(Locale.ROOT)
			).thenComparingInt(RpgLootEntry::id);
			case CHANCE -> Comparator.comparingDouble(RpgLootEntry::weight)
					.thenComparingInt(RpgLootEntry::id);
		};

		if (direction == SortDirection.DESCENDING) {
			comparator = comparator.reversed();
		}

		list.sort(comparator);
		return list;
	}

	/**
	 * Realiza o sorteio completo de itens para um baú usando o intervalo [minAmount, maxAmount]
	 * e o sistema de pesos de cada item cadastrado.
	 */
	public List<ItemStack> rollLoot(RandomSource random) {
		if (entries.isEmpty() || maxAmount <= 0) {
			return Collections.emptyList();
		}

		int count = minAmount == maxAmount
				? minAmount
				: minAmount + random.nextInt(maxAmount - minAmount + 1);

		if (count <= 0) {
			return Collections.emptyList();
		}

		double total = totalWeight();
		if (total <= 0.0) {
			return Collections.emptyList();
		}

		List<RpgLootEntry> entryList = new ArrayList<>(entries.values());
		List<ItemStack> rolled = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			double target = random.nextDouble() * total;
			double current = 0.0;
			RpgLootEntry selected = entryList.get(entryList.size() - 1);
			for (RpgLootEntry entry : entryList) {
				current += entry.weight();
				if (target <= current) {
					selected = entry;
					break;
				}
			}
			if (selected.item() != null) {
				rolled.add(selected.item().copy());
			}
		}

		return rolled;
	}
}
