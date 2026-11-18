package life.shizune.mistinn.registry;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.residue.DeathMark;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;

/** Data kept on each player: saved with the world, sent only to that player. */
public final class MistinnAttachments {
	/** How much salt the hunter carries. */
	public static final AttachmentType<Integer> SALT = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("salt"));

	/** Where the hunter last fell, and which gravestone holds their residue. */
	public static final AttachmentType<DeathMark> DEATH_MARK = AttachmentRegistry.<DeathMark>builder()
		.persistent(DeathMark.CODEC)
		.syncWith(DeathMark.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("death_mark"));

	/** Counts deaths so that only the latest gravestone can be opened. */
	public static final AttachmentType<Integer> RESIDUE_SERIAL = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("residue_serial"));

	/** The golden bell the hunter last rested at: where they come back. */
	public static final AttachmentType<GlobalPos> BELL = AttachmentRegistry.<GlobalPos>builder()
		.persistent(GlobalPos.CODEC)
		.syncWith(GlobalPos.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("bell"));

	private MistinnAttachments() {
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
