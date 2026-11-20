// Captures the CHPP file docs as Markdown, one file per CHPP file, in the format of
// docs/raw/chpp/*.md. The docs are login-only and built in the browser, so this runs in a
// logged-in Chrome tab: paste it into the DevTools console on any page under
// https://wwwNN.hattrick.org/Community/CHPP/NewDocs/, then
//
//   await chppCaptureAll()                             // every file on the index + datatypes
//   await chppCaptureAll(['training', 'playerstats'])  // just these
//
// It resolves to {files, chars, failed, text}; from a browser tool, return only the counts.
//
// Each file page is loaded in a hidden same-origin iframe. The page builds a `file` object
// (versions, changelog, inputs, output tree), and the Markdown comes from that object rather
// than from the rendered HTML; Input and References are the rendered text. DataTypes.aspx comes
// out as datatypes.md (its rendered text).
//
// The result is one text with a "===== <name>.md" line before each file. It is offered as the
// download chpp-docs.txt, and a button "chpp-docs.txt speichern" appears top left: Chrome lets a
// page start one download on its own and blocks the next ones until the user clicks. Then
//
//   python3 utils/chpp-docs/split.py ~/Downloads/chpp-docs.txt docs/raw/chpp

/**
 * One node of the docs page's model: an input, an action, a container or an element.
 * @typedef {Object} ChppDocNode
 * @property {string} id
 * @property {string} [desc]
 * @property {string} [text]
 * @property {string} [objectname]
 * @property {ChppDocNode} [container]
 * @property {ChppDocNode[]} [elements]
 * @property {ChppDocNode[]} [attr]
 * @property {ChppDocNode} [type]
 * @property {ChppDocNode|string} [ref]
 * @property {number} [firstVersion]
 * @property {number} [lastVersion]
 * @property {boolean} [onlySupporters]
 */

/**
 * The `file` object a CHPP docs page builds.
 * @typedef {Object} ChppDocFile
 * @property {{version: number, date: Date, isDefault: boolean, changes: Array}[]} fileVersions
 * @property {Object<number, ChppDocNode[]>} changelogInputAdd
 * @property {Object<number, ChppDocNode[]>} changelogInputRemove
 * @property {Object<number, ChppDocNode[]>} changelogInputActionAdd
 * @property {Object<number, ChppDocNode[]>} changelogInputActionRemove
 * @property {Object<number, ChppDocNode[]>} changelogOutputAdd
 * @property {Object<number, ChppDocNode[]>} changelogOutputRemove
 * @property {?Object} actionType
 * @property {Object<string, ChppDocNode|ChppDocNode[]>} container
 */

/** Plain text of a description that may carry HTML, whitespace collapsed. */
const chppClean = s => {
  const div = document.createElement('div');
  div.innerHTML = String(s || '');
  return div.textContent.split(/\s/).filter(Boolean).join(' ');
};

/** Rendered text of an element, without empty lines. */
const chppText = el => el.innerText.split('\n').filter(l => l.trim()).join('\n').trim();

/**
 * The docs page builds its dates as new Date(y, m, d) with a 1-based month, so getMonth()
 * already is the calendar month - and a December date has rolled over into January.
 */
const chppDate = (win, d) => {
  if (!(d instanceof win.Date) || Number.isNaN(d.getTime()) || d.getFullYear() <= 1900) {
    return '';
  }
  const december = d.getMonth() === 0;
  const y = december ? d.getFullYear() - 1 : d.getFullYear();
  const m = december ? 12 : d.getMonth();
  return y + '-' + String(m).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
};

const chppIsContainer = o => o.objectname === 'chppDocContainer';

const chppPath = o => {
  const p = [];
  for (let c = o; c; c = c.container) {
    p.unshift(c.id);
  }
  return p.join(' → ') + (chppIsContainer(o) ? ' (Container)' : ' (Element)');
};

/** The "## Changelog" section, newest version first. */
const chppChangelog = (win, F) => {
  const lines = ['## Changelog'];
  const versions = [...F.fileVersions].sort((a, b) => b.version - a.version);
  for (const v of versions) {
    const d = chppDate(win, v.date);
    lines.push('### Version ' + Math.floor(v.version / 10) + '.' + (v.version % 10)
      + (d ? ' (' + d + ')' : '') + (v.isDefault ? ' {default}' : ''));
    const items = (v.changes || []).map(c => chppClean(typeof c === 'string' ? c : (c.desc || c.text || String(c))));
    const add = (byVersion, fn) => items.push(...(byVersion?.[v.version] || []).map(fn));
    add(F.changelogInputRemove, x => 'Removed Input: ' + x.id);
    add(F.changelogInputAdd, x => 'Added Input: ' + x.id);
    add(F.changelogInputActionRemove, x => "Removed Action for 'actionType': " + x.id);
    add(F.changelogInputActionAdd, x => "Added Action for 'actionType': " + x.id + (x.desc ? ' (' + chppClean(x.desc) + ')' : ''));
    add(F.changelogOutputRemove, x => 'Removed Output: ' + chppPath(x));
    add(F.changelogOutputAdd, x => 'Added Output: ' + chppPath(x));
    const unique = [...new Set(items)];
    if (!unique.length && v === versions.at(-1)) {
      unique.push('First version');
    }
    lines.push(...unique.map(i => '- ' + i), '');
  }
  return { lines, latest: versions[0].version };
};

/** The "## Output" sections of the latest version, one per actionType. */
const chppOutput = (F, latest) => {
  const live = o => (o.firstVersion === undefined || o.firstVersion <= latest)
    && (o.lastVersion === undefined || o.lastVersion >= latest);
  const typeName = t => (t ? (t.desc || t.id) : '');
  const describe = o => {
    const attrs = (o.attr || []).map(x => "Attribute: '" + x.id + "' : " + typeName(x.type) + ' ' + chppClean(x.desc));
    const ref = o.ref ? '# See table ' + (o.ref.id || o.ref) : '';
    // the page's own wording, rendered before the description
    const supporters = o.onlySupporters ? 'This container and its elements is only shown if the user has supporter' : '';
    return chppClean([...attrs, supporters, chppClean(o.desc), ref].join(' '));
  };
  const tree = (o, depth, acc) => {
    const indent = '  '.repeat(depth);
    const d = describe(o);
    if (chppIsContainer(o)) {
      acc.push(indent + '- **' + o.id + '**' + (d ? ' — ' + d : ''));
      for (const c of (o.elements || []).filter(live)) {
        tree(c, depth + 1, acc);
      }
    } else {
      acc.push(indent + '- ' + o.id.trim() + ' : ' + typeName(o.type) + (d ? ' — ' + d : ''));
    }
    return acc;
  };
  const asList = r => {
    if (Array.isArray(r)) {
      return r;
    }
    return r ? [r] : [];
  };
  const lines = [];
  const rendered = [];
  for (const a of Object.keys(F.container)) {
    const roots = asList(F.container[a]).filter(o => !o.container && live(o));
    const body = roots.flatMap(x => tree(x, 0, [])).join('\n');
    if (body) {
      const same = rendered.find(x => x.body === body);
      lines.push(F.actionType ? '## Output (actionType=' + a + ')' : '## Output',
        same ? 'Same as actionType=' + same.a + '.' : body, '');
      rendered.push({ a, body });
    }
  }
  return lines;
};

/** One CHPP file page (its window and document) as Markdown. */
window.chppCapture = (win, doc, name) => {
  /** @type {ChppDocFile} */
  const F = win.file;
  // the file's description is the line right above the "Changelog (show)" toggle
  const bodyText = doc.body.innerText.split('\n').map(s => s.trim()).filter(Boolean);
  const ci = bodyText.findIndex(l => l.startsWith('Changelog ('));
  const changelog = chppChangelog(win, F);
  const lines = ['# ' + name, '', ci > 0 ? bodyText[ci - 1] : '', '',
    ...changelog.lines,
    '## Input', chppText(doc.getElementById('input')), '',
    ...chppOutput(F, changelog.latest)];
  // rendered reference tables; the box's own first line is its "References" heading
  const refEl = doc.getElementById('referenceBox');
  const refLines = refEl ? chppText(refEl).split('\n').filter(Boolean) : [];
  if (refLines[0] === 'References') {
    refLines.shift();
  }
  if (refLines.length) {
    lines.push('## References', ...refLines, '');
  }
  return lines.join('\n').trimEnd() + '\n';
};

window.chppLoad = url => new Promise((resolve, reject) => {
  const f = document.createElement('iframe');
  f.style.cssText = 'position:absolute;left:-9999px;width:1200px;height:800px';
  f.onload = () => setTimeout(() => resolve(f), 300);
  f.onerror = reject;
  f.src = url;
  document.body.appendChild(f);
});

/** One part of the download: "===== <name>.md" and the file's Markdown. */
const chppPart = async (base, n) => {
  if (n === 'datatypes') {
    const f = await chppLoad(base + 'DataTypes.aspx');
    const t = f.contentDocument.body.innerText;
    const s = t.indexOf('\nBoolean\n');
    const e = t.lastIndexOf('\nCommunity\n');
    f.remove();
    return '===== datatypes.md\n# CHPP data types\n\n' + t.slice(s + 1, e > s ? e : undefined).trim() + '\n';
  }
  const f = await chppLoad(base + 'File.aspx?name=' + n);
  const md = chppCapture(f.contentWindow, f.contentDocument, n);
  f.remove();
  return '===== ' + n + '.md\n' + md;
};

const chppSave = text => {
  const save = () => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
    a.download = 'chpp-docs.txt';
    a.click();
  };
  const b = document.createElement('button');
  b.textContent = 'chpp-docs.txt speichern';
  b.style.cssText = 'position:fixed;top:20px;left:20px;z-index:99999;font-size:20px;padding:16px';
  b.onclick = save;
  document.body.appendChild(b);
  save();
};

window.chppCaptureAll = async (names, download = true) => {
  const base = '/Community/CHPP/NewDocs/';
  let list = names;
  if (!list) {
    const idx = await chppLoad(base);
    list = [...new Set([...idx.contentDocument.querySelectorAll('a[href*="File.aspx?name="]')]
      .map(a => new URL(a.href).searchParams.get('name')))];
    idx.remove();
    list.push('datatypes');
  }
  const parts = [];
  const failed = [];
  // one page at a time, so the docs server sees a reader rather than a burst
  const next = async i => {
    if (i >= list.length) {
      return;
    }
    try {
      parts.push(await chppPart(base, list[i]));
    } catch (e) {
      failed.push(list[i] + ': ' + e.message);
    }
    await next(i + 1);
  };
  await next(0);
  const text = parts.join('');
  if (download) {
    chppSave(text);
  }
  return { files: parts.length, chars: text.length, failed, text };
};
