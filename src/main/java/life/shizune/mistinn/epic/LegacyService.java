package life.shizune.mistinn.epic;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 行囊: the epic's gear carried to another world. The server only hands it over: when the epic is written a hunter seals
 * what they wear and the arms in their hands, and the pack goes to their own game, which keeps it outside any world (as
 * the ledger is kept). In another world the hunter's game offers the pack back, a piece at a time, from the guide; the
 * server takes a piece only in the hunter's own single-player world of the story or free exploration (never the path's
 * rules or training, nor another epic), only the mod's gear and arms and a bow, one of each, and only where it fits.
 */
public final class LegacyService {
	private LegacyService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SealedPayload.TYPE, SealedPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClaimPayload.TYPE, ClaimPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClaimedPayload.TYPE, ClaimedPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SealPayload.TYPE, SealPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ClaimPayload.TYPE, (payload, context) -> claim(context.player(), payload.index(), payload.stack()));
		ServerPlayNetworking.registerGlobalReceiver(SealPayload.TYPE, (payload, context) -> seal(context.player()));
	}

	/** Whether a hunter may seal their pack here: the epic, written. */
	public static boolean sealable(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return EpicService.active(server) && EpicService.state(server).at() == EpicStep.ENDED;
	}

	/** Seals the hunter's pack: what they wear, and every weapon and bow in their hands' reach. */
	public static void seal(final ServerPlayer player) {
		if (!sealable(player)) {
			player.sendSystemMessage(Component.translatable("epic.mistinn.legacy.not_yet").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		List<ItemStack> pack = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				pack.add(player.getItemBySlot(slot).copyWithCount(1));
			}
		}
		GridInventory grid = GridService.get(player);
		for (ItemStack worn : new ItemStack[] {grid.mask(), grid.rig(), grid.backpack()}) {
			if (!worn.isEmpty()) {
				pack.add(worn.copyWithCount(1));
			}
		}
		for (int i = 0; i < 9; i++) {
			ItemStack held = player.getInventory().getItem(i);
			if (carried(held)) {
				pack.add(held.copyWithCount(1));
			}
		}
		ServerPlayNetworking.send(player, new SealedPayload(pack));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("epic.mistinn.legacy.sealed", pack.size()).withStyle(ChatFormatting.GOLD));
		Mistinn.LOGGER.info("Epic pack sealed: {} pieces", pack.size());
	}

	/** Whether a piece may travel in the pack: the mod's own gear and arms, or a bow. */
	public static boolean carried(final ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.BOW)) {
			return true;
		}
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals(Mistinn.MOD_ID) && (life.shizune.mistinn.weapon.WeaponStyle.of(stack) != null
			|| GridInventory.maskOf(stack) != null || GridInventory.rigOf(stack) != null || GridInventory.backpackOf(stack) != null
			|| stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE) != null);
	}

	/** Whether this world takes pieces from a pack: the hunter's own single-player story or free world. */
	public static boolean claimable(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		WorldMode mode = WorldMode.of(server);
		return (mode == WorldMode.STORY || mode == WorldMode.FREE) && server.isSingleplayerOwner(player.nameAndId());
	}

	/** A piece taken out of the pack: given if it may be, and the client told either way. */
	private static void claim(final ServerPlayer player, final int index, final ItemStack stack) {
		if (!claimable(player)) {
			ServerPlayNetworking.send(player, new ClaimedPayload(index, false, "epic.mistinn.legacy.not_here"));
			return;
		}
		if (!carried(stack)) {
			ServerPlayNetworking.send(player, new ClaimedPayload(index, false, "epic.mistinn.legacy.not_gear"));
			return;
		}
		ItemStack giving = stack.copyWithCount(1);
		GridService.insert(player, giving);
		if (!giving.isEmpty() && !player.getInventory().add(giving)) {
			ServerPlayNetworking.send(player, new ClaimedPayload(index, false, "epic.mistinn.legacy.no_room"));
			return;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new ClaimedPayload(index, true, ""));
	}

	/** Server to client: the sealed pack, for the hunter's own game to keep. */
	public record SealedPayload(List<ItemStack> pack) implements CustomPacketPayload {
		public static final Type<SealedPayload> TYPE = new Type<>(Mistinn.id("epic_pack_sealed"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SealedPayload> CODEC = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(32))
			.map(SealedPayload::new, SealedPayload::pack);

		@Override
		public Type<SealedPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: seal my pack (the guide's button). */
	public record SealPayload(boolean seal) implements CustomPacketPayload {
		public static final Type<SealPayload> TYPE = new Type<>(Mistinn.id("epic_pack_seal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SealPayload> CODEC = ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast()
			.map(SealPayload::new, SealPayload::seal);

		@Override
		public Type<SealPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: take this piece (the pack's own index for it) out of my pack. */
	public record ClaimPayload(int index, ItemStack stack) implements CustomPacketPayload {
		public static final Type<ClaimPayload> TYPE = new Type<>(Mistinn.id("epic_pack_claim"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ClaimPayload::index, ItemStack.STREAM_CODEC, ClaimPayload::stack, ClaimPayload::new);

		@Override
		public Type<ClaimPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: whether the piece was given, and if not, why (a translation key). */
	public record ClaimedPayload(int index, boolean given, String why) implements CustomPacketPayload {
		public static final Type<ClaimedPayload> TYPE = new Type<>(Mistinn.id("epic_pack_claimed"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClaimedPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ClaimedPayload::index, ByteBufCodecs.BOOL, ClaimedPayload::given, ByteBufCodecs.STRING_UTF8, ClaimedPayload::why,
			ClaimedPayload::new);

		@Override
		public Type<ClaimedPayload> type() {
			return TYPE;
		}
	}
}
