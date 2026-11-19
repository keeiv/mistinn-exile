package life.shizune.mistinn.grid;

import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from the grid panel. The meaning of {@code a}, {@code b}, {@code c} depends on the
 * action: an item index for PICK/DROP/HOTBAR, a section and cell for PLACE, a menu slot for GRAB.
 */
public record GridActionPayload(int action, int area, int a, int b, int c, boolean rotated) implements CustomPacketPayload {
	public static final int PICK = 0;
	public static final int PLACE = 1;
	public static final int BACKPACK = 2;
	public static final int DROP = 3;
	public static final int HOTBAR = 4;
	public static final int GRAB = 5;
	public static final int RIG_SLOT = 6;
	public static final int MASK_SLOT = 7;

	public static final Type<GridActionPayload> TYPE = new Type<>(Mistinn.id("grid_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GridActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GridActionPayload::action,
		ByteBufCodecs.VAR_INT, GridActionPayload::area,
		ByteBufCodecs.VAR_INT, GridActionPayload::a,
		ByteBufCodecs.VAR_INT, GridActionPayload::b,
		ByteBufCodecs.VAR_INT, GridActionPayload::c,
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
				case DROP -> GridService.drop(player, area, payload.a());
				case HOTBAR -> GridService.toHotbar(player, area, payload.a());
				case GRAB -> GridService.grabSlot(player, payload.a());
				case RIG_SLOT -> GridService.rigSlot(player);
				case MASK_SLOT -> GridService.maskSlot(player);
				default -> {
				}
			}
		});
	}
}
