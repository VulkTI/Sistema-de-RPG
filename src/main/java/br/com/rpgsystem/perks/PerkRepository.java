package br.com.rpgsystem.perks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Carrega e mantém em cache cada arquivo de perk mencionado por uma classe. */
public final class PerkRepository {
	private static final ConcurrentMap<Identifier, PerkDefinition> CACHE = new ConcurrentHashMap<>();

	private PerkRepository() {}

	public static PerkDefinition load(Identifier id) {
		return CACHE.computeIfAbsent(id, PerkRepository::read);
	}

	private static PerkDefinition read(Identifier id) {
		String resource = "/data/" + id.getNamespace() + "/perks/" + id.getPath() + ".json";
		try (InputStream stream = PerkRepository.class.getResourceAsStream(resource)) {
			if (stream == null) throw new IllegalStateException("Arquivo da perk não encontrado: " + resource);
			JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			return new PerkDefinition(
					id,
					json.get("name").getAsString(),
					json.get("description").getAsString(),
					json.get("effect").getAsString(),
					json.get("amount").getAsDouble());
		} catch (Exception exception) {
			throw new IllegalStateException("Não foi possível carregar a perk " + id, exception);
		}
	}
}
