// 配置字段审计：找出 configs 包外无引用（死配置）或仅 ConfigValidator 引用（无实际效果）的字段
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..');
const cfgDir = path.join(ROOT, 'common/src/main/java/com/z80z99/z80zhealthbar/config/configs');

const others = [];
function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (p.split(path.sep).includes('configs')) continue; // 跳过配置类自身
      walk(p);
    } else if (p.endsWith('.java')) others.push({ p, c: fs.readFileSync(p, 'utf8') });
  }
}
walk(path.join(ROOT, 'common/src/main/java'));
walk(path.join(ROOT, 'forge/src/main/java'));
walk(path.join(ROOT, 'fabric/src/main/java'));

const dead = [], validOnly = [];
for (const f of fs.readdirSync(cfgDir).filter(f => f.endsWith('.java'))) {
  const src = fs.readFileSync(path.join(cfgDir, f), 'utf8');
  const fields = [...src.matchAll(/public\s+(?:boolean|int|double|float|long|String)\s+(\w+)\s*[=;]/g)].map(m => m[1]);
  for (const fd of new Set(fields)) {
    let refs = 0; const where = [];
    const re = new RegExp('\\b' + fd + '\\b', 'g');
    for (const o of others) {
      const m = o.c.match(re);
      if (m) { refs += m.length; where.push(o.p.split(path.sep).pop()); }
    }
    if (refs === 0) dead.push(f + '  ' + fd);
    else if (where.every(w => w === 'ConfigValidator.java')) validOnly.push(f + '  ' + fd);
  }
}
console.log('scanned source files:', others.length);
console.log('=== 完全无引用（死配置）===');
dead.forEach(x => console.log('  ' + x));
console.log('=== 仅 ConfigValidator 引用（被钳制但无实际效果）===');
validOnly.forEach(x => console.log('  ' + x));
