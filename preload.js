const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('native', {
  load: () => ipcRenderer.sendSync('load'),
  save: s => ipcRenderer.sendSync('save', s),
  openBackups: () => ipcRenderer.send('openBackups'),
  version: ipcRenderer.sendSync('version')
});
