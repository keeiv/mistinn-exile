# Boss 大加強 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 熄燈人變成會即時思考的 boss：約 30 種可自然連貫的招式、看習慣也看當下的 AI（打分＋預測＋最多 6 步推演）、假動作與誠實的待機信號、更兇的外觀，並錄一段實機影片。

**Architecture:** 新的 `life.shizune.mistinn.boss` 套件放共用的招式資料、執行器、命中判定、感知、玩家模型與推演器；`Snuffer` 只宣告自己的招式表與階段規則。伺服器決定一切，動作以「招式編號＋經過 tick」同步給客戶端播 `anim/keeper/<招式>` 動畫。動畫、音效由倉庫外的 Python 腳本產生，貼圖交給 Codex。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3；Python 3.11（numpy、scipy、soundfile、Pillow、cv2）；Codex CLI（GPT 6.2 Sol、xhigh）畫貼圖；ffmpeg 合成影片。

**Spec:** `docs/superpowers/specs/2026-11-21-boss-overhaul-design.md`

## Global Constraints

- 伺服器權威：AI、命中、傷害只在伺服器決定；客戶端只播動畫與音效。
- 新的同步資料與附件不存檔（不加 `.persistent(...)`，實體不寫新的 NBT）。
- 每一招（含移動、假動作、信號）都有自己的開頭音效與動作預兆。
- 前搖最短：輕招 ≥ 8 tick、重招 ≥ 14 tick、抓取 ≥ 16 tick；連招接續招可縮短前搖，但仍有自己的音效。
- 反應延遲：大腦看到的玩家狀態比實際晚 4 tick。
- 推演深度 ≤ 6 步；每次思考平均 ≤ 1 毫秒、最壞 ≤ 3 毫秒，超過就用目前最好的結果。
- 信號誠實：做了信號，接下來 60 tick 內的下一個攻擊一定屬於該信號的類別；信號不能被假動作取代。
- 一場戰鬥：燈室連續 600 tick 沒有活著的追獵者，或燈守死亡，就清除學到的東西。
- 程式命名與註解用英文；玩家看得到的文字放 `zh_tw.json` 與 `en_us.json`。
- 音效只用 CC0 錄音或原創合成，來源記在 `CREDITS.md`；產生腳本放 `MistinnArt/mod/`，不進倉庫。
- 貼圖由 Codex 繪製（`codex exec -m gpt-6.2-sol -c model_reasoning_effort=xhigh`），畫風：每個部件用自己最深的顏色描邊、光從左上、每種材質 4～5 個色階。
- 提交作者只能是 `keeiv <234966047+keeiv@users.noreply.github.com>`，不加 Claude 署名；每個階段一條功能分支 → PR → `gh pr merge --auto --rebase` → 合併後刪分支、同步 main；每次提交後重建 JAR 與啟動器。

## Review Focus

1. **抓著或鉤著的玩家在招式中途死亡、離線或被傳送**：招式要放開他、照常收招，不得報錯或把玩家拉回。→ Task 1 測試 `grabbedHunterLeavingReleases`。
2. **滑鏟、突襲、跳躍撞牆或衝出燈室**：位移遇到方塊就停，不得卡進牆裡或衝出燈室半徑。→ Task 7 測試 `dashStopsAtWalls`。
3. **房間裡有創造模式、觀察者或非追獵者**：只把 `BodyService.applies` 的存活玩家當目標與觀察對象。→ Task 5 測試 `ignoresNonHunters`。
4. **燈守在招式中途死亡（含劇情死亡過場）**：招式立刻停止，不再造成傷害或位移。→ Task 1 測試 `deathStopsTheMove`。
5. **同一世界有好幾隻燈守**：每隻各自思考，各自守時間預算，不互相共用學習。→ Task 5 測試 `brainsAreIndependent`。

---

## 檔案結構

| 檔案 | 責任 |
| --- | --- |
| `src/main/java/life/shizune/mistinn/boss/BossMove.java` | 一招的資料（記錄＋建構器）：種類、輕重、預兆、前搖、出手、收招、命中、位移、使用條件、冷卻、類別、假動作／信號資訊 |
| `src/main/java/life/shizune/mistinn/boss/HitShape.java` | 命中形狀（扇形、膠囊、圓形、抓取）與「打到哪個部位」規則 |
| `src/main/java/life/shizune/mistinn/boss/MoveSet.java` | 一個 boss 的招式表與接續連線（招式圖） |
| `src/main/java/life/shizune/mistinn/boss/MoveRunner.java` | 執行一招的時間軸：預兆音效、出手判定、位移、接續窗口、中斷 |
| `src/main/java/life/shizune/mistinn/boss/Observation.java`、`PlayerTrace.java` | 每 tick 的玩家觀察與 4 tick 延遲的環狀紀錄 |
| `src/main/java/life/shizune/mistinn/boss/PlayerModel.java` | 位置預測、反應機率、習慣（單場戰鬥內學習） |
| `src/main/java/life/shizune/mistinn/boss/Planner.java` | 打分＋beam search 推演（≤ 6 步）＋時間預算 |
| `src/main/java/life/shizune/mistinn/boss/BossBrain.java` | 感知、選目標、決策點、戰鬥記憶生命週期、信號約束 |
| `src/main/java/life/shizune/mistinn/keeper/SnufferMoves.java` | 熄燈人的約 30 招與接續連線 |
| `src/main/java/life/shizune/mistinn/keeper/Snuffer.java` | 改用上述系統；保留黑暗階段、燈台、硬直、血條、劇情死亡、掉落 |
| `src/client/java/life/shizune/mistinn/client/keeper/SnufferModel.java`、`SnufferRenderState.java`、`SnufferRenderer.java` | 新模型形狀；依同步的招式編號播 `anim/keeper/<id>` |
| `src/main/resources/assets/mistinn/anim/keeper/*.json` | 每一招與待機的動畫（由 `MistinnArt/mod/take_keeper_*.py` 產生） |
| `src/main/resources/assets/mistinn/sounds/entity/snuffer/*.ogg`、`sounds.json`、`lang/*.json` | 每一招的預兆音效與字幕 |
| `src/main/resources/assets/mistinn/textures/entity/snuffer*.png` | Codex 繪製的新貼圖 |
| `src/gametest/java/life/shizune/mistinn/test/MistinnBossTest.java` | 系統、大腦、招式、會學與不退化的測試 |
| `src/gametest/java/life/shizune/mistinn/test/MistinnBossReelTest.java` | 實機影片的逐格擷取（`-Dmistinn.bossreel=true` 才執行） |

---

## 階段一（PR：`feat/boss-brain`）

### Task 1: 招式資料、命中與執行器，熄燈人原有三招搬上去

**Files:** Create `boss/BossMove.java`、`boss/HitShape.java`、`boss/MoveSet.java`、`boss/MoveRunner.java`、`keeper/SnufferMoves.java`；Modify `keeper/Snuffer.java`、`client/keeper/SnufferModel.java`、`client/keeper/SnufferRenderState.java`、`client/keeper/SnufferRenderer.java`、`MistinnClientGameTest.java`、`MistinnMotionReelTest.java`；Create `MistinnBossTest.java`（並登記在 `src/gametest/resources/fabric.mod.json`）

**Interfaces:**
- Produces:
  - `record BossMove(String id, Kind kind, Weight weight, SoundEvent tell, int windup, int active, int recovery, List<Strike> strikes, Motion motion, Range range, int cooldown, Set<String> families, @Nullable String imitates, @Nullable String signals)`；`enum Kind { ATTACK, MOVEMENT, FEINT, SIGNAL, IDLE }`；`enum Weight { LIGHT(8), HEAVY(14), GRAB(16), NONE(0); int minWindup }`；`record Strike(int tick, HitShape shape, PartRule part, float damage)`；`record Motion(List<Key> keys)`（`Key(int tick, double forward, double side, double up)`，以 boss 朝向為準的位移）；`record Range(double min, double max)`；`static BossMove.Builder attack(String id)` 等建構器。
  - `sealed interface HitShape { boolean hits(Mob boss, Player player); }`：`Sector(double radius, double halfAngle, double height)`、`Capsule(double reach, double radius, double height)`、`Circle(double radius, boolean groundOnly)`、`Grab(double reach)`；`interface PartRule { BodyService.Hit pick(Mob boss, Player player, RandomSource random); }`。
  - `final class MoveSet { MoveSet(List<BossMove> moves, List<Link> links); BossMove get(String id); int index(String id); List<BossMove> followUps(BossMove from, int tick); }`；`record Link(String from, String to, int earliest, int latest)`。
  - `final class MoveRunner { void start(BossMove move); boolean running(); @Nullable BossMove current(); int ticks(); boolean inChainWindow(); void tick(ServerLevel level, Mob boss, @Nullable LivingEntity target); void stop(); }`。
  - `Snuffer.perform(String id)`（取代 `start(Move)`）、`Snuffer.strikeNow(ServerLevel, ServerPlayer, String id)`、`Snuffer.moves()` 回傳 `MoveSet`；同步資料改為 `DATA_MOVE`（byte，招式編號，-1 為無）與 `DATA_MOVE_TICKS`；保留 `STAGGER` 狀態另用 `DATA_STAGGER`。客戶端 `SnufferRenderState.move`（`String`，可為 null）、`moveTicks`。

- [ ] **Step 1: Write the failing tests**（`MistinnBossTest`）
  - `everyMoveHasATell`：熄燈人每一招 `tell != null`，`windup >= weight.minWindup`，動畫 `Clip.find("keeper/" + id) != null`。
  - `sweepHitsInFrontNotBehind`：前方 3 格的假人被 `sweep` 打中，背後 3 格的沒有。
  - `deathStopsTheMove`：燈守在 `slam` 前搖中死亡，之後不再造成傷害、不再位移。
  - `grabbedHunterLeavingReleases`：`hook` 鉤中後玩家被傳送走，招式照常結束、不把玩家拉回。
- [ ] **Step 2: Run** `.\build.ps1 runClientGameTest -PonlyTest=MistinnBossTest --console=plain` → FAIL（類別不存在）。
- [ ] **Step 3: Implement** 上述介面；`SnufferMoves` 宣告 `sweep`、`slam`、`hook`（數值照現有：前搖 16／20／14、傷害 7／6／8，部位規則照現有），保留跳砸的起跳與落地判定、鉤鎖拉人後 10 tick 打頭。客戶端依 `move` 播 `anim/keeper/<id>`，舊的 `slam_gather`／`slam_air`／`slam_land` 合成一段 `slam`（由 `MistinnArt/mod/take_keeper_moves.py` 從舊動畫接起來）。
- [ ] **Step 4: Run** `MistinnBossTest` 與 `MistinnClientGameTest` → PASS（後者改用 `strikeNow(level, player, "sweep")` 等新 API）。
- [ ] **Step 5: Commit** `refactor: 熄燈人改用共用的 boss 招式系統`。

### Task 2: 感知與玩家模型

**Files:** Create `boss/Observation.java`、`boss/PlayerTrace.java`、`boss/PlayerModel.java`；Test `MistinnBossTest`

**Interfaces:**
- Produces: `record Observation(long time, Vec3 pos, Vec3 velocity, float yaw, PlayerAction action, float stamina, boolean legsGone, double reach)`；`enum PlayerAction { IDLE, WALK, SPRINT, SWING, ROLL, TREAT }`（ROLL 用 `RollService.dodging`、TREAT 用 `TreatmentService.active`、SWING 用伺服器的揮擊狀態）。`final class PlayerTrace { static final int DELAY = 4; void record(Observation o); @Nullable Observation seen(long now); List<Observation> history(long now, int ticks); }`。`final class PlayerModel { Vec3 predict(PlayerTrace trace, long now, int ahead); void learn(String family, Response r); Map<Response, Double> responses(String family); double preferredDistance(); int counterDelay(); }`；`enum Response { ROLL_LEFT, ROLL_RIGHT, ROLL_BACK, BACK_OFF, COUNTER, STAY }`。先驗：每種反應各 1 次（拉普拉斯平滑）。

- [ ] **Step 1: Tests**：`seesFourTicksLate`（第 10 tick 記錄的狀態在第 13 tick 看不到、第 14 tick 看得到）；`predictsStraightRuns`（等速直線移動的玩家，預測 10 tick 後的位置誤差 < 0.3 格）；`learnsRollSide`（對 `sweep` 類別學 6 次 `ROLL_LEFT`、1 次 `ROLL_RIGHT` 後，左翻機率 > 0.6）。
- [ ] **Step 2: Run** → FAIL。 **Step 3: Implement.** **Step 4: Run** → PASS。
- [ ] **Step 5: Commit** `feat: boss 會觀察並預測追獵者`。

### Task 3: 推演器（打分＋最多 6 步）

**Files:** Create `boss/Planner.java`；Test `MistinnBossTest`

**Interfaces:**
- Consumes: `MoveSet`、`BossMove`、`PlayerModel`、`PlayerTrace`。
- Produces: `final class Planner { static final int MAX_DEPTH = 6; Planner(MoveSet set, Weights weights, int beam, int branch); Plan plan(Situation now, long budgetNanos); }`；`record Situation(Vec3 bossPos, float bossYaw, String phase, Map<String, Integer> cooldowns, @Nullable BossMove last, int lastTicks, Vec3 hunterPos, Vec3 hunterVelocity, PlayerAction hunterAction, Map<Response, Double> responses, @Nullable String signal, int signalTicksLeft)`；`record Plan(List<BossMove> steps, double score, int depth, long nanos)`；`record Weights(double hit, double head, double legs, double risk, double range, double corner, double pressure, double bait, double variety)`。預設 beam 6、每步分支上限 12。

- [ ] **Step 1: Tests**：`neverLooksPastSix`（任何情境 `plan.depth() <= 6`）；`staysInBudget`（1000 個隨機情境的平均 < 1 ms、最大 < 3 ms）；`picksWhatLands`（玩家站在前方 4 格不動時，第一步是打得到的攻擊）；`punishesTreatment`（玩家在 10 格外治療時，計畫在 2 步內接近並攻擊）；`variesItself`（同一情境連續規劃 20 次，同一招連續出現不超過 3 次）。
- [ ] **Step 2: Run** → FAIL。 **Step 3: Implement**（beam search；每步對預測的反應情境取期望值；冷卻與接續窗口限制可走的招）。 **Step 4: Run** → PASS。
- [ ] **Step 5: Commit** `feat: boss 會打分並往前推演六步`。

### Task 4: 假動作、延遲出手與誠實信號

**Files:** Modify `boss/Planner.java`、`boss/MoveRunner.java`、`boss/BossMove.java`；Test `MistinnBossTest`

**Interfaces:**
- Produces: `BossMove.imitates`（假動作模仿的真招 id，用同一個預兆音效與動作）；`MoveRunner.delay(int ticks)`（延遲出手：在前搖最後一刻停住）；`Situation.signal`／`signalTicksLeft`；`Planner` 的信號約束：有信號時第一個攻擊必須在 `families` 內含該信號類別。

- [ ] **Step 1: Tests**：`feintsNeverHurt`（假動作在轉成真招前不造成傷害）；`baitsEarlyRollers`（反應模型顯示玩家看到 `sweep` 預兆有 80% 提早翻滾時，計畫第一步是假橫掃的比例 > 50%）；`signalsAreHonest`（做了耍槍信號後 60 tick 內的下一個攻擊一定屬於 `thrust` 類）；`signalsAreNeverFaked`（沒有任何假動作的 `imitates` 指向信號）。
- [ ] **Step 2: Run** → FAIL。 **Step 3: Implement.** **Step 4: Run** → PASS。
- [ ] **Step 5: Commit** `feat: boss 會用假動作與延遲出手，信號誠實`。

### Task 5: 大腦接上熄燈人

**Files:** Create `boss/BossBrain.java`；Modify `keeper/Snuffer.java`；Test `MistinnBossTest`

**Interfaces:**
- Produces: `final class BossBrain { BossBrain(MoveSet set, Planner planner); void observe(ServerLevel level, Mob boss, double roomRadius); @Nullable BossMove decide(ServerLevel level, Mob boss, MoveRunner runner); @Nullable ServerPlayer target(); void forget(); static final int FIGHT_END = 600; }`。決策點：無招式在跑、接續窗口內、移動招式中每 5 tick。選目標：距離、最近 60 tick 誰打過牠、誰好下手（精力 < 30%、治療中、腿傷）。

- [ ] **Step 1: Tests**（以腳本控制的假玩家，`FakePlayer` 或真玩家搭配伺服器端移動）：`learnsALeftRoller`（每次看到橫掃預兆就往左翻的玩家，戰鬥 60 秒後，打左側的招式被選中的比例比前 20 秒高）；`ignoresNonHunters`（創造模式與觀察者不被當作目標）；`brainsAreIndependent`（兩隻燈守各自學習，互不影響）；`forgetsAfterTheFight`（燈室 600 tick 沒有活著的追獵者後，學習歸零）；`darkPhaseAndLampsStillWork`（半血進黑暗、四盞燈點亮硬直，照舊）。
- [ ] **Step 2: Run** → FAIL。 **Step 3: Implement**（`Snuffer.customServerAiStep` 交給 `BossBrain`＋`MoveRunner`；硬直與黑暗階段照舊；`choose(distance)` 刪除）。 **Step 4: Run** `MistinnBossTest`、`MistinnClientGameTest`、`MistinnStoryTest` → PASS。
- [ ] **Step 5: Commit** `feat: 熄燈人改由會思考的大腦指揮`；開 PR、自動合併、重建。

## 階段二（PR：`feat/snuffer-body`）

### Task 6: 更兇的模型形狀與待機動作

**Files:** Modify `client/keeper/SnufferModel.java`（新部位：`claw_*`、`chain`、裂開的燈籠與燃燒的眼）；Create `anim/keeper/idle_*.json`；Modify 現有 `anim/keeper/*.json`（重做以符合新骨架）；暫用以現有配色延伸的貼圖排版。

- 方向（spec §7）：更高、駝背前傾、手臂更長、爪狀手指、裂開的燈籠頭裡一隻燃燒的眼、鎖鏈與鉤、破爛長袍。保留部位名稱 `head`、`body`、`skirt`、`right_arm`、`left_arm`、`right_leg`、`left_leg`。
- 待機：不帶信號的 `idle_tilt`（歪頭）、`idle_pace`（踱步）、`idle_breathe`（喘息）。
- [ ] **Step 1: Test** `idlesPlayWhenWaiting`：沒有出手時，客戶端每隔一段時間播待機動畫之一。 **Step 2** FAIL **Step 3** 實作與動畫 **Step 4** PASS；拍每個姿勢的截圖給使用者。
- [ ] **Step 5: Commit** `style: 熄燈人換上更兇的身形與待機動作`；PR、合併、重建。

## 階段三（PR：`feat/snuffer-dash`）

### Task 7: 滑鏟與高速突襲

**Files:** Modify `keeper/SnufferMoves.java`、`boss/MoveRunner.java`（位移遇方塊即停、不出燈室半徑）；Create `anim/keeper/slide.json`、`rush.json`；音效 `slide_*.ogg`、`rush_*.ogg`。

- 滑鏟：`MOVEMENT`＋膠囊命中打雙腿，滑行約 6 格，前搖 ≥ 8。高速突襲：前搖 ≥ 14，0.4 秒內衝近約 8 格接突刺（`Capsule`，打軀幹）。
- [ ] **Step 1: Tests** `dashStopsAtWalls`（對著牆滑鏟與突襲，燈守停在牆前、不在方塊裡）、`slideTakesTheLegs`、`rushClosesTheGap`（10 格外的玩家在 1 秒內被打到）。 **Step 2** FAIL **Step 3** 實作 **Step 4** PASS
- [ ] **Step 5: Commit** `feat: 熄燈人學會滑鏟與高速突襲`；PR、合併、重建。

## 階段四（PR：`feat/snuffer-moveset`）

### Task 8: 近戰招式（spec 第 6 節 7～17）
### Task 9: 中遠距離招式（18～23）
### Task 10: 假動作（24～27）與信號待機（耍槍、甩燈籠、拉緊鎖鏈、壓低身體）
### Task 11: 黑暗階段招式（28～30）

每個 Task 的步驟相同：

- [ ] **Step 1: Tests**：該批每一招都通過 `everyMoveHasATell`；各自的命中測試（打到預期部位、打不到背後或範圍外）；Task 10 另有 `signalsAreHonest` 對四個信號各測一次；Task 11 另有 `mistAmbushHasATell`（霧中突襲出現前 ≥ 14 tick 有音效預兆）。
- [ ] **Step 2: Run** → FAIL。
- [ ] **Step 3: Implement**：在 `SnufferMoves` 宣告招式與接續連線；用 `MistinnArt/mod/take_keeper_moves.py` 產生動畫；用 `MistinnArt/mod/make_keeper_sounds.py` 產生預兆音效（CC0 與合成）並更新 `sounds.json`、字幕、`CREDITS.md`。
- [ ] **Step 4: Run** `MistinnBossTest` → PASS；拍該批招式的動作截圖。
- [ ] **Step 5: Commit**（每批一個提交），全部完成後 PR、合併、重建。

## 階段五（PR：`style/snuffer-texture`）

### Task 12: Codex 繪製新貼圖

- [ ] **Step 1:** 匯出新模型的 UV 展開圖與目前貼圖給 Codex（`MistinnArt/mod/snuffer_uv.png`）。
- [ ] **Step 2:** `codex exec -m gpt-6.2-sol -c model_reasoning_effort=xhigh -C MistinnArt/mod -s workspace-write -i snuffer_uv.png -i snuffer.png "…"`：畫 `snuffer.png`、`snuffer_glow.png`、`snuffer_glow_soul.png` 到 `MistinnArt/mod/snuffer_texture_draft/`。
- [ ] **Step 3:** 遊戲內截圖檢查（一般與黑暗階段），不對就請 Codex 修。
- [ ] **Step 4: Commit** `style: 熄燈人換上新貼圖`；PR、合併、重建。

### Task 13: 實機影片

**Files:** Create `MistinnBossReelTest.java`（`-Dmistinn.bossreel=true` 才執行）；倉庫外 `MistinnArt/mod/make_boss_reel.py`。

- 腳本控制的追獵者在燈室與熄燈人交手約 60 秒：展示信號待機、各類招式、假動作、滑鏟與突襲，以及對「總往左翻」的玩家變招；第二段進入黑暗階段。
- 逐格擷取 1280×720 畫面（拍攝模式加光影），用 ffmpeg 合成 30 fps 的 MP4，加上字幕條標示招式名稱。
- [ ] **Step 1:** 寫擷取測試並跑出畫面。 **Step 2:** 合成影片並自己看過一遍。 **Step 3:** 用 `SendUserFile` 傳給使用者。

---

## 自我檢查

- Spec 覆蓋：§1→Task 1、5；§2→Task 1、4、全部招式的 `everyMoveHasATell`；§3→Task 2、5；§4→Task 3；§5→Task 4；§5b→Task 4、10；§6→Task 1、7～11；§7→Task 6、12；§8→Task 7～11；§9→階段一～五；實機影片→Task 13；驗證→各 Task 的測試；存檔→Global Constraints。
- 型別一致：`BossMove`、`MoveSet`、`MoveRunner`、`PlayerTrace`、`PlayerModel`、`Planner`、`Situation`、`Plan`、`BossBrain` 的名稱與簽章在各 Task 一致。
