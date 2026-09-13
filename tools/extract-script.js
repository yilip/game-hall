/* 从 survival.html 抽取内联 <script>，写到 .tmp-survival.js，供 node --check / 无头模拟使用 */
const fs = require('fs');
const path = require('path');

const HTML = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'survival.html');
const OUT = path.join(__dirname, '.tmp-survival.js');

const html = fs.readFileSync(HTML, 'utf8');
const re = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
let m, blocks = [];
while ((m = re.exec(html))) blocks.push(m[1]);
if (!blocks.length) { console.error('no script found'); process.exit(1); }
// 取最长的一段（游戏主逻辑）
blocks.sort((a, b) => b.length - a.length);
fs.writeFileSync(OUT, blocks[0], 'utf8');
console.error('extracted ' + blocks[0].length + ' chars -> ' + OUT);
