import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { parseCharacterAudioProfiles, validateVocalFilename, scaffoldDraft, inspectAudio } from './ludopaws-content-audio-tools.mjs';

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

test('authored-only and shared-fallback characters accept their own recording folders', () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ludopaws-own-voices-'));
  const base = 'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/';
  const write = (name, text) => {
    const target = path.join(tmp, name);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, text);
  };
  try {
    write(base + 'domain/audio/GeneratedCharacterAudioProfiles.kt',
      'LudoPawsCharacterAudioProfile("goat", "goat", X),\n' +
      'LudoPawsCharacterAudioProfile("donkey", null, X),\n' +
      'LudoPawsCharacterAudioProfile("goat_lumi", "goat", X)');
    write(base + 'domain/catalog/GeneratedCharacterDefinitions.kt',
      'val all = listOf(GoatDefinition.content, DonkeyDefinition.content, LumiGoatDefinition.content)');
    for (const folder of ['goat', 'donkey', 'lumi']) fs.mkdirSync(path.join(tmp, base, 'content', folder), { recursive: true });
    // Filename inspection only; fixtures are temporary, not real or shipped audio.
    write('audio/sfx/voices/donkey/lp_vocal_donkey_six_01.wav', 'filename fixture');
    write('audio/sfx/voices/goat_lumi/lp_vocal_goat_lumi_home_02.ogg', 'filename fixture');
    const report = inspectAudio(tmp);
    assert.deepEqual(report.errors, []);
    assert.equal(report.voiceCoverage.donkey.files, 1);
    assert.equal(report.voiceCoverage.goat_lumi.files, 1);
    assert.deepEqual(report.silentCharacterIds, []);
    assert.deepEqual(report.authoredOnlyCharacterIds, ['donkey']);
    write('audio/sfx/voices/dragon/lp_vocal_dragon_six_01.wav', 'filename fixture');
    assert.ok(inspectAudio(tmp).errors.some(x => x.includes('dragon')));
  } finally { fs.rmSync(tmp, { recursive: true, force: true }); }
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
