// Smoke test for Pilot Gap. Run before every push:
//   node tests/smoke.mjs
// Needs Node 18+ and Playwright (npm i playwright, then npx playwright install chromium),
// or set PLAYWRIGHT_PATH to an existing Playwright install.
// It serves the repo folder locally, opens the page at phone size, runs practice mode,
// checks the Settings panel and Mute button, and saves tests/phone.png to look at.
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

await p.click('[data-role=truck]'); await p.check('#practice'); await p.click('#start'); await p.waitForTimeout(1500);
const gap = await p.textContent('#gapANum');
check('practice mode shows a gap', /\d/.test(gap), gap);
check('settings hidden at start', !(await p.isVisible('#settings')));
await p.click('#toggleSettings'); await p.waitForTimeout(500);
check('settings opens', await p.isVisible('#settings'));
await p.click('#toggleSettings'); await p.waitForTimeout(500);
check('settings closes', !(await p.isVisible('#settings')));
await p.click('#mute'); await p.waitForTimeout(1200);
check('mute turns on for 5 min', /Unmute \((5:00|4:5\d)\)/.test(await p.textContent('#mute')), await p.textContent('#mute'));
await p.click('#mute'); await p.waitForTimeout(1200);
check('mute turns off', (await p.textContent('#mute')) === 'Mute 5 min', await p.textContent('#mute'));
check('no horizontal scroll', await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
await p.screenshot({ path: path.join(root, 'tests', 'phone.png') });
check('no script errors', errors.length === 0, errors);

await b.close(); srv.close();
console.log(fails.length ? `\n${fails.length} check(s) failed` : '\nAll checks passed');
process.exit(fails.length ? 1 : 0);
