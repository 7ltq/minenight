(() => {
  'use strict';
  const prices={monthly:{VIP:'4.99',MVP:'9.99'},lifetime:{VIP:'20.00',MVP:'30.00'}};
  document.querySelectorAll('input[name="billing"]').forEach(input=>input.addEventListener('change',()=>{
    const lifetime=input.value==='lifetime';
    document.querySelectorAll('[data-price]').forEach(el=>{el.textContent='€'+prices[input.value][el.dataset.price];});
    document.querySelectorAll('[data-period]').forEach(el=>{el.textContent=lifetime?'one-time':'/month';});
    document.querySelectorAll('[data-payment-note]').forEach(el=>{el.textContent=lifetime?'Lifetime · One-time payment':'Monthly subscription · Renews every month';});
  }));
  // Product URLs are placeholders only. Enabling checkout requires a future release.
})();
