import { createServer, type Server } from "node:http";
import { spawn, type ChildProcess } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { homedir, networkInterfaces } from "node:os";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { isIP } from "node:net";
import { WebSocketServer, WebSocket } from "ws";
import { MAX_WEBSOCKET_PAYLOAD_BYTES, ProtocolSession, redactForLog } from "@nucleoid/pi-remote-protocol";
import type { AuthService } from "./auth.js";
import type { V2SessionManager } from "./v2-session.js";

type Options = { auth: AuthService; host: string; port: number; headCursor: () => number; minimumCursor: () => number; onStop?: () => void; allowNoAuthFromLoopback?: boolean; control?: { accept: (ws: WebSocket) => void }; v2?: { manager: V2SessionManager; history: (processId: string) => any[]; command?: (processId: string, value: any) => void; verifier?: (token?: string) => string }; sessions?: { piCommand: string; profileRoot: string; legacyToken?: string } };
const reject = (socket: NodeJS.WritableStream, status: number) => { socket.write(`HTTP/1.1 ${status} ${status === 401 ? "Unauthorized" : "Not Found"}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`); (socket as any).destroy(); };
const validAdvertisedHost = (value: string) => value.length <= 253 && (isIP(value) !== 0 || /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)(?:\.(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?))*$/.test(value));

export function createControlServer(o: Options) {
  let server: Server, enabled = true;
  const wsServer = new WebSocketServer({ noServer: true, maxPayload: MAX_WEBSOCKET_PAYLOAD_BYTES, perMessageDeflate: false });
  const v2Clients = new Map<WebSocket, string>();
  const controlClients = new Set<WebSocket>();
  const v2Pending = new Map<WebSocket, { verifier: string }>();
  const spawned = new Map<number, { startedAt: number; child?: ChildProcess }>();
  const spawnedFile = o.sessions ? join(o.sessions.profileRoot, "spawned-sessions.json") : undefined;
  const alive = (pid: number) => { try { process.kill(pid, 0); return true; } catch { return false; } };
  const persistSpawned = () => { if (!spawnedFile) return; try { writeFileSync(spawnedFile, JSON.stringify([...spawned.keys()])); } catch { } };
  try { if (spawnedFile && existsSync(spawnedFile)) for (const pid of JSON.parse(readFileSync(spawnedFile, "utf8")) as number[]) if (alive(pid)) spawned.set(pid, { startedAt: Date.now() }); } catch { }
  const lanIPv4 = () => { for (const list of Object.values(networkInterfaces())) for (const net of list ?? []) if (net.family === "IPv4" && !net.internal) return net.address; return undefined; };
  const tryBindPending = () => { if (!o.v2) return; for (const [ws, entry] of [...v2Pending]) { try { const processId = o.v2.manager.bindLegacy(entry.verifier); v2Pending.delete(ws); v2Clients.set(ws, processId); ws.send(JSON.stringify(o.v2.manager.hello(processId))); for (const item of o.v2.manager.history(o.v2.history(processId))) ws.send(JSON.stringify(item)); } catch { /* still no live session */ } } };
  server = createServer(async (req, res) => {
    const address = req.socket.remoteAddress ?? "unknown", url = new URL(req.url ?? "/", "http://localhost"), path = url.pathname;
    if (path === "/setup" && o.sessions && req.method === "GET") {
      const loop = address === "::1" || address === "127.0.0.1" || address === "::ffff:127.0.0.1";
      if (!loop) { res.writeHead(403, { "content-type": "application/json" }); res.end('{"error":"loopback_only"}'); return; }
      const lan = lanIPv4() ?? "127.0.0.1";
      const deepLink = `pi-remote://${lan}:${o.port}?token=${encodeURIComponent(o.sessions.legacyToken ?? "")}`;
      let qrData = "";
      try { const qrcode = await import("qrcode"); qrData = await (qrcode.default ?? qrcode).toDataURL(deepLink, { width: 320, margin: 1 }); } catch { }
      res.setHeader("content-type", "text/html; charset=utf-8"); res.setHeader("cache-control", "no-store");
      res.end(`<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>πm Remote — Setup</title><style>body{font-family:system-ui,sans-serif;background:#170609;color:#FFF0F2;display:flex;justify-content:center;padding:32px}main{max-width:420px;width:100%;text-align:center}h1{color:#FF4D6D;margin:0 0 4px}p{color:#B0949C;margin:4px 0}code{background:#2B1016;padding:2px 8px;border-radius:6px;direction:ltr;display:inline-block}img{background:#fff;padding:10px;border-radius:14px;margin:14px 0}.ok{color:#4ade80}</style></head><body><main><h1>πm Remote</h1><p>Server running</p><p>Status: <span class="ok">● RUNNING</span> · Sessions: ${spawned.size + v2Clients.size}</p><p>IP: <code>${lan}</code></p><p>Port: <code>${o.port}</code></p>${qrData ? `<img src="${qrData}" alt="QR"><p>امسح هذا الرمز من تطبيق πm Remote</p>` : `<p>Deep link: <code>${deepLink}</code></p>`}<p style="font-size:12px">الاتصال دائم — يعمل حتى بدون أي جلسات</p></main></body></html>`);
      return;
    }
    const ok = o.auth.authorize({ path, address, scope: "admin", header: req.headers.authorization });
    const okV2 = ok ? true : !!o.sessions && o.auth.authorize({ path, address, scope: "v2", header: req.headers.authorization });
    if (!ok && !okV2) { res.writeHead(o.auth.failureReason(address) === "rate_limited" ? 429 : 401, { "content-type": "application/json", "cache-control": "no-store" }); res.end('{"error":"unauthorized"}'); return; }
    if (o.sessions && req.method === "POST" && (path === "/admin/sessions/spawn" || path === "/admin/sessions/close")) {
      if (path === "/admin/sessions/spawn") {
        try {
          const child = spawn(o.sessions.piCommand, ["--mode", "rpc"], { cwd: homedir(), env: { ...process.env, PI_REMOTE_PROFILE: o.sessions.profileRoot }, stdio: ["pipe", "ignore", "pipe"] });
          child.stdin?.on?.("error", () => { }); child.stderr?.on?.("data", () => { });
          const pid = child.pid ?? 0;
          child.on("exit", () => { spawned.delete(pid); persistSpawned(); });
          if (pid) { spawned.set(pid, { startedAt: Date.now(), child }); persistSpawned(); }
          res.writeHead(200); res.end(JSON.stringify({ spawned: pid > 0, pid }));
        } catch (error) { res.writeHead(500); res.end(JSON.stringify({ error: "spawn_failed", message: error instanceof Error ? error.message : "unknown" })); }
        return;
      }
      const pid = Number(url.searchParams.get("pid"));
      if (!Number.isInteger(pid) || pid <= 0 || !spawned.has(pid)) { res.writeHead(404); res.end('{"error":"unknown_session"}'); return; }
      try { process.kill(pid, "SIGTERM"); } catch { }
      const entry = spawned.get(pid); try { entry?.child?.stdin?.end(); } catch { }
      spawned.delete(pid); persistSpawned();
      res.writeHead(200); res.end(JSON.stringify({ closing: true, pid }));
      return;
    }
    if (o.sessions && req.method === "GET" && path === "/admin/sessions") {
      res.writeHead(200); res.end(JSON.stringify({ sessions: [...spawned.entries()].map(([pid, entry]) => ({ pid, alive: alive(pid), startedAt: entry.startedAt })) }));
      return;
    }
    if (!ok) { res.writeHead(o.auth.failureReason(address) === "rate_limited" ? 429 : 401, { "content-type": "application/json", "cache-control": "no-store" }); res.end('{"error":"unauthorized"}'); return; }
    res.setHeader("content-type", "application/json"); res.setHeader("cache-control", "no-store");
    if (req.method === "POST") {
      if (path === "/admin/stop" && o.onStop) { res.writeHead(202); res.end('{"stopping":true}'); setImmediate(o.onStop); return; }
      if (path === "/admin/v2-token") { const processId = url.searchParams.get("processId"), advertised = url.searchParams.get("advertisedHost"); if (!processId || !o.v2) { res.writeHead(400); res.end('{"error":"process_required"}'); return; } const fallback = o.host === "0.0.0.0" || o.host === "::" ? "127.0.0.1" : o.host, selected = advertised ?? fallback; if (!validAdvertisedHost(selected)) { res.writeHead(400); res.end('{"error":"invalid_advertised_host"}'); return; } const token = o.auth.issue("v2", processId); try { const verifier = o.v2.verifier?.(token) ?? createHash("sha256").update(token).digest("hex"); o.v2.manager.assign(verifier, processId); } catch { o.auth.revoke(token); res.writeHead(409); res.end('{"error":"process_unavailable"}'); return; } const host = isIP(selected) === 6 ? `[${selected}]` : selected; res.end(JSON.stringify({ deepLink: `pi-remote://${host}:${api.port}?token=${encodeURIComponent(token)}` })); return; }
      if (path === "/admin/v2-rotate") { o.auth.revokeScope("v2"); for (const ws of v2Clients.keys()) ws.close(1008, "token rotated"); res.end('{"rotated":true}'); return; }
      if (path === "/admin/disable") { enabled = false; for (const ws of wsServer.clients) ws.close(1008, "remote control disabled"); res.end('{"enabled":false}'); return; }
      if (path === "/admin/enable") { enabled = true; res.end('{"enabled":true}'); return; }
      res.writeHead(404); res.end('{"error":"not_found"}'); return;
    }
    if (req.method !== "GET") { res.writeHead(405, { allow: "GET, POST" }); res.end(); return; }
    if (path === "/health/live") res.end(JSON.stringify({ daemon: "pi-remote", version: "0.1.0", live: true }));
    else if (path === "/health/ready") res.end(JSON.stringify({ daemon: "pi-remote", version: "0.1.0", ready: true, enabled, headCursor: o.headCursor(), minimumCursor: o.minimumCursor() }));
    else if (path === "/protocol") res.end(JSON.stringify({ versions: [3, 2], v2Path: "/", v3Path: "/control" }));
    else if (path === "/capabilities") res.end(JSON.stringify({ durableReplay: true, heartbeatIntervalMs: 10000 }));
    else { res.writeHead(404); res.end('{"error":"not_found"}'); }
  });
  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url ?? "/", "http://localhost"), address = req.socket.remoteAddress ?? "unknown";
    if (!enabled) { reject(socket, 503); return; }
    if (url.pathname !== "/" && url.pathname !== "/control") { reject(socket, 404); return; }
    const isV2 = url.pathname === "/";
    const ok = o.auth.authorize({ path: url.pathname, address, scope: isV2 ? "v2" : "admin", header: req.headers.authorization, queryToken: isV2 ? url.searchParams.get("token") ?? undefined : undefined, allowLoopbackBypass: isV2 ? o.allowNoAuthFromLoopback : undefined });
    if (!ok) { reject(socket, o.auth.failureReason(address) === "rate_limited" ? 429 : 401); return; }
    wsServer.handleUpgrade(req, socket, head, ws => {
      if (!isV2) {
        controlClients.add(ws); ws.once("close", () => controlClients.delete(ws));
        if (o.control) { o.control.accept(ws); return; }
        const session = new ProtocolSession([3], []);
        ws.on("message", (data, binary) => { if (binary) { ws.close(1002, "text required"); return; } try { const reply = session.receive(JSON.parse(String(data))); if (reply) ws.send(JSON.stringify(reply)); } catch (e) { const safe = redactForLog(e); ws.send(JSON.stringify({ protocolVersion: 3, type: "protocol_error", code: (safe as any).code ?? "invalid_message", message: "Protocol error" })); ws.close(1002, "protocol error"); } });
        return;
      }
      if (!o.v2) { ws.close(1008, "v2 unavailable"); return; }
      const verifier = o.v2.verifier?.(url.searchParams.get("token") ?? undefined) ?? createHash("sha256").update(url.searchParams.get("token") ?? "loopback").digest("hex");
      try {
        const processId = o.v2.manager.bindLegacy(verifier);
        v2Clients.set(ws, processId); ws.once("close", () => v2Clients.delete(ws));
        ws.send(JSON.stringify(o.v2.manager.hello(processId)));
        for (const item of o.v2.manager.history(o.v2.history(processId))) ws.send(JSON.stringify(item));
        ws.on("message", (data, binary) => { if (binary) { ws.close(1002, "text required"); return; } try { const value = JSON.parse(String(data)), command = o.v2!.manager.command(value); o.v2!.command?.(processId, command); } catch (error) { if (error instanceof Error && error.message === "target_unavailable") { ws.send(JSON.stringify({ type: "error", code: "target_unavailable" })); return; } ws.send(JSON.stringify({ type: "error", code: "invalid_message" })); ws.close(1002, "invalid message"); } });
      } catch {
        // No live session yet: keep the phone connected (Connection ≠ Session).
        v2Pending.set(ws, { verifier }); ws.once("close", () => v2Pending.delete(ws));
        ws.send(JSON.stringify({ type: "hello", state: { isIdle: true, cwd: "", model: { provider: "", id: "" } }, noSession: true }));
        ws.on("message", (data, binary) => { if (!binary) try { const parsed = JSON.parse(String(data)); if (parsed && typeof parsed.type === "string" && parsed.type !== "ping") ws.send(JSON.stringify({ type: "error", code: "no_session", message: "No active Pi session. Create one from the sessions drawer." })); } catch { } });
      }
    });
  });
  const api = {
    listen: () => new Promise<void>((resolve, rejectError) => { server.listen(o.port, o.host, resolve).once("error", rejectError); const bindTimer = setInterval(tryBindPending, 2000); bindTimer.unref?.(); api.bindTimer = bindTimer; }),
    close: () => { if (api.bindTimer) clearInterval(api.bindTimer); for (const ws of v2Pending.keys()) ws.close(1001, "daemon stopping"); for (const ws of wsServer.clients) ws.terminate(); return Promise.all([new Promise<void>((resolve, rejectError) => wsServer.close(e => e ? rejectError(e) : resolve())), new Promise<void>((resolve, rejectError) => server.close(e => e ? rejectError(e) : resolve()))]).then(() => undefined); },
    publishV2(processId: string, event: any) { if (!o.v2) return; const mapped = o.v2.manager.history([event]); for (const value of mapped) for (const [ws, target] of v2Clients) if (target === processId && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value)); },
    get port() { const address = server.address(); return typeof address === "object" && address ? address.port : 0; }, server, bindTimer: undefined as unknown as NodeJS.Timeout,
  };
  return api;
}
