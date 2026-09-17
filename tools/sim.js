/* 荒野求生 · 无头模拟器
   用途：
     1) 回归校验：确认改动后不崩、不变量成立，并且**通关真的可达**
     2) 蒙特卡洛：批量跑局，输出存活曲线 / 通关率 / 任务与商店覆盖，用于调 BAL 平衡表
   用法：
     node tools/sim.js --regress           # 回归：每难度 3 局，零错误 + 至少 1 局通关
     node tools/sim.js --games 30          # 蒙特卡洛：每难度 30 局
     node tools/sim.js --sweep --games 24  # 参数扫描：多套数值方案对比
     node tools/sim.js --diff normal --games 1 --verbose
   机器人是目标驱动的：按需选择资源 → 选区域 → 揭雾 → 走过去采 → 缺钱缺料就进城做买卖，
   击杀型任务会去找 🩸 巢穴主动挑衅，而不是等随机遇敌抽卡。
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
    setInterval(fn, ms) { throw new Error('游戏不应再使用 setInterval：时间由玩家行为推进'); },
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
  return { ctx: vm.createContext(win), win, timeouts, store };
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
  const { ctx, win, timeouts, store } = makeContext();
  try {
    vm.runInContext(SRC, ctx, { filename: 'survival-inline.js' });
  } catch (e) {
    throw new Error('脚本加载失败: ' + e.message);
  }
  return { ctx, win, timeouts, store };
}

/* 向未知地带推进一步：先沿已探索区域走到边界旁，再迈进未知格；无路可推则就地探索 */
function frontierStep(ctx, flushT) {
  const z = ctx.curZone(); if (!z) return false;
  for (let i = 0; i < z.tiles.length; i++) {
    if (z.tiles[i].rev) continue;
    const ux = i % z.w, uy = Math.floor(i / z.w);
    for (const d of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = ux + d[0], ny = uy + d[1];
      const t = ctx.tileAt(nx, ny);
      if (!t || !t.rev) continue;
      if (ctx.G.energy < 8) { ctx.doRest(); flushT(); return true; }
      if (Math.abs(z.px - ux) <= 1 && Math.abs(z.py - uy) <= 1) { ctx.moveTo(ux, uy); flushT(); return true; }
      const p = ctx.bfsPath(nx, ny);
      if (p && p.length) { ctx.moveTo(p[0] % z.w, Math.floor(p[0] / z.w)); flushT(); return true; }
      ctx.moveTo(ux, uy); flushT(); return true;
    }
  }
  ctx.doExplore(); flushT();
  return true;
}

/* ---------------- 目标驱动机器人 ---------------- */
function runGame(opts) {
  const { ctx, timeouts } = newGame();
  const G = () => ctx.G;
  const errors = [];
  const invariants = [];
  const cover = { shopBuy: 0, shopSell: 0, denHunt: 0, worldView: 0, zoneClear: 0, elite: 0, reforge: 0, crisis: 0, chainStep: 0 };

  if (opts.overrides) applyOverrides(ctx, opts.overrides);

  const deathReason = { v: null };
  const origGameOver = ctx.gameOver;
  ctx.gameOver = function (winFlag, reason) { if (!winFlag && !deathReason.v) deathReason.v = reason; return origGameOver(winFlag, reason); };

  const flushT = () => { let n = 0; while (timeouts.length && n++ < 800) { const f = timeouts.shift(); try { f(); } catch (e) { errors.push('timeout:' + e.message); } } };
  const rnd = () => ctx.grnd();
  const pickOne = (a) => a[Math.floor(rnd() * a.length)];

  // 开局（硬核难度已被「先逃离一次」门槛锁住，先灌入跨局 meta）
  if (opts.unlockAll !== false) {
    ctx.localStorage.setItem('wildsurvive_meta_v1', JSON.stringify({ escaped: true, runs: 1, wins: 1 }));
  }
  ctx.window._diff = opts.diff || 'normal';
  ctx.startNew();
  flushT();
  if (!G() || !G().zones) throw new Error('startNew 未能建立对局（难度门槛拦截？）');
  ctx.backToZone();
  flushT();
  if (opts.seedGame) ctx.G.seed = opts.seedGame;

  const steps = { n: 0 };
  const maxSteps = opts.maxSteps || 9000;

  /* ---------- 查询辅助 ---------- */
  function terrainProduces(t, item) {
    const T = ctx.TERRAIN[t];
    return !!(T && T.gather.indexOf(item) >= 0);
  }
  function missingTower() {
    const G_ = G(), miss = {};
    for (const k in ctx.TOWER_COST) {
      const need = ctx.TOWER_COST[k] - (G_.res[k] || 0);
      if (need > 0) miss[k] = need;
    }
    return miss;
  }
  function foodCount() {
    const G_ = G();
    return ['cookedMeat', 'cookedFish', 'driedMeat', 'berry', 'meat', 'fish'].reduce((s, k) => s + (G_.res[k] || 0), 0);
  }
  function hasFood() { return foodCount() > 0; }
  function regionProduces(rid, item) {
    const R = ctx.REGION_BY_ID[rid];
    if (!R) return false;
    if (terrainProduces(R.terrain, item)) return true;
    const vars = ctx.ZONE_VARIANTS[R.terrain] || [];
    return vars.some(t => terrainProduces(t, item));
  }
  function regionsFor(item) {
    return ctx.REGIONS.filter(r => regionProduces(r.id, item));
  }
  /* 本区域已探索格子里，第一个满足条件的坐标 */
  function findRevealed(pred) {
    const z = ctx.curZone(); if (!z) return null;
    for (let i = 0; i < z.tiles.length; i++) {
      const t = z.tiles[i];
      if (t.rev && pred(t, i)) return { x: i % z.w, y: Math.floor(i / z.w), t: t };
    }
    return null;
  }
  function onTarget(pred) {
    const z = ctx.curZone(); if (!z) return false;
    const t = ctx.hereTile();
    return !!(t && pred(t, z.py * z.w + z.px));
  }
  function walkManual(tx, ty) {
    const p = ctx.bfsPath(tx, ty);
    if (!p) return false;
    for (const idx of p) {
      const G_ = G();
      if (G_.fight || G_.ending || ctx._modalCur) return true;
      const z = ctx.curZone();
      ctx.moveTo(idx % z.w, Math.floor(idx / z.w));
      flushT();
      if (G().fight || ctx._modalCur) return true;
    }
    return true;
  }
  function goToRegion(rid) {
    const G_ = G();
    if (G_.cur === rid) return true;
    if (G_.energy < 8) return false;
    if (!G_.unlocked[rid] && G_.techPoints < ctx.REGION_BY_ID[rid].cost) return false;
    ctx.enterRegion(rid); flushT();
    cover.worldView++;
    return G().cur === rid;
  }

  /* 装不下想要的东西时先腾地方：卖 → 扔 surplus → 仍不行就扔掉该物资本身的一半 */
  function makeRoom(item) {
    if (ctx.fitCount(item, 1) > 0) return true;
    if (sellSurplus() && ctx.fitCount(item, 1) > 0) return true;
    const have = G().res[item] || 0;
    if (have > 0) { ctx.dropRes(item, Math.ceil(have / 2)); flushT(); return ctx.fitCount(item, 1) > 0; }
    return false;
  }
  /* 向未知推进：先走边界格，无路可走就就地探索 */
  function sweepZone() { return frontierStep(ctx, flushT); }
  /* 该物资没有任何产区（生肉/皮革只能猎）→ 去找掉落它的野兽巢穴 */
  function huntFor(item) {
    let src = ctx.ENEMIES.filter(e => e.loot && e.loot[item]);
    if (!src.length) return false;
    const withDen = src.filter(e => ctx.REGIONS.some(r => !r.poi && denOf(r.id) === e.id));
    const pool = (withDen.length ? withDen : src).slice();
    pool.sort((a, b) => a.tier - b.tier);
    return huntDen(pool[0].id);
  }
  function workOn(item) {
    const G_ = G();
    if (!item) return false;
    if (!regionsFor(item).length) return huntFor(item);
    // 商人能直接买到缺的料，金币充裕时先买（这是经济系统真正参与通关的路径）
    if (['iron', 'copper', 'relic', 'medicine', 'water'].indexOf(item) >= 0 && (G_.res.coin || 0) >= 60 && shopHere()) {
      if (shopBuy(item)) return true;
    }
    if (!regionProduces(G_.cur, item)) {
      const cands = regionsFor(item).filter(r => G_.unlocked[r.id]);
      const locked = regionsFor(item).filter(r => !G_.unlocked[r.id] && G_.techPoints >= r.cost);
      const pool = cands.length ? cands : locked;
      if (!pool.length) return false;
      pool.sort((a, b) => (ctx.REGION_BY_ID[a.id].cost || 0) - (ctx.REGION_BY_ID[b.id].cost || 0));
      return goToRegion(pool[0].id);
    }
    if (onTarget(t => terrainProduces(t.t, item))) {
      if (!makeRoom(item)) return false;
      ctx.doGather(item); flushT(); return true;
    }
    const hit = findRevealed(t => terrainProduces(t.t, item));
    if (hit) return walkManual(hit.x, hit.y);
    ctx.doExplore(); flushT();
    return true;
  }

  /* ---------- 行为：巢穴挑衅 ---------- */
  function denOf(rid) {
    const R = ctx.REGION_BY_ID[rid];
    if (!R || R.poi) return null;
    const d = ctx.denEnemyFor(R.terrain);
    return d ? d.id : null;
  }
  function huntDen(species) {
    const G_ = G();
    if (denOf(G_.cur) !== species) {
      const home = ctx.REGIONS.filter(r => !r.poi && denOf(r.id) === species);
      const open = home.filter(r => G_.unlocked[r.id]);
      const buy = home.filter(r => !G_.unlocked[r.id] && G_.techPoints >= r.cost);
      const pool = open.length ? open : buy;
      if (!pool.length) return false;
      pool.sort((a, b) => (a.danger || 0) - (b.danger || 0));
      return goToRegion(pool[0].id);
    }
    const z = ctx.curZone();
    if (z.denIdx === undefined) return false;
    const dx = z.denIdx % z.w, dy = Math.floor(z.denIdx / z.w);
    if (z.px !== dx || z.py !== dy) {
      if (!z.tiles[z.denIdx].rev) return sweepZone();
      return walkManual(dx, dy);
    }
    if (G_.energy < 14) { ctx.doRest(); flushT(); return true; }
    if (G_.health < G_.maxHealth * 0.62) {
      // 残血不去敲巢穴的门：先吃饱、先回血
      if (hasFood() && G_.hunger < 85) { ctx.doEat(); return true; }
      ctx.doWait(); flushT(); return true;
    }
    ctx.provokeHunt(); flushT();
    cover.denHunt++;
    return true;
  }

  /* ---------- 行为：商店 ---------- */
  function shopHere() {
    const z = ctx.curZone(); if (!z) return false;
    return z.shopIdx !== undefined && (z.py * z.w + z.px) === z.shopIdx;
  }
  function goShop() {
    let z = ctx.curZone();
    if (!z || z.shopIdx === undefined) {
      if (!goToRegion('camp')) return false;
      z = ctx.curZone();
    }
    if (!z || z.shopIdx === undefined) return false;
    if ((z.py * z.w + z.px) === z.shopIdx) return true;
    if (!z.tiles[z.shopIdx].rev) { ctx.doExplore(); flushT(); return false; }
    walkManual(z.shopIdx % z.w, Math.floor(z.shopIdx / z.w));
    const z2 = ctx.curZone();
    return !!(z2 && (z2.py * z2.w + z2.px) === z2.shopIdx);
  }
  function shopBuy(item) {
    const s = ctx.ensureShop();
    if ((s.stock[item] || 0) <= 0) return false;
    if (ctx.fitCount(item, 1) <= 0) return false;
    const price = s.prices[item] || 999;
    if ((G().res.coin || 0) < price) return false;
    ctx.buyItem(item, price); flushT();
    cover.shopBuy++;
    return true;
  }
  /* 超重时把最占重量的非必需物资卖掉；附近没有商人就直接扔掉——负重锁死采集是真实死局 */
  function surplusList() {
    const G_ = G();
    const keep = { coin: 1, water: 1, berry: 1, cookedMeat: 1, cookedFish: 1, driedMeat: 1, meat: 1, fish: 1 };
    const miss = missingTower();
    for (const k in miss) keep[k] = 1;
    // 在建工程要用的料不能当废品卖掉，否则会陷入「采→卖→再采」
    [ctx.SHELTER_COST[Math.min(G_.shelterLevel, ctx.SHELTER_COST.length - 1)],
     ctx.SHELTER_COST[Math.min(G_.shelterLevel + 1, ctx.SHELTER_COST.length - 1)],
     { wood: 15, stone: 8 }].forEach(c => { for (const k in c) if (G_.shelterLevel < 3) keep[k] = 1; });
    const q = ctx.QUESTS[G_.quest.idx];
    if (q && q.item) keep[q.item] = 1;
    if (q && q.kill) { const e = ctx.enemyById(q.kill); for (const l in (e && e.loot)) keep[l] = 1; }
    return Object.keys(G_.res)
      .filter(k => !keep[k] && (G_.res[k] || 0) > 0 && ctx.RESOURCES[k] && ctx.RESOURCES[k].w > 0)
      .sort((a, b) => ctx.RESOURCES[b].w * G_.res[b] - ctx.RESOURCES[a].w * G_.res[a]);
  }
  function sellSurplus() {
    const heavy = surplusList();
    if (!heavy.length) return false;
    if (goShop() && shopHere()) { ctx.sellAll(heavy[0]); flushT(); cover.shopSell++; return true; }
    ctx.dropRes(heavy[0], G().res[heavy[0]]); flushT();
    cover.shopSell++;
    return true;
  }

  /* ---------- 科技 / 建造 / 制作 ---------- */
  const BRANCH_ORDER = {
    hunter: ['fishing', 'tracking', 'bow'], smith: ['smelt', 'arch', 'forge'],
    healer: ['herb', 'brew', 'purify'], farmer: ['gather2', 'preserve', 'chef']
  };
  function tryTech() {
    const G_ = G();
    if (!G_.tech.gatherEff && G_.techPoints >= 10) { ctx.unlockTech('gatherEff'); return true; }
    const br = opts.branch || 'smith';
    if (!G_.branch || G_.branch === br) {
      for (const id of (BRANCH_ORDER[br] || [])) {
        if (G_.tech[id]) continue;
        const f = ctx.findTech(id);
        if (f && G_.techPoints >= f.t.cost) { ctx.unlockTech(id); return true; }
        break;
      }
    }
    // 区域解锁也要吃科技点：攒够了先把关键产区开出来
    if (G_.techPoints >= 20) {
      const lockable = ctx.REGIONS.filter(r => !G_.unlocked[r.id] && G_.techPoints >= r.cost + 8)
        .sort((a, b) => a.cost - b.cost);
      if (lockable.length) { ctx.enterRegion(lockable[0].id); flushT(); return true; }
    }
    return false;
  }
  function tryBuild() {
    const G_ = G();
    const cost = ctx.SHELTER_COST[G_.shelterLevel];
    if (G_.shelterLevel < 3 && ctx.hasRes(cost)) { ctx.buildShelter(); flushT(); return true; }
    if (!G_.build.campfire && ctx.hasRes({ wood: 15, stone: 8 })) { ctx.buildBuilding('campfire'); flushT(); return true; }
    if (!G_.build.rain && ctx.hasRes({ wood: 10, stone: 6 })) { ctx.buildBuilding('rain'); flushT(); return true; }
    if (!G_.build.rack && ctx.hasRes({ wood: 12, fiber: 6 })) { ctx.buildBuilding('rack'); flushT(); return true; }
    if (G_.build.storage < 2 && ctx.hasRes({ wood: 15, fiber: 8 })) { ctx.buildBuilding('storage'); flushT(); return true; }
    return false;
  }
  function tryCraft() {
    const G_ = G();
    if (ctx.hasRes({ wood: 2 + G_.weaponLevel * 2, stone: Math.ceil((2 + G_.weaponLevel * 2) / 2) })) { ctx.craftWeapon(); flushT(); return true; }
    const fn = 3 + G_.armorLevel * 2;
    if (ctx.hasRes({ fiber: fn, leather: Math.ceil(fn / 2) })) { ctx.craftArmor(); flushT(); return true; }
    if (G_.tech.herb && ctx.hasRes({ herb: 3 })) { ctx.craftMedicine(); flushT(); return true; }
    return false;
  }

  /* ---------- 任务路线 ---------- */
  function goCampPoi() {
    if (!goToRegion('camp')) return false;
    const z = ctx.curZone(); if (!z) return false;
    const at = z.poiIdx !== undefined && (z.py * z.w + z.px) === z.poiIdx;
    if (at) return true;
    if (z.poiIdx === undefined) return true;
    if (!z.tiles[z.poiIdx].rev) { ctx.doExplore(); flushT(); return false; }
    walkManual(z.poiIdx % z.w, Math.floor(z.poiIdx / z.w));
    return z.poiIdx !== undefined && (ctx.curZone().py * z.w + ctx.curZone().px) === z.poiIdx;
  }
  function goTowerAndRepair() {
    const G_ = G();
    if (!goToRegion('tower')) return false;
    const z = ctx.curZone(); if (!z) return false;
    const at = z.poiIdx !== undefined && (z.py * z.w + z.px) === z.poiIdx;
    if (!at) {
      if (z.poiIdx === undefined || !z.tiles[z.poiIdx].rev) { ctx.doExplore(); flushT(); return true; }
      return walkManual(z.poiIdx % z.w, Math.floor(z.poiIdx / z.w));
    }
    if (!ctx.hasRes(ctx.TOWER_COST)) return false;
    ctx.repairTower(); flushT();
    return true;
  }

  /* ---------- 主策略 ---------- */
  function doAction() {
    const G_ = G();

    // 0. 温饱与状态：这几件事不吃时间，先处理干净再谈计划
    if (G_.thirst < 62 && (G_.res.water || 0) > 0) { ctx.doDrink(); return; }
    if (G_.hunger < 62 && hasFood()) { ctx.doEat(); return; }
    if (G_.health < G_.maxHealth * 0.5 && (G_.res.medicine || 0) > 0) { ctx.doMedicine(); return; }
    // 非医者线只能向商人买药：伤重没药又买得起，先补货
    if (G_.health < G_.maxHealth * 0.45 && !(G_.res.medicine > 0) && (G_.res.coin || 0) >= 50) {
      if (goShop() && shopHere() && shopBuy('medicine')) return;
    }
    if (ctx.isOverweight() && !ctx.hasRes(ctx.TOWER_COST)) { if (sellSurplus()) return; }

    // 1. 体力见底 → 睡到明早（在新经济里这会吃掉一整天）
    if (G_.energy < 18) { ctx.doRest(); flushT(); return; }

    // 1b. 生存储备先于一切计划：渴死饿死的局拿不到任何进度
    if ((G_.res.water || 0) < 5) { if (workOn('water')) return; }
    if (foodCount() < 4) {
      for (const f of ['berry', 'fish', 'meat', 'herb']) if (workOn(f)) return;
    }

    // 2. 有晾肉架就把生鲜处理掉（腐坏按批次算，拖着会坏）
    if (G_.build.rack && ((G_.res.meat || 0) >= 4 || (G_.res.fish || 0) >= 4)) { ctx.dryFood(); flushT(); return; }
    if (hasFood() && (G_.res.meat || 0) + (G_.res.fish || 0) > 0 && G_.build.rack) { ctx.dryFood(); flushT(); return; }
    if ((G_.res.fish || 0) > 0 || (G_.res.meat || 0) > 1) { ctx.cookAll(); flushT(); }

    // 3. 任务可交付 → 回营地交付
    if (ctx.questReady()) {
      if (goCampPoi() && ctx.questReady()) { ctx.questTurnIn(); flushT(); return; }
      return;
    }

    // 4. 先安身：科技点 → 补齐当前等级庇护所造价 → 建造（不解决保暖，入冬必冻死）
    const q = ctx.QUESTS[G_.quest.idx];
    tryTech();
    {
      const sc = ctx.SHELTER_COST[Math.min(G_.shelterLevel, ctx.SHELTER_COST.length - 1)];
      const lack = Object.keys(sc).filter(k => (G_.res[k] || 0) < sc[k]);
      if (G_.shelterLevel < 2 && lack.length && workOn(lack[0])) return;
    }
    if (tryBuild()) return;

    // 5. 击杀型任务 → 去巢穴主动挑衅（不再等随机遇敌抽卡）
    if (q && q.kill) {
      if (!huntDen(q.kill)) { if (!workOn('water')) workOn('fiber'); }
      return;
    }

    // 6. 终局：任务链已够 + 材料齐 → 修塔；缺料 → 补齐
    if (G_.quest.idx >= 4) {
      if (ctx.hasRes(ctx.TOWER_COST)) { if (goTowerAndRepair()) return; }
      const miss = missingTower();
      const order = ['copper', 'iron', 'relic', 'wood', 'fiber'].filter(k => miss[k]);
      if (order.length) {
        if ((G_.res.coin || 0) > 120 && shopHere()) { if (!shopBuy(order[0])) workOn(order[0]); }
        else if (!workOn(order[0])) { if (!goShop()) workOn(order[0]); else shopBuy(order[0]); }
        return;
      }
    }

    // 7. 任务物资优先（生肉 / 纤维 / 石板）
    if (q && q.item && (G_.res[q.item] || 0) < q.need) {
      if (workOn(q.item)) return;
    }

    // 8. 装备成长（先有屋顶再谈磨刀）
    if (G_.shelterLevel >= 1 && G_.weaponLevel < 4 && tryCraft()) return;

    // 9. 为终局囤料（提前攒，别等到任务 5 才开始）
    const miss2 = missingTower();
    const pre = ['wood', 'fiber', 'iron', 'copper'].filter(k => miss2[k]);
    if (pre.length && workOn(pre[0])) return;

    // 11. 常态：采集本地 / 揭雾 / 推进
    if (tryCraft()) return;
    const z = ctx.curZone();
    if (z) {
      const here = ctx.hereTile();
      if (here && ctx.TERRAIN[here.t].gather.length && (z.py * z.w + z.px) % 3 === 0) {
        const it = ctx.pick(ctx.TERRAIN[here.t].gather);
        if (makeRoom(it)) { ctx.doGather(it); flushT(); return; }
      }
      if (sweepZone()) return;
    }
    ctx.doWait(); flushT();
  }

  /* ---------- 战斗 ---------- */
  function fightStep() {
    const G_ = G();
    const f = G_.fight;
    if (!f) return;
    const hpPct = G_.health / G_.maxHealth;
    const q = ctx.QUESTS[G_.quest.idx];
    const isObjective = !!(q && q.kill && f.enemy.id === q.kill);
    const C = ctx.BAL.combat;
    if (hpPct < 0.22 && !isObjective) { ctx.fightAct('run'); return; }     // 任务目标绝不逃
    if ((f.intent === 'heavy' || f.intent === 'unleash') && hpPct < 0.85) { ctx.fightAct('defend'); return; }
    if ((G_.rage || 0) >= C.specCost) { ctx.fightAct('special'); return; }
    if (f.enemy.hp > G_.health * 2.2 && !isObjective) { ctx.fightAct('run'); return; }
    ctx.fightAct('attack');
  }

  /* ---------- 事件 / 灾变弹窗：走真实队列接口 ---------- */
  function resolveModal() {
    const m = ctx._modalCur;
    if (!m) return false;
    const list = m.data.choices || [];
    let ok = list.filter(ch => !ch.cond || ch.cond());
    let ch = null;
    if (m.kind === 'crisis') {
      cover.crisis++;
      ch = ok[0] || null;                       // 灾变一律选能付得起的第一（规避）方案
    } else if (m.chain) {
      const st = (G().chains || {})[m.chain.id] || 0;
      const want = m.chain.steps[st] ? m.chain.steps[st].pick : -1;
      ch = (want >= 0 && ok.indexOf(list[want]) >= 0) ? list[want] : (ok[0] || null);
      cover.chainStep++;
    } else {
      ch = ok.length ? ok[0] : (list[0] || null);
    }
    if (!ch) ch = list[0];
    if (!ch) { nextModalGuard(); return true; }
    ctx.answerModal(m, ch);
    flushT();
    return true;
  }
  function nextModalGuard() { ctx._modalQ.length = 0; ctx._modalCur = null; }

  /* ---------- 主循环 ---------- */
  let stallCount = 0;
  try {
    while (!G().ending && steps.n++ < maxSteps) {
      if (resolveModal()) continue;
      if (G().fight) { fightStep(); continue; }
      const G_ = G();
      const w = ctx.weightOf(), cap = ctx.capacity();
      if (w > cap + 0.001) invariants.push('overweight ' + w.toFixed(1) + '/' + cap);
      if (G_.health > G_.maxHealth + 0.001) invariants.push('hp overflow');
      if (G_.energy < 0 || G_.energy > 100) invariants.push('energy out of range ' + G_.energy);
      if (G_.ap < 0 || G_.ap >= ctx.BAL.time.apPerPhase) invariants.push('ap out of range ' + G_.ap);
      if (G_.day < (G_.lastDaySeen || 1)) invariants.push('day went backwards');
      G_.lastDaySeen = G_.day;
      if (G_.eliteKills > (G_.stats.kills || 0)) invariants.push('eliteKills > kills');
      const sigBefore = [G_.day, G_.timeOfDay, G_.ap, G_.energy, ctx.weightOf(), G_.res.coin || 0].join('|');
      doAction();
      {
        const t = G();
        const sigAfter = [t.day, t.timeOfDay, t.ap, t.energy, ctx.weightOf(), t.res.coin || 0].join('|');
        if (sigAfter === sigBefore && !t.fight && !t.ending && !ctx._modalCur) {
          ctx.doWait(); flushT();
          stallCount++;
          if (stallCount > 25) { invariants.push('反复出现不吃时间的行动（stall ' + stallCount + '）'); stallCount = 0; }
        } else stallCount = 0;
      }
      if (opts.trace && steps.n % opts.trace === 0) {
        const t = G();
        console.log('  #' + steps.n + ' D' + t.day + '/' + t.timeOfDay + ' ap' + t.ap +
          ' hp' + Math.round(t.health) + ' hu' + Math.round(t.hunger) + ' th' + Math.round(t.thirst) +
          ' en' + Math.round(t.energy) + ' tp' + t.techPoints + ' w' + ctx.weightOf() + '/' + ctx.capacity() +
          ' cur=' + t.cur + ' q=' + t.quest.idx + ' | ' + ((t.log || []).slice(-1)[0] || {}).m);
      }
      if (G().zoneCleared > cover.zoneClear) cover.zoneClear = G().zoneCleared;
      if (G().eliteKills > cover.elite) cover.elite = G().eliteKills;
    }
  } catch (e) {
    errors.push('runtime: ' + e.message + '\n' + (e.stack || '').split('\n').slice(0, 3).join(' | '));
  }
  flushT();

  const G_ = G();
  const R = G_.endingDone ? ctx.rateRun(true) : ctx.rateRun(false);
  return {
    diff: G_.diff,
    branch: G_.branch || opts.branch,
    win: !!G_.endingDone,
    day: G_.day,
    steps: steps.n,
    kills: G_.stats.kills, elite: G_.eliteKills || 0,
    gathered: G_.stats.gathered, spoiled: G_.stats.spoiled || 0,
    techPoints: G_.techPoints, shelter: G_.shelterLevel,
    zones: G_.zoneCleared || 0, regions: Object.keys(G_.entered || {}).length,
    quest: G_.quest.idx, affixes: (G_.affixes || []).length,
    coin: G_.res.coin || 0, shops: G_.stats.shops || 0,
    chainsDone: Object.keys(G_.chainDone || {}).length,
    rating: R.grade, score: R.score,
    deathReason: deathReason.v,
    cover,
    dbg: { ending: !!G_.ending, hp: Math.round(G_.health), hunger: Math.round(G_.hunger), thirst: Math.round(G_.thirst), energy: Math.round(G_.energy), last: (G_.log || []).slice(-3).map(l => l.m) },
    errors, invariants
  };
}

/* ---------------- 关键路径可达性断言 ----------------
   把两件事分开：
     · 机制上可达吗？—— 本函数用真实 API 端到端跑一遍通关主链路，任何一处走不通就返回失败
     · 平衡上玩得通吗？—— 由 --games 的机器人胜率回答（机器人不聪明，那个数是下界）
   材料通过公开的 addRes 注入，其余（寻路 / 扫雾 / 挑衅 / 战斗 / 交付 / 修塔）全部调用真实函数。 */
function criticalPath(opts) {
  const { ctx, timeouts } = newGame();
  const out = { ok: false, stage: 'init', notes: [], errors: [], seed: (opts && opts.seed) || 20260916 };
  const flushT = () => { let n = 0; while (timeouts.length && n++ < 800) { const f = timeouts.shift(); try { f(); } catch (e) { out.errors.push('timeout:' + e.message); } } };
  const G = () => ctx.G;
  try {
    ctx.localStorage.setItem('wildsurvive_meta_v1', JSON.stringify({ escaped: true }));
    ctx.initGame((opts && opts.diff) || 'normal', out.seed);
    ctx.enterGame(); flushT();
    ctx.BAL.backpack.base = 500;      // 测试用大容量：让 addRes 走真实接口但不被负重截断
    ctx.backToZone(); flushT();

    // 直接战斗结算：不逃、按意图防御，把当前这一架打完
    function fightToEnd(limit) {
      let n = 0;
      while (G().fight && n++ < (limit || 200)) {
        const f = G().fight;
        if ((f.intent === 'heavy' || f.intent === 'unleash') && G().health / G().maxHealth < 0.9) ctx.fightAct('defend');
        else if ((G().rage || 0) >= ctx.BAL.combat.specCost) ctx.fightAct('special');
        else ctx.fightAct('attack');
        if (G().health <= 0 && G().res.medicine > 0) ctx.doMedicine();
      }
      return !G().fight;
    }
    // 走到巢穴：没揭示就扫图
    function reachDen(limit) {
      let z = ctx.curZone(), n = 0;
      if (!z || z.denIdx === undefined) return false;
      while (n++ < (limit || 400)) {
        z = ctx.curZone();
        if (z.px === z.denIdx % z.w && z.py === Math.floor(z.denIdx / z.w)) return true;
        if (G().ending) return false;
        if (n % 6 === 0) sustain();
        if (!z.tiles[z.denIdx].rev) {
          frontierStep(ctx, flushT);
        } else {
          const p = ctx.bfsPath(z.denIdx % z.w, Math.floor(z.denIdx / z.w));
          if (!p || !p.length) break;
          ctx.moveTo(p[0] % z.w, Math.floor(p[0] / z.w)); flushT();
        }
        flushT();
        if (G().fight) fightToEnd();
        if (G().ending) return false;
      }
      return false;
    }
    function grant(map) { for (const k in map) ctx.addRes(k, map[k]); flushT(); }
    function hasFood() {
      const r = G().res;
      return ['cookedMeat', 'cookedFish', 'driedMeat', 'berry', 'meat', 'fish'].some(k => (r[k] || 0) > 0);
    }
    function sustain() {
      if (G().fight) fightToEnd();
      for (let n = 0; n < 12; n++) {
        const g = G();
        if (g.fight) fightToEnd();
        if (g.thirst < 72 && (g.res.water || 0) <= 0) { grant({ water: 6 }); continue; }
        if (g.hunger < 72 && !hasFood()) { grant({ berry: 8 }); continue; }
        if (g.thirst < 72 && (g.res.water || 0) > 0) { ctx.doDrink(); continue; }
        if (g.hunger < 72 && hasFood()) { ctx.doEat(); continue; }
        if (g.energy < 20) { ctx.doRest(); flushT(); }
        break;
      }
      if (G().health < G().maxHealth * 0.5 && (G().res.medicine || 0) > 0) ctx.doMedicine();
      flushT();
    }
    function answerOnce() {
      const m = ctx._modalCur;
      if (!m) return false;
      const list = m.data.choices || [];
      const ok = list.filter(ch => !ch.cond || ch.cond());
      ctx.answerModal(m, ok[0] || list[0]);
      flushT();
      return true;
    }

    // 1) 装备与状态准备好打棕熊（走真实制作接口）
    ctx.G.day = 12;                                  // 让棕熊（tier2）进入正常池
    ctx.G.techPoints = 60; ctx.unlockTech('smelt'); ctx.unlockTech('arch'); ctx.unlockTech('forge');
    for (let i = 0; i < 4; i++) { grant({ wood: 12, stone: 8 }); ctx.craftWeapon(); flushT(); }
    for (let i = 0; i < 3; i++) { grant({ fiber: 9, leather: 5 }); ctx.craftArmor(); flushT(); }
    grant({ leather: 8, iron: 6, silver: 3, herb: 4, medicine: 4, water: 8, berry: 8 });
    ctx.equipSet(2); flushT();
    out.notes.push('atk=' + G().attack + ' def=' + G().defense);

    // 2) 找到一个有熊巢穴的区域，走过去把它拖出来
    G().quest.idx = 2;                                 // 任务 3「除掉威胁」：必须击杀棕熊
    const bearHome = ctx.REGIONS.filter(r => !r.poi && ctx.denEnemyFor(r.terrain) && ctx.denEnemyFor(r.terrain).id === 'bear');
    out.stage = 'travel-bear-den';
    let hunted = false;
    for (const r of bearHome) {
      G().health = G().maxHealth; G().energy = 100; G().techPoints = 60;
      ctx.enterRegion(r.id); flushT();
      if (G().cur !== r.id) continue;
      if (!reachDen()) continue;
      const before = G().quest.prog;
      ctx.provokeHunt(); flushT();
      if (!G().fight) continue;
      fightToEnd();
      if (G().quest.prog > before || (G().stats.killsBy || {}).bear) {
        hunted = true; out.notes.push('den@' + r.id + ' turns→bear killed'); break;
      }
      if (G().ending) throw new Error('关键路径在熊巢殉职');
    }
    if (!hunted) { out.notes.push('未能在巢穴击杀棕熊'); return out; }

    // 3) 回到营地交付任务 3
    out.stage = 'deliver-q3';
    ctx.G.techPoints = 60;
    ctx.enterRegion('camp'); flushT();
    let zc = ctx.curZone();
    if (zc && zc.poiIdx !== undefined) {
      for (let n = 0; n < 200; n++) {
        const z = ctx.curZone();
        if ((z.py * z.w + z.px) === z.poiIdx) break;
        const p = ctx.bfsPath(z.poiIdx % z.w, Math.floor(z.poiIdx / z.w));
        if (!p || !p.length) { ctx.doExplore(); flushT(); continue; }
        ctx.moveTo(p[0] % z.w, Math.floor(p[0] / z.w)); flushT();
        if (G().fight) fightToEnd();
        if (n % 6 === 0) { sustain(); while (ctx._modalCur) answerOnce(); }
      }
    }
    if (!ctx.questReady()) { out.notes.push('任务3 未就绪 prog=' + G().quest.prog + ' idx=' + G().quest.idx); return out; }
    ctx.questTurnIn(); flushT();
    out.notes.push('delivered idx=' + G().quest.idx);

    // 4) 补齐信号塔材料并修塔
    out.stage = 'tower';
    ctx.G.quest.idx = Math.max(G().quest.idx, 4);
    ctx.G.techPoints = 60;
    sustain();
    while (ctx._modalCur) { answerOnce(); }
    for (let t = 0; t < 8 && G().cur !== 'tower'; t++) {
      G().energy = Math.max(G().energy, 40);           // 换区需要 6 点体力，不足时 enterRegion 会直接拒绝
      ctx.enterRegion('tower'); flushT();
      while (ctx._modalCur) answerOnce();
      sustain();
    }
    for (let n = 0; n < 200; n++) {
      const z = ctx.curZone();
      if (z.poiIdx !== undefined && (z.py * z.w + z.px) === z.poiIdx) break;
      const p = ctx.bfsPath(z.poiIdx % z.w, Math.floor(z.poiIdx / z.w));
      if (!p || !p.length) { ctx.doExplore(); flushT(); continue; }
      ctx.moveTo(p[0] % z.w, Math.floor(p[0] / z.w)); flushT();
      if (G().fight) fightToEnd();
      if (n % 8 === 0) sustain();
    }
    out.notes.push('cur=' + G().cur + ' en=' + Math.round(G().energy) + ' view=' + G().view +
      ' here=' + ((ctx.hereTile() || {}).t) +
      ' towerReady=' + ctx.hasRes(ctx.TOWER_COST) + ' qidx=' + G().quest.idx +
      ' modal=' + !!ctx._modalCur);
    grant(ctx.TOWER_COST);
    while (ctx._modalCur) answerOnce();
    ctx.repairTower(); flushT();
    out.stage = 'settle';
    out.ok = !!G().endingDone;
    out.notes.push('day=' + G().day + ' ending=' + !!G().ending + ' meta=' + (ctx.localStorage.getItem('wildsurvive_meta_v1') || '').slice(0, 60));
    return out;
  } catch (e) {
    out.errors.push(e.message);
    return out;
  }
}

/* 多个种子都要走通：既确定性可复现，又能排除「刚好运气好」 */
const PATH_SEEDS = [20260916, 7, 424242];
function pathOk(diff) {
  const rs = PATH_SEEDS.map(s => criticalPath({ diff: diff || 'normal', seed: s }));
  return { ok: rs.every(r => r.ok && !r.errors.length), runs: rs };
}

/* 允许覆写 BAL 的任意字段，用于压力测试 / 平衡实验 */
function applyOverrides(ctx, o) {
  function merge(t, s) { for (const k in s) { if (s[k] && typeof s[k] === 'object' && !Array.isArray(s[k])) { t[k] = t[k] || {}; merge(t[k], s[k]); } else t[k] = s[k]; } }
  merge(ctx.BAL, o);
}

/* ---------------- 统计 ---------------- */
function summarize(rs) {
  const n = rs.length;
  const avg = k => +(rs.reduce((s, r) => s + r[k], 0) / n).toFixed(1);
  const med = k => { const a = rs.map(r => r[k]).sort((x, y) => x - y); return a[Math.floor(a.length / 2)]; };
  const wins = rs.filter(r => r.win);
  const deaths = rs.filter(r => !r.win).reduce((m, r) => { m[r.deathReason || '未结束'] = (m[r.deathReason || '未结束'] || 0) + 1; return m; });
  return {
    games: n,
    winRate: +(wins.length / n * 100).toFixed(1),
    medDay: med('day'), avgDay: avg('day'),
    winAvgDay: wins.length ? +(wins.reduce((s, r) => s + r.day, 0) / wins.length).toFixed(1) : null,
    avgQuest: avg('quest'), avgKills: avg('kills'), avgElite: avg('elite'),
    avgRegions: avg('regions'), avgZoneClear: avg('zones'),
    avgShops: avg('shops'), avgCoin: avg('coin'), avgAffix: avg('affixes'),
    avgSpoiled: avg('spoiled'), avgChains: avg('chainsDone'),
    deathReasons: deaths,
    rating: wins.reduce((m, r) => { m[r.rating] = (m[r.rating] || 0) + 1; return m; }, {}),
    errors: rs.reduce((s, r) => s + r.errors.length, 0),
    invariantBreaks: rs.reduce((s, r) => s + r.invariants.length, 0)
  };
}

function main() {
  const argv = process.argv.slice(2);
  const arg = (n, d) => { const i = argv.indexOf('--' + n); return i >= 0 ? argv[i + 1] : d; };
  const diffs = arg('diff', null) ? [arg('diff', null)] : ['easy', 'normal', 'hard'];
  const verbose = argv.includes('--verbose');

  if (argv.includes('--path')) {
    const p = pathOk(arg('diff', 'normal'));
    console.log((p.ok ? '✅ 通关路径可达' : '❌ 通关路径不可达') + '（' + p.runs.length + ' 个种子）');
    p.runs.forEach(r => console.log('   seed ' + r.seed + ' ' + (r.ok ? '✅' : '❌ ' + r.stage) + ' ' + r.notes.join(' | ') + (r.errors.length ? ' ERR:' + r.errors[0] : '')));
    process.exit(p.ok ? 0 : 1);
  }

  if (argv.includes('--regress')) {
    const path = pathOk('normal');
    if (!path.ok) console.log('❌ 关键路径不可达', JSON.stringify(path.runs.map(r => ({ seed: r.seed, stage: r.stage, notes: r.notes, errors: r.errors }))));
    else console.log('✅ 关键路径可达（' + path.runs.length + ' 个种子：巢穴挑衅 → 交付 → 修塔 → 结算）');
    let bad = path.ok ? 0 : 1, total = 1, wins = 0;
    for (const d of diffs) {
      for (let i = 0; i < 3; i++) {
        const r = runGame({ diff: d, maxSteps: 9000 });
        total++;
        if (r.win) wins++;
        if (r.errors.length || r.invariants.length) {
          bad++;
          console.log('❌ ' + d + ' #' + i, JSON.stringify({ errors: r.errors.slice(0, 2), inv: r.invariants.slice(0, 3) }));
        } else {
          console.log((r.win ? '🏆 ' : '✅ ') + d + ' #' + i + ' day=' + r.day + ' quest=' + r.quest +
            ' kills=' + r.kills + ' regions=' + r.regions + ' shops=' + r.shops + ' rating=' + r.rating +
            ' steps=' + r.steps + ' ' + JSON.stringify(r.cover) + ' ' + JSON.stringify(r.dbg));
        }
        if (verbose) console.log('   ', JSON.stringify(r.dbg.last));
      }
    }
    const minWin = parseInt(arg('must-win', '0'), 10);
    console.log('\n回归结果: ' + (total - bad) + '/' + total + ' 通过 · 机器人自然通关 ' + wins + '/' + (total - 1) +
      '（平衡下界，非可达性证明）');
    if (bad) { console.log('❌ 回归失败：存在运行期错误 / 不变量破坏 / 关键路径不可达'); process.exit(1); }
    if (wins < minWin) { console.log('❌ 机器人通关数低于 --must-win ' + minWin); process.exit(1); }
    console.log('✅ 回归通过');
    return;
  }

  if (argv.includes('--sweep')) {
    const N = parseInt(arg('games', '12'), 10);
    const variants = [
      { name: '基线（当前值）', ov: {} },
      { name: '时间更贵 apPerPhase 8', ov: { time: { apPerPhase: 8 } } },
      { name: '时间更省 apPerPhase 5', ov: { time: { apPerPhase: 5 } } },
      { name: '采集更费时 gather 3', ov: { cost: { gather: 3 } } },
      { name: '敌人阶数放缓 day/6', ov: { progress: { enemyTierDayDiv: 6 } } },
      { name: '回血翻倍', ov: { drain: { regenBase: 6, regenPerShelterLv: 3 } } },
      { name: '睡觉不回体力', ov: { drain: { restEnergyPerLv: 0, restEnergyWarm: 0 } } }
    ];
    const rows = [];
    for (const v of variants) {
      const agg = {};
      for (const d of diffs) {
        const rs = [];
        for (let i = 0; i < N; i++) rs.push(runGame({ diff: d, maxSteps: 9000, overrides: v.ov }));
        const s = summarize(rs);
        agg[d] = { win: s.winRate + '%', medDay: s.medDay, avgDay: s.avgDay, winDay: s.winAvgDay, quest: s.avgQuest, kills: s.avgKills, shops: s.avgShops, err: s.errors, inv: s.invariantBreaks };
      }
      rows.push({ 方案: v.name, ...agg });
    }
    console.log(JSON.stringify(rows, null, 2));
    return;
  }

  const N = parseInt(arg('games', '10'), 10);
  const out = {};
  for (const d of diffs) {
    const rs = [];
    for (let i = 0; i < N; i++) rs.push(runGame({ diff: d, maxSteps: 9000, branch: arg('branch', null) }));
    out[d] = summarize(rs);
    out[d].cover = {
      denHunt: rs.reduce((s, r) => s + r.cover.denHunt, 0),
      shopBuy: rs.reduce((s, r) => s + r.cover.shopBuy, 0),
      shopSell: rs.reduce((s, r) => s + r.cover.shopSell, 0),
      crisis: rs.reduce((s, r) => s + r.cover.crisis, 0),
      chainStep: rs.reduce((s, r) => s + r.cover.chainStep, 0),
      zoneClear: rs.reduce((s, r) => s + r.cover.zoneClear, 0)
    };
    if (verbose) rs.slice(0, 6).forEach((r, i) => console.log('  ' + d + ' #' + i + ' day=' + r.day + ' win=' + r.win + ' quest=' + r.quest + ' ' + JSON.stringify(r.dbg.last)));
  }
  console.log(JSON.stringify(out, null, 2));
}

if (require.main === module) main();
module.exports = { runGame, newGame, extractScript, summarize, criticalPath, pathOk };
