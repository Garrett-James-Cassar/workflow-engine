package dev.workflow.app.api

/**
 * A standalone page that draws a Mermaid diagram. Mermaid renders in the browser (it is a JavaScript
 * library), so this is how the export endpoint returns a picture; the page also offers SVG and PNG
 * downloads of what it drew.
 */
object DiagramPage {
    const val MERMAID_JS = "https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"

    fun render(title: String, mermaid: String): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(title)}</title>
<style>
  :root { color-scheme: dark; --background: 150 8% 5%; --surface: 165 9% 9%; --border-strong: 150 6% 40%;
          --foreground: 47 17% 90%; --muted-foreground: 140 3% 56%; --paper: 40 20% 97%; --ring: 25 95% 53%; }
  body { margin: 0; padding: 24px; background: hsl(var(--background)); color: hsl(var(--foreground));
         font: 13px/20px "Hanken Grotesk", ui-sans-serif, system-ui, sans-serif; }
  h1 { font: 500 28px/32px Fraunces, ui-serif, Georgia, serif; margin: 0 0 4px; }
  p { color: hsl(var(--muted-foreground)); margin: 0 0 16px; }
  .bar { display: flex; gap: 8px; margin-bottom: 16px; }
  button { height: 36px; padding: 0 14px; border-radius: 7px; border: 1px solid hsl(var(--border-strong));
           background: transparent; color: hsl(var(--foreground)); font: 600 13px inherit; cursor: pointer; }
  button:focus-visible { outline: 2px solid hsl(var(--ring)); outline-offset: 2px; }
  .paper { background: hsl(var(--paper)); border-radius: 10px; padding: 16px; overflow: auto; }
  pre.mermaid { margin: 0; }
</style>
</head>
<body>
<h1>${escape(title)}</h1>
<p>Generated from the Kotlin DSL.</p>
<div class="bar">
  <button id="svg">Download SVG</button>
  <button id="png">Download PNG</button>
</div>
<div class="paper"><pre class="mermaid">${escape(mermaid)}</pre></div>
<script src="$MERMAID_JS"></script>
<script>
  const name = ${jsString(title)};
  mermaid.initialize({ startOnLoad: false, theme: 'default' });
  mermaid.run().then(() => {
    const svg = () => document.querySelector('.paper svg');
    const save = (href, ext) => { const a = document.createElement('a'); a.href = href; a.download = name + '.' + ext; a.click(); };
    const svgText = () => new XMLSerializer().serializeToString(svg());
    document.getElementById('svg').onclick = () =>
      save(URL.createObjectURL(new Blob([svgText()], { type: 'image/svg+xml' })), 'svg');
    document.getElementById('png').onclick = () => {
      const box = svg().getBoundingClientRect(), scale = 2, img = new Image();
      img.onload = () => {
        const c = document.createElement('canvas'); c.width = box.width * scale; c.height = box.height * scale;
        const g = c.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height);
        g.drawImage(img, 0, 0, c.width, c.height); save(c.toDataURL('image/png'), 'png');
      };
      img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgText());
    };
  });
</script>
</body>
</html>
""".trimStart()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun jsString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\""
}
