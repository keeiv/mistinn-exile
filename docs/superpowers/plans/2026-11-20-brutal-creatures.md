# 原版怪物野蠻化 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓原版怪物有真前搖、獵殺姿態、抽搐、雙人處決招式、骷髏爆頭、CC0 錄音合成的叫聲，並重做死亡畫面的心臟與「處決」字色。

**Architecture:** 伺服器決定前搖與處決的時機（`life.shizune.mistinn.enemy`），狀態用不存檔、同步給所有客戶端的附件傳給客戶端；客戶端以 `Clip` 關鍵影格動畫呈現雙方動作（`life.shizune.mistinn.client.enemy`）。聲音與心臟貼圖由倉庫外的 Python 腳本產生，倉庫只收成品。

**Tech Stack:** Java 25、Minecraft 26.3、Fabric Loader 0.20.1、Fabric API 0.166.2+26.3、Mixin；Python 3.11（numpy、scipy、soundfile、Pillow）。

**Spec:** `docs/superpowers/specs/2026-11-20-brutal-creatures-design.md`

## Global Constraints

- 伺服器權威：前搖、處決、命中只在伺服器決定；客戶端只呈現。
- 不改存檔格式：新附件一律不加 `.persistent(...)`。
- 伺服器邏輯在 `src/main`，畫面與動畫在 `src/client`；共用程式碼不得引用客戶端類別。
- Mixin 只在找不到事件時使用，注入點最小，類別註解寫明原因（英文）。
- 程式命名與註解用英文；玩家看得到的文字放 `assets/mistinn/lang`，`zh_tw.json` 與 `en_us.json` 都要有。
- 音效：32 kHz 單聲道 OGG Vorbis；來源逐項記在 `CREDITS.md`；原始錄音與產生腳本不進倉庫（腳本放 `MistinnArt/mod/`，素材在 `MistinnArt/cc0/`）。
- 貼圖原創，畫風照原版：每個部件用自己最深的顏色描邊、光從左上、每種材質 4～5 個色階。
- 提交作者只能是 `keeiv <234966047+keeiv@users.noreply.github.com>`，訊息不得有 Claude 署名；遠端流程：功能分支 → PR → CI 通過 → rebase 合併 → 刪分支。
- 前搖數值表、處決招式表以 spec 為準（下方任務會引用）。

## Review Focus

1. **處決中怪物或受害者斷線、換世界、被移除**：處決必須立即結束，受害者的移動鎖定屬性必須移除（否則重新登入後動不了）。→ Task 6 測試 `executionReleasesHoldOnLeave`。
2. **站位被牆或方塊擋住**（在走廊、洞穴被處決）：找不到空位時不發動，照一般攻擊處理，不可把怪物塞進牆裡。→ Task 6 測試 `blockedStandingSpotFallsBackToNormalHit`。
3. **多隻怪物同時圍攻倒地玩家**：只有一隻能處決；其他怪物的攻擊照常（可能先殺死玩家），不得同時開兩個處決。→ Task 6 測試 `secondAttackerCannotStartSecondExecution`。
4. **骷髏爆頭的箭被別人擋下**：箭命中擋路的玩家時，處決結束，受害者存活；擋箭的人照一般箭傷處理。→ Task 9 測試 `blockedHeadshotSavesVictim`。
5. **前搖中目標換成另一名玩家或目標死亡**：前搖中止，不得對新目標或屍體造成傷害。→ Task 2 測試 `windupCancelsWhenTargetDies`。

---

## 檔案結構

| 檔案 | 責任 |
| --- | --- |
| `src/main/java/life/shizune/mistinn/enemy/EnemyRig.java`（改） | 骨架族群；新增前搖長度與撲擊距離；移除 `Finisher` |
| `src/main/java/life/shizune/mistinn/enemy/WindupService.java`（新） | 前搖：攔截出手、計時、逼近、撲擊、命中 |
| `src/main/java/life/shizune/mistinn/mixin/MobWindupMixin.java`（新） | 攔截 `Mob.doHurtTarget`；前搖期間在 `serverAiStep` 尾端壓住移動 |
| `src/main/java/life/shizune/mistinn/enemy/ExecutionMove.java`（新） | 處決招式表：長度、致命 tick、站位間距、接觸點 |
| `src/main/java/life/shizune/mistinn/enemy/ExecutionService.java`（改） | 處決流程：站位、鎖定、接觸點傷勢、打斷規則、爆頭 |
| `src/main/java/life/shizune/mistinn/enemy/HuntingFlag.java`（新，視需要） | brain 型怪物的獵殺標記同步 |
| `src/main/java/life/shizune/mistinn/body/WoundService.java`（改） | 新增 `inflict` |
| `src/main/java/life/shizune/mistinn/registry/VanillaCreatureSounds.java`（改） | 前搖嘶吼與處決接觸音事件 |
| `src/client/java/life/shizune/mistinn/client/anim/Clip.java`（改） | 新增 `tracks(String group)` |
| `src/client/java/life/shizune/mistinn/client/anim/Poser.java`（改） | `root` 支援 `root.x`、`root.yaw` |
| `src/client/java/life/shizune/mistinn/client/enemy/ClipBones.java`（新） | 把 `bones.*` 與 `root.*` 軌道套到任意怪物模型 |
| `src/client/java/life/shizune/mistinn/client/enemy/EnemyMotion.java`（改） | 獵殺姿態、抽搐、前搖動畫、處決時改用招式動畫 |
| `src/client/java/life/shizune/mistinn/client/enemy/ExecutionView.java`（改） | 受害者動畫改用招式動畫、依招式取景 |
| `src/client/java/life/shizune/mistinn/client/enemy/HeadshotArrowLayer.java`（新） | 爆頭死亡後插在頭上的箭 |
| `src/client/java/life/shizune/mistinn/client/death/MissionEndScreen.java`（改） | 心臟依死因、赤紅色「處決」 |
| `src/main/resources/assets/mistinn/anim/enemy/*.json`（新） | 獵殺姿態、前搖動畫（每族群） |
| `src/main/resources/assets/mistinn/anim/execution/<move>/{striker,victim}.json`（新） | 九招雙人處決動畫 |
| `src/main/resources/assets/mistinn/textures/gui/death/{stabbed_heart,torn_heart}.png`（新） | 死亡畫面心臟；舊 `pierced_heart.png` 刪除 |
| `src/main/resources/assets/mistinn/sounds/...`、`sounds.json`、`assets/minecraft/sounds.json`（改） | 新叫聲、前搖嘶吼、處決接觸音 |
| `src/gametest/java/life/shizune/mistinn/test/TestFilter.java`（新） | 只跑指定測試 |
| `src/gametest/java/life/shizune/mistinn/test/MistinnCreatureTest.java`（新） | 前搖、獵殺姿態、抽搐、叫聲 |
| `src/gametest/java/life/shizune/mistinn/test/MistinnEnemyTest.java`（改） | 處決招式、爆頭、死亡畫面 |
| `MistinnArt/mod/make_creature_voices.py`、`make_death_hearts.py`（倉庫外） | 產生音效與貼圖 |

---

### Task 0: 只跑指定的客戶端測試

完整客戶端測試要 5 分鐘；之後每個任務的紅綠循環只跑自己的測試類別。

**Files:**
- Create: `src/gametest/java/life/shizune/mistinn/test/TestFilter.java`
- Modify: `build.gradle`（`-PonlyTest`）、每個測試類別 `runTest` 開頭

**Interfaces:**
- Produces: `TestFilter.skip(Class<?> test): boolean` — 系統屬性 `mistinn.only` 有值且不等於 `test.getSimpleName()` 時回傳 true。
- Produces: 指令 `.\build.ps1 runClientGameTest -PonlyTest=<ClassName>`

- [ ] **Step 1:** `build.gradle` 仿照 `animSheet` 區塊：`project.hasProperty("onlyTest")` 時對 `clientGameTest` 加 `vmArg "-Dmistinn.only=${project.onlyTest}"`。
- [ ] **Step 2:** 新增 `TestFilter.skip`，並在九個測試類別 `runTest` 第一行加 `if (TestFilter.skip(getClass())) return;`。
- [ ] **Step 3:** 驗證：`.\build.ps1 runClientGameTest -PonlyTest=MistinnRoomTest --console=plain`，log 只出現 Room 測試建立的世界，`BUILD SUCCESSFUL`，總時間明顯短於 5 分鐘。
- [ ] **Step 4:** Commit `test: 可只執行指定的客戶端測試`。

---

## A. 真前搖、獵殺姿態、抽搐

### Task 1: 族群的前搖數值

**Files:**
- Modify: `src/main/java/life/shizune/mistinn/enemy/EnemyRig.java`

**Interfaces:**
- Produces: `EnemyRig.windupTicks(): int`（0 表示沒有前搖）、`EnemyRig.lunge(): float`（格）。
- 本任務保留 `EnemyRig.Finisher`；Task 5 改用 `ExecutionMove.of(Mob)` 後才移除。
- 數值照 spec「各族群數值」表：UNDEAD 7/0.45、HUSK 8/0.45、DROWNED 7/0.45、SKELETON 6/0.40、PIGLIN 6/0.45、ILLAGER 6/0.45、ENDERMAN 5/0.50、CREAKING 8/0.40、WARDEN 10/0.60、SPIDER 5/0.55、VERMIN 3/0.30、RAVAGER 10/0.60、HOGLIN 8/0.60、QUADRUPED 6/0.50；其他族群 0/0。

- [ ] **Step 1:** 在 `MistinnCreatureTest`（新檔，含 Task 0 的 skip）寫 `rigsCarrySpecWindups`：對 `EntityTypes.ZOMBIE` 斷言 `EnemyRig.of(...).windupTicks() == 7`、`RAVAGER == 10`、`VEX`、`PHANTOM`、`BEE`、`SLIME`、`CREEPER` 為 0。
- [ ] **Step 2:** 跑 `-PonlyTest=MistinnCreatureTest`，預期編譯失敗（方法不存在）。
- [ ] **Step 3:** 在 enum 建構子加兩個欄位與 getter。
- [ ] **Step 4:** 再跑，PASS。
- [ ] **Step 5:** Commit `feat: 為怪物族群加上前搖數值`。

### Task 2: 伺服器前搖

**Files:**
- Create: `src/main/java/life/shizune/mistinn/enemy/WindupService.java`、`src/main/java/life/shizune/mistinn/mixin/MobWindupMixin.java`
- Modify: `src/main/resources/mistinn.mixins.json`、`src/main/java/life/shizune/mistinn/Mistinn.java`（`WindupService.init()`）、`src/main/java/life/shizune/mistinn/registry/VanillaCreatureSounds.java`
- Test: `MistinnCreatureTest`

**Interfaces:**
- Consumes: `EnemyRig.windupTicks()`、`EnemyRig.lunge()`、`BodyService.applies(Player)`、`ExecutionService.holdsMob(Mob)`。
- Produces:
  - `record WindupService.Windup(long start, int length, int target, boolean released)`，`STREAM_CODEC`；`AttachmentType<Windup> WindupService.WINDUP`（`syncWith(..., AttachmentSyncPredicate.all())`，不存檔，id `mistinn:windup`）。
  - `static boolean WindupService.intercept(Mob mob, ServerLevel level, Entity target)`：回傳 true 表示這次 `doHurtTarget` 已被前搖接管（呼叫端回傳 false）。
  - `static boolean WindupService.winding(Mob mob)`：前搖中（未出手）。
  - `static final int WindupService.FOLLOW_THROUGH = 5`、`CREEP = 0.30F`、`CHASE_CAP = 1.0F`。
  - `SoundEvent VanillaCreatureSounds.windup(EntityType<?>)`：每個有前搖的怪物一個 `mistinn:entity.<id>.windup`；骷髏類（skeleton、stray、bogged、parched、wither_skeleton）與 creaking 也註冊（音檔在 Task 11）。
- 行為（照 spec 第 1 節）：
  - `MobWindupMixin` 在 `Mob.doHurtTarget(ServerLevel, Entity)` HEAD：`WindupService.releasing` 為 true（出手時自己呼叫）就放行；否則 `intercept` 為 true 時 `cir.setReturnValue(false)`。
  - `MobWindupMixin` 在 `Mob.serverAiStep` TAIL：前搖中時 `getNavigation().stop()`，`zza = CREEP`、`xxa = 0`，`setSpeed(原速 × CREEP)`，`getLookControl().setLookAt(target)`，`setYRot` 轉向目標。
  - 出手（`elapsed == length`）：記下目標相對開始時的距離差 `retreat = clamp(dist(now) − dist(start), 0, CHASE_CAP)`；`mob.swing(MAIN_HAND)`；朝目標水平方向 `setDeltaMovement` 撲擊 `lunge + retreat`；命中：`mob.getAttackBoundingBox()` 沿撲擊方向 `expandTowards` `lunge + retreat` 後與 `target.getHitbox()` 相交、`hasLineOfSight`、同世界 → `releasing = true; mob.doHurtTarget(level, target); releasing = false`。
  - 出手後保留 `FOLLOW_THROUGH` tick 的 `released = true` 狀態，再移除附件。
  - 中止：怪物死亡或移除、`ExecutionService.holdsMob(mob)`、怪物成為乘客、劫毀獸 `getStunnedTick() > 0`、目標死亡／換世界／創造或旁觀。

- [ ] **Step 1: 寫失敗測試**（`MistinnCreatureTest`，平台與屍殼的建立方式比照 `MistinnEnemyTest.husk`，但保留 AI 與預設速度）：
  - `windupDelaysTheBlow`：玩家身體完好，屍殼自然出手；斷言 `WINDUP` 出現的那 tick 起連續 `8` tick 內玩家身體總健康值不變，第 `8` tick 之後 4 tick 內下降。
  - `rollDodgesTheRelease`：前搖開始後第 6 tick 由伺服器呼叫 `RollService` 的翻滾開始方法（實作者查現有測試如何觸發翻滾），斷言出手後 4 tick 內健康值不變。
  - `backingOffIsChased`：前搖開始後每 tick 由伺服器把玩家沿遠離方向移 0.18 格（`teleportTo`），斷言仍被打中。
  - `sprintingAwayEscapesRavager`：劫毀獸前搖開始後每 tick 移 0.30 格，斷言揮空。
  - `windupCancelsWhenTargetDies`：前搖中 `player.kill(...)`，斷言附件在 2 tick 內消失且怪物沒有呼叫成功的 `doHurtTarget`（`player.getLastDamageSource()` 不是該怪物的攻擊）。
- [ ] **Step 2:** 跑 `-PonlyTest=MistinnCreatureTest`，預期 `windupDelaysTheBlow` 失敗。
- [ ] **Step 3:** 實作上述介面與兩個 Mixin，註冊附件、tick（`ServerTickEvents.END_LEVEL_TICK`，追蹤前搖中的怪物用 `WeakHashMap<Mob, Boolean>` 或每世界集合）、播放 `windup` 音效（`SoundSource.HOSTILE`，音量 1.0，音高 0.9～1.1 隨機）。
- [ ] **Step 4:** 再跑，全部 PASS；另跑 `-PonlyTest=MistinnEnemyTest`，原本的實戰處決測試仍 PASS（處決由延後的那一擊觸發）。
- [ ] **Step 5:** Commit `feat: 原版怪物出手前有真前搖`。

### Task 3: 獵殺姿態、前搖動畫與抽搐（客戶端）

**Files:**
- Create: `src/client/java/life/shizune/mistinn/client/enemy/ClipBones.java`、`src/main/resources/assets/mistinn/anim/enemy/{hunt,windup}_<rig>.json`
- Modify: `Clip.java`、`EnemyMotion.java`、（視需要）`src/main/java/life/shizune/mistinn/enemy/HuntingFlag.java`
- Test: `MistinnCreatureTest`、截圖

**Interfaces:**
- Consumes: `WindupService.WINDUP`、`Mob.isAggressive()`。
- Produces:
  - `Clip.tracks(String group): Set<String>`：該群組的軌道名稱（不含群組前綴）。
  - `ClipBones.apply(Clip clip, float p, Model<?> model, float weight)`：`bones.<part>_x|_y|_z`（度）以 `weight` 加到同名 `ModelPart` 的旋轉；`root.pitch|roll|yaw`（度）、`root.x|y|z`（格，+y 向上、+z 朝前）套到模型根部件（y 與 z 轉成模型像素：`part.y -= y*16`、`part.z -= z*16`、`part.x += x*16`）。找不到的部件略過。
  - 動畫命名：`anim/enemy/hunt_<rig小寫>.json`、`anim/enemy/windup_<rig小寫>.json`；windup 動畫進度 0～`length/(length+5)` 為蓄力，其後為出手與收招。
  - `EnemyMotion.Sample` 新增 `float hunting`（0～1，8 tick 淡入淡出，由客戶端依 `isAggressive()` 或 `HuntingFlag` 平滑）、`float windup`（-1 表示無）、`boolean twitchy`（`entity.getType().is(EntityTypeTags.UNDEAD)` 且族群為人形或四足，或為 ENDERMAN、CREAKING）。
  - 抽搐：`static Twitch EnemyMotion.twitch(int entityId, float time, boolean hunting)` 決定論函式；以 `entityId` 與 `floor(time / 窗口)` 為種子；平常窗口 40～120 tick、獵殺時 20～40 tick；動作種類照 spec 第 4 節。
- 姿勢內容照 spec 第 2、3 節的表與描述；角度由實作者看截圖決定。

- [ ] **Step 1: 寫測試**：
  - `twitchIsDeterministic`：同一 `entityId` 與時間呼叫 `EnemyMotion.twitch` 兩次結果相同；10,000 tick 內平常狀態抽搐次數介於 10,000/120 與 10,000/40 之間。
  - `huntingSampleFollowsAggression`：生成屍殼並設 `setAggressive(true)`，等 10 tick，客戶端該實體的 render state `EnemyMotion.KEY` 的 `hunting() > 0.9`。
  - 截圖：每個族群各一隻，凍結（`setNoAi`）後分別拍閒置、獵殺、前搖蓄力 70%、出手第 2 tick 四張，檔名 `creature-<rig>-<state>`。
- [ ] **Step 2:** 跑，預期編譯失敗。
- [ ] **Step 3:** 實作 `Clip.tracks`、`ClipBones`、`EnemyMotion` 新欄位與套用順序：原版姿勢 → 呼吸／步態（現有）→ 獵殺（`hunt_*` × hunting）→ 抽搐 → 前搖（`windup_*`，前搖中權重 1）→ 受擊。處決中（`finishing >= 0`）跳過以上，交給 Task 7。
- [ ] **Step 4:** 寫各族群 `hunt_*`、`windup_*` 動畫；跑測試、看截圖對照圖，調到姿勢清楚且無明顯穿插。
- [ ] **Step 4b:** 骷髏類抽搐開始的那一 tick，在客戶端以 `level.playLocalSound` 於該骷髏位置播放現有的骷髏骨頭聲（`SoundEvents.SKELETON_STEP`，音量 0.35，音高 1.3～1.6），不需新素材。
- [ ] **Step 5:** brain 型怪物（豬布林、蠻兵、疣豬獸、殭屍疣豬獸、伏守者、嘎枝）若截圖顯示追擊時 `isAggressive()` 為 false，新增 `HuntingFlag`：伺服器每 10 tick 依 `Brain` 的 `ATTACK_TARGET` 記憶同步一個不存檔附件，客戶端 hunting 取兩者之一。
- [ ] **Step 6:** 再跑 PASS。Commit `feat: 原版怪物有獵殺姿態、前搖動畫與抽搐`。

---

## B. 雙人處決與骷髏爆頭

### Task 4: 傷勢與動畫工具

**Files:**
- Modify: `WoundService.java`、`Poser.java`

**Interfaces:**
- Produces: `static void WoundService.inflict(ServerPlayer player, BodyPart part, boolean fracture, boolean bleed)`：只把對應旗標設為 true，其他欄位不變，並同步。
- Produces: `Poser.root` 支援 `root.x`（格，向右為正）與 `root.yaw`（度），兩者缺少時為 0，現有動畫行為不變。

- [ ] **Step 1:** `MistinnEnemyTest` 加 `inflictMarksOnlyRequestedWound`：`inflict(player, RIGHT_ARM, true, false)` 後 `WoundService.get(player).get(RIGHT_ARM).fractured()` 為 true、`bleeding()` 為 false，其他部位 `any()` 為 false。
- [ ] **Step 2–4:** 跑失敗 → 實作 → PASS（另跑 `MistinnClientGameTest` 確認翻滾與死亡動畫截圖不變）。
- [ ] **Step 5:** Commit `refactor: 傷勢可直接標記，動畫根部支援橫移與轉向`。

### Task 5: 處決招式表

**Files:**
- Create: `src/main/java/life/shizune/mistinn/enemy/ExecutionMove.java`
- Modify: `EnemyRig.java`（移除 `Finisher`）、`ExecutionService.java`、`EnemyMotion.java`（移除舊 `finisher(...)`，暫以「處決中不套前搖與獵殺」代替，Task 7 補上招式動畫）

**Interfaces:**
- Produces:
  - `enum ExecutionMove { GRAPPLE_BITE, NECK_POUNCE, LEAP_SLAM, FIST_SLAM, CHARGE_MAUL, STOMP_STAB, SLASH, NECK_TWIST, HEADSHOT }`，欄位 `String id`（動畫資料夾名，小寫底線）、`int length`、`int kill`、`float gap`、`List<Beat> beats`、`boolean ranged`。
  - `record ExecutionMove.Beat(int tick, @Nullable BodyPart part, boolean fracture, boolean bleed, Hit sound)`；`enum ExecutionMove.Hit { BITE, SNAP, STAB, SLAM, TEAR }`。
  - `static @Nullable ExecutionMove ExecutionMove.of(Mob mob)`：依實體 id；骷髏、流髑、沼骸、枯骸手持弓（`mob.isHolding(Items.BOW)`）為 HEADSHOT，否則 SLASH。
  - `float spacing(Mob mob) = 0.3F + mob.getBbWidth() / 2 + gap`。
- 數值（tick；`HEADSHOT` 的 kill 由箭命中決定，`length` 為瞄準到放箭的 30）：

| 招式 | id | length | kill | gap | beats（tick 部位 傷 聲） |
| --- | --- | --- | --- | --- | --- |
| GRAPPLE_BITE | grapple_bite | 70 | 58 | 0.10 | 14 RIGHT_ARM 骨折 SNAP；34 HEAD 出血 BITE；48 HEAD 出血 TEAR |
| NECK_POUNCE | neck_pounce | 60 | 48 | 0.15 | 16 HEAD 出血 BITE；28 TORSO 出血 BITE；40 HEAD 出血 TEAR |
| LEAP_SLAM | leap_slam | 50 | 40 | 0.05 | 22 TORSO 骨折 SLAM |
| FIST_SLAM | fist_slam | 70 | 56 | 0.20 | 20 TORSO 骨折 SLAM；36 LEFT_ARM 骨折 SNAP；46 RIGHT_ARM 骨折 SNAP |
| CHARGE_MAUL | charge_maul | 60 | 46 | 0.30 | 20 TORSO 出血 TEAR |
| STOMP_STAB | stomp_stab | 60 | 40 | 0.25 | 24 TORSO 出血 STAB |
| SLASH | slash | 56 | 42 | 0.35 | 20 LEFT_ARM 出血 STAB；30 RIGHT_ARM 出血 STAB |
| NECK_TWIST | neck_twist | 50 | 36 | 0.15 | 12 LEFT_ARM 骨折 SNAP；12 RIGHT_ARM 骨折 SNAP |
| HEADSHOT | headshot | 30 | — | — | 無 |

- 對應怪物照 spec 第 6 節表。
- `ExecutionService.Sequence` 新增 `int move`（`ExecutionMove.ordinal()`），`IMPACT`／`DURATION` 常數改由招式提供。

- [ ] **Step 1:** 測試 `everyFinisherMobHasAMove`：spec 第 6 節表內每個怪物 `ExecutionMove.of(...)` 符合表；拿弓的骷髏為 HEADSHOT，空手骷髏與凋零骷髏為 SLASH；苦力怕為 null。
- [ ] **Step 2–4:** 跑失敗 → 實作（`ExecutionService` 暫時沿用舊流程，只是時間改讀 `move.kill()`）→ PASS；`MistinnEnemyTest` 原有段落仍 PASS。
- [ ] **Step 5:** Commit `refactor: 處決改由招式表決定時機`。

### Task 6: 處決的站位、鎖定、接觸點與打斷

**Files:**
- Modify: `ExecutionService.java`、`MobExecutionMixin.java`
- Test: `MistinnEnemyTest`

**Interfaces:**
- Consumes: `ExecutionMove`、`WoundService.inflict`、`VanillaCreatureSounds`（Task 11 的處決接觸音；本任務先用 `SoundEvents.PLAYER_ATTACK_CRIT` 等原版音代替，Task 11 換掉）。
- Produces:
  - 站位：`private static @Nullable Vec3 standingSpot(ServerPlayer victim, Mob mob, float spacing)`：從怪物指向的方向開始，依序試 0°、±30°、±60°、±90°，回傳第一個 `level.noCollision(mob, 移動後的 bbox)` 且腳下有方塊的位置；都不行回傳 null → `begins` 回傳 false（這一擊照一般攻擊）。
  - 開始時：怪物在 3 tick 內線性滑到站位（每 tick `snapTo`，面向受害者，`setDeltaMovement(ZERO)`）；受害者加屬性修正 `Attributes.MOVEMENT_SPEED` 與 `Attributes.JUMP_STRENGTH` 各 `-1.0 ADD_MULTIPLIED_TOTAL`，id `mistinn:execution_hold`。
  - 被抓住後每 tick 的中止條件只剩：任一方死亡或移除、換世界、受害者離線、受害者不再雙腿壞死（例如被治療）。不再檢查距離、視線、正前方。
  - 打斷：註冊 `ServerLivingEntityEvents.AFTER_DAMAGE`，受傷者是處決中的怪物、`source.getEntity()` 是**受害者以外**的 `Player` → 結束處決（受害者存活、傷勢保留）。開始條件的 `mob.hurtTime <= 0` 保留。
  - 接觸點：`elapsed == beat.tick()` 時 `inflict` 並在受害者位置播放對應聲音、`level.sendParticles`（BITE／TEAR／STAB 用紅色粒子 `DustParticleOptions(0x8A0A12, 1.2F)`，SNAP／SLAM 用 `ParticleTypes.CRIT`）。
  - 致命一擊：`elapsed == move.kill()` 時沿用現有 `strike`。
  - 結束（任何原因）一律移除受害者的屬性修正；`ServerPlayerEvents.LEAVE` 也移除。
- [ ] **Step 1: 寫測試**（每項用新生成的怪物，比照現有 `husk(...)`；怪物站在 1.4 格外）：
  - `executionSnapsStrikerToSpacing`：殭屍處決開始 4 tick 後，怪物與玩家的水平距離與 `GRAPPLE_BITE.spacing(mob)` 差距 < 0.05。
  - `executionLocksVictim`：開始後玩家的移動速度屬性值為 0；結束（死亡後重生）後恢復預設值。
  - `teammateInterruptKeepsWounds`：殭屍處決進行到 tick 50 時，用 Fabric API 的 `FakePlayer`（`net.fabricmc.fabric.api.entity.FakePlayer`）代表隊友，對怪物造成 1 點玩家攻擊傷害；斷言處決結束、玩家存活、`RIGHT_ARM` 骨折、`HEAD` 出血、移動鎖定已移除。
  - `victimCannotInterruptAlone`：處決中由受害者本人對怪物造成傷害（`mob.hurtServer(level, player.damageSources().playerAttack(player), 1)`），斷言處決仍在進行，最後由處決擊殺。
  - `blockedStandingSpotFallsBackToNormalHit`：玩家四周 1 格築牆只留怪物所在方向 0.5 格縫，觸發後斷言沒有 `SEQUENCE`、玩家受到一般傷害。
  - `secondAttackerCannotStartSecondExecution`：兩隻屍殼，第一隻開始處決後第二隻的攻擊不產生第二個 `SEQUENCE`（怪物端只有一隻帶 `striker`）。
  - `executionReleasesHoldOnLeave`：處決中呼叫 `ExecutionService` 的離線處理（直接觸發 `ServerPlayerEvents.LEAVE` 的處理方法），斷言屬性修正移除、怪物的 `SEQUENCE` 移除。
  - 九招各跑一次完整擊殺（HEADSHOT 在 Task 9），每招在 `kill × 0.25/0.5/0.75/1.0` 拍截圖 `execution-<id>-<n>`。
- [ ] **Step 2–4:** 跑失敗 → 實作 → PASS。
- [ ] **Step 5:** Commit `feat: 處決有固定站位、多段接觸傷勢，只能由隊友打斷`。

### Task 7: 九招雙人動畫與取景

**Files:**
- Create: `src/main/resources/assets/mistinn/anim/execution/<id>/striker.json`、`victim.json`（八招，HEADSHOT 在 Task 9）
- Modify: `EnemyMotion.java`（處決中：`ClipBones.apply(Clip.get("execution/" + id + "/striker"), elapsed / length, model, 1)`）、`ExecutionView.java`（`root`／`limbs` 改為 `Poser.root`／`Poser.limbs` 套 `victim` 動畫；`shot` 依動畫頂層數值 `camera_side`、`camera_height`、`camera_back`（格）取景，焦點為兩者中點）、`AvatarRollMixin`、`HumanoidRollMixin`（傳入招式）
- Test: Task 6 的截圖

**Interfaces:**
- Consumes: `ClipBones.apply`、`Poser.root/limbs`、`ExecutionService.Sequence.move()`、`ExecutionMove.values()`。
- Produces: `ExecutionView.Pose` 新增 `ExecutionMove move`。
- 動畫內容照 spec 第 6 節「動作」欄；接觸點的關鍵影格時間必須等於 `beat.tick / length`、致命一擊等於 `kill / length`，在動畫頂層加 `"length": <ticks>` 並以 `_comment` 寫明每個接觸點。
- 雙方的大位移（跳起、衝刺、甩飛、舉起）只寫在 `root` 軌道。

- [ ] **Step 1:** 加測試 `clipsMatchServerTimeline`（客戶端）：對每個非 HEADSHOT 招式，`Clip.get(...striker)` 與 `victim` 的 `number("length", 0)` 都等於 `move.length()`，且兩個動畫的關鍵影格集合都包含每個 `beat.tick/length` 與 `kill/length`（誤差 0.005）。
- [ ] **Step 2–3:** 跑失敗 → 寫八招的動畫檔與程式改動。
- [ ] **Step 4:** 跑 `-PonlyTest=MistinnEnemyTest`，PASS；用截圖做對照圖（每招一列四格），逐格檢查兩個模型不穿插、動作讀得出 spec 描述；不對就改動畫重跑。
- [ ] **Step 5:** 把對照圖用 `SendUserFile` 傳給使用者看。Commit `feat: 九種處決改成雙人招式動畫`。

### Task 8: 處決死亡的取景與死亡過場銜接

**Files:**
- Modify: `DeathScene.java`、`ExecutionView.java`

- [ ] **Step 1:** 測試延伸現有段落：每招擊殺後 `DeathScene.cause() == EXECUTION`，死亡過場前 0.67 秒仍看得到該怪物（`DeathScene.hides(striker) == false`），之後隱去；重生後鏡頭回到原本的視角。
- [ ] **Step 2–4:** 跑 → 修正 → PASS。
- [ ] **Step 5:** Commit `fix: 各處決招式銜接死亡過場與鏡頭`。

### Task 9: 骷髏爆頭

**Files:**
- Modify: `ExecutionService.java`（遠程開始條件與箭）、`EnemyMotion`／`ExecutionView`（HEADSHOT 動畫）
- Create: `anim/execution/headshot/{striker,victim}.json`、`src/client/java/life/shizune/mistinn/client/enemy/HeadshotArrowLayer.java`

**Interfaces:**
- 開始：`BodyService.hurt` 中，傷害來源 `source.is(DamageTypes.ARROW)`、直接實體為 `AbstractArrow`、`source.getEntity()` 為 `ExecutionMove.of(...) == HEADSHOT` 的怪物、受害者雙腿壞死 → 接管這一箭（`seize`，箭 `discard()`），開始 HEADSHOT；不做站位移動，只鎖定受害者。
- 瞄準 30 tick：骷髏 AI 暫停、轉向受害者頭部；每 tick 檢查視線，失去就中止。
- 放箭（tick 30）：伺服器生成一支 `Arrow`（擁有者為骷髏、`pickup = DISALLOWED`、無重力、速度 3.0 格/tick、朝受害者眼睛位置），標記 `mistinn.headshot` 並記在 `Sequence`。
- 箭命中受害者：在 `BodyService.hurt` 判斷是這支標記箭 → 以處決傷害擊殺（沿用 `strike`）。命中其他玩家：照一般箭傷，處決結束、受害者存活。箭 60 tick 內沒命中任何東西：處決結束。
- 死亡後頭上插箭：`HeadshotArrowLayer` 在受害者 `ExecutionView.Pose.move == HEADSHOT && completed` 時於頭部骨頭位置畫一支原版箭模型（以 Fabric `fabric-rendering-v1` 的生物渲染層註冊回呼加入；實作者確認 26.3 的回呼名稱）。

- [ ] **Step 1: 寫測試**：
  - `archerHeadshotsDownedHunter`：雙腿壞死的玩家，8 格外持弓骷髏（保留 AI）；斷言骷髏的箭命中後出現 HEADSHOT 的 `SEQUENCE`、瞄準期間玩家健康不變、放箭後 10 tick 內玩家死亡且死因為處決。
  - `strikingTheArcherSavesTheVictim`：瞄準中用 `FakePlayer` 打中骷髏，斷言處決結束、玩家存活。
  - `blockedHeadshotSavesVictim`：放箭那一刻在兩者之間放一個 `FakePlayer`（或盔甲座若無法以 FakePlayer 擋箭，實作者擇一），斷言受害者存活、處決結束。
  - `witherSkeletonSlashes`：凋零骷髏近戰觸發 SLASH。
  - 截圖：瞄準 50%、放箭、中箭、死亡過場頭上插箭。
- [ ] **Step 2–4:** 跑失敗 → 實作 → PASS。
- [ ] **Step 5:** Commit `feat: 骷髏用弓箭爆頭處決倒地的追獵者`。

---

## C. 叫聲（CC0 錄音合成）

### Task 10: 產生叫聲的腳本（倉庫外）

**Files:**
- Create: `MistinnArt/mod/make_creature_voices.py`（不進倉庫）

**Interfaces:**
- 用法：`python -I make_creature_voices.py <cc0資料夾> <MistinnExile 資料夾> [--only <creature>]`
- 讀 `MistinnArt/cc0/<id>/` 下所有 `.ogg`／`.wav`（含 `extracted/`），只取 spec 第 5 節列出的類別（以檔名判斷：monster、roar、grunt、growl、snarl、scream、breath、howl、hurt、die、death、zombie、moan、ghoul、bug、attack、eat、slime；排除 cute、burp、cough、barking、snore、misc、alien 以外經人工確認者）。
- 每個輸出由 2～3 段來源疊合；處理鏈：重取樣變調（族群表）、時間伸縮、`tanh` 飽和、低架濾波加厚、共鳴峰帶通、合成脈衝響應短殘響、淡入淡出；前搖嘶吼另加反轉吸氣前奏；響度對齊：輸出 RMS 等於同路徑舊檔的 RMS（新事件以同族群 `hurt` 的 RMS 為準）。
- 以固定亂數種子產生，結果可重現。
- 輸出覆寫 `src/main/resources/assets/mistinn/sounds/entity/vanilla/<creature>/<event>_<n>.ogg`（32 kHz、單聲道、Vorbis 品質 0.4）；新事件寫 `.../<creature>/windup_<n>.ogg` 與 `sounds/execution/<hit>_<n>.ogg`。
- 同時印出每個輸出用了哪些來源檔，供 `CREDITS.md`。

- [ ] **Step 1:** 先只做殭屍（`--only zombie`），輸出到暫存資料夾，拼成一段試聽檔 `zombie_preview.ogg`，用 `SendUserFile` 傳給使用者試聽；以使用者回饋調整處理鏈。
- [ ] **Step 2:** 腳本自檢：所有輸出可被 `soundfile` 讀回、取樣率 32000、單聲道、長度 0.2～4 秒、峰值 < 0.98、RMS 與目標差 < 1.5 dB。

### Task 11: 套用新叫聲、前搖嘶吼與處決接觸音

**Files:**
- Modify: `assets/minecraft/sounds.json`（取代的事件，版本數依實際檔案）、`assets/mistinn/sounds.json`（`entity.<id>.windup`、`execution.bite|snap|stab|slam|tear`）、`lang/zh_tw.json`、`lang/en_us.json`（新字幕）、`VanillaCreatureSounds.java`（`execution(Hit)`）、`ExecutionService.java`（接觸點改用新音）、`CREDITS.md`
- Test: `MistinnCreatureTest`

**Interfaces:**
- Produces: `SoundEvent VanillaCreatureSounds.execution(ExecutionMove.Hit hit)`。
- 取代範圍照 spec 第 5 節（含狼的 7 種 `growl` 與北極熊 `warning`，骷髏類、嘎枝、界伏蚌、旋風人、史萊姆類不換）。
- 字幕鍵：`subtitles.mistinn.entity.<id>.windup`（例：「殭屍蓄力撲來」／"Zombie lunges"）、`subtitles.mistinn.execution.<hit>`。

- [ ] **Step 1:** 測試 `everyVoiceEventResolves`（客戶端，比照 `MistinnClientGameTest` 第 1027 行附近的做法）：所有新事件與被取代事件在 `SoundManager` 都有聲音、每個聲音檔資源存在且能以遊戲的 OGG 解碼器開啟、每個新事件都有兩種語言字幕。
- [ ] **Step 2:** 跑腳本產生全部檔案；跑測試 PASS。
- [ ] **Step 3:** 更新 `CREDITS.md`：Sources 表加 8 個來源（id、作品、作者、授權、網址，照 spec 表）；「Vanilla creature voices」段落改寫，逐資料夾寫明來源 id 與處理方式，未換的資料夾維持 `synth`。
- [ ] **Step 4:** Commit `feat: 原版怪物叫聲改用 CC0 錄音合成，新增前搖嘶吼與處決音效`。

---

## D. 死亡畫面

### Task 12: 心臟與赤紅色「處決」

**Files:**
- Create: `MistinnArt/mod/make_death_hearts.py`（倉庫外）、`textures/gui/death/stabbed_heart.png`、`textures/gui/death/torn_heart.png`（48×48，與 `living_heart.png` 同尺寸）
- Delete: `textures/gui/death/pierced_heart.png`
- Modify: `MissionEndScreen.java`、`lang/*.json`

**Interfaces:**
- 心臟照 spec 第 8 節：以 `living_heart.png` 的輪廓與色階為參考重畫（不是直接複製改色），整體暗、偏灰；`stabbed_heart`：短刀從右上斜插入，刀刃一半沒入，傷口兩道血痕往下；`torn_heart`：同一顆心臟加兩處撕裂缺口與齒痕，血色更深，也插著同一把刀。
- `MissionEndScreen`：`HEART_SIZE = 48`；死因為 `EXECUTION` 用 `torn_heart`，其他用 `stabbed_heart`。
- 赤紅色：死因為 `EXECUTION` 時，死因行改由畫面自行組字：`Component.translatable("death.mistinn.cause.execution", 受害者, 攻擊者, Component.translatable("death.mistinn.executed").withColor(0xB3121F))`；受害者與攻擊者名稱取自原本死亡訊息 `TranslatableContents` 的參數。字串：`death.mistinn.cause.execution` = 「%1$s 被 %2$s %3$s了」／"%1$s was %3$s by %2$s"；`death.mistinn.executed` = 「處決」／"executed"。

- [ ] **Step 1:** 測試：`MistinnEnemyTest` 處決死亡後，`MissionEndScreen` 的死因元件中含有顏色 `0xB3121F` 的「處決」片段（遍歷 `Component` 的樣式）；拍 `mission-end-execution`。`MistinnClientGameTest` 一般死亡拍 `mission-end`。
- [ ] **Step 2:** 跑失敗 → 寫腳本產生兩張貼圖（放大 8 倍的預覽圖用 `SendUserFile` 傳給使用者）→ 改畫面 → PASS。
- [ ] **Step 3:** 看兩張截圖確認心臟清楚、字色正確。Commit `style: 死亡畫面改用插刀的心臟，處決二字改為赤紅色`。

---

## E. 收尾

### Task 13: 文件、完整測試、PR 與本機建置

**Files:**
- Modify: `README.md`、`docs/README.en.md`（功能一覽：原版怪物、處決、死亡畫面）、`docs/phase2-design.md`（處決規則）、spec（若數值有調整）

- [ ] **Step 1:** 更新雙語自述文件，照 `CONTRIBUTING.md` 的寫法。
- [ ] **Step 2:** `.\build.ps1 build runClientGameTest --console=plain`，全部 PASS；看所有新截圖。
- [ ] **Step 3:** 把分支上的提交整理成一個提交（`git reset --soft main` 後重新提交），訊息照專案格式：`feat: ` 標題 + 說明段落 + 條列；作者 `keeiv`，無 Claude 署名。
- [ ] **Step 4:** 推送 `feat/brutal-creatures`、開 PR（說明含問題、變更後行為、驗證、存檔相容：新附件皆不存檔）；CI 通過後 `gh pr merge --rebase --delete-branch`；本機 `main` 對齊遠端。
- [ ] **Step 5:** 重建 JAR，放進 `MistinnLauncher/src-tauri/resources/mods/` 與 `dist/`，跑啟動器測試並打包，核對免安裝版與安裝檔都內嵌這份 JAR，複製到 `dist/`。
