package life.shizune.mistinn.grid;

import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from the grid panel. The meaning of {@code a}, {@code b}, {@code c}, {@code d} depends
 * on the action: an item index for PICK, QUICK and HALF (with {@code b} 1 for a whole stack on
 * DROP); a section and cell for PLACE and ONE; a section, cell and hotbar slot for SWAP; a menu
 * slot for GRAB.
 */
public record GridActionPayload(int action, int area, int a, int b, int c, int d, boolean rotated) implements CustomPacketPayload {
	public static final int PICK = 0;
	public static final int PLACE = 1;
	public static final int BACKPACK = 2;
	public static final int DROP = 3;
	/** Shift-click: where the open screen sends a stack from the inventory. */
	public static final int QUICK = 4;
	public static final int GRAB = 5;
	public static final int RIG_SLOT = 6;
	public static final int MASK_SLOT = 7;
	/** A number key or the offhand key over a cell. */
	public static final int SWAP = 8;
	/** Right-click on an item with nothing carried: half of it. */
	public static final int HALF = 9;
	/** Right-click on a cell while carrying: one of it down. */
	public static final int ONE = 10;
	/** Double-click while carrying: gather what matches. */
	public static final int COLLECT = 11;

	public static final Type<GridActionPayload> TYPE = new Type<>(Mistinn.id("grid_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GridActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GridActionPayload::action,
		ByteBufCodecs.VAR_INT, GridActionPayload::area,
		ByteBufCodecs.VAR_INT, GridActionPayload::a,
		ByteBufCodecs.VAR_INT, GridActionPayload::b,
		ByteBufCodecs.VAR_INT, GridActionPayload::c,
		ByteBufCodecs.VAR_INT, GridActionPayload::d,
		ByteBufCodecs.BOOL, GridActionPayload::rotated,
		GridActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			var player = context.player();
			if (player.isSpectator()) {
				return;
			}
			Area area = Area.byId(payload.area());
			switch (payload.action()) {
				case PICK -> GridService.pick(player, area, payload.a());
				case PLACE -> GridService.place(player, area, payload.a(), payload.b(), payload.c(), payload.rotated());
				case BACKPACK -> GridService.backpackSlot(player);
				case DROP -> GridService.drop(player, area, payload.a(), payload.b() == 1);
				case QUICK -> GridService.quickMove(player, area, payload.a());
				case GRAB -> GridService.grabSlot(player, payload.a());
				case RIG_SLOT -> GridService.rigSlot(player);
				case MASK_SLOT -> GridService.maskSlot(player);
				case SWAP -> GridService.swap(player, area, payload.a(), payload.b(), payload.c(), payload.d(), payload.rotated());
				case HALF -> GridService.half(player, area, payload.a());
				case ONE -> GridService.placeOne(player, area, payload.a(), payload.b(), payload.c(), payload.rotated());
				case COLLECT -> GridService.collect(player);
				default -> {
				}
			}
		});
	}
}
