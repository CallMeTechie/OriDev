# Phase 13 Tier 4 — Premium Features Blueprint — 2026-04-15

> Architect blueprint for WifiLock (A), Bandwidth Throttling (B), Chunked Resumable
> Transfers (C), the Premium Paywall system (D), and the free-tier Ad Placement
> system (E). Read-only research pass confirmed all source facts cited below.
>
> **Binding design specs** (per CLAUDE.md memory "Mockups are binding 1:1 spec"):
> - `/root/OriDev/Mockups/paywall.html` — PaywallScreen layout, SKU tiles, CTA/Restore
> - `/root/OriDev/Mockups/ad-placements.html` — All six ad placements A–F + PremiumGate
>   styling for the locked throttle slider
>
> Both mockups are authored in the existing Ori:Dev design language (Inter, Indigo
> #6366F1, Gray50 bg, OriCard/OriButton primitives). Implementation must match pixel
> values, not reinterpret them.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :feature-premium  (new)                                                          │
│  PaywallScreen ─── PaywallViewModel ─── PurchaseUseCase / RestorePurchasesUseCase│
│  PremiumGate<T> composable (wraps any gated content with upsell card)            │
│  BandwidthThrottleSlider composable (used inside :feature-connections)           │
│  AdSlotHost composable ─── reads isPremium; delegates to :core-ads when free     │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                  │ depends on :domain, :core-billing, :core-ads, :core-ui
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :core-billing  (new)                                                             │
│  BillingClientLauncher (interface) ←── RealBillingClientLauncher (production)    │
│  FakeBillingClientLauncher (test double, in androidTest/test source sets)        │
│  BillingModule (Hilt)                                                            │
├──────────────────────────────────────────────────────────────────────────────────┤
│  :core-ads  (new)                                                                 │
│  AdLoader (interface) ←── AdMobAdLoader (production wraps GMA SDK)               │
│  FakeAdLoader (test double, seedable outcomes)                                   │
│  AdBannerView / AdNativeCardView / AdInterstitialHost — AndroidView wrappers     │
│  AdsModule (Hilt)                                                                │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                  │
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :domain  (extensions)                                                            │
│  PremiumEntitlement (interface)  PremiumFeatureKey (enum)                        │
│  BandwidthLimit (value class)    TransferChunk (model)                           │
│  PremiumRepository (interface)   TransferChunkRepository (interface)             │
│  AdSlot (enum)  AdRules (value object)  AdGate (interface)                       │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                  │
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :data  (implementations)                                                         │
│  PremiumRepositoryImpl ─ EncryptedSharedPreferences entitlement cache            │
│  TransferChunkRepositoryImpl ─ TransferChunkDao ─ transfer_chunks table          │
│  ServerProfileEntity (+maxBandwidthKbps column)  MIGRATION_3_4                   │
│  TransferChunkEntity (new)                                                       │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                  │
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :app/service  (engine layer additions)                                           │
│  TransferEngineService (+WifiLock acquire/release)                               │
│  TransferWorkerCoroutine (+chunk-mode branch + isPremium gate)                   │
│  SshTransferExecutor (+ThrottledInputStream/OutputStream wrapper)                │
│  FtpTransferExecutor (+ThrottledInputStream/OutputStream wrapper)                │
└──────────────────────────────────────────────────────────────────────────────────┘
                                  │
┌──────────────────────────────────────────────────────────────────────────────────┐
│  :core-network  (token-bucket throttle primitives)                               │
│  ThrottledInputStream  ThrottledOutputStream  TokenBucket                        │
└──────────────────────────────────────────────────────────────────────────────────┘
```

Feature modules `:feature-connections`, `:feature-transfers`, `:feature-filemanager`,
`:feature-settings` each depend on `:feature-premium` only via `:domain` interfaces —
they never import `:feature-premium` directly. `:feature-settings` references
`PremiumEntitlement` via `:domain` and navigates to PaywallScreen via the host
navigation graph in `:app`.

Ad placements are rendered via a shared `AdSlotHost(slot: AdSlot)` composable
exposed from `:feature-premium`. Feature modules drop `AdSlotHost(AdSlot.TRANSFER_QUEUE_INLINE)`
into their screens; `:feature-premium` internally checks `isPremium` and either
returns `Unit` (premium path) or forwards to `:core-ads`'s `AdBannerView` /
`AdNativeCardView`. This keeps both the billing and ad SDKs behind a single gate
with a single call site per placement.

---

## 2. Module + Package Layout

### New modules
```
:core-billing
  src/main/kotlin/dev/ori/core/billing/
    BillingClientLauncher.kt      — interface (mirrors BiometricPromptLauncher pattern)
    BillingPurchaseOutcome.kt     — sealed class
    RealBillingClientLauncher.kt  — wraps BillingClient v7
    BillingModule.kt              — Hilt bindings
  src/test/kotlin/dev/ori/core/billing/
    FakeBillingClientLauncher.kt  — test double

:core-ads
  src/main/kotlin/dev/ori/core/ads/
    AdLoader.kt                   — interface (load/show/destroy lifecycle)
    AdSlot.kt                     — re-export from :domain for GMA unit id mapping
    AdLoadResult.kt               — sealed: Loaded / Failed(code) / NoFill
    AdMobAdLoader.kt              — production: GMA SDK 23.x wrapper
    AdBannerView.kt               — @Composable wraps AndroidView<AdView>
    AdNativeCardView.kt           — @Composable wraps AndroidView<NativeAdView>
    AdInterstitialHost.kt         — side-effect composable; shows InterstitialAd
    AdsModule.kt                  — Hilt bindings; reads AdSlot → unit id map
  src/test/kotlin/dev/ori/core/ads/
    FakeAdLoader.kt               — test double

:feature-premium
  src/main/kotlin/dev/ori/feature/premium/
    navigation/PremiumNavigation.kt
    ui/PaywallScreen.kt
    ui/PaywallViewModel.kt
    ui/PaywallUiState.kt
    ui/PremiumGate.kt             — generic composable wrapper
    ui/PremiumUpsellCard.kt       — used by PremiumGate + Settings house ad
    ui/AdSlotHost.kt              — reads isPremium, routes to :core-ads or no-op
    ui/BandwidthThrottleSlider.kt — locked + unlocked states; mockup E
    di/PremiumModule.kt
```

### Module dependency rule check
`:feature-connections` adds `:domain` dep (already present) to read `BandwidthLimit`.
It navigates to `:feature-premium`'s paywall route via the root nav graph in `:app`
— no direct module import.

`AdSlotHost` is the one seam where features *do* import from `:feature-premium`.
This is acceptable because `:feature-premium` is explicitly the **premium + ads
surface layer** and sits above domain — it is a peer to other feature modules but
owns the cross-cutting premium concern. To keep the "feature modules cannot import
each other" rule technically intact, we treat `:feature-premium` as a **core-ish
feature module** and update `.github/ci/check-forbidden-imports.sh` with an
explicit allowlist: `feature-*` may import `dev.ori.feature.premium.ui.AdSlotHost`
and `dev.ori.feature.premium.ui.PremiumGate` only. All other `dev.ori.feature.premium.*`
imports from other feature modules fail the check.

---

## 3. File Plan

### New files

| Path | Rationale |
|---|---|
| `:core-billing/.../BillingClientLauncher.kt` | Testability seam; hides BillingClient from unit tests |
| `:core-billing/.../BillingPurchaseOutcome.kt` | Sealed result type returned to feature layer |
| `:core-billing/.../RealBillingClientLauncher.kt` | Production BillingClient v7 wiring |
| `:core-billing/.../BillingModule.kt` | Hilt: binds Real impl, provides BillingClient singleton |
| `:core-billing/.../FakeBillingClientLauncher.kt` | androidTest/test double |
| `:core-billing/build.gradle.kts` | Android library; deps: billing-ktx, hilt |
| `:feature-premium/.../PremiumNavigation.kt` | Route constants + NavGraph extension |
| `:feature-premium/.../PaywallScreen.kt` | Pricing UI; 3 SKU tiles + Restore link |
| `:feature-premium/.../PaywallViewModel.kt` | Hilt ViewModel; exposes PaywallUiState |
| `:feature-premium/.../PaywallUiState.kt` | Sealed Loading/Ready/Purchased/Error states |
| `:feature-premium/.../PremiumGate.kt` | `@Composable fun PremiumGate(key, isPremium, onUpgradeTap, content)` |
| `:feature-premium/.../PremiumModule.kt` | Hilt: binds PremiumRepository, use-cases |
| `:feature-premium/build.gradle.kts` | Android library; deps: core-billing, domain, core-ui |
| `:domain/.../model/BandwidthLimit.kt` | `@JvmInline value class BandwidthLimit(val kbps: Int?)` |
| `:domain/.../model/TransferChunk.kt` | Domain model for chunk (no Room imports) |
| `:domain/.../model/PremiumFeatureKey.kt` | `enum { BANDWIDTH_THROTTLE, CHUNKED_TRANSFER }` |
| `:domain/.../repository/PremiumRepository.kt` | Interface: isPremium Flow, entitlement cache ops |
| `:domain/.../repository/TransferChunkRepository.kt` | Interface: upsert/query chunk rows |
| `:domain/.../usecase/PurchaseUseCase.kt` | Single-purpose: invokes BillingClientLauncher |
| `:domain/.../usecase/RestorePurchasesUseCase.kt` | Calls billing restore; updates cache |
| `:domain/.../usecase/CheckPremiumUseCase.kt` | Returns `Flow<Boolean>` from PremiumRepository |
| `:data/.../entity/TransferChunkEntity.kt` | Room entity for transfer_chunks table |
| `:data/.../dao/TransferChunkDao.kt` | upsert, getByTransferId, deleteByTransferId |
| `:data/.../repository/PremiumRepositoryImpl.kt` | EncryptedSharedPreferences cache + billing refresh |
| `:data/.../repository/TransferChunkRepositoryImpl.kt` | Delegates to TransferChunkDao |
| `:core-network/.../throttle/TokenBucket.kt` | Pure-Kotlin token-bucket; no Guava |
| `:core-network/.../throttle/ThrottledInputStream.kt` | Wraps InputStream; blocks on bucket |
| `:core-network/.../throttle/ThrottledOutputStream.kt` | Wraps OutputStream; blocks on bucket |
| `:core-ads/.../AdLoader.kt` | Interface — testability seam for GMA SDK |
| `:core-ads/.../AdMobAdLoader.kt` | Production wrapper over `com.google.android.gms:play-services-ads` |
| `:core-ads/.../FakeAdLoader.kt` | Test double with seedable `nextResult` |
| `:core-ads/.../AdLoadResult.kt` | Sealed: `Loaded`/`Failed(code)`/`NoFill` |
| `:core-ads/.../AdBannerView.kt` | `AndroidView<AdView>` composable |
| `:core-ads/.../AdNativeCardView.kt` | `AndroidView<NativeAdView>` composable matching connection-row style |
| `:core-ads/.../AdsModule.kt` | Hilt: unit-id map per AdSlot; dev-keys via BuildConfig |
| `:core-ads/build.gradle.kts` | Library; deps: GMA SDK, hilt, compose |
| `:domain/.../model/AdSlot.kt` | Enum matching mockup placements A–F |
| `:domain/.../model/AdRules.kt` | Value object for frequency caps (`maxPerScreen`, `interstitialCooldownMs`) |
| `:domain/.../repository/AdGate.kt` | Interface `suspend fun shouldShow(slot): Boolean` with entitlement + cooldown logic |
| `:data/.../ads/AdGateImpl.kt` | Entitlement check + `SharedPreferences`-backed cooldown tracker |
| `:feature-premium/.../ui/AdSlotHost.kt` | Routes slot to banner/native/interstitial based on enum |
| `:feature-premium/.../ui/PremiumUpsellCard.kt` | House ad composable (mockup placement D) |
| `:feature-premium/.../ui/BandwidthThrottleSlider.kt` | Locked+unlocked slider states (mockup placement E) |

### Modified files

| Path | Change |
|---|---|
| `:domain/.../model/ServerProfile.kt` | +`val maxBandwidthKbps: Int? = null` |
| `:data/.../entity/ServerProfileEntity.kt` | +`val maxBandwidthKbps: Int? = null` |
| `:data/.../db/Migrations.kt` | Add `MIGRATION_3_4` |
| `:data/.../db/OriDevDatabase.kt` | version 3→4; add TransferChunkEntity; add TransferChunkDao accessor |
| `:app/.../service/TransferEngineService.kt` | +WifiLock acquire in `onCreate`, release in `onDestroy` |
| `:app/.../service/TransferWorkerCoroutine.kt` | +chunk-mode branch in `runTransfer`; inject `PremiumEntitlement` + `TransferChunkRepository` |
| `:app/.../service/TransferWorkerCoroutineFactory.kt` | +constructor params for chunk repo + premium entitlement |
| `:app/.../service/SshTransferExecutor.kt` | Wrap streams in `ThrottledInputStream`/`ThrottledOutputStream` when `maxBandwidthKbps != null && isPremium` |
| `:app/.../service/FtpTransferExecutor.kt` | Same throttle wrapping as SSH |
| `:app/.../service/TransferEngineModule.kt` | Bind new factory params |
| `app/src/main/AndroidManifest.xml` | +`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` |
| `:feature-connections/.../AddEditConnectionScreen.kt` | +BandwidthThrottleSlider section inside Advanced (mockup E); `PremiumGate` wraps it |
| `:feature-connections/.../AddEditConnectionViewModel.kt` | +`MaxBandwidthKbpsChanged` event; form state field |
| `:feature-connections/.../AddEditConnectionFormState.kt` | +`maxBandwidthKbps: Int?` |
| `:feature-connections/.../ConnectionListScreen.kt` | Insert `AdSlotHost(AdSlot.CONNECTION_LIST_NATIVE)` at list slot 3 (mockup B) |
| `:feature-transfers/.../TransferQueueScreen.kt` | Insert `AdSlotHost(AdSlot.TRANSFER_QUEUE_INLINE)` between active + queued sections (mockup A) |
| `:feature-filemanager/.../FileManagerScreen.kt` | Insert `AdSlotHost(AdSlot.FILE_MANAGER_STICKY)` as bottom sticky when column scrolled (mockup C) |
| `:feature-settings/.../sections/AccountPremiumSection.kt` | Replace placeholder row with live entitlement status + `PremiumUpsellCard` house-ad variant (mockup D) |
| `:feature-settings/.../ui/SettingsViewModel.kt` | Inject `CheckPremiumUseCase`; expose `isPremium` in state |
| `:feature-settings/.../ui/SettingsState.kt` | +`isPremium: Boolean` |
| `.github/ci/check-forbidden-imports.sh` | Add allowlist for `dev.ori.feature.premium.ui.{AdSlotHost,PremiumGate}` |
| `app/src/main/AndroidManifest.xml` | +GMA meta-data `APPLICATION_ID` + `com.google.android.gms.ads.AD_MANAGER_APP` |
| `gradle/libs.versions.toml` | +`play-services-ads = "23.6.0"` |
| Root `settings.gradle.kts` | +`:core-billing`, `:core-ads`, `:feature-premium` |

---

## 4. Domain Types

```kotlin
// :domain/model/BandwidthLimit.kt
@JvmInline value class BandwidthLimit(val kbps: Int?) {
    val isUnlimited get() = kbps == null || kbps == 0
    companion object {
        val UNLIMITED = BandwidthLimit(null)
        val PRESETS = listOf(64, 128, 256, 512, 1024, 2048, 5120, 10240)
    }
}

// :domain/model/PremiumFeatureKey.kt
enum class PremiumFeatureKey { BANDWIDTH_THROTTLE, CHUNKED_TRANSFER }

// :domain/model/TransferChunk.kt
data class TransferChunk(
    val id: Long = 0,
    val transferId: Long,
    val index: Int,
    val offsetBytes: Long,
    val lengthBytes: Long,
    val sha256Expected: String?,
    val status: ChunkStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
)
enum class ChunkStatus { PENDING, ACTIVE, COMPLETED, FAILED }

// :domain/repository/PremiumRepository.kt
interface PremiumRepository {
    val isPremium: Flow<Boolean>
    suspend fun refreshEntitlement()
    suspend fun cacheEntitlement(value: Boolean)
    suspend fun getCachedEntitlement(): Boolean
    suspend fun getLastRefreshedAt(): Long?
}

// :domain/repository/TransferChunkRepository.kt
interface TransferChunkRepository {
    suspend fun upsertChunk(chunk: TransferChunk): Long
    suspend fun getChunksForTransfer(transferId: Long): List<TransferChunk>
    suspend fun updateChunkStatus(id: Long, status: ChunkStatus, error: String?)
    suspend fun deleteChunksForTransfer(transferId: Long)
}

// :domain/model/AdSlot.kt
enum class AdSlot {
    TRANSFER_QUEUE_INLINE,    // mockup A — adaptive banner
    CONNECTION_LIST_NATIVE,   // mockup B — native card at slot 3
    FILE_MANAGER_STICKY,      // mockup C — bottom sticky banner
    SETTINGS_HOUSE_UPSELL,    // mockup D — first-party house ad
    // Placement E (locked throttle slider) is a PremiumGate, not an ad — no slot
    // Placement F (post-transfer interstitial) deferred to Phase 14 — see Q13
}

// :domain/model/AdRules.kt
data class AdRules(
    val maxBannersPerScreen: Int = 1,
    val houseAdDismissedForMs: Long = 7 * 24 * 60 * 60 * 1000L,  // 7 days
)

// :domain/repository/AdGate.kt
interface AdGate {
    /** Returns false when isPremium=true, cooldown active, or slot temporarily disabled. */
    suspend fun shouldShow(slot: AdSlot): Boolean
    /** Record that an ad was shown for frequency capping. */
    suspend fun recordShown(slot: AdSlot)
    /** Record user dismissal (7-day suppression for house ad). */
    suspend fun recordDismissed(slot: AdSlot)
}
```

SKUs (matching `Mockups/paywall.html` prices):
- `oridev_premium_monthly` — €4.99/month subscription
- `oridev_premium_yearly` — €29.99/year subscription (default-selected, "Most popular")
- `oridev_premium_lifetime` — €59.99 one-time (IN_APP)

Note: pricing is set in Play Console, not hardcoded — the mockup strings are the
**display target**, real values come from `BillingClient.queryProductDetails()` at
runtime. The paywall screen must render whatever Play returns; €-symbols in the
mockup are a reference for German locale formatting.

---

## 5. Paywall + Gate Design

### PremiumGate composable contract
```kotlin
@Composable
fun PremiumGate(
    featureKey: PremiumFeatureKey,
    isPremium: Boolean,
    onUpgradeTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isPremium) content()
    else PremiumUpsellCard(featureKey = featureKey, onUpgradeTap = onUpgradeTap)
}
```

`PremiumUpsellCard`: `OriCard` with `PremiumGold` accent border (1 dp), Lucide `Crown`
icon (20 dp, PremiumGold tint), headline "Unlock with Premium", sub-body keyed by
`featureKey`, full-width `OriButton` "Upgrade to Premium" in Indigo500 fill.

For the throttle slider, a disabled `Slider` is rendered behind a semi-opaque scrim,
with `PremiumBadge` (already exists at
`:feature-settings/.../components/PremiumBadge.kt`) overlaid top-right. Tapping the
locked area calls `onUpgradeTap`.

### PaywallScreen layout — bound to `Mockups/paywall.html`

**Structure (top → bottom):**
1. `OriTopBar` with back arrow + title "Ori:Dev Premium" + right-side "Ori:Dev" logo
2. Hero: 64 dp rounded crown icon (gradient `#FEF3C7 → #FDE68A`, border
   `#F59E0B`, shadow `rgba(245,158,11,0.2)`), 32 sp bold title with "Ori:Dev Premium"
   gradient (`#6366F1 → #8B5CF6`), 15 sp subtitle
3. Feature list: `OriCard` with 4 rows, each row = 32 dp icon in `accent-subtle`
   container + title + description. Icons (Lucide vendored):
   - `FileText` — Chunked resumable transfers
   - `Zap` — Per-connection bandwidth throttle
   - `BanSign`/`XCircle` — Completely ad-free
   - `Heart` — Support indie development
4. "Choose your plan" section label (11 sp uppercase, tracking 0.8)
5. Three SKU tiles (mobile: stacked; unfolded ≥900 dp: 3-column grid):
   - Unselected: 1.5 dp `#E5E7EB` border, white bg
   - Selected: 1.5 dp Indigo500 border, `#EEF2FF` bg, 3 dp `accent-subtle` outer glow
   - "Most popular" ribbon: Indigo500 pill, 10 sp uppercase, positioned at
     `top: -9dp, left: 20dp`
   - Left block: name (15 sp semibold) + blurb (12.5 sp tertiary)
   - Right block: price (18 sp bold tabular-nums) + period (11.5 sp tertiary)
     + optional "Save 50%" pill (success green, 10.5 sp)
6. Primary CTA: full-width 52 dp `OriButton` in Indigo500 with leading `Zap` icon;
   shadow `0 4 16 rgba(99,102,241,0.25)` → hover lift `translateY(-1)`
7. CTA meta: 11.5 sp muted text "Auto-renews. Cancel anytime in Google Play."
8. Restore link: centered `TextButton` "Restore purchases", 13 sp Indigo500
9. Trust row: 3 items ("Secure via Google Play", "Offline-aware", "Works on Wear OS")
   each with 14 dp success-green check icon, 11.5 sp tertiary text, separated by
   top border

**Default selected SKU:** Yearly (mockup paywall.html:508). This drives the best LTV
and is marked "Most popular". User can tap the other tiles to change selection.

**UI States → mockup mapping:**
- `Loading`: replace SKU grid with full-screen `OriLoadingIndicator`
- `Ready(skus, selected)`: mockup.html as-is
- `Purchasing`: CTA button in loading state (keep structure, swap label for spinner)
- `Purchased`: auto-navigate back + emit one-shot `SnackbarEvent.Success`
- `Error(message)`: `OriMiniBar` (red) above CTA; dismissible; rest of UI interactive

### Flows
- Billing error → `PaywallUiState.Error(message)` → dismissible `OriMiniBar`
- Purchase success → `PaywallUiState.Purchased` → auto-navigate back; `cacheEntitlement(true)`
- Restore → `RestorePurchasesUseCase` → `cacheEntitlement(true)` + snackbar "Purchases restored"

---

## 6. Billing Testability Seam

Mirrors `BiometricPromptLauncher` at
`core-security/.../biometric/BiometricPromptLauncher.kt`.

```kotlin
interface BillingClientLauncher {
    suspend fun queryProductDetails(skus: List<String>): List<ProductDetails>
    suspend fun launchPurchaseFlow(activity: Activity, details: ProductDetails): BillingPurchaseOutcome
    suspend fun queryPurchases(productType: String): List<Purchase>
    suspend fun acknowledgePurchase(token: String): BillingPurchaseOutcome
}

sealed class BillingPurchaseOutcome {
    object Success : BillingPurchaseOutcome()
    data class Pending(val orderId: String?) : BillingPurchaseOutcome()
    data class Error(val code: Int, val message: String) : BillingPurchaseOutcome()
    object UserCancelled : BillingPurchaseOutcome()
}
```

`RealBillingClientLauncher` wraps `BillingClient` v7 (already in
`libs.versions.toml` at `billing = "7.1.1"`). Bridges the async `PurchasesUpdatedListener`
callback to `suspendCancellableCoroutine` the same way `RealBiometricPromptLauncher`
bridges `AuthenticationCallback`.

`FakeBillingClientLauncher` in `src/test`: holds `var nextOutcome` set before each act.

Play Billing classes confined to `:core-billing/src/main` — zero leakage into
`:domain`, `:data`, or `:feature-premium` test sources.

---

## 7. Entitlement Caching

Storage: `EncryptedSharedPreferences` keyed `"premium_entitlement"` (Boolean) +
`"premium_last_refreshed_at"` (Long epoch ms). Same security-crypto library used by
`CredentialStore` in `:core-security`.

| Situation | Behaviour |
|---|---|
| Cold-start online | Read cache for instant gate; background `refreshEntitlement()` on `Application.onCreate`. UI reflects live value within ~2 s. |
| Cold-start offline | Use cache. Age ≤ 72 h → grant. Older → revoke, show "Verify subscription" banner. |
| Subscription cancellation | Play reports on next `queryPurchases()`. `refreshEntitlement()` on next `onResume` flips cache. |
| Purchase success | Immediate `cacheEntitlement(true)` in `PurchaseUseCase`. |
| Lifetime purchase | `queryPurchases(IN_APP)` returns active token permanently. |

**Refresh trigger points (Q7):**
1. `OriDevApplication.onCreate()` — background launch, non-blocking
2. `MainActivity.onResume()` — throttled to once per 5 min
3. Explicit "Restore purchases" — always runs

**Grace period:** 72 h (Q3).

---

## 8. Throttling Implementation

### Token-bucket math
```
TokenBucket(
    capacityBytes = maxBandwidthKbps * 1024 * REFILL_PERIOD_MS / 1000,
    refillRateBytes = maxBandwidthKbps * 1024,   // per second
)
```

Per-second wall-clock refill (Q6). Bucket accumulates
`(elapsed_ms / 1000.0) * refillRateBytes` tokens. On `read()`/`write()` of N bytes,
grant `min(N, available)`; if insufficient, coroutine-suspend via
`delay(((N - available) / refillRateBytes) * 1000)`.

### Wrapper placement

**SshTransferExecutor:** after resolving the SSHJ stream handle, wrap raw
`InputStream`/`OutputStream` before the progress-pumping loop. The `onProgress` lambda
fires after each throttled chunk — progress reflects actual throughput, not requested.

**FtpTransferExecutor:** `FtpClient.uploadFileResumableDedicated` owns the stream
internally. New overload `...Throttled(...)` takes a `TokenBucket?` and wraps the
`FTPClient` data stream.

**BandwidthLimit resolution:** FTP executor already resolves `ServerProfile` at
`FtpTransferExecutor.kt:105`. SSH executor currently resolves only a session id —
needs `connectionRepository.getProfileById(profileId)` addition (red flag, see below).

Progress tick rate `PROGRESS_THROTTLE_MS = 500L` unchanged: at 64 kbps that window
passes 4 KB — still a meaningful update.

---

## 8a. Ad Integration (Placements A–F)

### Scope note
**Placement F (post-completion interstitial) is deferred to Phase 14** per Q13 decision.
The mockup at `ad-placements.html` placement F remains as reference for the future
implementation. V1 ships with 4 soft placements (A, B, C, D) + the PremiumGate (E).

### Ad network choice
**Google Mobile Ads (GMA) SDK 23.x** via `play-services-ads`. Rationale:
- First-party Google SDK, guaranteed Play Store policy compliance
- Already trusted by Android developers; established reporting + fill-rate data
- Native Compose integration via `AndroidView` wrappers
- Free tier + no SDK size premium over alternatives
- Revenue-share fair on indie scale

Alternatives rejected: AppLovin (opaque rev-share on low volume), ironSource
(over-instrumented for a single-dev indie app), Unity Ads (gaming-focused).

### Placement → AdSlot → GMA unit-id mapping

| Mockup | AdSlot enum | GMA format | Cooldown | Rules |
|---|---|---|---|---|
| A | `TRANSFER_QUEUE_INLINE` | Adaptive Banner | None (persistent) | 1 per screen; hidden during conflict modal |
| B | `CONNECTION_LIST_NATIVE` | Native (custom) | None | slot 3 only, never first/last, hidden during add/edit |
| C | `FILE_MANAGER_STICKY` | Adaptive Banner | None | sticks to scroll container bottom; hidden during multi-select + DnD |
| D | `SETTINGS_HOUSE_UPSELL` | House / first-party | 7d dismissal window | not counted against ad-per-screen cap |
| E | *PremiumGate, not ad* | — | — | locked `OriSlider` with scrim + `PremiumBadge` |
| F | *Deferred to Phase 14* | — | — | — |

Unit IDs are configured via `AdsModule` from `BuildConfig.AD_UNIT_*` fields. Debug
builds use the Google test unit IDs (`ca-app-pub-3940256099942544/...`) so the dev
flow never risks invalidating real ad traffic.

### AdLoader testability seam

Mirrors `BillingClientLauncher` + `BiometricPromptLauncher` patterns:

```kotlin
interface AdLoader {
    suspend fun loadBanner(slot: AdSlot): AdLoadResult
    suspend fun loadNative(slot: AdSlot): AdLoadResult
    fun destroy(slot: AdSlot)
}

sealed class AdLoadResult {
    data class Loaded(val handle: Any) : AdLoadResult()
    data class Failed(val code: Int, val message: String) : AdLoadResult()
    object NoFill : AdLoadResult()
}
```

`AdMobAdLoader` implementation uses GMA v23 suspend bridges (`loadAd()` →
`suspendCancellableCoroutine`). `handle` is `Any` to keep GMA classes out of
`:domain` and test sources; the Compose `AdBannerView` downcasts to `AdView`
before wiring into `AndroidView`. Zero GMA imports leak to `:domain`,
`:feature-premium`, or any feature module.

### AdGate logic

Single entry point for "should this slot render right now":

```kotlin
class AdGateImpl @Inject constructor(
    private val premiumRepo: PremiumRepository,
    private val adPrefs: AdPreferences,       // DataStore-backed cooldown cache
    private val rules: AdRules,
) : AdGate {
    override suspend fun shouldShow(slot: AdSlot): Boolean {
        if (premiumRepo.getCachedEntitlement()) return false
        return when (slot) {
            AdSlot.SETTINGS_HOUSE_UPSELL ->
                adPrefs.msSince(slot) >= rules.houseAdDismissedForMs || !adPrefs.isDismissed(slot)
            else -> true
        }
    }
}
```

### AdSlotHost composable (feature-premium side)

```kotlin
@Composable
fun AdSlotHost(
    slot: AdSlot,
    modifier: Modifier = Modifier,
) {
    val vm: AdSlotHostViewModel = hiltViewModel(key = slot.name)
    val state by vm.state.collectAsStateWithLifecycle()
    when (state) {
        AdSlotHostState.Hidden -> Unit  // premium path = no composition cost
        is AdSlotHostState.Banner -> AdBannerView(handle = (state as ...).handle, modifier)
        is AdSlotHostState.Native -> AdNativeCardView(handle = ..., modifier)
        is AdSlotHostState.House -> PremiumUpsellCard(onUpgradeTap = ..., modifier)
        AdSlotHostState.Loading -> Spacer(modifier)
    }
}
```

**Deferred state:** `AdSlotHostState.Interstitial` is not in the v1 sealed class.
When Phase 14 adds placement F, a new state variant will be added without touching
existing call sites — the compiler enforces exhaustiveness.

`isPremium` is collected via `CheckPremiumUseCase` inside
`AdSlotHostViewModel.init`, so when a user purchases, all ad slots across the
app flip to `Hidden` within one recomposition cycle.

### Privacy + compliance

- **UMP consent** (User Messaging Platform): GMA SDK 23 requires
  `ConsentInformation.requestConsentInfoUpdate()` at `Application.onCreate`.
  Runs before any ad load. Non-consenting users see non-personalized ads (NPA).
- **GDPR / CCPA**: UMP handles both. No extra code required.
- **Families policy**: Ori:Dev is not a kids app; no additional restrictions.
- **Ad testing**: add test device IDs to `RequestConfiguration.Builder` keyed off
  `BuildConfig.DEBUG` so dev builds always get test ads regardless of unit ID.

### Pixel budget (binding to `ad-placements.html`)

- Banner (A, C): 50 dp height baseline, adaptive to 320–728 width
- Native (B): mirrors `conn-row` padding `12 dp` outer, `14 dp` inner for ad content
- House (D): 16 dp padding, 36 dp crown icon, gradient `#EEF2FF → #EDE9FE`,
  1 dp `#EEF2FF` border, decorative radial gradient offset `-40 -40` with `120 dp`
  radius (pure CSS/draw, no bitmap)
- Locked slider (E): 14 dp inner padding, 4 dp track, 14 dp thumb, scrim at
  `rgba(255,255,255,0.55)` with `1px backdrop-blur`
- *Interstitial (F): deferred — pixel values in mockup remain as Phase 14 reference*

Every value above is grep-able in `ad-placements.html` as the source of truth.

---

## 9. Chunked Transfer Schema + State Machine

### transfer_chunks table
```sql
CREATE TABLE transfer_chunks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    transferId   INTEGER NOT NULL REFERENCES transfer_records(id) ON DELETE CASCADE,
    chunkIndex   INTEGER NOT NULL,
    offsetBytes  INTEGER NOT NULL,
    lengthBytes  INTEGER NOT NULL,
    sha256       TEXT,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER NOT NULL DEFAULT 0,
    lastError    TEXT,
    UNIQUE(transferId, chunkIndex)
);
CREATE INDEX idx_chunks_transfer_status ON transfer_chunks(transferId, status);
```

### State machine per chunk
```
PENDING → ACTIVE → COMPLETED
                 ↘ FAILED (attempts >= maxRetryAttempts)
         ↗ (retry: ACTIVE, attempts++)
```

### TransferWorkerCoroutine chunk-mode branch

```kotlin
if (transfer.totalBytes >= CHUNK_THRESHOLD_BYTES && isPremium) {
    runChunkedTransfer(transfer)
} else {
    runSingleShotTransfer(transfer)
}
```

`runChunkedTransfer`:
1. `ceil(totalBytes / CHUNK_SIZE_BYTES)` chunks; last = remainder.
2. `chunkRepo.getChunksForTransfer(transferId)`; if empty, seed PENDING rows.
3. Iterate non-COMPLETED in index order:
   a. ACTIVE; call executor's `(offsetBytes, lengthBytes)` overload.
   b. Success: `updateChunkStatus(COMPLETED)`.
   c. Failure: `attempts++`; `< maxRetryAttempts` → PENDING again; else FAILED → throw.
4. All COMPLETED → `updateProgress(totalBytes, totalBytes)` → COMPLETED.
5. CancellationException → mark in-progress chunk PENDING (resumes next run).

SHA-256 best-effort: SFTP via `SFTPClient.checkFileContents`, FTP skipped.

```kotlin
const val CHUNK_SIZE_BYTES: Long = 64 * 1024 * 1024   // Q4
const val CHUNK_THRESHOLD_BYTES: Long = 256 * 1024 * 1024  // Q5
```

---

## 10. WifiLock Lifecycle

`TransferEngineService` acquires `WifiManager.WifiLock(WIFI_MODE_FULL_HIGH_PERF)` in
`onCreate()` (tag `"oridev:transfers"`) and releases in `onDestroy()`.

Not keyed to active-count: service stops via `stopSelf()` when
`observeNonTerminalCount() == 0` (`TransferEngineService.kt:73–75`). Lock naturally
spans the active-transfer window. Reacting to inner collector would race between
`count==0` and the next enqueue.

`WAKE_LOCK` already in manifest line 11. Add `ACCESS_WIFI_STATE` + `CHANGE_WIFI_STATE`.
`WIFI_MODE_FULL_HIGH_PERF` deprecated at API 34 but functional; fall back to
`WIFI_MODE_FULL_LOW_LATENCY` via `Build.VERSION.SDK_INT` guard.

`WifiManager` via `applicationContext.getSystemService(WIFI_SERVICE)` in `onCreate()`.
Lock ref: nullable field; release guard: `if (wifiLock?.isHeld == true) wifiLock.release()`.

---

## 11. Persistence Schema

### Current schema (v3)
**server_profiles:** id, name, host, port, protocol, username, authMethod, credentialRef,
sshKeyType, startupCommand, projectDirectory, claudeCodeModel, claudeMdPath, isFavorite,
lastConnected, createdAt, sortOrder, require2fa

**transfer_records:** id, serverProfileId, sourcePath, destinationPath, direction,
status, totalBytes, transferredBytes, fileCount, filesTransferred, startedAt, completedAt,
errorMessage, retryCount, queuedAt, nextRetryAt

### MIGRATION_3_4
```sql
ALTER TABLE server_profiles ADD COLUMN maxBandwidthKbps INTEGER;

CREATE TABLE IF NOT EXISTS transfer_chunks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    transferId  INTEGER NOT NULL,
    chunkIndex  INTEGER NOT NULL,
    offsetBytes INTEGER NOT NULL,
    lengthBytes INTEGER NOT NULL,
    sha256      TEXT,
    status      TEXT NOT NULL DEFAULT 'PENDING',
    attempts    INTEGER NOT NULL DEFAULT 0,
    lastError   TEXT,
    FOREIGN KEY(transferId) REFERENCES transfer_records(id) ON DELETE CASCADE,
    UNIQUE(transferId, chunkIndex)
);
CREATE INDEX IF NOT EXISTS idx_chunks_transfer_status
    ON transfer_chunks(transferId, status);
```

`OriDevDatabase` → version 4; +`TransferChunkEntity::class`; +`transferChunkDao()`.

---

## 12. Testing Plan

### Unit (JUnit 5 + MockK + Turbine)

**`TokenBucketTest`** (4) — consume_exactCapacity_drains, consume_overCapacity_blocks,
refill_afterDelay_grantsTokens, consume_zeroBandwidth_noThrottle

**`ThrottledInputStreamTest`** (3) — read_withBucket_throttlesRate,
read_unbucketedStream_passthrough, close_propagatesToDelegate

**`PremiumRepositoryImplTest`** (5) — isPremium_cachedTrue_emitsTrue,
refreshEntitlement_billingReturnsActive_cachesTrue,
refreshEntitlement_billingReturnsExpired_cachesFalse,
getCachedEntitlement_offline_returnsCachedValue, getLastRefreshedAt_neverRefreshed_returnsNull

**`PurchaseUseCaseTest`** (3) — invoke_billingSuccess_cachesTrueAndReturnsSuccess,
invoke_billingUserCancel_returnsUserCancelled, invoke_billingError_returnsError

**`CheckPremiumUseCaseTest`** (2) — invoke_flowEmitsRepoValue, invoke_premiumChanges_flowUpdates

**`TransferChunkRepositoryImplTest`** (4) — upsertChunk_newRow_insertsAndReturnsId,
getChunksForTransfer_multipleChunks_orderedByIndex, updateChunkStatus_updatesRow,
deleteChunksForTransfer_removesAllRows

**`AdGateImplTest`** (4) — shouldShow_premiumUser_returnsFalseForAllSlots,
shouldShow_freeUser_banner_returnsTrue, shouldShow_houseAdDismissedWithin7d_returnsFalse,
recordShown_updatesTimestamp

### Robolectric (`:app`)
**`WifiLockTest`** (3) — onCreate_acquiresWifiLock, onDestroy_releasesWifiLock,
onDestroy_lockNotHeld_noException

**`TransferWorkerCoroutineChunkTest`** (4) — execute_largeFile_usesChunkedPath,
execute_smallFile_usesSingleShotPath, execute_chunkFailure_retriesChunk,
execute_cancellation_marksChunkPending

### Instrumentation / Hilt-android-testing (`:app`)
**`ChunkedTransferEngineTest`** (2) — enqueue_256MBFile_completesViaChunks,
resume_partialChunked_resumesFromLastCompletedChunk

**`BillingEntitlementIntegrationTest`** (3) — premiumPurchase_gateFreeAndUnlocks,
subscription_expires_gateClosesOnNextRefresh, restorePurchases_activeToken_cachesTrue

### Compose UI (`:feature-premium`)
**`PaywallScreenTest`** (4) — loading_showsLoadingIndicator, ready_showsThreeSKUTiles,
purchaseSuccess_navigatesBack, restoreTap_callsRestoreUseCase

**`PremiumGateTest`** (3) — isPremium_true_showsContent, isPremium_false_showsUpsellCard,
isPremium_false_upgradeTap_callsCallback

**`AdSlotHostTest`** (4) — premium_renders_noOp (empty Spacer only),
free_bannerSlot_rendersAdBannerView, free_houseSlot_rendersPremiumUpsellCard,
premiumToggle_flipsToHidden_withinRecomposition

**`PaywallScreenPricingTilesTest`** (4) — ready_yearlyTileSelectedByDefault,
tap_monthlyTile_updatesSelection, tap_lifetimeTile_updatesSelection, ctaText_reflectsSelectedSku

**`BandwidthThrottleSliderTest`** (3) — free_showsScrimAndUnlockPill,
premium_showsActiveSlider, scrimTap_callsOnUpgradeTap

**Total: 49 new tests.**

---

## 13. Build Sequence

```
P13.1 — DB schema + migration + chunk entity/DAO
  Files: TransferChunkEntity, TransferChunkDao, MIGRATION_3_4,
         OriDevDatabase (v4), ServerProfileEntity (+column)
  Tests: TransferChunkRepositoryImplTest (4)
  Blocks: all others

P13.2 — Domain types + interfaces + use-cases
  Files: BandwidthLimit, TransferChunk, PremiumFeatureKey,
         PremiumRepository, TransferChunkRepository,
         PurchaseUseCase, RestorePurchasesUseCase, CheckPremiumUseCase
  Tests: CheckPremiumUseCaseTest (2), PurchaseUseCaseTest (3)
  Depends: P13.1
  Blocks: P13.3, P13.4

──── PARALLEL (subagents A + B after P13.2) ────────────

[A] P13.3 — core-billing module + PremiumRepositoryImpl
      Files: BillingClientLauncher, BillingPurchaseOutcome,
             RealBillingClientLauncher, BillingModule,
             FakeBillingClientLauncher, PremiumRepositoryImpl
      Tests: PremiumRepositoryImplTest (5)
      Depends: P13.2
      Blocks: P13.5, P13.6

[B] P13.4 — TokenBucket + ThrottledStreams + WifiLock
      Files: TokenBucket, ThrottledInputStream, ThrottledOutputStream,
             TransferEngineService (WifiLock), AndroidManifest (+perms)
      Tests: TokenBucketTest (4), ThrottledInputStreamTest (3),
             WifiLockTest (3)
      Depends: P13.2
      Blocks: P13.5

──── SEQUENTIAL ────────────

P13.5 — Engine integration: throttle wrappers + chunk-mode branch
  Files: SshTransferExecutor, FtpTransferExecutor,
         TransferWorkerCoroutine (chunk branch),
         TransferWorkerCoroutineFactory, TransferEngineModule
  Tests: TransferWorkerCoroutineChunkTest (4, Robolectric),
         ChunkedTransferEngineTest (2, instrumentation)
  Depends: P13.3 + P13.4

P13.6 — feature-premium module: PaywallScreen + PremiumGate + BandwidthThrottleSlider
  Files: PaywallScreen (bound to Mockups/paywall.html), PaywallViewModel,
         PaywallUiState, PremiumGate, PremiumUpsellCard,
         BandwidthThrottleSlider (bound to mockup E),
         PremiumNavigation, PremiumModule
  Tests: PaywallScreenTest (4), PaywallScreenPricingTilesTest (4),
         PremiumGateTest (3), BandwidthThrottleSliderTest (3),
         BillingEntitlementIntegrationTest (3)
  Depends: P13.3

──── PARALLEL (subagents C + D after P13.6) ────────────

[C] P13.7 — :core-ads module + AdLoader seam + AdGate impl
      Files: AdLoader, AdLoadResult, AdMobAdLoader, FakeAdLoader,
             AdBannerView, AdNativeCardView,
             AdsModule, AdGateImpl, AdGate interface (:domain),
             AdSlot + AdRules (:domain), AdPreferences (DataStore),
             AndroidManifest (+GMA meta-data), libs.versions.toml (+GMA)
      Tests: AdGateImplTest (4)
      Depends: P13.6
      Blocks: P13.9

[D] P13.8 — UI call sites: throttle slider + settings section
      Files: AddEditConnectionScreen (+slider, mockup E),
             AddEditConnectionViewModel, AccountPremiumSection (live + mockup D),
             SettingsViewModel, SettingsState
      Depends: P13.5 + P13.6

──── SEQUENTIAL ────────────

P13.9 — Ad placement integration (feature-premium AdSlotHost + call sites)
  Files: AdSlotHost (feature-premium), AdSlotHostViewModel,
         TransferQueueScreen (+AdSlotHost, mockup A),
         ConnectionListScreen (+AdSlotHost slot 3, mockup B),
         FileManagerScreen (+AdSlotHost sticky, mockup C),
         .github/ci/check-forbidden-imports.sh (premium allowlist)
  Tests: AdSlotHostTest (4)
  Depends: P13.7

P13.10 — UMP consent flow + privacy policy wiring
  Files: OriDevApplication (+UMP ConsentInformation request),
         Application init of GMA SDK after consent
  Depends: P13.7
  Notes: runs before any ad load; non-blocking if consent already granted

P13.11 — CI + semgrep + detekt + forbidden-imports validation
  Run full gauntlet. Verify AdSlotHost allowlist works end-to-end.
```

---

## 14. Decisions for Open Questions

| # | Question | Decision | Why |
|---|---|---|---|
| Q1 | Paywall gates B + C? | **YES, both gated** | Throttling + chunked transfers are the primary premium differentiation drivers. Decoupling would fragment gate logic and require separate entitlement keys forever. Single `isPremium` check is simpler. |
| Q2 | 3 SKUs vs fewer | **3: monthly/yearly/lifetime** | Industry standard. Monthly (~$4.99) low barrier, yearly (~$29.99) LTV, lifetime (~$59.99) for sub-averse power users. `IN_APP` + subs coexist in BillingClient v7. |
| Q3 | Offline grace period | **72 hours** | Catches cancellations within a business week; survives a flight/data-roaming dead zone. Pragmatic middle ground between Apple (30d) and Play weekly enforcement. |
| Q4 | Chunk size | **64 MiB fixed** | Adaptive adds complexity with minimal benefit now. Aligned with common FS block + SSH MTU multiples. Revisit Tier 5 if telemetry shows issues. |
| Q5 | Chunking threshold | **256 MiB** | Below 256 MiB the 6-row DAO overhead per 64 MiB chunk isn't worth it. For 4 GB+ files, losing progress on a single resume is too costly. |
| Q6 | Token-bucket refill granularity | **Per-second wall-clock** | Sub-second refill grants faster than slow networks can absorb → burst overshoot. Matches kbps unit semantics. Amortizes clock-call overhead. |
| Q7 | Entitlement refresh sites | **`Application.onCreate` (bg) + `onResume` 5-min throttled + explicit Restore** | Cold-start for fresh check on first connection. `onResume` catches mid-session cancellations. Throttle prevents hammering Play on every tab switch. |
| Q8 | Free-user throttle slider UX | **Visible but locked with PremiumBadge overlay + upsell card** | Hiding removes teaser value. Disabled slider + overlay communicates feature + creates upgrade motivation. Consistent with `PremiumGate` pattern. |
| Q9 | Paywall mockup | **Exists at `Mockups/paywall.html` — binding 1:1 spec** | Authored 2026-04-15 in existing Ori:Dev design language. PaywallScreen implementation must match pixel values from mockup, not reinterpret. Section 5 of this plan enumerates the bound values. |
| Q10 | "Restore purchases" row ownership | **`:feature-settings` owns row; calls `RestorePurchasesUseCase` from `:domain`** | Feature modules cannot import each other. `:feature-settings` already depends on `:domain`. Paywall nav via root nav graph route — no direct import. |
| Q11 | Ad network SDK | **Google Mobile Ads (GMA) SDK 23.x** | First-party, Play policy safe, free, compose-compatible via AndroidView. Alternatives (AppLovin, ironSource, Unity) rejected — see 8a rationale. |
| Q12 | Ad placements in scope for v1 | **All 6 from `ad-placements.html` (A–F)** | Mockup is authored, CTA surfaces are identified, frequency caps designed. Removing any would mean redrawing the mockup. |
| Q13 | Post-completion interstitial (F) — too aggressive? | **Remove from v1 scope** | Indie target audience (power SSH/SFTP users) is especially sensitive to interstitials → 1-star review risk. Paywall + 4 soft placements (A/B/C/D) provide sufficient monetization without full-screen interruption. Keep mockup placement F as reference; defer implementation to Phase 14 with real telemetry to justify it. |
| Q14 | House ad in Settings (D) — dismissal window | **7 days** | Long enough that users who explicitly dismissed don't feel nagged; short enough that renewals of interest happen. Re-appears after 7 d as a fresh opportunity. |
| Q15 | Ad composables — recomposition on purchase | **`isPremium` → `Hidden` state within 1 frame** | `AdSlotHostViewModel` collects `CheckPremiumUseCase` directly; state flow emission flips to `Hidden` on the next recomposition. No app restart required. GMA `AdView` is destroyed via `onDispose` when state changes. |
| Q16 | Wear OS premium parity | **Wear runs completely without premium gates — all features free on Wear** | Wear's surface is small (tiles, quick actions, transfer notifications). Gating there adds complexity (DataLayer entitlement sync, new tile states, test matrix doubling) for marginal revenue. Keep Wear free and friction-free: it's the "reward" users get for being on the platform. No `WearDataSyncPublisher` payload change needed; no new Wear-side code. |
| Q17 | Ad unit-id source at build time | **`BuildConfig.AD_UNIT_*` via `secrets.properties` locally + GitHub Secrets in CI — same pattern as `KEYSTORE_*`** | Mirrors the existing keystore workflow documented in `docs/SECRETS_SETUP.md`. Debug builds hardcode Google test unit IDs (`ca-app-pub-3940256099942544/...`) unconditionally via `if (BuildConfig.DEBUG)` — never read from secrets. Release builds read real IDs from `~/.gradle/secrets.properties` locally and from GitHub Secrets in CI. Ad unit IDs are not cryptographic secrets (visible in APK), but keeping them in `secrets.properties` prevents Git leakage and lets you swap AdMob units without a commit. |

---

## 15. Source-Confirmed State (as of 2026-04-15)

- DB schema version **3** — `OriDevDatabase.kt:33`
- `MIGRATION_1_2` adds `require2fa`; `MIGRATION_2_3` adds `queuedAt`/`nextRetryAt` + index — `Migrations.kt:1–27`
- `ServerProfileEntity` has 18 fields, no bandwidth column — `ServerProfileEntity.kt:10–29`
- `ServerProfile` domain mirrors entity 1:1 — `ServerProfile.kt:7–26`
- `TransferEngineService.stopSelf()` fires when `observeNonTerminalCount() == 0` — `TransferEngineService.kt:73–75`; `onDestroy` at 98–102 is correct WifiLock release point
- `WAKE_LOCK` present in `AndroidManifest.xml:11`; `ACCESS_WIFI_STATE` + `CHANGE_WIFI_STATE` absent
- `billing-ktx` = `7.1.1` already in `libs.versions.toml:41`
- `PremiumGold = Color(0xFFFFD700)` at `Color.kt:121`
- `PremiumBadge` composable at `feature-settings/.../components/PremiumBadge.kt:32`
- `AccountPremiumSection` shows "Bald verfügbar" placeholder at lines 22–39
- `LucideIcons.Crown` at `core/core-ui/.../lucide/crown.kt`; `Zap` at `.../lucide/zap.kt`
- `BiometricPromptLauncher` pattern at `core-security/.../biometric/BiometricPromptLauncher.kt:30–37` — exact contract to replicate
- `FtpTransferExecutor` resolves `ServerProfile` at line 105
- `SshTransferExecutor` resolves only by session id at line 65 — needs `ConnectionRepository.getProfileById()` addition (red flag)
- `TransferWorkerCoroutine.PROGRESS_THROTTLE_MS = 500L` at line 264; unchanged by throttling
- `AddEditConnectionScreen` uses `AnimatedVisibility` for Advanced section at line 269
- Robolectric 4.13 in versions catalog line 57 — WifiLock tests use `ShadowWifiManager`
- No `:core-billing`, `:core-ads`, or `:feature-premium` modules in `settings.gradle.kts:1–43`
- `Mockups/paywall.html` — binding spec authored 2026-04-15; contains complete PaywallScreen pixel values
- `Mockups/ad-placements.html` — binding spec authored 2026-04-15; contains all 6 placements with phone-frame previews, ad styling, and frequency-cap rules
- `Mockups/index.html` — now references both new mockups in the feature-screen grid (lines added after watch.html entry)
- No `play-services-ads` entry yet in `libs.versions.toml` — needs to be added in P13.7
- GMA SDK 23.6.0 is current stable as of 2026-04; compatible with minSdk 34 and Kotlin 2.1

---

## Red Flags (review before execution)

1. **SshTransferExecutor profile resolution gap.** Currently resolves only a session string, not the full `ServerProfile`. Adding `connectionRepository.getProfileById()` is a non-trivial dep-graph change. FTP executor has precedent (line 105).
2. ~~**No paywall mockup.**~~ **Resolved 2026-04-15** — `Mockups/paywall.html` + `Mockups/ad-placements.html` authored; section 5 binds implementation to pixel values.
3. **`WIFI_MODE_FULL_HIGH_PERF` deprecated at API 34.** Plan includes `WIFI_MODE_FULL_LOW_LATENCY` fallback; both behave identically on most Pixel Fold hardware. Confirm acceptable.
4. **`TransferWorkerCoroutineFactory` signature break.** Injecting `PremiumEntitlement` + `TransferChunkRepository` will require coordinated updates to `TransferEngineModule` + existing Hilt bindings + tests.
5. **GMA SDK size impact.** `play-services-ads` 23.6.0 adds roughly 3–4 MB to the release APK (compressed). R8 minification + per-ABI splits absorb some of this, but base install grows. Acceptable for the revenue trade-off; flag in changelog.
6. **Feature-module import allowlist.** The `AdSlotHost` + `PremiumGate` allowlist in `.github/ci/check-forbidden-imports.sh` is a deliberate rule relaxation. Must be narrow: only those 2 symbols from `:feature-premium`. Any broader leakage is a regression.
7. **Play Console SKU setup required before P13.6 can be tested end-to-end.** User has confirmed a draft exists; the 3 SKU IDs (`oridev_premium_monthly`, `oridev_premium_yearly`, `oridev_premium_lifetime`) must be created in Play Console → Monetization before `BillingEntitlementIntegrationTest` can hit live endpoints. `FakeBillingClientLauncher` tests are unblocked regardless.
8. **UMP consent timing.** GMA SDK 23 refuses to load ads until `ConsentInformation.requestConsentInfoUpdate()` completes. Must run in `Application.onCreate` *before* any screen mounts an `AdSlotHost`, otherwise the first screen shows empty placeholders. P13.10 is explicitly sequenced after P13.7.
9. ~~**Wear premium parity (Q16).**~~ **Resolved 2026-04-15** — Wear runs fully free, no gating, no DataLayer payload change, no new tile states. Simpler + better user experience on the smaller surface.
10. ~~**Interstitial churn risk (Q13).**~~ **Resolved 2026-04-15** — Placement F (post-completion interstitial) removed from v1 scope. Deferred to Phase 14 pending real user telemetry. Indie power-user audience is too sensitive to full-screen ad interruption to justify the revenue gain.
