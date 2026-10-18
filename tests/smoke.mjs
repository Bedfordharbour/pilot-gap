// Smoke test for Pilot Gap. Run before every push:
//   node tests/smoke.mjs
// Needs Node 18+ and Playwright (npm i playwright, then npx playwright install chromium),
// or set PLAYWRIGHT_PATH to an existing Playwright install.
// It serves the repo folder locally, opens the page at phone size, runs practice mode,
// checks one-truck and two-truck layouts, gap settings, Settings and Mute,
// scrambling, the trip log and message timing, and saves screenshots in tests/ to look at.
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
check('no update box when the website has the same version', !(await p.isVisible('#updateBox')));
check('relay library loads from repo', await p.evaluate(() => typeof mqtt) === 'object');
const code0 = await p.inputValue('#trip');
check('default trip code is 123456', code0 === '123456', code0);
await p.click('#newTrip');
const code1 = await p.inputValue('#trip');
check('New code makes a random 6-character code', /^[A-Z0-9]{6}$/.test(code1) && code1 !== '123456', code1);
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

// Scrambled relay messages (8b) and settings from Truck 1 reaching a pilot (relay simulated)
await p.click('[data-role=rear]'); await p.uncheck('#practice');
await p.fill('#trip', '123'); await p.click('#start'); await p.waitForTimeout(400);
check('codes shorter than 4 are refused', await p.isVisible('#setupErr'), await p.textContent('#setupErr'));
await p.fill('#trip', 'BHE777'); await p.click('#start');
await p.waitForFunction(() => window.__pg.S.key !== null, null, { timeout: 15000 });
const tid = await p.evaluate(() => window.__pg.S.topicId);
check('relay topic does not contain the trip code', !!tid && !tid.includes('BHE777') && tid.length === 24, tid);
const sealed = await p.evaluate(() => window.__pg.seal({id:'x', trucks:2, t:Date.now(), ranges:{front:{min:400,max:1800}, mid:{min:10,max:90}, rear:{min:150,max:900}}}));
check('messages are scrambled', !sealed.includes('ranges') && !sealed.includes('1800'), sealed.slice(0, 40));
await p.evaluate(t => window.__pg.onRelayMessage('cfg', t), sealed);
await p.waitForTimeout(1200);
check('pilot picks up Truck 1 gaps', (await p.textContent('#gap_rear_range')) === '150–900 m', await p.textContent('#gap_rear_range'));
check('pilot shows truck gap when Truck 1 says two trucks', (await p.locator('#gaps .gap').count()) === 3);
await p.click('#toggleSettings'); await p.waitForTimeout(400);
check('pilot cannot edit gaps', !(await p.isVisible('#l_fmin')) && /Set by Truck 1/.test(await p.textContent('#liveSrc')), await p.textContent('#liveSrc'));
check('Save trip log button in settings', await p.isVisible('#saveLog'));
// a message scrambled with another code, and a plain-text message, are both ignored
const foreign = await p.evaluate(async () => {
  const enc = new TextEncoder();
  const base = await crypto.subtle.importKey('raw', enc.encode('OTHER9'), 'PBKDF2', false, ['deriveKey']);
  const k = await crypto.subtle.deriveKey({name:'PBKDF2', salt:enc.encode('bhe-pilotgap v2'), iterations:150000, hash:'SHA-256'}, base, {name:'AES-GCM', length:256}, false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM', iv}, k, enc.encode(JSON.stringify({id:'y', trucks:1, t:Date.now(), ranges:{front:{min:1,max:2}, mid:{min:0,max:100}, rear:{min:5,max:6}}}))));
  const all = new Uint8Array(12 + ct.length); all.set(iv); all.set(ct, 12);
  return btoa(String.fromCharCode(...all));
});
await p.evaluate(t => window.__pg.onRelayMessage('cfg', t), foreign);
await p.evaluate(() => window.__pg.onRelayMessage('cfg', JSON.stringify({id:'z', trucks:1, t:Date.now(), ranges:{front:{min:1,max:2}, mid:{min:0,max:100}, rear:{min:5,max:6}}})));
const badSealed = await p.evaluate(() => window.__pg.seal({id:'x', trucks:1, t:Date.now(), ranges:{front:{min:900,max:100}, mid:{min:0,max:100}, rear:{min:100,max:1000}}}));
await p.evaluate(t => window.__pg.onRelayMessage('cfg', t), badSealed);
await p.waitForTimeout(1200);
// clock check: a live position stamped 60 s in the past (sender clock slow) still counts as fresh; a stored one does not
const old = await p.evaluate(() => window.__pg.seal({id:'q', lat:-33.86, lon:121.89, spd:22, hdg:0, acc:5, t:Date.now()-60000}));
await p.evaluate(t => window.__pg.onRelayMessage('lead', t, false), old);
await p.waitForTimeout(300);
check('live message from a phone with a slow clock is fresh', await p.evaluate(() => Date.now() - window.__pg.S.peers.lead.t < 5000));
await p.evaluate(t => window.__pg.onRelayMessage('lead', t, true), old);
await p.waitForTimeout(300);
check('stored message keeps its own (old) time', await p.evaluate(() => Date.now() - window.__pg.S.peers.lead.t > 50000));
check('wrong-code, plain and bad messages ignored', (await p.textContent('#gap_rear_range')) === '150–900 m', await p.textContent('#gap_rear_range'));

// Trip log (8a)
const logNow = await p.evaluate(() => window.__pg.logText(window.__pg.S.log));
check('log records start, version and settings', /start\s+version 1\.\d+\.\d+/.test(logNow) && /cfg\s+from Truck 1: front 400–1800 m/.test(logNow), logNow.slice(0, 400));
check('log records ignored messages', /unreadable message ignored|bad gap settings ignored/.test(logNow));
check('log records gap changes', /gap\s+Rear gap:/.test(logNow));
await p.click('#stop'); await p.waitForTimeout(400);
check('End trip offers to save the log', await p.isVisible('#lastLog') && /Trip ended/.test(await p.textContent('#lastLogTitle')), await p.textContent('#lastLogTitle'));
const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 5000 }).catch(() => null), p.click('#saveLastLog')]);
check('Save trip log gives a file', !!dl && /^pilotgap-log-\d{8}-\d{4}-rear\.txt$/.test(dl.suggestedFilename()), dl && dl.suggestedFilename());
await p.screenshot({ path: path.join(root, 'tests', 'phone-trip-ended.png') });
await p.goto('http://localhost:8765/'); await p.waitForTimeout(500);
check('page opens with 123456 again', (await p.inputValue('#trip')) === '123456', await p.inputValue('#trip'));
check('last trip log still offered after reload', await p.isVisible('#lastLog'));
// update check: serve a newer version number and check the box appears, without reloading
await p.goto('http://localhost:8765/'); await p.waitForTimeout(300);
await p.route('**/?check=*', async r => { const res = await r.fetch(); const body = (await res.text()).replace(/var VERSION = "[^"]+"/, 'var VERSION = "9.9.9 (test)"'); await r.fulfill({ response: res, body }); });
await p.evaluate(() => window.__pg.checkUpdate()); await p.waitForTimeout(800);
check('newer version on website shows update box', await p.isVisible('#updateBox') && /9\.9\.9/.test(await p.textContent('#updateInfo')), await p.textContent('#updateInfo'));
await p.click('[data-role=lead]'); await p.check('#practice'); await p.click('#start'); await p.waitForTimeout(1500);
check('update banner during a trip, no reload by itself', await p.locator('#banners button', { hasText: 'Update now' }).count() === 1 && await p.isVisible('#gaps'));
await p.screenshot({ path: path.join(root, 'tests', 'phone-update.png') });
await p.unroute('**/?check=*');
check('no script errors', errors.length === 0, errors);

await b.close(); srv.close();
console.log(fails.length ? `\n${fails.length} check(s) failed` : '\nAll checks passed');
process.exit(fails.length ? 1 : 0);
