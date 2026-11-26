package life.shizune.mistinn.enemy;

import net.minecraft.sounds.SoundEvent;

/** A creature that cries out with its own voice when it winds up a blow, instead of its kin's. */
public interface Voiced {
	SoundEvent windupCry();
}
