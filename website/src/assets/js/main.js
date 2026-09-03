/**
 * 乾腾元官网 · 交互脚本
 * 无第三方依赖，全部能力：移动导航、滚动进场、数字滚动、表单校验
 */
(function () {
  'use strict';

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------------------------------------------------------------- 顶部导航 */
  const header = document.getElementById('siteHeader');
  const nav = document.getElementById('primaryNav');
  const toggle = document.getElementById('navToggle');
  const scrim = document.getElementById('navScrim');

  // 滚动后加深分隔线
  const onScroll = () => {
    if (header) header.classList.toggle('is-scrolled', window.scrollY > 8);
  };
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  function setNav(open) {
    if (!nav || !toggle) return;
    nav.classList.toggle('is-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? '关闭导航菜单' : '打开导航菜单');
    document.body.classList.toggle('nav-open', open);
    if (scrim) scrim.hidden = !open;
  }

  if (toggle) {
    toggle.addEventListener('click', () => {
      setNav(toggle.getAttribute('aria-expanded') !== 'true');
    });
  }
  if (scrim) scrim.addEventListener('click', () => setNav(false));
  if (nav) {
    nav.addEventListener('click', (e) => {
      if (e.target.closest('a')) setNav(false);
    });
  }
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') setNav(false);
  });
  window.addEventListener('resize', () => {
    if (window.innerWidth > 768) setNav(false);
  });

  // 构建期未标记 active 时的兜底（按路径匹配）
  if (!document.querySelector('.nav-link[data-active]')) {
    const here = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.nav-link').forEach((link) => {
      if (link.getAttribute('href') === '/' + here) {
        link.setAttribute('data-active', 'true');
        link.setAttribute('aria-current', 'page');
      }
    });
  }

  /* ------------------------------------------------------------ 滚动进场动画 */
  const revealEls = document.querySelectorAll('.reveal');
  if (reduceMotion || !('IntersectionObserver' in window)) {
    revealEls.forEach((el) => el.classList.add('is-visible'));
  } else {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry, i) => {
          if (!entry.isIntersecting) return;
          const el = entry.target;
          const delay = Number(el.dataset.revealDelay || 0) || Math.min(i * 60, 240);
          setTimeout(() => el.classList.add('is-visible'), delay);
          io.unobserve(el);
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
    );
    revealEls.forEach((el) => io.observe(el));
  }

  /* ------------------------------------------------------------------ 数字滚动 */
  function animateCount(el) {
    const target = parseFloat(el.dataset.count);
    if (Number.isNaN(target)) return;
    const prefix = el.dataset.prefix || '';
    const suffix = el.dataset.suffix || '';
    const decimals = (el.dataset.count.split('.')[1] || '').length;
    const duration = 1100;
    const start = performance.now();

    const step = (now) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      const value = target * eased;
      el.textContent = prefix + value.toFixed(decimals) + suffix;
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  const counters = document.querySelectorAll('[data-count]');
  if (reduceMotion || !('IntersectionObserver' in window)) {
    counters.forEach((el) => {
      el.textContent = (el.dataset.prefix || '') + el.dataset.count + (el.dataset.suffix || '');
    });
  } else {
    const cio = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          animateCount(entry.target);
          cio.unobserve(entry.target);
        });
      },
      { threshold: 0.5 }
    );
    counters.forEach((el) => cio.observe(el));
  }

  /* -------------------------------------------------------------- 预约表单校验 */
  const form = document.getElementById('leadForm');
  if (form) {
    const status = document.getElementById('formStatus');

    const rules = {
      company: (v) => (v.trim().length >= 2 ? '' : '请填写公司名称'),
      name: (v) => (v.trim().length >= 2 ? '' : '请填写联系人姓名'),
      contact: (v) => {
        const value = v.trim();
        const isMobile = /^1[3-9]\d{9}$/.test(value);
        const isEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
        return isMobile || isEmail ? '' : '请填写正确的手机号或邮箱';
      },
      scene: (v) => (v ? '' : '请选择想先聊的场景')
    };

    const validateField = (input) => {
      const rule = rules[input.name];
      if (!rule) return true;
      const message = rule(input.value);
      const errorEl = form.querySelector(`[data-error-for="${input.name}"]`);
      input.classList.toggle('is-invalid', Boolean(message));
      if (errorEl) errorEl.textContent = message;
      return !message;
    };

    form.querySelectorAll('input, select').forEach((input) => {
      input.addEventListener('blur', () => validateField(input));
      input.addEventListener('input', () => {
        if (input.classList.contains('is-invalid')) validateField(input);
      });
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const inputs = Array.from(form.querySelectorAll('input, select'));
      const valid = inputs.map(validateField).every(Boolean);

      if (!valid) {
        if (status) {
          status.textContent = '还有信息没填完，请检查标红的字段。';
          status.dataset.state = 'error';
          status.classList.add('is-visible');
        }
        const firstBad = form.querySelector('.is-invalid');
        if (firstBad) firstBad.focus();
        return;
      }

      const submitBtn = form.querySelector('button[type="submit"]');
      const original = submitBtn ? submitBtn.textContent : '';
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '正在打开邮件…';
      }

      // 收件人：默认 coolxuhanbing@gmail.com，可在 about.html 的表单 data-email 上改
      const recipient = (form.dataset.email || 'coolxuhanbing@gmail.com').trim();
      const val = (name) => (form.elements[name] ? String(form.elements[name].value).trim() : '');
      const sceneSel = form.elements['scene'];
      const sceneText =
        sceneSel && sceneSel.selectedOptions && sceneSel.selectedOptions[0]
          ? sceneSel.selectedOptions[0].textContent.trim()
          : val('scene');

      const bodyLines = [
        '收到一条新的「预约演示」申请：',
        '',
        `公司名称：${val('company')}`,
        `联系人：${val('name')}`,
        `联系方式：${val('contact')}`,
        `意向场景：${sceneText}`,
        '',
        `提交时间：${new Date().toLocaleString('zh-CN')}`,
        '—— 来自乾腾元官网预约表单'
      ];
      const subject = `【乾腾元】新的预约演示申请 · ${val('company')}`;

      // 纯静态站点无后端，采用 mailto 触发草稿到接收邮箱。
      // 如需零点击「真·自动发送」，把下面这行换成 EmailJS / Formspree 调用即可（见 README）。
      const mailto = `mailto:${recipient}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(
        bodyLines.join('\n')
      )}`;

      // 延迟一拍再跳转，确保状态文案先渲染出来
      window.setTimeout(() => {
        window.location.href = mailto;
      }, 120);

      form.reset();
      if (status) {
        status.textContent = '已为你生成邮件草稿，在邮件客户端点「发送」即可把需求发给顾问。';
        status.dataset.state = 'ok';
        status.classList.add('is-visible');
      }
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = original;
      }
    });
  }
})();
