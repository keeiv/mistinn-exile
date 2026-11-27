package life.shizune.mistinn.client.path;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.path.PathService;
import life.shizune.mistinn.terra.CrystalElder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * 念頭. On the tillers' way a thought now and then comes up in the walker's head that is not quite their own: the one
 * moving them. A tiller who only wanted to see the sea thinks, near the crown elder, that they might win; and then
 * wonders why they thought it. Which version comes answers how this hunter has walked the way in other worlds (the
 * ledger's kind or cruel). Each comes to each walker once, faintly, above the middle of the screen.
 */
public final class Thoughts implements HudElement {
	/** The thoughts there are, and how many lines each has. */
	public enum Thought {
		/** A new walker, a little while after taking up the way. */
		TAKEOVER("takeover", 2),
		/** The first walker, when the hunter has walked the way in another world. */
		AGAIN("again", 1),
		/** A walker who has fallen in another world. */
		FELL_BEFORE("fell_before", 1),
		/** The first blow at a creature. */
		FIRST_BLOW("first_blow", 2),
		/** The crown elder, near. */
		ELDER("elder", 2),
		/** Little strength left. */
		LOW("low", 2),
		/** Half of the twelve gone. */
		HALF("half", 1),
		/** The way's last stretch, toward the sea. */
		SEA("sea", 1);

		public final String id;
		public final int lines;

		Thought(final String id, final int lines) {
			this.id = id;
			this.lines = lines;
		}
	}

	/** Ticks after a walker takes up the way before anything comes to them. */
	public static final int SETTLE = 80;
	/** Ticks a thought takes to come up, and to go. */
	private static final int IN = 14;
	private static final int OUT = 24;
	/** Ticks of quiet between one line and the next. */
	private static final int GAP = 26;
	private static final int COLOUR = 0xC8D6DC;

	private static final Set<String> HAD = new HashSet<>();
	private static final Deque<String> COMING = new ArrayDeque<>();
	private static @Nullable Component showing;
	private static int shownTicks;
	private static int shownFor;
	private static int quiet;
	private static int walker = -1;
	private static int settled;

	/** Each tick: what is coming to the walker now. */
	public static void tick(final Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			forget();
			return;
		}
		PathService.View view = mc.player.getAttached(PathService.VIEW);
		if (view != null && view.stage().ordinal() < PathService.Step.ENDED.ordinal() && mc.player.isAlive() && !mc.player.isSpectator()) {
			way(mc, view);
		}
		advance(mc);
	}

	/** What the way brings to its walker now. */
	private static void way(final Minecraft mc, final PathService.View view) {
		if (view.walker() != walker) {
			// what was coming to the one before does not come to the next
			walker = view.walker();
			settled = 0;
			COMING.clear();
		}
		settled++;
		Ledger ledger = LedgerClient.ledger();
		if (settled == SETTLE) {
			if (walker > 0) {
				think(Thought.TAKEOVER);
			} else if (ledger.worlds("path") >= 2) {
				think(Thought.AGAIN);
			}
			if (ledger.fallen(walker) > 0) {
				think(Thought.FELL_BEFORE);
			}
		}
		if (settled > SETTLE * 2) {
			if (mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Enemy) {
				think(Thought.FIRST_BLOW);
			}
			if (view.stage() == PathService.Step.ELDER
				&& !mc.level.getEntitiesOfClass(CrystalElder.class, mc.player.getBoundingBox().inflate(40.0), CrystalElder::isAlive).isEmpty()) {
				think(Thought.ELDER);
			}
			if (BodyService.get(mc.player).total() < 6.0F * BodyService.max(mc.player) * 0.3F) {
				think(Thought.LOW);
			}
			if (view.fallen() >= PathService.PEOPLE / 2) {
				think(Thought.HALF);
			}
			if (view.stage() == PathService.Step.SEA) {
				think(Thought.SEA);
			}
		}
	}

	/** A line (a translation key, read in the hunter's version) comes up in their head, after whatever is already coming. */
	public static void come(final String key) {
		COMING.add(key);
	}

	/** Brings a thought to this walker, once. */
	public static void think(final Thought thought) {
		if (!HAD.add(walker + ":" + thought.id)) {
			return;
		}
		for (int i = 0; i < thought.lines; i++) {
			COMING.add("path.mistinn.thought." + thought.id + "." + i);
		}
	}

	/** The line in the walker's head now, or null. */
	public static @Nullable Component showing() {
		return showing;
	}

	private static void advance(final Minecraft mc) {
		if (showing != null) {
			if (++shownTicks >= shownFor) {
				showing = null;
				quiet = GAP;
			}
			return;
		}
		if (quiet > 0) {
			quiet--;
			return;
		}
		while (!COMING.isEmpty()) {
			// a version may leave a line out: the cruel one does not wonder
			Component line = Component.translatable(Haunting.key(COMING.poll()));
			String text = line.getString();
			if (text.isBlank()) {
				continue;
			}
			showing = line.copy().withStyle(ChatFormatting.ITALIC);
			shownTicks = 0;
			shownFor = IN + 50 + text.length() * 4 + OUT;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.55F, 0.22F));
			return;
		}
	}

	private static void forget() {
		HAD.clear();
		COMING.clear();
		showing = null;
		walker = -1;
		settled = 0;
		quiet = 0;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Component line = showing;
		Minecraft mc = Minecraft.getInstance();
		if (line == null || mc.gui.hud.isHidden()) {
			return;
		}
		float t = shownTicks + deltaTracker.getGameTimeDeltaPartialTick(false);
		float alpha = Math.min(Mth.clamp(t / IN, 0.0F, 1.0F), Mth.clamp((shownFor - t) / OUT, 0.0F, 1.0F));
		int a = Math.max(4, Math.round(220 * alpha * alpha));
		Font font = mc.font;
		int width = g.guiWidth();
		int y = Math.round(g.guiHeight() * 0.31F);
		// it drifts up a little as it comes
		y += Math.round(4.0F * (1.0F - Mth.clamp(t / (IN * 2.0F), 0.0F, 1.0F)));
		for (var part : font.split(line, Math.min(300, width - 40))) {
			g.text(font, part, (width - font.width(part)) / 2, y, a << 24 | COLOUR, true);
			y += 11;
		}
	}
}
