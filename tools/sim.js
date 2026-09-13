/* 荒野求生 · 无头模拟器
   用途：
     1) 回归校验：确认改动后游戏不崩、不变量成立、通关路径可达
     2) 蒙特卡洛：批量跑局，输出存活曲线 / 单局时长 / 专精分布，用于调 BAL 平衡表
   用法：
     node tools/sim.js                       # 默认：每难度 30 局
     node tools/sim.js --games 50 --diff hard
     node tools/sim.js --regress             # 回归模式：详细不变量校验
*/
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const HTML = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'survival.html');

/* ---------------- DOM 桩 ---------------- */
function makeEl(tag) {
  const el = {
    tagName: (tag || 'div').toUpperCase(),
    textContent: '', value: '', className: '',
    dataset: {}, style: {}, onclick: null, parentNode: null,
    childNodes: [], children: [], offsetWidth: 0, offsetHeight: 0,
    scrollTop: 0, scrollHeight: 0,
    classList: {
      _s: new Set(),
      add(c) { this._s.add(c); }, remove(c) { this._s.delete(c); },
      toggle(c, on) { if (on === undefined) { this._s.has(c) ? this._s.delete(c) : this._s.add(c); } else { on ? this._s.add(c) : this._s.delete(c); } },
      contains(c) { return this._s.has(c); }
    },
    appendChild(c) { c.parentNode = el; el.childNodes.push(c); el.children.push(c); return c; },
    removeChild(c) { const i = el.childNodes.indexOf(c); if (i >= 0) el.childNodes.splice(i, 1); const j = el.children.indexOf(c); if (j >= 0) el.children.splice(j, 1); c.parentNode = null; return c; },
    insertBefore(c) { return el.appendChild(c); },
    addEventListener() {}, removeEventListener() {},
    setAttribute() {}, getAttribute() { return null; }, removeAttribute() {},
    querySelector() { return makeEl('div'); },
    querySelectorAll() { return []; },
    getBoundingClientRect() { return { left: 0, top: 0, width: 900, height: 600, right: 900, bottom: 600 }; },
    setPointerCapture() {}, releasePointerCapture() {},
    focus() {}, blur() {}, click() { if (typeof el.onclick === 'function') el.onclick({ preventDefault() {} }); },
    remove() {}, insertAdjacentHTML() {}, cloneNode() { return makeEl(tag); },
    get firstChild() { return el.childNodes[0] || null; }
  };
  // innerHTML 赋值应清空子节点（真实 DOM 行为），否则桩元素会无限增长导致 OOM
  let _html = '';
  Object.defineProperty(el, 'innerHTML', {
    get() { return _html; },
    set(v) { _html = String(v); el.childNodes.length = 0; el.children.length = 0; },
    enumerable: true, configurable: true
  });
  return el;
}

function makeContext() {
  const els = {};
  const store = {};
  const timeouts = [];
  const document = {
    readyState: 'complete',
    getElementById(id) { return els[id] || (els[id] = makeEl('div')); },
    createElement(t) { return makeEl(t); },
    createElementNS(ns, t) { return makeEl(t); },
    createTextNode(t) { const e = makeEl('span'); e.textContent = t; return e; },
    querySelector() { return makeEl('div'); },
    querySelectorAll() { const a = []; a.forEach = Array.prototype.forEach; return a; },
    addEventListener() {}, removeEventListener() {},
    body: makeEl('body'), documentElement: makeEl('html'), head: makeEl('head')
  };
  const localStorage = {
    getItem(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
    setItem(k, v) { store[k] = String(v); },
    removeItem(k) { delete store[k]; },
    clear() { for (const k in store) delete store[k]; }
  };
  const win = {
    document, localStorage,
    addEventListener() {}, removeEventListener() {},
    requestAnimationFrame(fn) { return 0; },
    cancelAnimationFrame() {},
    setTimeout(fn, ms) { timeouts.push(fn); return timeouts.length; },
    clearTimeout() {},
    setInterval() { return 0; },   // 时间由模拟器手动推进
    clearInterval() {},
    AudioContext: undefined, webkitAudioContext: undefined,
    innerWidth: 1280, innerHeight: 900, devicePixelRatio: 1,
    matchMedia() { return { matches: false, addListener() {}, addEventListener() {} }; },
    location: { href: 'http://localhost/survival.html', search: '' },
    _tick: null
  };
  win.window = win;
  win.self = win;
  win.globalThis = win;
  win.top = win;
  win.parent = win;
  win.navigator = { userAgent: 'node-sim', language: 'zh-CN' };
  win.console = console;
  win.Math = Math;
  win.JSON = JSON;
  win.localStorage = localStorage;
  win.setTimeout = win.setTimeout;
  win.setInterval = win.setInterval;
  win.clearInterval = win.clearInterval;
  win.alert = () => {};
  win.confirm = () => true;
  return { ctx: vm.createContext(win), win, timeouts };
}

/* ---------------- 载入游戏脚本 ---------------- */
function extractScript() {
  const html = fs.readFileSync(HTML, 'utf8');
  const re = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
  let m, blocks = [];
  while ((m = re.exec(html))) blocks.push(m[1]);
  blocks.sort((a, b) => b.length - a.length);
  return blocks[0];
}
const SRC = extractScript();

function newGame() {
  const { ctx, win, timeouts } = makeContext();
  try {
    vm.runInContext(SRC, ctx, { filename: 'survival-inline.js' });
  } catch (e) {
    throw new Error('脚本加载失败: ' + e.message);
  }
  return { ctx, win, timeouts };
}

/* ---------------- 机器人策略 ---------------- */
function runGame(opts) {
  const { ctx, timeouts } = newGame();
  const G = () => ctx.G;
  const errors = [];
  const invariants = [];

  if (opts.overrides) applyOverrides(ctx, opts.overrides);

  // 拦截弹窗，改为记录到 pending
  const pending = { event: null, crisis: null };
  const deathReason = { v: null };
  const origGameOver = ctx.gameOver;
  ctx.gameOver = function (win, reason) { if (!win && !deathReason.v) deathReason.v = reason; return origGameOver(win, reason); };
  const realPickEvent = ctx.pickEvent;
  ctx.triggerEvent = function () {
    // 走真实的选事件逻辑（含事件链），只把弹窗换成记录
    const evt = realPickEvent();
    ctx.G.stats.events++;
    pending.event = evt;
  };
  ctx.triggerCrisis = function () {
    const pool = ctx.CRISES.filter(c => c.id !== ctx.G.lastCrisis);
    const c = ctx.pick(pool);
    ctx.G.lastCrisis = c.id;
    ctx.G.pendingCrisis = c;
    pending.crisis = c;
  };

  const flushT = () => { let n = 0; while (timeouts.length && n++ < 500) { const f = timeouts.shift(); try { f(); } catch (e) { errors.push('timeout:' + e.message); } } };

  // 开局
  ctx.window._diff = opts.diff || 'normal';
  ctx.startNew();
  flushT();

  const g = G();
  g.view = 'zone';
  if (!g.zones[g.cur]) g.zones[g.cur] = ctx.makeZone(g.cur);

  let ticks = 0, steps = 0;
  const maxSteps = opts.maxSteps || 4000;
  const branchOrder = { hunter: ['fishing', 'tracking', 'bow'], smith: ['smelt', 'arch', 'forge'], healer: ['herb', 'brew', 'purify'], farmer: ['gather2', 'preserve', 'chef'] };
  const wantBranch = opts.branch || ctx.pick(['hunter', 'smith', 'healer', 'farmer']);

  function tryTech() {
    const G_ = G();
    // 优先「高效采集」，再走专精路线
    if (G_.techPoints >= 10 && !G_.tech.gatherEff) { ctx.unlockTech('gatherEff'); return; }
    const list = branchOrder[wantBranch] || [];
    for (const id of list) {
      if (G_.tech[id]) continue;
      const f = ctx.findTech(id);
      if (f && G_.techPoints >= f.t.cost) { ctx.unlockTech(id); return; }
      break;
    }
  }

  function doAction() {
    const G_ = G();
    // 1. 温饱（把阈值设高，模拟「会玩的玩家」）
    if (G_.hunger < 60) { ctx.cookAll(); ctx.doEat(); }
    if (G_.thirst < 60) { ctx.doDrink(); }
    // 2. 回血 / 休息
    if (G_.health < G_.maxHealth * 0.5 && (G_.res.medicine || 0) > 0) ctx.doMedicine();
    if (G_.energy < 30) { ctx.doRest(); return; }
    // 2b. 有晾肉架就把生鲜处理掉，避免腐坏
    if (G_.build.rack && ((G_.res.meat || 0) > 2 || (G_.res.fish || 0) > 2)) { ctx.dryFood(); return; }
    // 3. 任务交付
    if (ctx.questReady && ctx.questReady()) { ctx.questTurnIn(); return; }
    // 4. 科技
    tryTech();
    // 5. 修塔（目标）
    if (G_.quest.idx >= 4 && ctx.hasRes(ctx.TOWER_COST)) {
      if (G_.cur !== 'tower') { ctx.enterRegion('tower'); }
      const t = ctx.hereTile();
      if (t && t.t === 'tower') { ctx.repairTower(); flushT(); return; }
      // 走向塔基
      const z = ctx.curZone();
      if (z) {
        const mid = Math.floor(z.h / 2) * z.w + Math.floor(z.w / 2);
        if (z.py * z.w + z.px !== mid) {
          const tx = mid % z.w, ty = Math.floor(mid / z.w);
          if (ctx.bfsPath(tx, ty)) { walkManual(ctx, tx, ty); return; }
        }
      }
    }
    // 6. 建造（有余力时）
    if (!G_.shelterLevel && ctx.hasRes({ wood: 5, stone: 3 })) { ctx.buildShelter(); return; }
    if (!G_.build.rack && ctx.hasRes({ wood: 12, fiber: 6 })) { ctx.buildBuilding('rack'); return; }
    if (!G_.build.rain && ctx.hasRes({ wood: 10, stone: 6 })) { ctx.buildBuilding('rain'); return; }
    if (!G_.build.campfire && ctx.hasRes({ wood: 15, stone: 8 })) { ctx.buildBuilding('campfire'); return; }
    // 7. 装备
    if (ctx.hasRes({ wood: 2 + G_.weaponLevel * 2, stone: Math.ceil((2 + G_.weaponLevel * 2) / 2) })) { ctx.craftWeapon(); return; }
    if (ctx.hasRes({ fiber: 3 + G_.armorLevel * 2, leather: Math.ceil((3 + G_.armorLevel * 2) / 2) })) { ctx.craftArmor(); return; }
    // 7b. 缺水是硬死亡条件：没水时优先回水源区
    if ((G_.res.water || 0) <= 0 && G_.thirst < 75) {
      const wetId = ['beach', 'lake'].find(id => G_.unlocked[id]);
      if (wetId && G_.cur !== wetId) { ctx.enterRegion(wetId); flushT(); return; }
      const zz = ctx.curZone();
      const tt = ctx.hereTile();
      const wet = (x) => x && ctx.TERRAIN[x.t] && ctx.TERRAIN[x.t].gather.indexOf('water') >= 0;
      if (wet(tt) && G_.gatherCd <= 0) { ctx.doGather('water'); flushT(); return; }
      if (zz) {
        for (let i = 0; i < zz.tiles.length; i++) {
          if (!zz.tiles[i].rev || !wet(zz.tiles[i])) continue;
          const tx = i % zz.w, ty = Math.floor(i / zz.w);
          if (ctx.bfsPath(tx, ty)) { walkManual(ctx, tx, ty); return; }
        }
      }
      if (wetId) { ctx.doExplore(); flushT(); return; }
    }
    // 8. 区域推进：目标资源（石/铁/铜/石板）分散在洞穴·山地·遗迹，必须主动换区
    if (Math.random() < 0.20) {
      const wanted = ['cave', 'mountain', 'ruins', 'lake', 'plain', 'forest', 'swamp', 'volcano'];
      const locked = ctx.REGIONS
        .filter(rr => !G_.unlocked[rr.id] && G_.techPoints >= rr.cost)
        .sort((a, b) => wanted.indexOf(a.id) - wanted.indexOf(b.id));
      if (locked.length) { ctx.enterRegion(locked[0].id); flushT(); return; }
      const tgt = wanted.find(id => G_.unlocked[id] && id !== G_.cur);
      if (tgt) { ctx.enterRegion(tgt); flushT(); return; }
    }
    // 9. 采集 / 探索 / 移动
    const z = ctx.curZone();
    if (!z) return;
    const tile = ctx.hereTile();
    const r = Math.random();
    if (tile && tile.t && ctx.TERRAIN[tile.t] && ctx.TERRAIN[tile.t].gather.length && G_.gatherCd <= 0 && r < 0.62) {
      ctx.doGather(ctx.pick(ctx.TERRAIN[tile.t].gather));
      flushT();
      return;
    }
    if (r < 0.78) { ctx.doExplore(); flushT(); return; }
    // 移动：优先未探索方向
    const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]];
    const cand = [];
    for (const d of dirs) {
      const nx = z.px + d[0], ny = z.py + d[1];
      const t = ctx.tileAt(nx, ny);
      if (t) cand.push({ x: nx, y: ny, rev: t.rev });
    }
    if (cand.length) {
      const unk = cand.filter(c => !c.rev);
      const tgt = unk.length && Math.random() < 0.7 ? ctx.pick(unk) : ctx.pick(cand);
      ctx.moveTo(tgt.x, tgt.y);
      flushT();
      return;
    }
    // 换个区域
    const locked = ctx.REGIONS.filter(rr => !G_.unlocked[rr.id] && G_.techPoints >= rr.cost);
    if (locked.length) { ctx.enterRegion(ctx.pick(locked).id); flushT(); return; }
    ctx.gameTick();
  }

  function walkManual(c, tx, ty) {
    const p = c.bfsPath(tx, ty);
    if (!p) return;
    for (const idx of p) {
      const G_ = G();
      if (G_.fight || G_.ending) return;
      const x = idx % c.curZone().w, y = Math.floor(idx / c.curZone().w);
      c.moveTo(x, y);
      flushT();
      if (G().fight) return;
    }
  }

  function fightStep() {
    const G_ = G();
    const f = G_.fight;
    if (!f) return;
    const hpPct = G_.health / G_.maxHealth;
    // 危险时尝试逃跑
    if (hpPct < 0.25 && Math.random() < 0.35) { ctx.fightAct('run'); return; }
    const it = f.intent;
    if ((it === 'heavy' || it === 'unleash') && hpPct < 0.9) { ctx.fightAct('defend'); return; }
    if ((G_.rage || 0) >= ctx.BAL.combat.specCost) { ctx.fightAct('special'); return; }
    if (hpPct < 0.4 && (G_.res.medicine || 0) > 0) { ctx.doMedicine(); }
    ctx.fightAct('attack');
  }

  function resolveModal() {
    if (pending.crisis) {
      const c = pending.crisis; pending.crisis = null;
      const ok = c.choices.filter(ch => !ch.cond || ch.cond());
      const ch = ok.length ? (opts.greedy ? ok[0] : ctx.pick(ok)) : ctx.pick(c.choices);
      ctx.resolveCrisis(ch);
      flushT();
      return true;
    }
    if (pending.event) {
      const e = pending.event; pending.event = null;
      const ok = e.choices.filter(ch => !ch.cond || ch.cond());
      const ch = ok.length ? (opts.greedy ? ok[0] : ctx.pick(ok)) : ctx.pick(e.choices);
      ctx.resolveEvent(e, ch);
      flushT();
      return true;
    }
    return false;
  }

  try {
    while (!G().ending && steps++ < maxSteps) {
      if (resolveModal()) continue;
      if (G().fight) { fightStep(); continue; }
      // 不变量：负重
      const G_ = G();
      const w = ctx.weightOf();
      if (w > ctx.capacity() + 0.001) invariants.push('overweight ' + w.toFixed(1) + '/' + ctx.capacity());
      if (G_.health > G_.maxHealth + 0.001) invariants.push('hp overflow');
      if (G_.energy < 0 || G_.energy > 100) invariants.push('energy out of range ' + G_.energy);
      // 每 3 步推进一次时间（模拟玩家操作节奏）
      if (steps % 3 === 0) { ctx.gameTick(); ticks++; flushT(); }
      else doAction();
    }
  } catch (e) {
    errors.push('runtime: ' + e.message + '\n' + (e.stack || '').split('\n').slice(0, 3).join(' | '));
  }

  const G_ = G();
  return {
    diff: G_.diff,
    branch: G_.branch || wantBranch,
    win: !!G_.endingDone,
    died: G_.ending && !G_.endingDone,
    day: G_.day,
    ticks, steps,
    kills: G_.stats.kills, elite: G_.eliteKills || 0,
    gathered: G_.stats.gathered,
    techPoints: G_.techPoints,
    shelter: G_.shelterLevel,
    zones: G_.zoneCleared || 0,
    quest: G_.quest.idx,
    chainsDone: Object.keys(G_.chainDone || {}).length,
    chainProg: (G_.chains || {}).A + '/' + (G_.chains || {}).B + '/' + (G_.chains || {}).C,
    deathReason: deathReason.v,
    dbg: { ending: !!G_.ending, endDone: !!G_.endingDone, hp: Math.round(G_.health), hunger: Math.round(G_.hunger), thirst: Math.round(G_.thirst), energy: Math.round(G_.energy), last: (G_.log || []).slice(-3).map(l => l.m) },
    rating: G_.endingDone ? (G_.day <= 20 ? 'S' : G_.day <= 35 ? 'A' : G_.day <= 55 ? 'B' : 'C') : 'D',
    errors, invariants
  };
}

/* 允许覆写 BAL 的任意字段，用于压力测试 / 平衡实验 */
function applyOverrides(ctx, o) {
  function merge(t, s) { for (const k in s) { if (s[k] && typeof s[k] === 'object' && !Array.isArray(s[k])) { t[k] = t[k] || {}; merge(t[k], s[k]); } else t[k] = s[k]; } }
  merge(ctx.BAL, o);
}

/* ---------------- 主流程 ---------------- */
function main() {
  const argv = process.argv.slice(2);
  const arg = (n, d) => { const i = argv.indexOf('--' + n); return i >= 0 ? argv[i + 1] : d; };
  const games = parseInt(arg('games', '0'), 10);
  const diff = arg('diff', null);
  const regress = argv.includes('--regress');

  if (regress) {
    const diffs = diff ? [diff] : ['easy', 'normal', 'hard'];
    let bad = 0, total = 0;
    for (const d of diffs) {
      for (let i = 0; i < 3; i++) {
        const r = runGame({ diff: d, maxSteps: 3000 });
        total++;
        if (r.errors.length || r.invariants.length) {
          bad++;
          console.log('❌ ' + d + ' #' + i, JSON.stringify({ errors: r.errors.slice(0, 2), inv: r.invariants.slice(0, 3) }));
        } else {
          console.log('✅ ' + d + ' #' + i + ' day=' + r.day + ' win=' + r.win + ' kills=' + r.kills + ' branch=' + r.branch + ' steps=' + r.steps + ' ' + JSON.stringify(r.dbg));
        }
      }
    }
    console.log('\n回归结果: ' + (total - bad) + '/' + total + ' 通过');
    process.exit(bad ? 1 : 0);
  }

  if (argv.includes('--sweep')) {
    const variants = [
      { name: '基线（当前值）', ov: {} },
      { name: '消耗 -25%', ov: { drain: { hunger: 2, thirst: 3 } } },
      { name: '敌人阶数放缓 (day/6)', ov: { progress: { enemyTierDayDiv: 6 } } },
      { name: '回血翻倍', ov: { drain: { regenBase: 4, regenPerShelterLv: 2 }, rest: { healBase: 8, healPerLv: 4 } } },
      { name: '消耗-25% + 阶数放缓', ov: { drain: { hunger: 2, thirst: 3 }, progress: { enemyTierDayDiv: 6 } } }
    ];
    const rows = [];
    for (const v of variants) {
      const agg = {};
      for (const d of ['easy', 'normal', 'hard']) {
        const rs = [];
        for (let i = 0; i < (parseInt(arg('games', '24'), 10)); i++) {
          rs.push(runGame({ diff: d, maxSteps: 3000, overrides: v.ov }));
        }
        const a = k => +(rs.reduce((s, r) => s + r[k], 0) / rs.length).toFixed(1);
        const days = rs.map(r => r.day).sort((x, y) => x - y);
        agg[d] = { day: a('day'), p25: days[Math.floor(days.length * 0.25)], p75: days[Math.floor(days.length * 0.75)], kills: a('kills'), quest: a('quest') };
      }
      rows.push({ 方案: v.name, ...agg });
    }
    console.log(JSON.stringify(rows, null, 2));
    return;
  }

  const N = games || 30;
  const diffs = diff ? [diff] : ['easy', 'normal', 'hard'];
  const out = {};
  let allErr = 0;
  for (const d of diffs) {
    const rs = [];
    for (let i = 0; i < N; i++) {
      const r = runGame({ diff: d, maxSteps: 3000 });
      if (r.errors.length) { allErr++; if (allErr <= 3) console.log('⚠️ runtime error:', r.errors[0]); }
      rs.push(r);
    }
    const wins = rs.filter(r => r.win);
    const avg = k => (rs.reduce((s, r) => s + r[k], 0) / rs.length);
    const med = k => { const a = rs.map(r => r[k]).sort((x, y) => x - y); return a[Math.floor(a.length / 2)]; };
    const branchDist = {};
    rs.forEach(r => { branchDist[r.branch] = (branchDist[r.branch] || 0) + 1; });
    const deaths = rs.filter(r => !r.win).reduce((m, r) => { m[r.deathReason || '未结束'] = (m[r.deathReason || '未结束'] || 0) + 1; return m; }, {});
    const avgOf = (k) => +(rs.reduce((s, r) => s + (r.dbg[k] || 0), 0) / rs.length).toFixed(1);
    out[d] = {
      games: N,
      winRate: +(wins.length / N * 100).toFixed(1),
      deathState: { hunger: avgOf('hunger'), thirst: avgOf('thirst'), energy: avgOf('energy') },
      avgQuest: +avg('quest').toFixed(2),
      avgDay: +avg('day').toFixed(1),
      medDay: med('day'),
      winAvgDay: wins.length ? +(wins.reduce((s, r) => s + r.day, 0) / wins.length).toFixed(1) : null,
      avgKills: +avg('kills').toFixed(1),
      avgElite: +avg('elite').toFixed(2),
      avgTech: +avg('techPoints').toFixed(1),
      avgZones: +avg('zones').toFixed(2),
      branchDist, deathReasons: deaths,
      rating: wins.reduce((m, r) => { m[r.rating] = (m[r.rating] || 0) + 1; return m; }, {})
    };
  }
  console.log(JSON.stringify(out, null, 2));
  if (allErr) console.log('\n⚠️ 运行期错误合计: ' + allErr);
}

if (require.main === module) main();
module.exports = { runGame, newGame, extractScript };
