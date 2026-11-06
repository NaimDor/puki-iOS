const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch();
  for (const [name, W, H] of [['puki_header_3840x1646', 3840, 1646], ['puki_search_3840x2560', 3840, 2560]]) {
    const p = await b.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
    await p.setContent(`<html><body style="margin:0;background:#5EC4F2">${fs.readFileSync(__dirname + '/' + name + '.svg', 'utf8')}</body></html>`);
    await p.screenshot({ path: __dirname + '/' + name + '_rgba.png', clip: { x: 0, y: 0, width: W, height: H } });
  }
  await b.close();
})();
