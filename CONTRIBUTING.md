<div align="center">

[EN](docs/CONTRIBUTING.en.md) | **繁中**

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/logo/mistinn-wordmark-light.png">
  <img src="assets/logo/mistinn-wordmark.png" alt="霧宿：追放" width="220">
</picture>

</div>

# 貢獻指南

本專案接受錯誤修復、功能、測試、美術與文件更新。一般貢獻使用 Fork 與 Pull Request；維護者可依已確認的變更範圍直接提交至 `main`。

## 變更範圍

局部修正、數值調整與文件更新可直接提交 PR。新增依賴、改變存檔格式（玩家資料、背包內容、方塊實體）、新增核心系統，以及改變既有玩法規則（死亡懲罰、品級顏色、格子規則）的變更，應先以 Issue 說明目的、相容性與存檔遷移方式，再進行實作。

## 使用 AI 工具

這個專案不排斥 AI。用 AI 工具協助撰寫、重構或找問題的貢獻，和手寫的一視同仁，不需要標註，也不會因此被拒絕。

條件只有一個：**Pull Request 必須沒有安全隱患才會合併。** 不論程式出自誰、用什麼工具寫，只要審查或 CI 發現問題，修好之前都不會通過。送出之前請確認：

- 你讀過並理解送出的每一行，審查時能說明它為什麼這樣寫。看不懂的程式不要送。
- 伺服器不信任客戶端：格子背包等操作，客戶端只送出請求，所有檢查都在伺服器端完成（參考 `GridService`）。新的網路封包必須在伺服器端檢查每個欄位的範圍。
- 不會產生物品複製：任何把物品移進或移出格子背包的程式，都要確認來源確實扣除了同樣的數量。
- 檔案路徑、網址與指令不使用未經檢查的輸入。
- 新增的依賴是真實存在、仍在維護、沒有已知高風險通報的套件。工具有時會給出不存在的套件名稱，安裝前請自己查證。
- **貼圖與模型必須是原創。** 不接受從其他遊戲、材質包、AI 圖像產生器或網路取得的美術。音效可用已查證的 CC0 錄音製作，使用的錄音與配樂逐檔記在 [CREDITS.md](CREDITS.md)。只提交成品（PNG、OGG、JSON），繪製用的原始檔（專案檔、產生腳本、分層圖）不要放進倉庫。

註解寫給之後維護的人看：說明程式為什麼這樣做，不要留下與工具的對話或修改過程。

## 開發環境

需要 JDK 25；Gradle 由專案內的 wrapper 下載。

```powershell
git clone https://github.com/keeiv/mistinn-exile.git
cd mistinn-exile
.\build.ps1 build
```

原始碼放在 OneDrive 或含中文的路徑時使用 `build.ps1`；其他路徑可以直接使用 `./gradlew build`。

## 程式碼與素材

- 類別、方法與變數使用清楚的英文命名；註解與文件字串一律用英文。
- 玩家看得到的文字放在 `assets/mistinn/lang`，`zh_tw.json` 與 `en_us.json` 都要有。裝備附兩行故事描述（`.lore.1`、`.lore.2`）。
- 伺服器邏輯放在 `src/main`，畫面與動畫放在 `src/client`；不要在共用程式碼引用客戶端類別。
- Mixin 只在找不到事件可用時使用，注入點盡量小，並寫明為什麼需要。
- 新物品要在 `Tiers` 給定品級、在 `ItemSizes` 給定佔格，並加進創造模式分頁。
- 品級顏色固定：N 鏽褐、I 灰、II 綠、III 藍、IV 紫、V 金、VI 紅。
- 畫風照原版物品：每個部件用自己最深的顏色描邊，光從左上方打，每種材質 4～5 個色階；手持模型使用原版的擠出式像素圖。

## 測試

```powershell
.\build.ps1 runClientGameTest
```

客戶端遊戲測試會開啟一個真正的遊戲視窗，跑完武器揮擊、金鐘歇息與重生、死亡與殘香、格子背包與品級，並把截圖存到 `build/run/clientGameTest/screenshots`。所有檢查都要通過；改到畫面時，請打開截圖親眼確認。

新功能請在測試裡加上可觀察的檢查（例如物品放進了哪一格、死亡後保留了什麼），而不是只截圖。

README 與官網的照片用拍攝模式在遊戲裡擺好再拍：

```powershell
.uild.ps1 runClientGameTest -PphotoMode
```

拍攝模式會加入 Sodium 與 Iris，以最高畫質把每個場景拍成 1920×1080，存到 `build/run/clientGameTest/photos`。光影包不隨倉庫提供，請自行把 Complementary Reimagined r5.9.3 放進 `build/run/clientGameTest/shaderpacks`，並在同一個執行資料夾的 `config/iris.properties` 啟用。

## 格式與品質檢查

提交前執行與 CI 一致的檢查：

```powershell
.\build.ps1 build
```

CI 在 Linux 上以 JDK 25 執行 `./gradlew build`。客戶端遊戲測試需要顯示裝置，目前在本機執行。

## 提交與 Pull Request

提交訊息與 PR 標題使用 Conventional Commits 前綴，搭配簡潔的繁體中文描述：

- `feat:` 新功能，例如 `feat: 新增第一位燈守熄燈人`
- `fix:` 錯誤修復，例如 `fix: 重生後回到金鐘旁而不是出生點`
- `docs:` 文件更新
- `style:` 格式調整
- `refactor:` 重構
- `test:` 測試更新
- `chore:` 雜項

PR 說明應包含問題、變更後的行為與驗證結果（跑過哪些測試、看過哪些截圖）。改變存檔格式時，補充舊存檔的相容與遷移方式；有相關 Issue 時附上連結。

只提交與變更相關的檔案。`build/`、`run/`、`.gradle/`、測試世界與任何金鑰都不得提交。

## 回報問題

請到[森之宿茶室官網](https://moriteahouse.shizune.life/account)回報（作品選「霧宿：追放」），附上 Minecraft 與模組版本、重現步驟，以及 `logs/latest.log`。

## 授權

目前版本使用 **AGPL-3.0-only**，作者為 **Keeiv**；完整條文見 [LICENSE](LICENSE)，著作權聲明見 [NOTICE](NOTICE)。新貢獻採用相同授權，並保留貢獻者的著作權聲明。

專案原創的貼圖、模型與合成音效採相同授權。使用的公眾領域錄音與配樂來源見 [CREDITS.md](CREDITS.md)。
