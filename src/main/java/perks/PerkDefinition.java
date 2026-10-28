package br.com.rpgsystem.perks;

import net.minecraft.resources.Identifier;

/** Metadados de uma perk lidos de um JSON individual em data/<namespace>/perks/. */
public record PerkDefinition(Identifier id, String name, String description, String effect, double amount) {}
