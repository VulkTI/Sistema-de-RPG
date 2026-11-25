package br.com.rpgsystem.dungeon.network;

import br.com.rpgsystem.RpgMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class DungeonPayloads {
	private DungeonPayloads() {}

	public record StringPayload(String json) implements CustomPacketPayload {
		public static final StreamCodec<RegistryFriendlyByteBuf, StringPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, StringPayload::json, StringPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			throw new UnsupportedOperationException();
		}
	}

	public record OpenSetup(String json) implements CustomPacketPayload {
		public static final Type<OpenSetup> TYPE = new Type<>(RpgMod.id("dungeon_open_setup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenSetup> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenSetup::json, OpenSetup::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record StartBuild(String json) implements CustomPacketPayload {
		public static final Type<StartBuild> TYPE = new Type<>(RpgMod.id("dungeon_start_build"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StartBuild> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, StartBuild::json, StartBuild::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SaveDungeonSetup(String json) implements CustomPacketPayload {
		public static final Type<SaveDungeonSetup> TYPE = new Type<>(RpgMod.id("dungeon_save_dungeon_setup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveDungeonSetup> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SaveDungeonSetup::json, SaveDungeonSetup::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CancelSetup() implements CustomPacketPayload {
		public static final Type<CancelSetup> TYPE = new Type<>(RpgMod.id("dungeon_cancel_setup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CancelSetup> CODEC = StreamCodec.unit(new CancelSetup());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record OpenStructureConfig(String json) implements CustomPacketPayload {
		public static final Type<OpenStructureConfig> TYPE = new Type<>(RpgMod.id("dungeon_open_struct_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenStructureConfig> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenStructureConfig::json, OpenStructureConfig::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SaveStructureConfig(String json) implements CustomPacketPayload {
		public static final Type<SaveStructureConfig> TYPE = new Type<>(RpgMod.id("dungeon_save_struct_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveStructureConfig> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SaveStructureConfig::json, SaveStructureConfig::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CancelStructureConfig() implements CustomPacketPayload {
		public static final Type<CancelStructureConfig> TYPE = new Type<>(RpgMod.id("dungeon_cancel_struct_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CancelStructureConfig> CODEC = StreamCodec.unit(new CancelStructureConfig());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record UpdateTempStructureConfig(String json) implements CustomPacketPayload {
		public static final Type<UpdateTempStructureConfig> TYPE = new Type<>(RpgMod.id("dungeon_update_temp_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTempStructureConfig> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, UpdateTempStructureConfig::json, UpdateTempStructureConfig::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SetEditorTime(long ticks) implements CustomPacketPayload {
		public static final Type<SetEditorTime> TYPE = new Type<>(RpgMod.id("dungeon_set_editor_time"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetEditorTime> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, SetEditorTime::ticks, SetEditorTime::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SetEditorCycle(boolean daylightCycle) implements CustomPacketPayload {
		public static final Type<SetEditorCycle> TYPE = new Type<>(RpgMod.id("dungeon_set_editor_cycle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetEditorCycle> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, SetEditorCycle::daylightCycle, SetEditorCycle::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record EditWaveAction(int waveIndex) implements CustomPacketPayload {
		public static final Type<EditWaveAction> TYPE = new Type<>(RpgMod.id("dungeon_edit_wave"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EditWaveAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, EditWaveAction::waveIndex, EditWaveAction::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record EditPortalAction(String portalType) implements CustomPacketPayload {
		public static final Type<EditPortalAction> TYPE = new Type<>(RpgMod.id("dungeon_edit_portal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EditPortalAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, EditPortalAction::portalType, EditPortalAction::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record OpenMobTool(String json) implements CustomPacketPayload {
		public static final Type<OpenMobTool> TYPE = new Type<>(RpgMod.id("dungeon_open_mob_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenMobTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenMobTool::json, OpenMobTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SaveMobTool(String json) implements CustomPacketPayload {
		public static final Type<SaveMobTool> TYPE = new Type<>(RpgMod.id("dungeon_save_mob_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveMobTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SaveMobTool::json, SaveMobTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record OpenChestTool(String json) implements CustomPacketPayload {
		public static final Type<OpenChestTool> TYPE = new Type<>(RpgMod.id("dungeon_open_chest_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenChestTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenChestTool::json, OpenChestTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SaveChestTool(String json) implements CustomPacketPayload {
		public static final Type<SaveChestTool> TYPE = new Type<>(RpgMod.id("dungeon_save_chest_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveChestTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SaveChestTool::json, SaveChestTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record OpenAreaTool(String json) implements CustomPacketPayload {
		public static final Type<OpenAreaTool> TYPE = new Type<>(RpgMod.id("dungeon_open_area_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenAreaTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, OpenAreaTool::json, OpenAreaTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record SaveAreaTool(String json) implements CustomPacketPayload {
		public static final Type<SaveAreaTool> TYPE = new Type<>(RpgMod.id("dungeon_save_area_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveAreaTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SaveAreaTool::json, SaveAreaTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record DeleteAreaTool(String areaName) implements CustomPacketPayload {
		public static final Type<DeleteAreaTool> TYPE = new Type<>(RpgMod.id("dungeon_delete_area_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DeleteAreaTool> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, DeleteAreaTool::areaName, DeleteAreaTool::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CancelAreaTool() implements CustomPacketPayload {
		public static final Type<CancelAreaTool> TYPE = new Type<>(RpgMod.id("dungeon_cancel_area_tool"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CancelAreaTool> CODEC = StreamCodec.unit(new CancelAreaTool());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CycleToolScroll(boolean forward) implements CustomPacketPayload {
		public static final Type<CycleToolScroll> TYPE = new Type<>(RpgMod.id("dungeon_cycle_scroll"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CycleToolScroll> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, CycleToolScroll::forward, CycleToolScroll::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record PortalAimSync(String dungeonName, int dungeonLevel, int timeLeftSeconds, boolean completed) implements CustomPacketPayload {
		public static final Type<PortalAimSync> TYPE = new Type<>(RpgMod.id("dungeon_portal_aim_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PortalAimSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, PortalAimSync::dungeonName,
				ByteBufCodecs.VAR_INT, PortalAimSync::dungeonLevel,
				ByteBufCodecs.VAR_INT, PortalAimSync::timeLeftSeconds,
				ByteBufCodecs.BOOL, PortalAimSync::completed,
				PortalAimSync::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record PromptOfficialDuplicate(String officialName) implements CustomPacketPayload {
		public static final Type<PromptOfficialDuplicate> TYPE = new Type<>(RpgMod.id("dungeon_prompt_official"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PromptOfficialDuplicate> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, PromptOfficialDuplicate::officialName,
				PromptOfficialDuplicate::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ConfirmOfficialDuplicate(String officialName) implements CustomPacketPayload {
		public static final Type<ConfirmOfficialDuplicate> TYPE = new Type<>(RpgMod.id("dungeon_confirm_official"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmOfficialDuplicate> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ConfirmOfficialDuplicate::officialName,
				ConfirmOfficialDuplicate::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Pacote enviado pelo cliente quando P é pressionado durante sessão de edição de dungeon.
	 *  O servidor responde reenviando o OpenSetup com os dados atuais da sessão. */
	public record RequestReopenSetup() implements CustomPacketPayload {
		public static final Type<RequestReopenSetup> TYPE = new Type<>(RpgMod.id("dungeon_request_reopen_setup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestReopenSetup> CODEC = StreamCodec.unit(new RequestReopenSetup());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}
}
