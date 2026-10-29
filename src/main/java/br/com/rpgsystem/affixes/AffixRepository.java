package br.com.rpgsystem.affixes;

import br.com.rpgsystem.RpgMod;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositório recarregável de afixos definidos por JSON/data pack. */
public final class AffixRepository extends SimpleJsonResourceReloadListener<AffixDefinition>
		implements IdentifiableResourceReloadListener {
	private static volatile Map<Identifier, AffixDefinition> definitions = Map.of();

	private AffixRepository() {
		super(AffixDefinition.RESOURCE_CODEC, FileToIdConverter.json("affixes"));
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new AffixRepository());
	}

	public static Collection<AffixDefinition> all() {
		return List.copyOf(definitions.values());
	}

	public static Optional<AffixDefinition> byId(Identifier id) {
		return Optional.ofNullable(definitions.get(id));
	}

	public static Optional<AffixDefinition> byPath(String path) {
		Identifier id = Identifier.tryParse(path.contains(":") ? path : RpgMod.MOD_ID + ":" + path);
		return id == null ? Optional.empty() : byId(id);
	}

	@Override
	public Identifier getFabricId() {
		return RpgMod.id("affixes");
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(RpgMod.id("item_groups"));
	}

	@Override
	protected void apply(Map<Identifier, AffixDefinition> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, AffixDefinition> resolved = new LinkedHashMap<>();
		loaded.forEach((id, definition) -> resolved.put(id, definition.withId(id)));
		definitions = Map.copyOf(resolved);
		RpgMod.LOGGER.info("{} afixo(s) carregado(s).", definitions.size());
	}
}
