# Panda character

## Release holds

Price changes and publishing remain PENDING at the user's request. Do not change live Play Console prices, deploy the server, or publish the app as part of this work.

## Local character implementation

- Adds Panda to the native 3D character registry with a separate renderer and avatar, rounded black ears, black eye patches, white head/belly and articulated dark limbs.
- Reuses the existing live blink, gaze and breathing system. Adds gentle idle sway, planted cell-hop movement, home celebration and all existing reaction cues.
- Activity follow-up: a 12-second idle sequence smoothly lowers the shoulders onto four paws, holds that support, and stands again. Articulated front paws meet the same -.72 model-space floor as the rear paws. A separate stance spring blends out the posture during gameplay reactions; arms wave asymmetrically when standing, with stronger happy/victory and sad gestures. Added pure stance-cycle regression coverage; device visual acceptance is still pending.
- Uses the existing soft paw footstep fallback. Panda vocal recordings are not supplied yet; Panda stays voice-free rather than borrowing another animal's voice.
- Registers server-side identity for a future coordinated rollout. Android still sends the compatible default identity to the existing production server, preserving local Panda selection.
- Does not create a paid Panda offer, set a price or grant paid ownership. Existing selection/unlock checks remain authoritative: a registered character without an individual paid offer is locally available under the existing starter-pack policy. Panda therefore appears as a selectable local character without spending or granting currency. Any future paid/unlock offer needs a separate decision.

## Verification

Run content generation consistency and character schema/domain/store source gates. Update roster assertions and add Panda registration, identity compatibility and audio-profile coverage. Full APK/lint/device visual acceptance remains for the agreed final validation, so visual quality is not yet device-verified.
