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

const uploads = [];
const srv = http.createServer((q, r) => {
  if (q.method === 'POST' && q.url.startsWith('/upload')) {   // stand-in for the Google Apps Script receiver
    let b = ''; q.on('data', c => b += c); q.on('end', () => { try { uploads.push(JSON.parse(b)); } catch (e) {}
      r.writeHead(200, { 'content-type': 'application/json' }); r.end(JSON.stringify({ ok: true })); }); return;
  }
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
check('default trip code is 234567', code0 === '234567', code0);
await p.click('#newTrip');
const code1 = await p.inputValue('#trip');
check('New code makes a random 6-character code', /^[A-Z0-9]{6}$/.test(code1) && code1 !== '234567', code1);
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
await p.evaluate(() => window.__pg.setUploadUrl('/upload'));
await p.fill('#trip', 'BHE777'); await p.click('#start'); await p.waitForTimeout(300);
check('agreement asked before the first real trip', await p.isVisible('#agree') && /I agree to share my location as part of my piloting \/ driver duties/.test(await p.textContent('#agreeText')));
await p.screenshot({ path: path.join(root, 'tests', 'phone-agreement.png') });
await p.click('#agreeYes'); await p.waitForTimeout(200);
check('agreement needs a name', await p.isVisible('#agreeErr'));
await p.fill('#agreeName', 'Test Driver'); await p.click('#agreeYes');
await p.waitForFunction(() => window.__pg.S.key !== null, null, { timeout: 15000 });
check('trip starts after agreeing', await p.isVisible('#gaps'));
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
// stored messages left by an earlier trip (over 10 min old) are ignored: no ghost vehicle, no old settings
const ghost = await p.evaluate(() => window.__pg.seal({id:'g', lat:-33.87, lon:121.89, spd:0, hdg:0, acc:5, t:Date.now()-20*60000}));
await p.evaluate(t => window.__pg.onRelayMessage('truck2', t, true), ghost);
const oldCfg = await p.evaluate(() => window.__pg.seal({id:'x', trucks:1, t:Date.now()-20*60000, ranges:{front:{min:1,max:2}, mid:{min:0,max:100}, rear:{min:7,max:8}}}));
await p.evaluate(t => window.__pg.onRelayMessage('cfg', t, true), oldCfg);
await p.waitForTimeout(300);
check('old stored position from an earlier trip ignored', await p.evaluate(() => !window.__pg.S.peers.truck2));
check('old stored gap settings ignored', await p.evaluate(() => window.__pg.S.cfg.ranges.rear.min === 150));
check('old stored messages logged', await p.evaluate(() => /ignored old stored Truck 2 position/.test(window.__pg.logText(window.__pg.S.log)) && /ignored old stored gap settings/.test(window.__pg.logText(window.__pg.S.log))));
await p.evaluate(() => { window.__pg.S.peers.truck2 = {lat:-33.87, lon:121.89, spd:0, hdg:0, acc:5, t:Date.now()-20*60000}; window.__pg.S.cfg.trucks = 1; });
check('a Truck 2 not heard from for 10 min does not force two-truck mode', await p.evaluate(() => !window.__pg.twoTrucks()));
await p.evaluate(() => { delete window.__pg.S.peers.truck2; window.__pg.S.cfg.trucks = 2; });
check('wrong-code, plain and bad messages ignored', (await p.textContent('#gap_rear_range')) === '150–900 m', await p.textContent('#gap_rear_range'));

// Trip log (8a)
const logNow = await p.evaluate(() => window.__pg.logText(window.__pg.S.log));
check('log records start, version and settings', /start\s+version 1\.\d+\.\d+/.test(logNow) && /cfg\s+from Truck 1: front 400–1800 m/.test(logNow), logNow.slice(0, 400));
check('log records ignored messages', /unreadable message ignored|bad gap settings ignored/.test(logNow));
check('log records gap changes', /gap\s+Rear gap:/.test(logNow));
check('log header records the agreement', /Location agreement: Test Driver, agreed \d{4}-\d\d-\d\d/.test(logNow), logNow.slice(0, 300));
// own position on log lines (fake GPS fix)
await p.evaluate(() => { const S = window.__pg.S; S.lastFix = { lat: -33.86150, lon: 121.89120, acc: 6, spd: 22, time: Date.now() }; });
await p.evaluate(() => window.__pg.S.lastSample = 0); await p.waitForTimeout(1300);
const logPos = await p.evaluate(() => window.__pg.logText(window.__pg.S.log));
check('log lines carry this phone\'s position', /sample .*@-33\.86150,121\.89120/.test(logPos), logPos.split('\n').filter(l => /sample/.test(l)).slice(-1)[0]);
await p.click('#stop'); await p.waitForTimeout(1200);
const up = uploads[uploads.length - 1];
check('End trip sends the log automatically', !!up && up.key === 'bhe-pg-7Kq2vX9m' && /^pilotgap-\d{8}-\d{4}-rear-TestDriver-[a-z0-9]+\.txt$/.test(up.name) && /End trip pressed/.test(up.text), up && up.name);
check('start screen says the log was sent', /Sent to the office at/.test(await p.textContent('#lastLogUp')), await p.textContent('#lastLogUp'));
check('End trip offers to save the log', await p.isVisible('#lastLog') && /Trip ended/.test(await p.textContent('#lastLogTitle')), await p.textContent('#lastLogTitle'));
const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 5000 }).catch(() => null), p.click('#saveLastLog')]);
check('Save trip log gives a file', !!dl && /^pilotgap-log-\d{8}-\d{4}-rear\.txt$/.test(dl.suggestedFilename()), dl && dl.suggestedFilename());
await p.screenshot({ path: path.join(root, 'tests', 'phone-trip-ended.png') });
await p.goto('http://localhost:8765/'); await p.waitForTimeout(500);
check('page opens with 234567 again', (await p.inputValue('#trip')) === '234567', await p.inputValue('#trip'));
check('last trip log still offered after reload', await p.isVisible('#lastLog'));
// a log still waiting to send is kept when a new log starts (not overwritten), and sent later
const rearSent = uploads.filter(u => /-rear-/.test(u.name)).length;
await p.evaluate(() => localStorage.setItem('pg_uppending', '1'));
await p.click('[data-role=lead]'); await p.check('#practice'); await p.click('#start'); await p.waitForTimeout(600);
check('unsent log kept when a new log starts', await p.evaluate(() => window.__pg.unsent().length === 1 && /-rear-/.test(window.__pg.unsent()[0].name)), await p.evaluate(() => window.__pg.unsent().map(x => x.name)));
check('practice code not put in the address', await p.evaluate(() => !/PRACTICE/.test(location.search)), await p.evaluate(() => location.search));
await p.click('#stop'); await p.waitForTimeout(300);
await p.evaluate(() => { window.__pg.setUploadUrl('/upload'); window.__pg.sendUnsent(); }); await p.waitForTimeout(800);
check('older unsent log sent later', uploads.filter(u => /-rear-/.test(u.name)).length === rearSent + 1 && await p.evaluate(() => window.__pg.unsent().length === 0));
// update check: serve a newer version number and check the box appears, without reloading
await p.goto('http://localhost:8765/'); await p.waitForTimeout(300);
await p.route('**/?check=*', async r => { const res = await r.fetch(); const body = (await res.text()).replace(/var VERSION = "[^"]+"/, 'var VERSION = "9.9.9 (test)"'); await r.fulfill({ response: res, body }); });
await p.evaluate(() => window.__pg.checkUpdate()); await p.waitForTimeout(800);
check('newer version on website shows update box', await p.isVisible('#updateBox') && /9\.9\.9/.test(await p.textContent('#updateInfo')), await p.textContent('#updateInfo'));
const before = uploads.length;
await p.click('[data-role=lead]'); await p.check('#practice'); await p.click('#start'); await p.waitForTimeout(1500);
check('practice mode needs no agreement', await p.isVisible('#gaps'));
check('update banner during a trip, no reload by itself', await p.locator('#banners button', { hasText: 'Update now' }).count() === 1 && await p.isVisible('#gaps'));
await p.screenshot({ path: path.join(root, 'tests', 'phone-update.png') });
await p.unroute('**/?check=*');
await p.click('#stop'); await p.waitForTimeout(800);
check('practice trips are not sent', uploads.length === before, uploads.length - before);
check('practice trip started its own log, not the real one', await p.evaluate(() => /-lead-/.test(localStorage.getItem('pg_logname')) && localStorage.getItem('pg_logkey').startsWith('P|')));
check('no script errors', errors.length === 0, errors);

// ---- Inside the Android app (the app's side replaced by a stand-in that records calls) ----
const ctxApp = await b.newContext({ viewport: { width: 375, height: 812 } });
await ctxApp.addInitScript(() => {
  window.__calls = [];
  const rec = (n) => (...a) => { window.__calls.push([n, ...a]); };
  window.PilotGapNative = { tripStart: rec('tripStart'), tripEnd: rec('tripEnd'), say: rec('say'), stopSpeech: rec('stopSpeech'),
    beep: rec('beep'), beepTwice: rec('beepTwice'), vibrate: rec('vibrate'), saveLog: rec('saveLog'), reload: rec('reload'),
    appVersion: () => '1.0.0', hasLocation: () => true };
});
const a = await ctxApp.newPage(); a.on('pageerror', e => errors.push('app: ' + e.message));
await a.goto('http://localhost:8765/'); await a.waitForTimeout(300);
check('app: download box hidden inside the app', !(await a.isVisible('#appBox')));
await a.evaluate(() => window.__pg.setUploadUrl('/upload'));
await a.click('[data-role=truck]'); await a.click('#start'); await a.waitForTimeout(200);
await a.fill('#agreeName', 'App Driver'); await a.click('#agreeYes');
await a.waitForFunction(() => window.__pg.S.key !== null, null, { timeout: 15000 });
const calls1 = await a.evaluate(() => window.__calls.map(c => c[0]));
check('app: Start starts background tracking', calls1.includes('tripStart'), calls1);
check('app: App shown in the status row', await a.isVisible('#appWrap'));
await a.evaluate(() => window.__pgNativeFix(-33.8615, 121.8912, 6, 22.2, 90));
await a.waitForTimeout(300);
check('app: GPS fixes from the app are used', await a.evaluate(() => !!window.__pg.S.lastFix && Math.abs(window.__pg.S.lastFix.lat + 33.8615) < 1e-6 && window.__pg.S.lastFix.spd === 22.2));
// lead pilot 3 km ahead -> front gap too far -> alarm through the app's voice, beep and vibration
const lead = await a.evaluate(() => window.__pg.seal({id:'L', lat:-33.8615+3000/111320, lon:121.8912, spd:22, hdg:0, acc:5, t:Date.now()}));
await a.evaluate(t => window.__pg.onRelayMessage('lead', t, false), lead);
await a.evaluate(() => window.__pgNativeTick()); await a.waitForTimeout(1500);
const calls2 = await a.evaluate(() => window.__calls.filter(c => ['say','beep','beepTwice','vibrate'].includes(c[0])).map(c => c[0] + (c[1] ? ':' + String(c[1]).slice(0, 30) : '')));
check('app: alarm spoken by the app', calls2.some(c => /^say:Front gap too far/.test(c)), calls2);
check('app: double beep and vibration by the app', calls2.some(c => c.startsWith('beepTwice')) && calls2.includes('vibrate'), calls2);
await a.evaluate(() => window.__pgNativeEvent('gpsoff')); await a.waitForTimeout(1200);
check('app: GPS-off warning shown', /GPS \(location\) is turned off/.test(await a.textContent('#banners')), await a.textContent('#banners'));
await a.evaluate(() => window.__pgNativeEvent('gpson'));
await a.evaluate(() => window.__pgNativeEvent('approxonly')); await a.waitForTimeout(1200);
check('app: approximate-only location explained', /Use precise location/.test(await a.textContent('#banners')), await a.textContent('#banners'));
await a.evaluate(() => window.__pgNativeEvent('gpson'));
// periodic jobs run from render (driven by the app's tick in the background): 10-min upload
const upBefore = uploads.length;
await a.evaluate(() => { const S = window.__pg.S; S.connected = true; S.lastUpload = Date.now() - 11 * 60000; window.__pg.periodic(Date.now()); S.connected = false; });
await a.waitForTimeout(800);
check('app: log stays marked unsent during a trip after a 10-minute upload', await a.evaluate(() => localStorage.getItem('pg_uppending') === '1'));
await a.evaluate(() => window.__pgNativeFix(-33.8615 + 0.02, 121.8912, 1500, null, null)); await a.waitForTimeout(200);
check('app: no speed worked out from a rough (network) position', await a.evaluate(() => window.__pg.S.lastFix.spd == null), await a.evaluate(() => window.__pg.S.lastFix.spd));
await a.evaluate(() => window.__pgNativeFix(-33.8615, 121.8912, 5, null, null)); await a.evaluate(() => window.__pgNativeFix(-33.8615 + 0.0004, 121.8912, 60, null, null, true)); await a.waitForTimeout(200);
check('app: no speed worked out from a network position (app marks it)', await a.evaluate(() => window.__pg.S.lastFix.spd == null && window.__pg.S.lastFix.net === true), await a.evaluate(() => window.__pg.S.lastFix));
check('app: 10-minute upload runs from the tick, not a page timer', uploads.length === upBefore + 1 && /-truck-AppDriver-/.test(uploads[uploads.length - 1].name), uploads.length - upBefore);
await a.click('#stop'); await a.waitForTimeout(800);
const calls3 = await a.evaluate(() => window.__calls.map(c => c[0]));
check('app: End trip stops background tracking', calls3.includes('tripEnd'), calls3);
await a.click('#saveLastLog'); await a.waitForTimeout(200);
const saved = await a.evaluate(() => window.__calls.find(c => c[0] === 'saveLog'));
check('app: Save trip log goes through the app', !!saved && /^pilotgap-log-/.test(saved[1]) && /running in the Android app 1\.0\.0/.test(saved[2]), saved && saved[1]);
await a.screenshot({ path: path.join(root, 'tests', 'phone-app.png') });
await ctxApp.close();
// Android browser (not the app) sees the download box
const ctxDroid = await b.newContext({ viewport: { width: 375, height: 812 }, userAgent: 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36' });
const dp = await ctxDroid.newPage(); await dp.goto('http://localhost:8765/'); await dp.waitForTimeout(300);
check('Android browser: download box shown', await dp.isVisible('#appBox') && (await dp.getAttribute('#appLink', 'href')) === 'app/PilotGap.apk');
await dp.screenshot({ path: path.join(root, 'tests', 'phone-android-browser.png') });
await ctxDroid.close();
check('no script errors (app)', errors.filter(e => e.startsWith('app:')).length === 0, errors);

await b.close(); srv.close();
console.log(fails.length ? `\n${fails.length} check(s) failed` : '\nAll checks passed');
process.exit(fails.length ? 1 : 0);
