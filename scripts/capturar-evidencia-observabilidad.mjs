/**
 * P-2 — Captura de evidencia de observabilidad.
 *
 * Genera las capturas exigidas por el entregable: los cuatro dashboards de
 * Grafana con datos reales y una alerta disparada (Prometheus + Alertmanager).
 *
 * Requisitos: el stack levantado (`docker compose up -d`) y trafico ya generado,
 * si no los paneles salen vacios. El perfil debe ser `staging` o `prod`: en `dev`
 * el backend no emite JSON y los paneles de logs no pueden filtrar por usuario
 * ni por endpoint.
 *
 *   node scripts/capturar-evidencia-observabilidad.mjs [directorio-salida]
 *
 * Playwright se toma de e2e/node_modules para no duplicar la dependencia.
 */
// @playwright/test es CommonJS: import por defecto y desestructurar.
import playwright from '../e2e/node_modules/@playwright/test/index.js';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';

const { chromium } = playwright;

const SALIDA = resolve(process.argv[2] ?? 'docs/testing/capturas');
const GRAFANA = process.env.GRAFANA_URL ?? 'http://localhost:3001';
const PROMETHEUS = process.env.PROMETHEUS_URL ?? 'http://localhost:9090';
const ALERTMANAGER = process.env.ALERTMANAGER_URL ?? 'http://localhost:9093';
const USUARIO = 'admin';
const CLAVE = process.env.GRAFANA_ADMIN_PASSWORD;

// Ventana temporal de los dashboards.
//
// Ojo al elegirla: las metricas llevan la etiqueta `profile`, asi que un rango que
// abarque un cambio de perfil (dev -> staging) muestra las dos series a la vez y los
// paneles salen duplicados: dos tiles de uptime, `activas/activas` en el pool, dos
// `hilos vivos`. La ventana debe empezar despues del ultimo arranque del backend.
const RANGO = `from=${process.env.CAPTURA_DESDE ?? 'now-25m'}&to=${process.env.CAPTURA_HASTA ?? 'now'}`;

const DASHBOARDS = [
  ['inventory-infra', '01-infraestructura'],
  ['inventory-aplicacion', '02-aplicacion'],
  ['inventory-negocio', '03-negocio'],
  ['inventory-seguridad', '04-seguridad'],
];

if (!CLAVE) {
  console.error('Falta GRAFANA_ADMIN_PASSWORD. Exportala desde .env antes de ejecutar.');
  process.exit(1);
}

/**
 * Grafana monta los paneles por scroll. Sin recorrer el dashboard entero, los que
 * quedan bajo el pliegue no llegan a existir en el DOM y la captura sale con las
 * cabeceras de fila y nada debajo. `fullPage` no basta: estira la pagina pero no
 * dispara el montaje.
 */
async function recorrerDashboard(page) {
  await page.evaluate(async () => {
    const esScrollable = (el) => el && el.scrollHeight > el.clientHeight + 50;
    const candidatos = [
      document.querySelector('[data-testid="Dashboard.Canvas"]'),
      document.querySelector('.scrollbar-view'),
      document.querySelector('main'),
      document.scrollingElement,
    ].filter(esScrollable);
    const cont = candidatos[0] ?? document.scrollingElement;
    const paso = cont.clientHeight * 0.8;
    for (let y = 0; y < cont.scrollHeight; y += paso) {
      cont.scrollTop = y;
      await new Promise((r) => setTimeout(r, 400));
    }
    cont.scrollTop = 0;
    await new Promise((r) => setTimeout(r, 400));
  });
}

/** Espera a que los paneles terminen de consultar, no solo a que cargue el HTML. */
async function esperarPaneles(page) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await recorrerDashboard(page);
  // Grafana marca los paneles en vuelo con este atributo; si no aparece ninguno
  // es que ya resolvieron todos.
  await page
    .waitForFunction(() => document.querySelectorAll('[data-testid="panel-loading-bar"]').length === 0, {
      timeout: 15_000,
    })
    .catch(() => {});
  await page.waitForTimeout(2_500);
}

/**
 * Devuelve [totalPaneles, panelesSinDatos]. El total importa tanto como el resto:
 * si sale 0 la captura esta vacia y el "0 sin datos" seria un falso verde.
 */
async function inventarioPaneles(page) {
  return page.evaluate(() => {
    const paneles = [...document.querySelectorAll('.react-grid-item')];
    const vacios = paneles.filter((p) => /No data|Sin datos/i.test(p.textContent ?? '')).length;
    return [paneles.length, vacios];
  });
}

const ANCHO = 1920;
const ALTO_BASE = 1080;
const ALTO_MAX = 4000; // tope para no generar PNG inmanejables

const navegador = await chromium.launch();
const contexto = await navegador.newContext({
  viewport: { width: ANCHO, height: ALTO_BASE },
  deviceScaleFactor: 2, // legible al proyectar en clase
});
const page = await contexto.newPage();

await mkdir(SALIDA, { recursive: true });

console.log('== Login en Grafana ==');
await page.goto(`${GRAFANA}/login`, { waitUntil: 'domcontentloaded' });
await page.fill('input[name="user"]', USUARIO);
await page.fill('input[name="password"]', CLAVE);
await page.click('button[type="submit"]');
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20_000 });
console.log('  ok');

console.log('== Dashboards ==');
let incidencias = 0;
for (const [uid, nombre] of DASHBOARDS) {
  await page.setViewportSize({ width: ANCHO, height: ALTO_BASE });
  await page.goto(`${GRAFANA}/d/${uid}?kiosk&${RANGO}`, { waitUntil: 'domcontentloaded' });
  await esperarPaneles(page);

  // Se agranda el viewport hasta que el dashboard entero quepa y se captura sin
  // `fullPage`. Con `fullPage` Chromium redimensiona la ventana durante la captura,
  // Grafana desmonta y remonta los paneles, y la imagen sale en blanco aunque el
  // DOM tenga los paneles: el dashboard de Aplicacion salia con solo las cabeceras.
  const alto = await page.evaluate(() => {
    const cont =
      document.querySelector('[data-testid="Dashboard.Canvas"]') ??
      document.querySelector('.scrollbar-view') ??
      document.scrollingElement;
    return Math.ceil(Math.max(cont.scrollHeight, document.body.scrollHeight));
  });
  await page.setViewportSize({ width: ANCHO, height: Math.min(Math.max(alto + 80, ALTO_BASE), ALTO_MAX) });
  await esperarPaneles(page);

  const [total, vacios] = await inventarioPaneles(page);
  await page.screenshot({ path: `${SALIDA}/${nombre}.png` });
  const aviso = total === 0 ? '  <- CAPTURA VACIA' : vacios > 0 ? '  <- revisar' : '';
  if (total === 0 || vacios > 0) incidencias++;
  console.log(`  ${nombre}.png  paneles: ${total}  sin datos: ${vacios}  alto: ${alto}px${aviso}`);
}

console.log('== Alerta disparada ==');
await page.goto(`${PROMETHEUS}/alerts?search=`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2_000);
await page.screenshot({ path: `${SALIDA}/05-prometheus-alertas.png`, fullPage: true });

const estado = await page.evaluate(() => {
  const texto = document.body.innerText;
  const m = texto.match(/ProductosBajoMinimo[\s\S]{0,200}/);
  return m ? m[0].replace(/\s+/g, ' ').slice(0, 120) : 'no encontrada';
});
console.log(`  05-prometheus-alertas.png  ->  ${estado}`);

await page.goto(`${ALERTMANAGER}/#/alerts`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(3_000);
await page.screenshot({ path: `${SALIDA}/06-alertmanager.png`, fullPage: true });
console.log('  06-alertmanager.png');

await navegador.close();
console.log(`\nCapturas en ${SALIDA}`);
if (incidencias > 0) {
  console.log(`${incidencias} dashboard(s) con paneles vacios: no sirven como evidencia sin revisar.`);
  process.exitCode = 1;
}
