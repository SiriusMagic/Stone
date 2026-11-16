package com.zirakusa.stone.nativeapp.feature.player

import org.json.JSONTokener

/**
 * Finds the lyrics on the page open in the lyrics browser. Order: the user's selection, Google's
 * lyrics panel (one span per line, hidden "Más" part included), known lyrics sites, Google's
 * lyrics card, and finally the smallest block with many short lines.
 */
internal object LyricsWebExtractor {
    const val SCRIPT = """
(function() {
  function text(el) { return (el && el.innerText || '').trim(); }
  function count(t) { return t.split('\n').filter(function(l) { return l.trim(); }).length; }
  var selected = window.getSelection ? window.getSelection().toString().trim() : '';
  if (count(selected) > 1) return selected;
  var spans = document.querySelectorAll('[data-lyricid] span[jsname="YS01Ge"], span[jsname="YS01Ge"]');
  if (spans.length > 3) {
    var out = [], last = null;
    spans.forEach(function(s) {
      var parent = s.parentElement;
      if (last && parent !== last) out.push('');
      out.push((s.textContent || '').trim());
      last = parent;
    });
    return out.join('\n');
  }
  var sites = ['[data-lyrics-container="true"]', '.lyric-original', '.cnt-letra', '.lyrics__content__ok',
    '.mxm-lyrics__content', '#lyric-body-text', '#songLyricsDiv', '.songLyricsV14', '#lyrics', '.lyrics', '.letra'];
  for (var i = 0; i < sites.length; i++) {
    var found = Array.from(document.querySelectorAll(sites[i])).map(text).filter(function(t) { return t; });
    var joined = found.join('\n\n');
    if (count(joined) > 3) return joined;
  }
  var az = document.querySelector('.ringtone ~ div:not([class])');
  if (az && count(text(az)) > 3) return text(az);
  var cards = Array.from(document.querySelectorAll('[data-attrid], [aria-label]')).filter(function(el) {
    var key = ((el.getAttribute('data-attrid') || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase();
    return (key.indexOf('lyrics') >= 0 || key.indexOf('letra') >= 0) && count(text(el)) > 3;
  }).sort(function(a, b) { return text(b).length - text(a).length; });
  if (cards.length) return text(cards[0]);
  var blocks = [];
  document.querySelectorAll('div, p, pre, section, article').forEach(function(el) {
    var t = text(el), lines = count(t);
    if (lines < 8 || lines > 400 || t.length / lines > 70) return;
    if (el.querySelectorAll('a').length > lines / 3) return;
    blocks.push({ t: t, lines: lines });
  });
  if (!blocks.length) return '';
  var most = Math.max.apply(null, blocks.map(function(b) { return b.lines; }));
  return blocks.filter(function(b) { return b.lines >= most * 0.8; })
    .sort(function(a, b) { return a.t.length - b.t.length; })[0].t;
})();
"""

    /** evaluateJavascript returns the string JSON-encoded. */
    fun decode(raw: String?): String = runCatching { JSONTokener(raw ?: "").nextValue()?.toString().orEmpty() }.getOrDefault("")
        .takeUnless { it == "null" }.orEmpty()
}
