package br.com.rpgsystem.network;

import br.com.rpgsystem.RpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Payloads entre o servidor (autoridade dos dados) e a interface do cliente. */
public final class RpgPayloads {
	private RpgPayloads() {}

	public record Snapshot(String json) implements CustomPacketPayload {
		public static final Type<Snapshot> TYPE = new Type<>(RpgMod.id("snapshot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Snapshot::json, Snapshot::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SelectClass(String classId) implements CustomPacketPayload {
		public static final Type<SelectClass> TYPE = new Type<>(RpgMod.id("select_class"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectClass> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SelectClass::classId, SelectClass::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record LearnSkill(String skillId) implements CustomPacketPayload {
		public static final Type<LearnSkill> TYPE = new Type<>(RpgMod.id("learn_skill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LearnSkill> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, LearnSkill::skillId, LearnSkill::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record LearnUniversalSkill(String skillId, String nodeId) implements CustomPacketPayload {
		public static final Type<LearnUniversalSkill> TYPE = new Type<>(RpgMod.id("learn_universal_skill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LearnUniversalSkill> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, LearnUniversalSkill::skillId,
				ByteBufCodecs.STRING_UTF8, LearnUniversalSkill::nodeId,
				LearnUniversalSkill::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}
}
