/* =====================================================================
 * 管理后台公共脚本（原生 JavaScript，无第三方依赖）
 * 只做“体验增强”：所有业务校验（库存不足、幂等、权限）都在服务端完成，
 * 关闭 JS 后页面依然可以正常提交和使用。
 * ===================================================================== */
(function () {
    'use strict';
    var SVG_NS = 'http://www.w3.org/2000/svg';
    var nf = new Intl.NumberFormat('zh-CN');

    function el(tag, attrs, parent) {
        var n = document.createElementNS(SVG_NS, tag);
        Object.keys(attrs || {}).forEach(function (k) { n.setAttribute(k, attrs[k]); });
        if (parent) parent.appendChild(n);
        return n;
    }

    /* 1. 防重复提交：提交后禁用按钮并显示加载圈（服务端还有 requestId 幂等号兜底） */
    function initSubmitOnce() {
        document.querySelectorAll('form[data-once]').forEach(function (form) {
            form.addEventListener('submit', function () {
                form.querySelectorAll('button[type=submit]').forEach(function (btn) {
                    btn.disabled = true;
                    btn.insertAdjacentHTML('afterbegin', '<span class="spinner" aria-hidden="true"></span>');
                    var svg = btn.querySelector('svg'); if (svg) svg.remove();
                    btn.lastChild.textContent = btn.textContent.trim() + '…';
                });
            });
        });
    }

    /* 2. 危险操作二次确认：使用自定义 <dialog> 替代 window.confirm，支持 Esc 取消、焦点归还 */
    function initConfirm() {
        var dlg = document.getElementById('confirm-dialog');
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                if (form.dataset.confirmed === '1') return;          // 已确认，放行
                e.preventDefault();
                if (!dlg || typeof dlg.showModal !== 'function') {   // 老浏览器降级为原生 confirm
                    if (window.confirm(form.dataset.confirm)) { form.dataset.confirmed = '1'; form.submit(); }
                    return;
                }
                var trigger = document.activeElement;
                dlg.querySelector('#confirm-msg').textContent = form.dataset.confirm;
                var btnText = (form.querySelector('button[type=submit]') || {}).textContent || '确认';
                var ok = dlg.querySelector('[data-confirm-ok]');
                ok.textContent = '确认' + btnText.trim();
                dlg.showModal();
                dlg.querySelector('[data-confirm-cancel]').focus();
                var done = function (yes) {
                    dlg.close();
                    ok.onclick = null;
                    if (yes) { form.dataset.confirmed = '1'; form.submit(); }
                    else if (trigger) trigger.focus();
                };
                ok.onclick = function () { done(true); };
                dlg.querySelector('[data-confirm-cancel]').onclick = function () { done(false); };
                dlg.oncancel = function () { if (trigger) setTimeout(function () { trigger.focus(); }, 0); };
            });
        });
    }

    /* 3. 分页链接保留当前查询条件，只替换 page 参数 */
    function initPager() {
        document.querySelectorAll('a[data-page]').forEach(function (a) {
            var params = new URLSearchParams(window.location.search);
            params.set('page', a.dataset.page);
            a.href = window.location.pathname + '?' + params.toString();
        });
    }

    /* 4. 库存水位条：宽度 = 库存 / (阈值 × 3)，刻度线位于阈值处（满格代表 3 倍阈值以上） */
    function paintGauge(g, stock, threshold) {
        var full = Math.max(threshold * 3, 1);
        var pct = Math.max(0, Math.min(100, stock / full * 100));
        if (stock > 0 && pct < 3) pct = 3;                       // 有库存时至少露出一点
        g.style.setProperty('--w', pct + '%');
        g.style.setProperty('--m', Math.min(100, threshold / full * 100) + '%');
        g.classList.toggle('low', stock <= threshold);
        g.classList.toggle('zero', stock <= 0);
    }
    function initGauges() {
        document.querySelectorAll('.gauge[data-stock]').forEach(function (g) {
            paintGauge(g, Number(g.dataset.stock), Number(g.dataset.threshold || 0));
        });
    }

    /* 5. 出入库表单：右侧“库存预览”，实时计算操作后的库存（仅提示，不拦截提交） */
    function initPreview() {
        var panel = document.querySelector('[data-preview]');
        var select = document.querySelector('select[data-stock-hint]');
        if (!panel || !select) return;
        var kind = panel.dataset.preview;
        var qtyInput = document.getElementById('quantity');
        var $ = function (s) { return panel.querySelector(s); };
        var update = function () {
            var opt = select.options[select.selectedIndex];
            var has = opt && opt.dataset.stock !== undefined;
            $('[data-preview-empty]').hidden = has;
            $('[data-preview-body]').hidden = !has;
            if (!has) return;
            var stock = Number(opt.dataset.stock), unit = opt.dataset.unit || '', th = Number(opt.dataset.threshold || 0);
            var qty = Math.max(0, parseInt(qtyInput && qtyInput.value, 10) || 0);
            var after = kind === 'out' ? stock - qty : stock + qty;
            $('[data-p-name]').textContent = opt.dataset.name || opt.textContent;
            $('[data-p-stock]').textContent = nf.format(stock) + ' ' + unit;
            $('[data-p-qty]').textContent = (qty ? (kind === 'out' ? '−' : '+') : '') + nf.format(qty) + ' ' + unit;
            $('[data-p-after]').textContent = nf.format(after) + ' ' + unit;
            paintGauge($('[data-p-gauge]'), Math.max(after, 0), th);
            var note = $('[data-p-note]'), text = note.querySelector('span');
            note.classList.remove('bad', 'good');
            if (kind === 'out' && qty > stock) {
                note.classList.add('bad');
                text.textContent = '出库数量超过当前库存，提交后服务端会拒绝本次出库，库存保持不变。';
                $('[data-p-after]').classList.add('text-danger');
            } else {
                $('[data-p-after]').classList.toggle('text-danger', after <= th);
                if (qty > 0 && after <= th) text.textContent = '操作后库存将低于预警阈值（' + th + ' ' + unit + '）。';
                else if (qty > 0) { note.classList.add('good'); text.textContent = '库存充足，可以提交。实际结果以服务端原子更新为准。'; }
                else text.textContent = '填写数量后预览操作后的库存。';
            }
        };
        select.addEventListener('change', update);
        if (qtyInput) qtyInput.addEventListener('input', update);
        update();
    }

    /* 6. 提示：成功 Toast 4 秒后淡出，可手动关闭 */
    function initToasts() {
        document.querySelectorAll('.toast').forEach(function (t) {
            var close = function () { t.classList.add('hide'); setTimeout(function () { t.remove(); }, 220); };
            var btn = t.querySelector('[data-dismiss]'); if (btn) btn.addEventListener('click', close);
            var timer = setTimeout(close, 4000);
            t.addEventListener('mouseenter', function () { clearTimeout(timer); });
        });
    }

    /* 7. 侧栏抽屉（小屏）+ 面包屑 + 当前页 aria-current */
    function initNav() {
        var body = document.body, toggle = document.querySelector('[data-nav-toggle]');
        var setOpen = function (open) { body.classList.toggle('nav-open', open); if (toggle) toggle.setAttribute('aria-expanded', open); };
        if (toggle) toggle.addEventListener('click', function () { setOpen(!body.classList.contains('nav-open')); });
        document.querySelectorAll('[data-nav-close]').forEach(function (o) { o.addEventListener('click', function () { setOpen(false); }); });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape') setOpen(false); });
        var active = document.querySelector('.nav a.active'), crumbs = document.getElementById('crumbs');
        if (active) active.setAttribute('aria-current', 'page');
        if (active && crumbs) {
            var h1 = document.querySelector('.page-head h1');
            var parts = [active.dataset.group, active.textContent.trim()];
            if (h1 && h1.textContent.trim() !== parts[1]) parts.push(h1.textContent.trim());
            crumbs.innerHTML = '';
            parts.forEach(function (t, i) {
                if (i) { var s = document.createElement('span'); s.className = 'sep'; s.textContent = '/'; crumbs.appendChild(s); }
                var n = document.createElement(i === parts.length - 1 ? 'b' : 'span'); n.textContent = t; crumbs.appendChild(n);
            });
        }
        document.querySelectorAll('[data-back]').forEach(function (b) { b.addEventListener('click', function () { history.back(); }); });
    }

    /* 8. 近 7 日出入库柱状图（原生 SVG）：数据取自页面内的可访问表格，支持线性/对数刻度 */
    function initTrendChart() {
        var box = document.querySelector('[data-chart=trend]');
        if (!box) return;
        var rows = Array.prototype.slice.call(box.querySelectorAll('[data-chart-source] tbody tr')).map(function (tr) {
            var td = tr.querySelectorAll('td');
            return { d: td[0].textContent.trim(), i: Number(td[1].textContent), o: Number(td[2].textContent) };
        });
        var tip = document.createElement('div'); tip.className = 'chart-tip'; box.appendChild(tip);
        var svg = el('svg', { 'class': 'chart-svg', role: 'img', 'aria-label': '近 7 日每日入库与出库件数柱状图', preserveAspectRatio: 'none' });
        box.insertBefore(svg, box.firstChild);

        function draw(scale) {
            while (svg.firstChild) svg.removeChild(svg.firstChild);
            svg.style.height = '260px';   // 先复位再测量，使图表高度跟随同排卡片
            var W = box.clientWidth - 40 || 600, H = Math.max(260, box.clientHeight - 26), L = 52, R = 8, T = 18, B = 30;
            svg.setAttribute('viewBox', '0 0 ' + W + ' ' + H);
            svg.style.height = H + 'px';
            var max = Math.max.apply(null, rows.map(function (r) { return Math.max(r.i, r.o); }).concat([1]));
            var y, ticks = [];
            if (scale === 'log') {
                var top = Math.pow(10, Math.ceil(Math.log10(max + 1)));
                y = function (v) { return H - B - (Math.log10(v + 1) / Math.log10(top + 1)) * (H - T - B); };
                for (var p = 1; p <= top; p *= 10) ticks.push(p);
                ticks.unshift(0);
            } else {
                var step = Math.pow(10, Math.floor(Math.log10(max))), nice = Math.ceil(max / step) * step;
                if (nice / step > 6) step *= 2;
                nice = Math.ceil(max / step) * step;
                y = function (v) { return H - B - v / nice * (H - T - B); };
                for (var t = 0; t <= nice + 1e-9; t += step) ticks.push(t);
            }
            ticks.forEach(function (t) {
                el('line', { 'class': 'grid-line', x1: L, x2: W - R, y1: y(t), y2: y(t) }, svg);
                var lab = el('text', { 'class': 'axis-label', x: L - 8, y: y(t) + 4, 'text-anchor': 'end' }, svg);
                lab.textContent = t >= 10000 ? (t / 10000) + '万' : nf.format(t);
            });
            var slot = (W - L - R) / rows.length, bw = Math.min(22, slot / 4);
            rows.forEach(function (r, k) {
                var cx = L + slot * k + slot / 2;
                [['i', 'bar-in', -1], ['o', 'bar-out', 1]].forEach(function (s) {
                    var v = r[s[0]], yy = y(v), h = Math.max(0, H - B - yy);
                    if (v > 0 && h < 2) { h = 2; yy = H - B - 2; }
                    var x = cx + (s[2] < 0 ? -bw - 2 : 2);
                    el('rect', { 'class': s[1], x: x, y: yy, width: bw, height: h, rx: 3 }, svg);
                    if (v > 0) {
                        var t = el('text', { 'class': 'bar-val', x: x + bw / 2, y: yy - 5, 'text-anchor': 'middle' }, svg);
                        t.textContent = v >= 10000 ? (v / 10000).toFixed(1) + '万' : nf.format(v);
                    }
                });
                var lab = el('text', { 'class': 'axis-label', x: cx, y: H - 8, 'text-anchor': 'middle' }, svg);
                lab.textContent = slot < 52 ? r.d.split('-').pop() : r.d;
                var hit = el('rect', { 'class': 'hit', x: L + slot * k, y: T, width: slot, height: H - T - B }, svg);
                hit.addEventListener('mousemove', function (e) {
                    tip.innerHTML = '<b>' + r.d + '</b><br>入库 ' + nf.format(r.i) + ' 件　出库 ' + nf.format(r.o) + ' 件';
                    var bx = box.getBoundingClientRect();
                    tip.style.left = Math.min(e.clientX - bx.left + 12, bx.width - 170) + 'px';
                    tip.style.top = (e.clientY - bx.top - 50) + 'px';
                    tip.classList.add('show');
                });
                hit.addEventListener('mouseleave', function () { tip.classList.remove('show'); });
            });
        }
        var current = 'linear';
        document.querySelectorAll('[data-scale]').forEach(function (b) {
            b.addEventListener('click', function () {
                current = b.dataset.scale;
                document.querySelectorAll('[data-scale]').forEach(function (x) { x.setAttribute('aria-pressed', x === b); });
                draw(current);
            });
        });
        draw(current);
        var rt; window.addEventListener('resize', function () { clearTimeout(rt); rt = setTimeout(function () { draw(current); }, 120); });
    }

    /* 9. 库存健康度环形图（原生 SVG） */
    function initDonut() {
        var wrap = document.querySelector('[data-chart=donut]');
        if (!wrap) return;
        var total = Number(wrap.dataset.total), low = Number(wrap.dataset.low), ok = Math.max(total - low, 0);
        var svg = wrap.querySelector('svg.donut'), r = 54, c = 2 * Math.PI * r;
        el('circle', { cx: 70, cy: 70, r: r, fill: 'none', stroke: '#e6ebea', 'stroke-width': 16 }, svg);
        if (total > 0) {
            var okLen = c * ok / total;
            el('circle', { cx: 70, cy: 70, r: r, fill: 'none', stroke: 'var(--teal)', 'stroke-width': 16,
                'stroke-dasharray': okLen + ' ' + (c - okLen), transform: 'rotate(-90 70 70)' }, svg);
            el('circle', { cx: 70, cy: 70, r: r, fill: 'none', stroke: 'var(--signal)', 'stroke-width': 16,
                'stroke-dasharray': (c - okLen) + ' ' + okLen, 'stroke-dashoffset': -okLen, transform: 'rotate(-90 70 70)' }, svg);
        }
        var pct = total ? Math.round(ok / total * 100) : 0;
        var t1 = el('text', { 'class': 'donut-center', x: 70, y: 72, 'text-anchor': 'middle' }, svg); t1.textContent = pct + '%';
        var t2 = el('text', { 'class': 'donut-sub', x: 70, y: 92, 'text-anchor': 'middle' }, svg); t2.textContent = '库存充足';
    }

    /* 10. 大数字加千分位 */
    function initNumbers() {
        document.querySelectorAll('[data-format-number]').forEach(function (n) {
            var v = Number(n.textContent.trim()); if (!isNaN(v)) n.textContent = nf.format(v);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initNav(); initSubmitOnce(); initConfirm(); initPager(); initGauges();
        initPreview(); initToasts(); initTrendChart(); initDonut(); initNumbers();
    });
})();
