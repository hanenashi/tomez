const blocks = [...document.querySelectorAll('[data-lang]')];
const buttons = [...document.querySelectorAll('[data-lang-button]')];
function setLang(lang) {
  const chosen = lang === 'cs' ? 'cs' : 'en';
  document.documentElement.lang = chosen;
  blocks.forEach(el => el.hidden = el.dataset.lang !== chosen);
  buttons.forEach(button => {
    const active = button.dataset.langButton === chosen;
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
  });
  try { localStorage.setItem('tomez-lang', chosen); } catch (_) { /* Storage is optional. */ }
}
buttons.forEach(button => button.addEventListener('click', () => setLang(button.dataset.langButton)));
let saved;
try { saved = localStorage.getItem('tomez-lang'); } catch (_) { /* Use browser language. */ }
setLang(saved || (navigator.language.toLowerCase().startsWith('cs') ? 'cs' : 'en'));
