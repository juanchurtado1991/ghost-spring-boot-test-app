// ─── Chart ────────────────────────────────────────────────────────────────────

const heapChart = buildHeapChart();

function buildHeapChart() {
    const ctx = document.getElementById('heapChart').getContext('2d');
    return new Chart(ctx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Heap',
                data: [],
                borderColor: '#6366f1',
                backgroundColor: 'rgba(99, 102, 241, 0.05)',
                borderWidth: 1.5,
                fill: true,
                tension: 0.4,
                pointRadius: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: { display: false },
                y: { min: 0, max: 100, grid: { color: '#ffffff05' }, ticks: { color: '#94a3b8', font: { size: 10 } } }
            },
            plugins: { legend: { display: false } }
        }
    });
}

// ─── Server stats (SSE) ───────────────────────────────────────────────────────

const statsStream = new EventSource('/api/v1/stats');
statsStream.onmessage = (e) => updateServerStats(JSON.parse(e.data));

function updateServerStats(stats) {
    setText('heap-val',    `${stats.usedHeapMb.toFixed(1)} MB`);
    setText('alloc-val',   `${stats.totalAllocatedGb.toFixed(2)} GB`);
    setText('threads-val', stats.activeThreads);
    pushChartPoint(stats.usedHeapMb);
}

function pushChartPoint(value) {
    heapChart.data.labels.push('');
    heapChart.data.datasets[0].data.push(value);
    if (heapChart.data.labels.length > 60) {
        heapChart.data.labels.shift();
        heapChart.data.datasets[0].data.shift();
    }
    heapChart.update('none');
}

// ─── Benchmark ────────────────────────────────────────────────────────────────

async function startTest(engine) {
    const operation = document.getElementById('operation-select').value;
    const mode      = document.getElementById('mode-select').value;

    setBenchmarkRunning(engine, true);
    clearLog();

    try {
        const data = await fetchBenchmarkResult(engine, operation, mode);
        renderBenchmarkResult(engine, operation, mode, data);
    } catch (err) {
        log(`!!! ERROR: ${err.message}`);
    } finally {
        setBenchmarkRunning(engine, false);
    }
}

async function fetchBenchmarkResult(engine, operation, mode) {
    const url = `/api/v1/benchmark/run?engine=${engine}&operation=${operation}&mode=${mode}`;
    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
}

function renderBenchmarkResult(engine, operation, mode, data) {
    const allocKb   = toKb(data.garbageBytes);
    const latencyMs = toNumber(data.latencyMs);
    const payloadKb = toKb(data.payloadSize);

    updateDashboard(allocKb, latencyMs);
    printResultLog(engine, operation, mode, allocKb, latencyMs, payloadKb);
}

function updateDashboard(allocKb, latencyMs) {
    const allocMb = allocKb / 1024;
    setText('garbage-val',    allocMb.toFixed(2));
    setText('latency-val',    latencyMs.toFixed(2));
    setText('efficiency-val', latencyMs > 0 ? (1000 / latencyMs).toFixed(1) : '--');
}

function printResultLog(engine, operation, mode, allocKb, latencyMs, payloadKb) {
    log(`>>> ENGINE: ${engine.toUpperCase()} [${operation.toUpperCase()} - ${mode.toUpperCase()}]`, true);
    log(`>>> RAW GARBAGE: ${allocKb.toFixed(2)} KB`);
    log(`>>> INTERNAL LATENCY: ${latencyMs.toFixed(2)} ms`);
    if (operation === 'write') log(`>>> PAYLOAD SIZE: ${payloadKb.toFixed(2)} KB`);
    log('------------------------------');
    log('>>> TEST COMPLETED', true);
    log(`>>> FINAL VERDICT: ${engine.toUpperCase()} is ready.`);
}

// ─── UI state ─────────────────────────────────────────────────────────────────

const ENGINE_COLORS = { ghost: '#6366f1', kotlinx: '#7c3aed', jackson: '#94a3b8' };

function setBenchmarkRunning(engine, running) {
    const status    = document.getElementById('engine-status');
    const buttons   = document.querySelectorAll('.btn');
    const activeBtn = document.querySelector(`.btn-${engine}`);

    status.innerText   = running ? `RUNNING ${engine.toUpperCase()}...` : 'READY';
    status.style.color = running ? (ENGINE_COLORS[engine] ?? '#94a3b8') : '';
    buttons.forEach(b => b.disabled = running);
    activeBtn.classList.toggle('loading', running);
}

// ─── Log ──────────────────────────────────────────────────────────────────────

function clearLog() {
    document.getElementById('log-list').innerHTML = '';
}

function log(text, highlight = false) {
    const list  = document.getElementById('log-list');
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerText = text;
    if (highlight) {
        entry.style.color      = 'var(--accent)';
        entry.style.fontWeight = '900';
        entry.style.borderLeft = '2px solid var(--accent)';
    }
    list.appendChild(entry);
    list.scrollTop = list.scrollHeight;
}

// ─── Utils ────────────────────────────────────────────────────────────────────

function setText(id, value) {
    document.getElementById(id).innerText = value;
}

function toNumber(value) {
    const n = Number(value);
    return isFinite(n) ? n : 0;
}

function toKb(bytes) {
    return toNumber(bytes) / 1024;
}
