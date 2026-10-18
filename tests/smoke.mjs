// Smoke test for Pilot Gap. Run before every push:
//   node tests/smoke.mjs
// Needs Node 18+ and Playwright (npm i playwright, then npx playwright install chromium),
// or set PLAYWRIGHT_PATH to an existing Playwright install.
// It serves the repo folder locally, opens the page at phone size, runs practice mode,
// checks one-truck and two-truck layouts, gap settings, Settings and Mute,
// and saves tests/phone.png and tests/phone-two-trucks.png to look at.
// It exits with code 1 if any check fails.
import { createRequire } from 'module';
import http from 'http'; import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const srv = http.createServer((q, r) => {
  const f = path.join(root, decodeURIComponent(q.url.split('?')[0]).replace(/\/$/, '/index.html'));
  if (!f.startsWith(root) || !fs.existsSync(f)) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': f.endsWith('.js') ? 'text/javascript' : 'text/html' });
  r.end(fs.readFileSync(f));
}).listen(8765);

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 375, height: 812 } });
const errors = []; p.on('pageerror', e => errors.push(e.message));
const fails = [];
const check = (name, ok, got) => { console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : '  got: ' + JSON.stringify(got))); if (!ok) fails.push(name); };

await p.goto('http://localhost:8765/');
check('relay library loads from repo', await p.evaluate(() => typeof mqtt) === 'object');
check('default trip code is 1234', (await p.inputValue('#trip')) === '1234', await p.inputValue('#trip'));
check('version shown', /\d+\.\d+\.\d+/.test(await p.textContent('.ver')), await p.textContent('.ver'));
check('screen options off by default', !(await p.isChecked('#keepOn')) && !(await p.isChecked('#forceOn')));
check('four roles offered', (await p.locator('.role').count()) === 4);

// Truck 1 sets the gaps; defaults front 500-2000, between trucks 0-100, rear 100-1000
await p.click('[data-role=truck]');
check('Truck 1 sees gap settings', await p.isVisible('#s_fmin'));
const d = await p.evaluate(() => ['s_fmin','s_fmax','s_mmin','s_mmax','s_rmin','s_rmax'].map(i => document.getElementById(i).value).join(','));
check('default gaps 500-2000 / 0-100 / 100-1000', d === '500,2000,0,100,100,1000', d);
check('between-trucks row hidden with one truck', !(await p.isVisible('#s_mmin')));
await p.click('[data-role=lead]');
check('pilots do not get gap settings', !(await p.isVisible('#s_fmin')) && await p.isVisible('#setupRangeNote'));

// One truck, practice mode, as Truck 1
await p.click('[data-role=truck]'); await p.check('#practice'); await p.click('#start'); await p.waitForTimeout(1500);
check('one truck: two gaps', (await p.locator('#gaps .gap').count()) === 2);
check('practice mode shows a gap', /\d/.test(await p.textContent('#gap_front_num')), await p.textContent('#gap_front_num'));
check('rear range shown 100-1000', (await p.textContent('#gap_rear_range')) === '100–1000 m', await p.textContent('#gap_rear_range'));
check('settings hidden at start', !(await p.isVisible('#settings')));
await p.click('#toggleSettings'); await p.waitForTimeout(500);
check('settings opens', await p.isVisible('#settings'));
check('Truck 1 can edit gaps in settings', await p.isVisible('#l_fmin'));
await p.click('#toggleSettings'); await p.waitForTimeout(500);
check('settings closes', !(await p.isVisible('#settings')));
await p.click('#mute'); await p.waitForTimeout(1200);
check('mute turns on for 5 min', /Unmute \((5:00|4:5\d)\)/.test(await p.textContent('#mute')), await p.textContent('#mute'));
await p.click('#mute'); await p.waitForTimeout(1200);
check('mute turns off', (await p.textContent('#mute')) === 'Mute 5 min', await p.textContent('#mute'));
check('no horizontal scroll (one truck)', await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await p.screenshot({ path: path.join(root, 'tests', 'phone.png') });
await p.click('#stop');

// Two trucks, practice mode, as Truck 2
await p.click('[data-role=truck2]'); await p.check('#practice'); await p.check('#s_two'); await p.click('#start'); await p.waitForTimeout(1500);
check('two trucks: three gaps', (await p.locator('#gaps .gap').count()) === 3);
check('two trucks: four speeds', (await p.locator('#speeds > div').count()) === 4);
check('truck gap range 0-100', (await p.textContent('#gap_mid_range')) === '0–100 m', await p.textContent('#gap_mid_range'));
check('truck gap label for Truck 2', (await p.textContent('#gap_mid_who')) === 'Truck 1 ahead of you', await p.textContent('#gap_mid_who'));
check('no horizontal scroll (two trucks)', await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await p.screenshot({ path: path.join(root, 'tests', 'phone-two-trucks.png'), fullPage: true });
await p.click('#stop');

// Settings from Truck 1 reach a pilot's phone (relay message simulated)
await p.click('[data-role=rear]'); await p.uncheck('#practice'); await p.click('#start'); await p.waitForTimeout(800);
await p.evaluate(() => window.__pg.onRelayMessage('cfg', JSON.stringify({id:'x', trucks:2, t:Date.now(), ranges:{front:{min:400,max:1800}, mid:{min:10,max:90}, rear:{min:150,max:900}}})));
await p.waitForTimeout(1200);
check('pilot picks up Truck 1 gaps', (await p.textContent('#gap_rear_range')) === '150–900 m', await p.textContent('#gap_rear_range'));
check('pilot shows truck gap when Truck 1 says two trucks', (await p.locator('#gaps .gap').count()) === 3);
await p.click('#toggleSettings'); await p.waitForTimeout(400);
check('pilot cannot edit gaps', !(await p.isVisible('#l_fmin')) && /Set by Truck 1/.test(await p.textContent('#liveSrc')), await p.textContent('#liveSrc'));
await p.evaluate(() => window.__pg.onRelayMessage('cfg', JSON.stringify({id:'x', trucks:1, t:Date.now(), ranges:{front:{min:900,max:100}, mid:{min:0,max:100}, rear:{min:100,max:1000}}})));
await p.waitForTimeout(1200);
check('bad settings ignored', (await p.textContent('#gap_rear_range')) === '150–900 m', await p.textContent('#gap_rear_range'));
await p.click('#stop');
check('no script errors', errors.length === 0, errors);

await b.close(); srv.close();
console.log(fails.length ? `\n${fails.length} check(s) failed` : '\nAll checks passed');
process.exit(fails.length ? 1 : 0);
