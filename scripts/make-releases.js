// 从 docs/architecture.md 的变更记录生成 GitHub releases（1.0.130~1.0.151）。
// 用法：node scripts/make-releases.js [from] [to]
// 无本地 jar 的版本只写说明；1.0.149+ 附双端 jar（若存在）。
const { execSync } = require('child_process');
const fs = require('fs');

const FROM = parseInt(process.argv[2] || '130', 10);
const TO = parseInt(process.argv[3] || '151', 10);
const lines = fs.readFileSync('docs/architecture.md', 'utf8')
  .split(/\r?\n/).filter(l => l.startsWith('- **'));

// 每个版本的正文 = 收集自上一个“版本 N”条目之后到“版本 N”为止的特性条目（倒序合并）
function bodyFor(v) {
  const verMark = `- **版本 1.0.${v}（`;
  const vi = lines.findIndex(l => l.startsWith(verMark));
  if (vi < 0) return null;
  const out = [];
  for (let i = vi - 1; i >= 0; i--) {
    const l = lines[i];
    if (l.startsWith('- **版本 1.0.')) break;
    if (l.startsWith('- **')) out.push(l.replace(/^- \*\*(.+?)（\d{4}-\d{2}-\d{2}）\*\*：/, '### $1\n\n'));
  }
  return out.join('\n\n');
}

for (let v = TO; v >= FROM; v--) {
  const tag = `v1.0.${v}`;
  const body = bodyFor(v);
  if (!body) { console.log(`skip ${tag} (no changelog)`); continue; }
  const full = `## Z80Z 血条 1.0.${v}\n\n${body}\n\n---\n\n适用于 Minecraft 1.20.1，需要 Java 17。无需 Architectury API。\n`;
  const jarF = `forge/build/libs/z80z-healthbar-forge-1.0.${v}.jar`;
  const jarFa = `fabric/build/libs/z80z-healthbar-fabric-1.0.${v}.jar`;
  const assets = [];
  if (fs.existsSync(jarF)) assets.push(jarF);
  if (fs.existsSync(jarFa)) assets.push(jarFa);
  const tmp = `build/rel-note-${v}.md`;
  fs.writeFileSync(tmp, full, 'utf8');
  const cmd = assets.length
    ? `gh release create ${tag} --title "Z80Z Health Bar 1.0.${v}" --notes-file "${tmp}" ${assets.map(a => `"${a}"`).join(' ')}`
    : `gh release create ${tag} --title "Z80Z Health Bar 1.0.${v}" --notes-file "${tmp}"`;
  try {
    execSync(cmd, { stdio: 'inherit' });
    console.log(`created ${tag} (${assets.length} assets)`);
  } catch (e) {
    console.log(`FAILED ${tag}`);
  }
}
