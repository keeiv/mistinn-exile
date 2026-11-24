package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;

/** How the story of a world ended: the salt sent up to the anchor, or thrown into the Mist Inn's fire. */
public enum StoryEnding {
	/** 錨: the salt went up; the station holds, and another hunter is sent down. */
	ANCHOR("anchor"),
	/** 霧宿: the salt burned in the Mist Inn's hearth; the anchor starves and its chain parts. */
	INN("inn");

	public static final Codec<StoryEnding> CODEC = Codec.STRING.xmap(StoryEnding::byId, ending -> ending.id);

	public final String id;

	StoryEnding(final String id) {
		this.id = id;
	}

	public static StoryEnding byId(final String id) {
		return "inn".equals(id) ? INN : ANCHOR;
	}
}
