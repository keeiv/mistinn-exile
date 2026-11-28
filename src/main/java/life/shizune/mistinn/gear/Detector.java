package life.shizune.mistinn.gear;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 探測儀: worn where a mask is worn, it gives the face no armour. With an empty hand and nothing in reach to use, the
 * hunter reaches up and touches it: for four minutes they see the ores about them, iron and finer, each kind outlined in
 * its own colour, through stone. It is worn out after five touches, and between two it needs five minutes.
 */
public final class Detector {
	/** How many times it can be touched before it is spent. */
	public static final int USES = 5;
	/** How long between two touches, and how long the ores stay shown, in ticks. */
	public static final int COOLDOWN = 6000;
	public static final int SHOWN = 4800;
	/** How far round the hunter it reaches, and at most how many ores it shows. */
	public static final int RADIUS = 24;
	public static final int MOST = 320;
	/** The kinds of ore it tells apart, finest last; each has its colour in the hunter's view. */
	public enum Ore {
		IRON(0xD8AF93), GOLD(0xFCE34B), LAPIS(0x3A63D8), REDSTONE(0xE0302A), EMERALD(0x22D86A), DIAMOND(0x5BEFF5), CRYSTAL(0xC9F7FF), DEBRIS(0xA0634A);

		public final int colour;

		Ore(final int colour) {
			this.colour = colour;
		}
	}

	/** When this hunter may touch it again (game time). */
	public static final AttachmentType<Long> READY = AttachmentRegistry.<Long>builder()
		.persistent(Codec.LONG)
		.buildAndRegister(Mistinn.id("detector_ready"));

	private Detector() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TouchPayload.TYPE, TouchPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OresPayload.TYPE, OresPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ReachPayload.TYPE, ReachPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TouchPayload.TYPE, (payload, context) -> touch(context.player()));
	}

	public static boolean is(final ItemStack stack) {
		return stack.is(MistinnItems.DETECTOR);
	}

	/** Whether this hunter wears one where a mask is worn. */
	public static boolean worn(final ServerPlayer player) {
		return is(GridService.get(player).mask());
	}

	/** What kind of ore this is, or null for anything else. */
	public static @org.jspecify.annotations.Nullable Ore oreOf(final BlockState state) {
		if (state.is(BlockItemTags.IRON_ORES.block())) {
			return Ore.IRON;
		}
		if (state.is(BlockItemTags.GOLD_ORES.block())) {
			return Ore.GOLD;
		}
		if (state.is(BlockItemTags.LAPIS_ORES.block())) {
			return Ore.LAPIS;
		}
		if (state.is(BlockItemTags.REDSTONE_ORES.block())) {
			return Ore.REDSTONE;
		}
		if (state.is(BlockItemTags.EMERALD_ORES.block())) {
			return Ore.EMERALD;
		}
		if (state.is(BlockItemTags.DIAMOND_ORES.block())) {
			return Ore.DIAMOND;
		}
		if (state.is(MistinnBlocks.VEIN_CRYSTAL_ORE)) {
			return Ore.CRYSTAL;
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return Ore.DEBRIS;
		}
		return null;
	}

	/** The hunter touched it: if it is worn, rested and not spent, the ores about them show. Returns whether they did. */
	public static boolean touch(final ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			return false;
		}
		GridInventory inv = GridService.get(player);
		ItemStack worn = inv.mask();
		if (!is(worn)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = player.getAttachedOrElse(READY, 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.mistinn.detector.resting", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return false;
		}
		player.setAttached(READY, now + COOLDOWN);
		ItemStack spent = worn.copy();
		spent.hurtAndBreak(1, level, player, item -> {
		});
		GridService.set(player, inv.withMask(spent.isEmpty() ? ItemStack.EMPTY : spent));
		// the hand comes up to it, seen by all
		ReachPayload reach = new ReachPayload(player.getId());
		ServerPlayNetworking.send(player, reach);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, reach);
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.9F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
		ServerPlayNetworking.send(player, scan(level, player.blockPosition()));
		if (spent.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.detector.spent").withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** The ores within reach of a place, nearest first, at most {@link #MOST}. */
	public static OresPayload scan(final ServerLevel level, final BlockPos centre) {
		List<long[]> found = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), centre.getY() - RADIUS);
		int maxY = Math.min(level.getMaxY(), centre.getY() + RADIUS);
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				if (!level.hasChunk((centre.getX() + x) >> 4, (centre.getZ() + z) >> 4)) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					at.set(centre.getX() + x, y, centre.getZ() + z);
					Ore ore = oreOf(level.getBlockState(at));
					if (ore != null) {
						found.add(new long[] {at.asLong(), ore.ordinal(), (long) x * x + (long) (y - centre.getY()) * (y - centre.getY()) + (long) z * z});
					}
				}
			}
		}
		found.sort(Comparator.comparingLong(e -> e[2]));
		int count = Math.min(MOST, found.size());
		List<Long> positions = new ArrayList<>(count);
		List<Integer> kinds = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			positions.add(found.get(i)[0]);
			kinds.add((int) found.get(i)[1]);
		}
		return new OresPayload(positions, kinds, SHOWN);
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** The hunter, empty-handed with nothing to use in reach, touched what they wear on their face. */
	public record TouchPayload() implements CustomPacketPayload {
		public static final Type<TouchPayload> TYPE = new Type<>(Mistinn.id("detector_touch"));
		public static final StreamCodec<ByteBuf, TouchPayload> CODEC = StreamCodec.unit(new TouchPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The ores it found: where, what kind, and for how many ticks they show. */
	public record OresPayload(List<Long> positions, List<Integer> kinds, int ticks) implements CustomPacketPayload {
		public static final Type<OresPayload> TYPE = new Type<>(Mistinn.id("detector_ores"));
		public static final StreamCodec<ByteBuf, OresPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MOST)), OresPayload::positions,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST)), OresPayload::kinds,
			ByteBufCodecs.VAR_INT, OresPayload::ticks,
			OresPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A hunter reached up to touch theirs (the hand to the head, seen by all). */
	public record ReachPayload(int entity) implements CustomPacketPayload {
		public static final Type<ReachPayload> TYPE = new Type<>(Mistinn.id("detector_reach"));
		public static final StreamCodec<ByteBuf, ReachPayload> CODEC = ByteBufCodecs.VAR_INT.map(ReachPayload::new, ReachPayload::entity);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
