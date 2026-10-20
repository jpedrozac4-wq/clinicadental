/* Minimal Chrome DevTools Protocol smoke harness (no dependencies).
   Drives a headless Chrome against the running stack, records console errors,
   failed requests and screenshots for each step of the scenario.

   Usage:
     docker compose up -d                 # stack must be reachable
     node tools/browser-smoke.mjs

   Environment:
     BASE_URL   front end origin            (default http://localhost)
     CHROME_BIN absolute path to chrome     (default: Windows Chrome install)
     CDP_PORT   remote debugging port       (default 9333, not the MCP's 9222)
     OUT_DIR    screenshots and report.json (default tools/artifacts)

   Exit code is 0 when the report has no fatal error, 1 otherwise. */

import { mkdirSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const CHROME =
    process.env.CHROME_BIN ||
    (process.platform === "win32"
        ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
        : "google-chrome");
const PORT = Number(process.env.CDP_PORT || 9333);
const BASE = process.env.BASE_URL || "http://localhost";
const OUT = process.env.OUT_DIR || join(HERE, "artifacts");
const USER_DIR = join(OUT, "chrome-profile");

mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class Cdp {
    constructor(ws) {
        this.ws = ws;
        this.seq = 0;
        this.pending = new Map();
        this.console = [];
        this.exceptions = [];
        this.logs = [];
        this.failed = [];
        this.badStatus = [];
    }

    static async connect(wsUrl) {
        const ws = new WebSocket(wsUrl);
        await new Promise((resolve, reject) => {
            ws.onopen = resolve;
            ws.onerror = () => reject(new Error("ws open failed"));
        });
        const cdp = new Cdp(ws);
        ws.onmessage = (event) => {
            let msg;
            try {
                msg = JSON.parse(event.data);
            } catch {
                return; // Ignore malformed CDP frames instead of breaking the socket.
            }
            if (msg.id !== undefined && cdp.pending.has(msg.id)) {
                const { resolve, reject } = cdp.pending.get(msg.id);
                cdp.pending.delete(msg.id);
                if (msg.error) reject(new Error(`${msg.error.message} (${msg.method ?? ""})`));
                else resolve(msg.result);
                return;
            }
            cdp.record(msg);
        };
        return cdp;
    }

    record(msg) {
        const p = msg.params || {};
        if (msg.method === "Runtime.consoleAPICalled") {
            const text = (p.args || []).map((a) => a.value ?? a.description ?? a.type).join(" ");
            if (p.type === "error" || p.type === "warning") {
                this.console.push({ level: p.type, text });
            }
        } else if (msg.method === "Runtime.exceptionThrown") {
            const d = p.exceptionDetails || {};
            this.exceptions.push({
                text: d.exception?.description || d.text || "unknown exception",
                url: d.url,
                line: d.lineNumber
            });
        } else if (msg.method === "Log.entryAdded") {
            const e = p.entry || {};
            if (e.level === "error" || e.level === "warning") {
                this.logs.push({ level: e.level, text: e.text, source: e.source });
            }
        } else if (msg.method === "Network.loadingFailed") {
            this.failed.push({ url: p.requestId, error: p.errorText, type: p.type });
        } else if (msg.method === "Network.responseReceived") {
            const r = p.response || {};
            if (r.status >= 400) {
                this.badStatus.push({ status: r.status, url: r.url });
            }
        }
    }

    send(method, params = {}) {
        const id = ++this.seq;
        this.ws.send(JSON.stringify({ id, method, params }));
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject });
            setTimeout(() => {
                if (this.pending.has(id)) {
                    this.pending.delete(id);
                    reject(new Error(`timeout: ${method}`));
                }
            }, 20000);
        });
    }

    async evaluate(expression) {
        const result = await this.send("Runtime.evaluate", {
            expression: `(() => { ${expression} })()`,
            returnByValue: true,
            awaitPromise: true
        });
        if (result.exceptionDetails) {
            throw new Error(result.exceptionDetails.exception?.description || "evaluate failed");
        }
        return result.result.value;
    }

    async waitFor(expression, timeoutMs = 12000, label = expression) {
        const deadline = Date.now() + timeoutMs;
        while (Date.now() < deadline) {
            try {
                if (await this.evaluate(expression)) return true;
            } catch {
                /* page still navigating */
            }
            await sleep(200);
        }
        throw new Error(`waitFor timeout: ${label}`);
    }

    async screenshot(file) {
        const shot = await this.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
        writeFileSync(`${OUT}/${file}`, Buffer.from(shot.data, "base64"));
    }

    drain() {
        const out = {
            console: this.console.splice(0),
            exceptions: this.exceptions.splice(0),
            logs: this.logs.splice(0),
            failed: this.failed.splice(0),
            badStatus: this.badStatus.splice(0)
        };
        return out;
    }
}

/** Waits until the mounted view has no spinner left, i.e. it settled. */
async function settle(cdp, timeoutMs = 15000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const pending = await cdp.evaluate(
            "return document.querySelectorAll('#erp-view .spinner').length;"
        );
        if (pending === 0) {
            await sleep(350);
            return true;
        }
        await sleep(250);
    }
    return false;
}

async function fetchJson(url, tries = 60) {    for (let i = 0; i < tries; i++) {
        try {
            const res = await fetch(url);
            if (res.ok) return res.json();
        } catch {
            /* not up yet */
        }
        await sleep(500);
    }
    throw new Error(`cannot reach ${url}`);
}

const chrome = spawn(CHROME, [
    "--headless=new",
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${USER_DIR}`,
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--window-size=1440,900",
    "about:blank"
], { stdio: ["ignore", "pipe", "pipe"] });

const chromeStderr = [];
chrome.stderr.on("data", (data) => chromeStderr.push(data.toString()));
chrome.stdout.on("data", () => {});

const report = { base: BASE, steps: [], findings: [] };

function findingsFor(label, drained) {
    const items = [];
    drained.console.forEach((c) => items.push(`${label}: console.${c.level} -> ${c.text}`));
    drained.exceptions.forEach((e) => items.push(`${label}: uncaught -> ${e.text}`));
    drained.logs.forEach((l) => items.push(`${label}: log.${l.level} (${l.source}) -> ${l.text}`));
    drained.badStatus.forEach((b) => items.push(`${label}: HTTP ${b.status} -> ${b.url}`));
    drained.failed.forEach((f) => items.push(`${label}: request failed (${f.error})`));
    return items;
}

try {
    const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`);
    report.browser = version.Browser;

    const targets = await fetchJson(`http://127.0.0.1:${PORT}/json/list`);
    const page = targets.find((t) => t.type === "page");
    const cdp = await Cdp.connect(page.webSocketDebuggerUrl);

    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Log.enable");
    await cdp.send("Network.enable");
    await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: 1440, height: 900, deviceScaleFactor: 1, mobile: false
    });

    // ---------------- Scenario 1: storefront ----------------
    await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
    await sleep(2500);
    report.steps.push({
        step: "storefront",
        title: await cdp.evaluate("return document.title;"),
        productCards: await cdp.evaluate("return document.querySelectorAll('.product-card').length;"),
        filterButtons: await cdp.evaluate("return document.querySelectorAll('.filter-btn').length;"),
        hasErpLink: await cdp.evaluate("return Boolean(document.querySelector('a[href=\"portal-erp.html\"]'));"),
        gridColumns: await cdp.evaluate(
            "const g = document.querySelector('.product-grid'); return g ? getComputedStyle(g).gridTemplateColumns.split(' ').filter(Boolean).length : 0;"
        ),
        brokenImages: await cdp.evaluate(
            "return Array.from(document.querySelectorAll('.product-card img')).filter(i => i.complete && i.naturalWidth === 0).length;"
        ),
        totalImages: await cdp.evaluate("return document.querySelectorAll('.product-card img').length;")
    });
    await cdp.screenshot("01-storefront.png");
    report.findings.push(...findingsFor("storefront", cdp.drain()));

    // ---------------- Scenario 2: login gate ----------------
    await cdp.send("Page.navigate", { url: `${BASE}/portal-erp.html` });
    await sleep(2000);
    report.steps.push({
        step: "login-gate",
        title: await cdp.evaluate("return document.title;"),
        gateVisible: await cdp.evaluate("return !document.querySelector('#login-gate').hidden;"),
        shellHidden: await cdp.evaluate("return document.querySelector('#erp-shell').hidden;"),
        demoProfiles: await cdp.evaluate("return document.querySelectorAll('[data-demo-user]').length;")
    });
    await cdp.screenshot("02-login-gate.png");
    report.findings.push(...findingsFor("login-gate", cdp.drain()));

    // ---------------- Scenario 3: wrong credentials ----------------
    await cdp.evaluate(`
        document.querySelector('#login-username').value = 'gerentegeneral';
        document.querySelector('#login-password').value = 'clave-incorrecta';
        document.querySelector('#login-form').requestSubmit();
        return true;
    `);
    await sleep(2500);
    report.steps.push({
        step: "login-bad-credentials",
        errorShown: await cdp.evaluate("return !document.querySelector('#login-error').hidden;"),
        errorText: await cdp.evaluate("return document.querySelector('#login-error').textContent;"),
        stillOnGate: await cdp.evaluate("return !document.querySelector('#login-gate').hidden;")
    });
    await cdp.screenshot("03-login-error.png");
    report.findings.push(...findingsFor("login-bad-credentials", cdp.drain()));

    // ---------------- Scenario 4: real login as gerentegeneral ----------------
    await cdp.evaluate(`
        document.querySelector('#login-username').value = 'gerentegeneral';
        document.querySelector('#login-password').value = 'Password123!';
        document.querySelector('#login-form').requestSubmit();
        return true;
    `);
    await cdp.waitFor("return !document.querySelector('#erp-shell').hidden;", 15000, "shell visible");
    await sleep(1500);
    report.steps.push({
        step: "login-gerentegeneral",
        shellVisible: await cdp.evaluate("return !document.querySelector('#erp-shell').hidden;"),
        user: await cdp.evaluate("return document.querySelector('#current-user-name').textContent;"),
        role: await cdp.evaluate("return document.querySelector('#current-user-role').textContent;"),
        hash: await cdp.evaluate("return window.location.hash;"),
        menu: await cdp.evaluate(
            "return Array.from(document.querySelectorAll('.sidebar-link')).map(a => a.dataset.routeId);"
        ),
        viewTitle: await cdp.evaluate(
            "return document.querySelector('#erp-view h2')?.textContent || '(sin titulo)';"
        ),
        viewCards: await cdp.evaluate("return document.querySelectorAll('#erp-view .card').length;"),
        tokenStored: await cdp.evaluate("return Boolean(localStorage.getItem('erp.auth.token'));")
    });
    await cdp.screenshot("04-shell-gerente.png");
    report.findings.push(...findingsFor("login-gerentegeneral", cdp.drain()));

    // ---------------- Scenario 5: walk every sidebar module ----------------
    const routeIds = await cdp.evaluate(
        "return Array.from(document.querySelectorAll('.sidebar-link')).map(a => a.dataset.routeId);"
    );
    for (const routeId of routeIds) {
        await cdp.evaluate(`document.querySelector('.sidebar-link[data-route-id="${routeId}"]').click(); return true;`);
        await settle(cdp, 15000);
        const state = {
            step: `module-${routeId}`,
            hash: await cdp.evaluate("return window.location.hash;"),
            heading: await cdp.evaluate("return document.querySelector('#erp-view h2')?.textContent || '(sin titulo)';"),
            hasLoading: await cdp.evaluate("return document.querySelectorAll('#erp-view .spinner').length > 0;"),
            hasErrorState: await cdp.evaluate("return document.querySelectorAll('#erp-view .state-box-error').length > 0;"),
            errorText: await cdp.evaluate(
                "return document.querySelector('#erp-view .state-box-error .text-muted')?.textContent || '';"
            ),
            tables: await cdp.evaluate("return document.querySelectorAll('#erp-view .data-table').length;"),
            activeLinks: await cdp.evaluate("return document.querySelectorAll('.sidebar-link.active').length;")
        };
        report.steps.push(state);
        await cdp.screenshot(`05-module-${routeId}.png`);
        report.findings.push(...findingsFor(`module-${routeId}`, cdp.drain()));
    }

    // ---------------- Scenario 6: reload persistence ----------------
    await cdp.send("Page.navigate", { url: `${BASE}/portal-erp.html` });
    await sleep(3000);
    report.steps.push({
        step: "reload-with-session",
        shellVisible: await cdp.evaluate("return !document.querySelector('#erp-shell').hidden;"),
        user: await cdp.evaluate("return document.querySelector('#current-user-name').textContent;"),
        hash: await cdp.evaluate("return window.location.hash;"),
        viewHeading: await cdp.evaluate("return document.querySelector('#erp-view h2')?.textContent || '(sin titulo)';")
    });
    await cdp.screenshot("06-reload-session.png");
    report.findings.push(...findingsFor("reload-with-session", cdp.drain()));

    // ---------------- Scenario 7: RBAC as cajero ----------------
    await cdp.evaluate(`
        localStorage.clear();
        sessionStorage.clear();
        return true;
    `);
    await cdp.send("Page.navigate", { url: `${BASE}/portal-erp.html` });
    await sleep(2500);
    await cdp.evaluate(`
        document.querySelector('#login-username').value = 'cajero1';
        document.querySelector('#login-password').value = 'Password123!';
        document.querySelector('#login-form').requestSubmit();
        return true;
    `);
    await cdp.waitFor("return !document.querySelector('#erp-shell').hidden;", 15000, "cajero shell");
    await sleep(1500);
    report.steps.push({
        step: "login-cajero-rbac",
        role: await cdp.evaluate("return document.querySelector('#current-user-role').textContent;"),
        hash: await cdp.evaluate("return window.location.hash;"),
        menu: await cdp.evaluate(
            "return Array.from(document.querySelectorAll('.sidebar-link')).map(a => a.dataset.routeId);"
        ),
        heading: await cdp.evaluate("return document.querySelector('#erp-view h2')?.textContent || '(sin titulo)';"),
        errorState: await cdp.evaluate("return document.querySelectorAll('#erp-view .state-box-error').length > 0;")
    });
    await cdp.screenshot("07-shell-cajero.png");
    report.findings.push(...findingsFor("login-cajero-rbac", cdp.drain()));

    // ---------------- Scenario 7b: POS sale through the real UI ----------------
    await cdp.evaluate(`
        Array.from(document.querySelectorAll('#erp-view button'))
            .find(button => button.textContent.includes('Agregar')).click();
        return true;
    `);
    await sleep(600);
    report.steps.push({
        step: "pos-add-item",
        ticketLines: await cdp.evaluate("return document.querySelectorAll('#erp-view .ticket-line').length;"),
        total: await cdp.evaluate("return document.querySelector('#erp-view .pos-total-value')?.textContent;")
    });

    await cdp.evaluate("document.querySelector('#btn-pos-cobrar').click(); return true;");
    await sleep(3000);
    report.steps.push({
        step: "pos-cobrar",
        successToast: await cdp.evaluate("return document.querySelector('.toast-success')?.textContent || '';"),
        errorToast: await cdp.evaluate("return document.querySelector('.toast-error')?.textContent || '';"),
        pendingLines: await cdp.evaluate("return document.querySelectorAll('#erp-view .ticket-line').length;")
    });
    await cdp.screenshot("09-pos-after-sale.png");
    report.findings.push(...findingsFor("pos-cobrar", cdp.drain()));

    // ---------------- Scenario 7c: logout closes the session ----------------
    await cdp.evaluate("document.querySelector('#btn-logout').click(); return true;");
    await sleep(700);
    const modalShown = await cdp.evaluate("return Boolean(document.querySelector('.modal-overlay.active .btn-danger'));");
    await cdp.evaluate("document.querySelector('.modal-overlay.active .btn-danger').click(); return true;");
    await sleep(2500);
    report.steps.push({
        step: "logout",
        confirmModalShown: modalShown,
        gateVisible: await cdp.evaluate("return !document.querySelector('#login-gate').hidden;"),
        shellHidden: await cdp.evaluate("return document.querySelector('#erp-shell').hidden;"),
        tokenCleared: await cdp.evaluate("return localStorage.getItem('erp.auth.token') === null;"),
        hashCleared: await cdp.evaluate("return window.location.hash === '';")
    });
    await cdp.screenshot("10-after-logout.png");
    report.findings.push(...findingsFor("logout", cdp.drain()));

    // ---------------- Scenario 8: mobile viewport ----------------
    // La sesion se cerro en 7c: se vuelve a entrar para medir el shell movil.
    await cdp.evaluate(`
        document.querySelector('#login-username').value = 'gerentegeneral';
        document.querySelector('#login-password').value = 'Password123!';
        document.querySelector('#login-form').requestSubmit();
        return true;
    `);
    await cdp.waitFor("return !document.querySelector('#erp-shell').hidden;", 15000, "shell for mobile");
    await settle(cdp, 15000);
    await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: 390, height: 844, deviceScaleFactor: 2, mobile: true
    });
    await sleep(1200);
    report.steps.push({
        step: "mobile-shell",
        toggleVisible: await cdp.evaluate(
            "return getComputedStyle(document.querySelector('#sidebar-toggle')).display !== 'none';"
        ),
        sidebarHidden: await cdp.evaluate(
            "return getComputedStyle(document.querySelector('#erp-sidebar')).display === 'none';"
        )
    });
    await cdp.screenshot("08-mobile.png");
    report.findings.push(...findingsFor("mobile-shell", cdp.drain()));

    report.ok = true;
} catch (error) {
    report.ok = false;
    report.fatal = error.message;
} finally {
    try {
        spawn("taskkill", ["/F", "/T", "/PID", String(chrome.pid)], { stdio: "ignore" });
    } catch {
        /* already gone */
    }
    chrome.kill();
    report.chromeStderr = chromeStderr.join("").split("\n").slice(0, 10);
    writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
}
