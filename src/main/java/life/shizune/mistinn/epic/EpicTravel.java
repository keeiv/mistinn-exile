package life.shizune.mistinn.epic;

import io.netty.buffer.ByteBuf;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.udasru.UdasruPlaces;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The bell carries a hunter: from the map, to the door of any bell tower they have rung. A short rest between journeys,
 * none at all while downed, and only in Udasru itself.
 */
public final class EpicTravel {
	/** Ticks between two journeys. */
	public static final long REST = 200L;
	/** When this hunter may travel again (game time). */
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.<Long>builder()
		.buildAndRegister(Mistinn.id("epic_travel_ready"));

	private EpicTravel() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TowerPayload.TYPE, TowerPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TowerPayload.TYPE, (payload, context) -> travel(context.player(), payload.tower()));
	}

	/** Sends a hunter to a rung tower's door; says why not when it cannot. Returns whether they went. */
	public static boolean travel(final ServerPlayer player, final int index) {
		ServerLevel level = player.level();
		if (!EpicService.active(level.getServer()) || index < 0 || index >= UdasruPlaces.Tower.values().length
			|| !level.dimension().equals(Level.OVERWORLD) || !player.isAlive()) {
			return false;
		}
		UdasruPlaces.Tower tower = UdasruPlaces.Tower.values()[index];
		if (!EpicService.state(level.getServer()).rungTower(tower.bit())) {
			player.sendSystemMessage(Component.translatable("epic.mistinn.travel.unrung").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (life.shizune.mistinn.team.DownService.isDown(player)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < player.getAttachedOrElse(READY_AT, 0L)) {
			player.sendSystemMessage(Component.translatable("epic.mistinn.travel.resting").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		player.setAttached(READY_AT, now + REST);
		level.getChunk(tower.x >> 4, (tower.z + 7) >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tower.x, tower.z + 7);
		BlockPos from = player.blockPosition();
		level.playSound(null, from, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.teleportTo(level, tower.x + 0.5, y, tower.z + 7.5, Set.of(), 180.0F, 0.0F, true);
		level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	/** Client to server: carry me to this tower (its order in {@link UdasruPlaces.Tower}). */
	public record TowerPayload(int tower) implements CustomPacketPayload {
		public static final Type<TowerPayload> TYPE = new Type<>(Mistinn.id("epic_tower_travel"));
		public static final StreamCodec<ByteBuf, TowerPayload> CODEC = ByteBufCodecs.VAR_INT.map(TowerPayload::new, TowerPayload::tower);

		@Override
		public Type<TowerPayload> type() {
			return TYPE;
		}
	}
}
