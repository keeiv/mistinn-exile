package life.shizune.mistinn.voice;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 獵人的聲音: every hunter has a voice, chosen the first time they enter a world: a man's (about twenty-five) or a
 * girl's (of high-school age). It is heard when they are struck, badly wounded, driven to effort by a heavy blow or a
 * roll, winded, and when they fall. The choice is kept with the hunter and known to everyone near them, since each
 * client plays a hunter's cries itself. A hunter who has not chosen keeps the plain sounds.
 */
public enum Voice {
	MALE(MistinnSounds.VOICE_MALE_HURT, MistinnSounds.VOICE_MALE_HURT_BAD, MistinnSounds.VOICE_MALE_EFFORT, MistinnSounds.VOICE_MALE_LAND,
		MistinnSounds.VOICE_MALE_DEATH, MistinnSounds.VOICE_MALE_BREATH, MistinnSounds.VOICE_MALE_PANT),
	FEMALE(MistinnSounds.VOICE_FEMALE_HURT, MistinnSounds.VOICE_FEMALE_HURT_BAD, MistinnSounds.VOICE_FEMALE_EFFORT, MistinnSounds.VOICE_FEMALE_LAND,
		MistinnSounds.VOICE_FEMALE_DEATH, MistinnSounds.VOICE_FEMALE_BREATH, MistinnSounds.VOICE_FEMALE_PANT);

	/** Below this share of their health a blow draws the cry of a bad wound. */
	public static final float BAD = 0.4F;

	/** 0: not chosen yet; otherwise the voice's ordinal + 1. Saved with the hunter and sent to everyone who sees them. */
	public static final AttachmentType<Integer> CHOICE = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("voice"));

	public final SoundEvent hurt;
	public final SoundEvent hurtBad;
	public final SoundEvent effort;
	public final SoundEvent land;
	public final SoundEvent death;
	public final SoundEvent breath;
	public final SoundEvent pant;

	Voice(final SoundEvent hurt, final SoundEvent hurtBad, final SoundEvent effort, final SoundEvent land, final SoundEvent death,
		final SoundEvent breath, final SoundEvent pant) {
		this.hurt = hurt;
		this.hurtBad = hurtBad;
		this.effort = effort;
		this.land = land;
		this.death = death;
		this.breath = breath;
		this.pant = pant;
	}

	/** The hunter's voice; null while they have not chosen one. */
	public static @Nullable Voice of(final Player player) {
		int choice = player.getAttachedOrElse(CHOICE, 0);
		return choice >= 1 && choice <= values().length ? values()[choice - 1] : null;
	}

	public static void choose(final ServerPlayer player, final Voice voice) {
		player.setAttached(CHOICE, voice.ordinal() + 1);
	}

	/** What a blow draws from this hunter: the cry of a bad wound when little health is left; null for the plain sound. */
	public static @Nullable SoundEvent hurt(final Player player) {
		Voice voice = of(player);
		if (voice == null) {
			return null;
		}
		return player.getHealth() < player.getMaxHealth() * BAD ? voice.hurtBad : voice.hurt;
	}

	/** The breath driven out behind a heavy blow or a roll, heard by everyone near. */
	public static void effort(final ServerPlayer player) {
		Voice voice = of(player);
		if (voice != null) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), voice.effort, SoundSource.PLAYERS,
				0.7F, 0.96F + player.getRandom().nextFloat() * 0.08F);
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ChoicePayload.TYPE, ChoicePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChoicePayload.TYPE, (payload, context) -> {
			if (payload.voice() >= 0 && payload.voice() < values().length) {
				choose(context.player(), values()[payload.voice()]);
			}
		});
	}

	/** The hunter chose a voice (by ordinal). */
	public record ChoicePayload(int voice) implements CustomPacketPayload {
		public static final Type<ChoicePayload> TYPE = new Type<>(Mistinn.id("voice_choice"));
		public static final StreamCodec<ByteBuf, ChoicePayload> CODEC = ByteBufCodecs.VAR_INT.map(ChoicePayload::new, ChoicePayload::voice);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
