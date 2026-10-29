import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { parseCharacterAudioProfiles, validateVocalFilename, scaffoldDraft } from './ludopaws-content-audio-tools.mjs';

test('profile parser retains shared owners and intentional silent entries', () => {
  assert.deepEqual(parseCharacterAudioProfiles(
    'LudoPawsCharacterAudioProfile("dog", "dog", X),\nLudoPawsCharacterAudioProfile("donkey", null, X),\nLudoPawsCharacterAudioProfile("goat_lumi", "goat", X)',
  ), [
    { id: 'dog', voiceOwner: 'dog' },
    { id: 'donkey', voiceOwner: null },
    { id: 'goat_lumi', voiceOwner: 'goat' },
  ]);
});

test('recorded names preserve Windows numbered duplicate suffixes', () => {
  assert.equal(validateVocalFilename('lp_vocal_horse_capture_01 (2).wav', 'horse'), true);
  assert.equal(validateVocalFilename('lp_vocal_cat_home_lane_02.ogg', 'cat'), true);
  assert.equal(validateVocalFilename('lp_vocal_cat_unregistered_02.wav', 'cat'), false);
  assert.equal(validateVocalFilename('lp_vocal_pig_home_01.wav', 'goat'), false);
});

test('draft command creates instructions, not playable or purchasable production content', () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ludopaws-audio-draft-'));
  try {
    const target = scaffoldDraft(tmp, 'dice', 'dice_example');
    assert.match(fs.readFileSync(path.join(target, 'README.md'), 'utf8'), /settleSoundPaths/);
    assert.throws(() => scaffoldDraft(tmp, 'dice', 'dice_example'), /already exists/);
    assert.throws(() => scaffoldDraft(tmp, 'dice', '../escape'), /id must match/);
  } finally { fs.rmSync(tmp, { recursive: true, force: true }); }
});
