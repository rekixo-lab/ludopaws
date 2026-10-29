const major = Number(process.versions.node.split(".")[0]);
if (major !== 22) {
  console.error(`Backend tests require Node 22 (current: ${process.versions.node}). Select the repository .node-version before running npm test.`);
  process.exitCode = 1;
}
