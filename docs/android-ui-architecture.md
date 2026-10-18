# LudoProof Android Architecture

## Principle

LudoProof now uses a **feature-first source tree**: find the product feature first, then its presentation, data, or domain code.

Runtime Kotlin package/class names are intentionally preserved during this migration. That keeps manifest identities, intents, tests, persisted data, and proof behavior stable while making the VS Code file tree scalable.

## Source tree

```text
com/rekixo/ludopaws/
├─ core/
│  ├─ network/ConnectivityMonitor.kt
│  └─ ui/
│     ├─ theme/LudoProofTheme.kt
│     ├─ art/ArcadeBackdropView.kt
│     └─ dialogs/
│        ├─ ArcadeDialogs.kt
│        └─ components/
│           ├─ DialogEvidenceComponents.kt
│           ├─ DialogMetricComponents.kt
│           └─ DialogShellComponents.kt
│
├─ game/
│  ├─ domain/model/GameModels.kt
│  └─ ui/
│     ├─ components/
│     │  ├─ LudoBoardView.kt
│     │  └─ DiceView.kt
│     └─ art/GameResultArtView.kt
│
└─ feature/
   ├─ home/
   │  └─ presentation/
   │     ├─ HomeActivity.kt
   │     ├─ components/
   │     │  ├─ HomeHeaderUi.kt
   │     │  ├─ HomeHeroUi.kt
   │     │  ├─ HomeModesUi.kt
   │     │  └─ HomeLayoutUi.kt
   │     └─ art/
   │        ├─ HomeHeroArtView.kt
   │        └─ ModeArtView.kt
   │
   ├─ online/
   │  ├─ data/
   │  │  ├─ remote/GameApi.kt
   │  │  └─ local/
   │  │     ├─ CachedMatchStore.kt
   │  │     ├─ PendingOperationStore.kt
   │  │     ├─ PendingRollStore.kt
   │  │     └─ SecureSessionStore.kt
   │  └─ presentation/
   │     ├─ MainActivity.kt
   │     ├─ state/OnlineGameUiState.kt
   │     ├─ actions/
   │     │  ├─ OnlineMatchActions.kt
   │     │  ├─ OnlineResponseRenderer.kt
   │     │  ├─ OnlineVerificationRenderer.kt
   │     │  └─ OnlineSessionActions.kt
   │     ├─ components/
   │     │  ├─ OnlineHeaderUi.kt
   │     │  ├─ OnlineLobbyUi.kt
   │     │  ├─ OnlineStatusUi.kt
   │     │  ├─ OnlineActionUi.kt
   │     │  └─ OnlinePrimitives.kt
   │     └─ art/OnlineLobbyArtView.kt
   │
   ├─ offline/
   │  ├─ domain/engine/OfflineGameEngine.kt
   │  └─ presentation/
   │     ├─ OfflineGameActivity.kt
   │     ├─ state/OfflineSetupUiState.kt
   │     ├─ actions/OfflineGameActions.kt
   │     ├─ common/OfflineCommonUi.kt
   │     ├─ setup/
   │     │  ├─ OfflineSetupScreenUi.kt
   │     │  └─ OfflineSetupSelectionUi.kt
   │     ├─ gameplay/
   │     │  ├─ OfflineGameplayScreenUi.kt
   │     │  └─ OfflineGameplayControlsUi.kt
   │     └─ art/OfflineSetupArtView.kt
   │
   ├─ proof/
   │  ├─ domain/
   │  │  ├─ core/
   │  │  │  ├─ NativeDiceEngine.kt
   │  │  │  ├─ LocalSecretBinding.kt
   │  │  │  └─ SeedCommitment.kt
   │  │  └─ offline/
   │  │     ├─ OfflineLudoV4Binding.kt
   │  │     └─ OfflineRandomnessAudit.kt
   │  └─ presentation/
   │     ├─ dialogs/
   │     │  ├─ NaturalWorldAuditDialogUi.kt
   │     │  └─ ProofHistoryDialogUi.kt
   │     └─ art/NaturalWorldMapView.kt
   │
   └─ settings/
      └─ presentation/SettingsDialogUi.kt
```

## State ownership

- Online mutable session/screen state -> `OnlineGameUiState` + `OnlineGameStateHolder`.
- Offline setup selections -> `OfflineSetupUiState` + `OfflineSetupStateHolder`.
- Offline gameplay state -> `OfflineGameEngine`.
- Proof/randomness state and algorithms -> `feature/proof/domain/`.

The state holders are framework-independent on purpose. The app still uses `android.app.Activity`; AndroidX/ViewModel migration should be a separate lifecycle change, not mixed into a folder-architecture refactor.

## Editing map

- Home profile/connectivity -> `feature/home/presentation/components/HomeHeaderUi.kt`
- Home hero/quick actions -> `feature/home/presentation/components/HomeHeroUi.kt`
- Home mode cards/footer -> `feature/home/presentation/components/HomeModesUi.kt`
- Online lobby -> `feature/online/presentation/components/OnlineLobbyUi.kt`
- Online status/result -> `feature/online/presentation/components/OnlineStatusUi.kt`
- Online controls -> `feature/online/presentation/components/OnlineActionUi.kt`
- Online create/join/roll/move -> `feature/online/presentation/actions/OnlineMatchActions.kt`
- Online API/cache/session -> `feature/online/data/`
- Offline setup -> `feature/offline/presentation/setup/`
- Offline gameplay -> `feature/offline/presentation/gameplay/`
- Offline engine -> `feature/offline/domain/engine/`
- Proof implementation -> `feature/proof/domain/`
- Shared board/dice -> `game/ui/components/`
- Shared theme/dialog shells -> `core/ui/`

## Guardrails

1. New code goes under the owning feature first.
2. Cross-feature reusable UI goes to `core/ui/` or `game/ui/`.
3. Activities own lifecycle and wiring, not large UI builders.
4. New mutable screen state must join the feature state holder instead of becoming another unrelated Activity field.
5. Network/storage code never belongs in UI component files.
6. Proof/cryptographic algorithms never belong in presentation code.
7. UI/action files should normally stay below about 500 lines and split by visible responsibility.
8. Proof/engine code may stay larger when cohesion/auditability matters; those splits require dedicated conformance tests.
9. Any folder move affecting security/conformance paths updates those gates in the same change.
