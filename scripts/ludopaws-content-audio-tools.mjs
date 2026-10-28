import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const CHARACTER_CUES = new Set([
  'six', 'yard_exit', 'capture', 'captured', 'safe', 'home_lane', 'home',
  'frustrated', 'third_six', 'idle', 'nervous', 'proud', 'victory', 'defeat',
]);
const ID = /^[a-z][a-z0-9_]{1,31}$/;

const read = (root, relative) => fs.readFileSync(path.join(root, relative), 'utf8');

export function parseCharacterAudioProfiles(source) {
  const matches = [...source.matchAll(/LudoPawsCharacterAudioProfile\(\s*"([^"]+)"\s*,\s*(null|"([^"]+)")/g)];
  return matches.map(match => ({
    id: match[1],
    voiceOwner: match[2] === 'null' ? null : match[3],
  }));
}

export function validateVocalFilename(fileName, voiceOwner) {
  const prefix = `lp_vocal_${voiceOwner}_`;
  if (!fileName.startsWith(prefix)) return false;
  const cueAndVariant = fileName.slice(prefix.length);
  const match = cueAndVariant.match(/^([a-z_]+)_(\d{2})(?: \(\d+\))?\.(wav|ogg)$/i);
  return Boolean(match && CHARACTER_CUES.has(match[1].toLowerCase()));
}

export function inspectAudio(root = process.cwd()) {
  const profiles = parseCharacterAudioProfiles(read(
    root,
    'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/audio/LudoPawsCharacterAudioProfiles.kt',
  ));
  const errors = [];
  const ids = profiles.map(profile => profile.id);
  const uniqueIds = new Set(ids);
  if (uniqueIds.size !== ids.length) errors.push('Duplicate character audio profile ID');
  for (const profile of profiles) {
    if (!ID.test(profile.id)) errors.push(`Invalid character audio ID: ${profile.id}`);
    if (profile.voiceOwner && !ID.test(profile.voiceOwner)) {
      errors.push(`Invalid voice owner for ${profile.id}: ${profile.voiceOwner}`);
    }
  }
  const definitions = read(
    root, 'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/domain/catalog/CharacterDefinitions.kt',
  );
  const registration = definitions.split('val all = listOf(')[1]?.split(')')[0] ?? '';
  const shippedCount = [...registration.matchAll(/[A-Za-z]+Definition\.content/g)].length;
  if (shippedCount !== profiles.length) {
    errors.push(`CharacterDefinitions has ${shippedCount} entries but audio profiles have ${profiles.length}`);
  }
  const registeredFolders = fs.readdirSync(path.join(
    root, 'android/app/src/main/java/com/rekixo/ludopaws/feature/characters/content',
  ), { withFileTypes: true }).filter(entry => entry.isDirectory() && entry.name !== 'packs')
    .map(entry => entry.name === 'lumi' ? 'goat_lumi' : entry.name);
  for (const folderId of registeredFolders) {
    if (!uniqueIds.has(folderId)) errors.push(`Unregistered character audio folder: ${folderId}`);
  }
  for (const id of ids) {
    if (!registeredFolders.includes(id)) errors.push(`Missing character content folder for audio profile: ${id}`);
  }
  const voiceOwners = new Set(profiles.map(x => x.voiceOwner).filter(Boolean));
  for (const profile of profiles) {
    if (profile.voiceOwner && !uniqueIds.has(profile.voiceOwner)) {
      errors.push(`Unknown voice owner ${profile.voiceOwner} for ${profile.id}`);
    }
  }
  const voicesDir = path.join(root, 'audio/sfx/voices');
  const recorded = Object.fromEntries([...voiceOwners].map(id => [id, { files: 0, cues: new Set() }]));
  for (const entry of fs.readdirSync(voicesDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const files = fs.readdirSync(path.join(voicesDir, entry.name))
      .filter(name => /\.(wav|ogg)$/i.test(name));
    if (files.length && !voiceOwners.has(entry.name)) {
      errors.push(`Audio voice directory has no registered voice owner: ${entry.name}`);
      continue;
    }
    for (const name of files) {
      if (!validateVocalFilename(name, entry.name)) {
        errors.push(`Unrecognized voice cue filename: ${entry.name}/${name}`);
        continue;
      }
      const cue = name.slice(`lp_vocal_${entry.name}_`.length)
        .replace(/_\d{2}(?: \(\d+\))?\.(wav|ogg)$/i, '');
      recorded[entry.name].files += 1;
      recorded[entry.name].cues.add(cue);
    }
  }
  return {
    errors,
    characters: profiles.length,
    silentCharacterIds: profiles.filter(x => x.voiceOwner === null).map(x => x.id),
    voiceCoverage: Object.fromEntries(Object.entries(recorded).map(([id, coverage]) => [
      id, { files: coverage.files, cues: [...coverage.cues].sort(), missingOptionalCues: [...CHARACTER_CUES].filter(c => !coverage.cues.has(c)) },
    ])),
  };
}

export function scaffoldDraft(root, type, id) {
  if (!['character', 'board', 'dice'].includes(type)) throw new Error('type must be character, board or dice');
  if (!ID.test(id)) throw new Error('id must match /^[a-z][a-z0-9_]{1,31}$/');
  const target = path.join(root, 'content-drafts', type, id);
  if (fs.existsSync(target)) throw new Error(`Draft already exists: ${target}`);
  fs.mkdirSync(target, { recursive: true });
  const instructions = type === 'character'
    ? [
      'Create 3D renderer, motion, avatar, character definition and pack membership.',
      'Register stable IDs in CharacterDefinitions and the renderer registry.',
      'Add a character audio profile; Donkey is intentionally silent until its own voice set is authored.',
      `Place optional voice files under audio/sfx/voices/${id}/lp_vocal_${id}_<cue>_01.wav`,
      `Place optional footsteps under audio/sfx/gameplay/movement/step/${id}/lp_sfx_step_${id}_01.wav`,
    ]
    : type === 'board'
      ? [
        'Create a BoardDefinition and BoardRenderer when a new renderer is needed.',
        'Register stable IDs in BoardRegistry and keep cosmetics unavailable until QA.',
        'Optional backgroundMusicPaths reference files under audio/music/.',
      ]
      : [
        'Create a DiceDefinition and DiceRenderer when a new renderer is needed.',
        'Register stable IDs in DiceRegistry and keep cosmetics unavailable until QA.',
        'Optional rollSoundPaths and settleSoundPaths reference files under audio/sfx/.',
      ];
  fs.writeFileSync(path.join(target, 'README.md'),
    `# Draft ${type}: ${id}\n\nNot registered or purchasable. Complete these tasks before release:\n\n` +
    instructions.map((line, i) => `${i + 1}. ${line}`).join('\n') + '\n\n' +
    'Validate with: node scripts/ludopaws-content-audio-tools.mjs validate\n');
  return target;
}

function main(args) {
  const [command = 'report', type, id] = args;
  if (command === 'draft') {
    console.log(`Created ${scaffoldDraft(process.cwd(), type, id)}`);
    return;
  }
  if (command !== 'report' && command !== 'validate') {
    throw new Error('Usage: node scripts/ludopaws-content-audio-tools.mjs [report|validate|draft <character|board|dice> <id>]');
  }
  const report = inspectAudio();
  if (command === 'report') {
    console.log(JSON.stringify(report, null, 2));
  } else {
    console.log(`Validated ${report.characters} character audio profiles and ${Object.keys(report.voiceCoverage).length} recorded voice owners.`);
  }
  if (report.errors.length) {
    throw new Error('Audio content validation failed:\n' + report.errors.join('\n'));
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(process.argv.slice(2)); } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
