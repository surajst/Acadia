/*
 * Global button loaders for ACADIA web pages.
 *
 * - Any <form> submit spins + disables its submit button so slow POST
 *   round-trips give visible feedback (the page navigates away right after).
 * - Any button with data-loader (typically an AJAX trigger) spins on click;
 *   its handler is responsible for re-enabling via window.acadiaResetLoader(btn)
 *   or by reloading the page.
 *
 * Idempotent and opt-out aware: a button already spinning is left alone, and
 * a form/button marked data-no-loader is skipped (e.g. logout).
 */
(function () {
  if (window.__acadiaLoaders) return; // guard against double-include
  window.__acadiaLoaders = true;

  var css = '.acadia-spinner{display:inline-block;width:14px;height:14px;border:2px solid rgba(255,255,255,0.45);border-top-color:#fff;border-radius:50%;animation:acadiaSpin .6s linear infinite;vertical-align:-2px;margin-right:6px}'
    + '@keyframes acadiaSpin{to{transform:rotate(360deg)}}'
    + 'button.js-loading,[data-loader].js-loading{opacity:.85;cursor:progress}';
  var style = document.createElement('style');
  style.textContent = css;
  document.head.appendChild(style);

  function spin(btn, fallbackText) {
    if (!btn || btn.classList.contains('js-loading') || btn.disabled) return;
    btn.dataset.origHtml = btn.innerHTML;
    btn.classList.add('js-loading');
    // Keep the button's own label so the spinner reads correctly everywhere
    // ("Sign In" stays "Sign In", "Add Classroom" stays "Add Classroom").
    // An explicit data-loading-text wins; icon-only buttons fall back.
    var own = (btn.textContent || '').trim();
    var label = btn.dataset.loadingText || own || fallbackText || 'Working…';
    btn.innerHTML = '<span class="acadia-spinner"></span>' + label;
    btn.disabled = true;
  }

  // Expose a reset so AJAX handlers can restore a button after their request.
  window.acadiaResetLoader = function (btn) {
    if (!btn || !btn.classList.contains('js-loading')) return;
    if (btn.dataset.origHtml != null) btn.innerHTML = btn.dataset.origHtml;
    btn.classList.remove('js-loading');
    btn.disabled = false;
  };

  // Form submits — bubble phase on document, so we run AFTER the form's own
  // handlers. AJAX forms call preventDefault() and never navigate, so we skip
  // them (spinning a button they never reset would hang it); only real
  // navigating submissions get the loader.
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!(form instanceof HTMLFormElement) || form.hasAttribute('data-no-loader')) return;
    if (e.defaultPrevented) return; // AJAX / handled-in-page submit
    if (typeof form.checkValidity === 'function' && !form.checkValidity()) return;
    var btn = form.querySelector('button[type="submit"], input[type="submit"]');
    spin(btn, 'Saving…');
  }, false);

  // Opt-in AJAX buttons.
  document.addEventListener('click', function (e) {
    var btn = e.target.closest ? e.target.closest('[data-loader]') : null;
    if (btn) spin(btn, btn.getAttribute('data-loader') || 'Working…');
  }, true);

  // Dismissible flash banners: any element marked data-flash gets a close (✕)
  // button; data-autohide="<ms>" also fades it out automatically.
  function initFlash() {
    var nodes = document.querySelectorAll('[data-flash]');
    for (var i = 0; i < nodes.length; i++) {
      (function (el) {
        if (el.dataset.flashInit) return;
        el.dataset.flashInit = '1';
        if (getComputedStyle(el).position === 'static') el.style.position = 'relative';
        var x = document.createElement('button');
        x.type = 'button';
        x.textContent = '✕';
        x.setAttribute('aria-label', 'Dismiss');
        x.style.cssText = 'position:absolute;top:8px;right:10px;padding:2px 6px;border:0;background:transparent;cursor:pointer;font-size:13px;line-height:1;color:inherit;opacity:.55';
        x.addEventListener('mouseenter', function () { x.style.opacity = '1'; });
        x.addEventListener('mouseleave', function () { x.style.opacity = '.55'; });
        function dismiss() { el.style.transition = 'opacity .35s'; el.style.opacity = '0'; setTimeout(function () { el.remove(); }, 350); }
        x.addEventListener('click', dismiss);
        el.appendChild(x);
        var ah = parseInt(el.dataset.autohide || '0', 10);
        if (ah > 0) setTimeout(dismiss, ah);
      })(nodes[i]);
    }
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initFlash);
  else initFlash();
})();
