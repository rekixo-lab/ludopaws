import fs from "node:fs";
import path from "node:path";

const repoRoot = process.cwd();
const androidRoot = path.join(repoRoot, "android", "app", "src", "main");
const manifestPath = path.join(androidRoot, "AndroidManifest.xml");
const stringsPath = path.join(androidRoot, "res", "values", "strings.xml");

// Scan the current production source namespace. The old com/ludoproof/game
// paths no longer exist after the Rekixo package migration and silently skipped
// the branding check for the real presentation code.
const presentationRoots = [
  path.join(androidRoot, "java", "com", "rekixo", "ludopaws"),
];

const requiredStrings = new Map([
  ["app_name", "Ludo Paws"],
  ["store_title", "Ludo Paws: Talking Animals"],
  ["brand_tagline", "Roll. Race. Roar!"],
]);

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...walk(full));
    } else {
      out.push(full);
    }
  }
  return out;
}

function isPresentationFile(file) {
  if (!file.endsWith(".kt")) return false;
  const normalized = file.split(path.sep).join("/");
  return normalized.includes("/presentation/") || normalized.includes("/game/ui/");
}

function kotlinStringLiterals(source) {
  const literals = [];
  for (const match of source.matchAll(/"""[\s\S]*?"""/g)) {
    literals.push(match[0]);
  }
  for (const match of source.matchAll(/"(?:\\.|[^"\\])*"/g)) {
    literals.push(match[0]);
  }
  return literals;
}

const violations = [];

for (const root of presentationRoots) {
  for (const file of walk(root).filter(isPresentationFile)) {
    const source = fs.readFileSync(file, "utf8");
    for (const literal of kotlinStringLiterals(source)) {
      if (literal.includes("LudoProof")) {
        violations.push(
          `${path.relative(repoRoot, file)} contains legacy consumer branding in a string literal: ${literal.slice(0, 140)}`,
        );
      }
    }
  }
}

const manifest = fs.readFileSync(manifestPath, "utf8");
if (!manifest.includes('android:label="@string/app_name"')) {
  violations.push("AndroidManifest.xml must use @string/app_name for the application label.");
}
if (!manifest.includes('android:icon="@mipmap/ic_ludo_paws_launcher"')) {
  violations.push("AndroidManifest.xml must use @mipmap/ic_ludo_paws_launcher for the launcher icon.");
}
if (!manifest.includes('android:roundIcon="@mipmap/ic_ludo_paws_launcher"')) {
  violations.push("AndroidManifest.xml must use @mipmap/ic_ludo_paws_launcher for the round launcher icon.");
}
const adaptiveIconPath = path.join(androidRoot, "res", "mipmap-anydpi-v26", "ic_ludo_paws_launcher.xml");
if (!fs.existsSync(adaptiveIconPath)) {
  violations.push("Missing adaptive Ludo Paws launcher resource: res/mipmap-anydpi-v26/ic_ludo_paws_launcher.xml");
}

const strings = fs.readFileSync(stringsPath, "utf8");
for (const [name, value] of requiredStrings) {
  const expected = `<string name="${name}">${value}</string>`;
  const resource = strings.match(new RegExp(`<string\\b[^>]*\\bname="${name}"[^>]*>([\\s\\S]*?)</string>`));
  if (resource?.[1] !== value) {
    violations.push(`strings.xml is missing required brand string: ${expected}`);
  }
}

if (violations.length > 0) {
  console.error("Ludo Paws consumer-brand gate failed:\n");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("Ludo Paws consumer-brand gate passed.");
