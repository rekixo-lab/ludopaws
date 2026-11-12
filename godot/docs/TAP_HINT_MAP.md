# Tap Hand hint map (V2 Guidance)

One shared system (`scripts/ui/hints/guidance.gd`, autoload `Guidance`) shows the 3D tap
hand (`assets/ui/icons/tap_hand.svg`) on the most important next action after the user has
been idle. Pure policy (idle, priority, back-off, caps) is `scripts/ui/hints/tap_hint_policy.gd`.

Global rules (every row):
- Shows only after the idle time with **no tap / scroll / drag / key**; any input hides it at
  once and restarts the idle clock.
- One hand at a time: the highest-priority available target wins.
- Back-off: each time a hand is ignored (input elsewhere) the next idle wait for that target
  grows by 50 %; after 6 shows in a session a non-essential target is not shown again.
- "Play hints" off (Settings) silences everything except **essential** rows (Demo, first
  launch).
- Never while a dialog covers the target, during moves / dice rolls / robot or remote turns,
  or before a Pass & Play handoff is confirmed (the handoff dialog is the modal).
- Reduced Motion: the hand stays still and only fades / pulses gently.

| Screen | Where a user gets stuck | Target | Idle | Priority | Essential | Never when |
|---|---|---|---|---|---|---|
| First launch · Age entry | Slider untouched, Continue disabled | Age slider | 3 s | 70 | yes | dialog open |
| First launch · Age entry | Age chosen, not continued | Continue | 3 s | 75 | yes | button disabled |
| Demo match | First roll not made | Own dice | 1.5 s | 90 | yes | not own turn, rolling, moving, Skip dialog open |
| Demo match | Rolled, paw not chosen | Legal paw (board) | live cadence | — | — | — (board-drawn hint, see below) |
| Demo match | Six rolled: roll again | Own dice (same row as first roll) | 1.5 s | 90 | yes | as above |
| Demo results | Not continuing | Continue | 1.5 s | 80 | yes | — |
| Match (Robot / P&P / Demo) | Own turn, dice not tapped | Own dice | 3 s | 80 | no | robot / remote turn, roll in flight, move presenting, handoff dialog, results |
| Match (all modes) | Rolled, paw not chosen | Legal paws | live: 1.4 s of every 3.6 s | — | — | Board-drawn **live** LudoPaws3DLegalHaloView cadence on every legal paw (live behaviour kept, new icon); no extra Guidance hand to avoid two hands |
| Match · Pass & Play | Handoff not confirmed | READY (dialog) | 5 s | 95 | no | — |
| Results | Match over, nothing tapped | Play Again (Rematch) | 5 s | 60 | no | dialog open |
| Home (new user, 0 games) | Does not know where to start | Robot Match mode card | 5 s | 40 | no | any game played |
| Robot / Pass & Play setup | Setup shown, not started | Start Game | 5 s | 50 | no | dialog open |
| Rewards | Daily gift claimable | Claim | 5 s | 55 | no | already claimed |
| Rewards | Free spin available | Spin | 5 s | 54 | no | already spun |
| Shop · unlock chooser dialog | Chooser open, no choice | Primary option | 5 s | 70 | no | — |
| Notice dialogs (single OK) | Dialog left open | OK | 5 s | 30 | no | — |

Deliberately **not** hinted:
- Shop / Wallet items and Gem plans: no nudge towards buying (only available rewards above).
- Online / Team Up / Friends lobbies: matchmaking needs the online service (not built);
  their Find / Create buttons get hints when online play ships.
- Settings toggles, Profile, Badges, Leaderboard, Collections: nothing is required there.
- Demo "Skip": skipping is optional, so the hand never points at it.
- Robot / remote dice: never tappable, never hinted.
