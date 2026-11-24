const { app, BrowserWindow, ipcMain, shell, Menu, dialog } = require('electron');
const path = require('path'), fs = require('fs');
const DATA = () => path.join(app.getPath('userData'), 'data.json');
const BK = () => path.join(app.getPath('userData'), 'backups');

function dailyBackup() {
  try {
    if (!fs.existsSync(DATA())) return;
    fs.mkdirSync(BK(), { recursive: true });
    const f = path.join(BK(), 'data-' + new Date().toISOString().slice(0, 10) + '.json');
    if (!fs.existsSync(f)) fs.copyFileSync(DATA(), f);
    const all = fs.readdirSync(BK()).filter(n => n.startsWith('data-')).sort();
    while (all.length > 60) fs.unlinkSync(path.join(BK(), all.shift()));
  } catch (e) { console.error(e); }
}
ipcMain.on('load', e => {
  try { e.returnValue = fs.existsSync(DATA()) ? fs.readFileSync(DATA(), 'utf8') : null; }
  catch (err) { e.returnValue = null; }
});
ipcMain.on('save', (e, str) => {
  try {
    const tmp = DATA() + '.tmp';
    fs.writeFileSync(tmp, str, 'utf8'); fs.renameSync(tmp, DATA());
    dailyBackup(); e.returnValue = true;
  } catch (err) { e.returnValue = false; dialog.showErrorBox('خطأ في الحفظ', String(err)); }
});
ipcMain.on('openBackups', () => { fs.mkdirSync(BK(), { recursive: true }); shell.openPath(BK()); });
ipcMain.on('version', e => { e.returnValue = app.getVersion(); });

function createWindow() {
  const win = new BrowserWindow({
    width: 1360, height: 860, minWidth: 1000, minHeight: 640, show: false,
    title: 'مرافق برو', icon: path.join(__dirname, 'app', 'icon.png'), backgroundColor: '#f4f1ea',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: false }
  });
  win.loadFile(path.join(__dirname, 'app', 'index.html'));
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); shell.openExternal(url); } });
}
Menu.setApplicationMenu(null);
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { const w = BrowserWindow.getAllWindows()[0]; if (w) { if (w.isMinimized()) w.restore(); w.focus(); } });
  app.whenReady().then(() => { dailyBackup(); createWindow(); });
  app.on('window-all-closed', () => app.quit());
}
