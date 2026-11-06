#!/usr/bin/env node
/* តេស្ត logic folder/file path ដែល ide.js ប្រើពិត (គ្មាន DOM)។
 * រត់: node static/ide/test_folder_paths.cjs
 * វាយកកូដពិតពី ide.js (region folder-paths) មក eval — មិនមែនសរសេរឡើងវិញទេ។
 */
'use strict';
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'ide.js');
const src = fs.readFileSync(file, 'utf8');

let fails = 0;
function eq(actual, expected, label) {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a === e) {
    console.log('  ok   ' + label);
  } else {
    fails++;
    console.log('  FAIL ' + label + '  got ' + a + '  want ' + e);
  }
}
function ok(cond, label) {
  if (cond) console.log('  ok   ' + label);
  else { fails++; console.log('  FAIL ' + label); }
}

console.log('== helpers មានក្នុង ide.js ==');
for (const name of ['validPath', 'normFolder', 'parentFolders', 'folderIssue', 'uniquePath', 'safeName', 'buildTree', 'detectMode']) {
  ok(new RegExp('function ' + name + '\\s*\\(').test(src), name + ' defined');
}

console.log('== region folder-paths ==');
const m = src.match(/\/\* #region folder-paths \*\/([\s\S]*?)\/\* #endregion \*\//);
if (!m) {
  console.error('  FAIL region folder-paths not found');
  process.exit(1);
}
const api = new Function(
  m[1] + '\nreturn {validPath: validPath, normFolder: normFolder,' +
  ' parentFolders: parentFolders, folderIssue: folderIssue, uniquePath: uniquePath,' +
  ' safeName: safeName, buildTree: buildTree, detectMode: detectMode};'
)();
const { validPath, normFolder, parentFolders, folderIssue, uniquePath, safeName,
  buildTree, detectMode } = api;

console.log('== normFolder ==');
eq(normFolder('assets'), 'assets', 'plain');
eq(normFolder('assets/'), 'assets', 'trailing slash');
eq(normFolder('/a//b/'), 'a/b', 'leading + double slash');
eq(normFolder('  src  '), 'src', 'spaces trimmed');
eq(normFolder('a\\b'), 'a/b', 'backslashes');
eq(normFolder(''), '', 'empty');

console.log('== validPath ==');
ok(validPath('assets'), 'folder without extension valid');
ok(validPath('app/src/main/MainActivity.kt'), 'nested file valid');
ok(validPath('.gitignore'), '.gitignore valid');
ok(validPath('.env'), '.env valid');
ok(!validPath('.'), 'bare dot rejected');
ok(!validPath('a/../b'), '.. rejected');
ok(!validPath('../x'), 'leading .. rejected');
ok(!validPath('/abs'), 'absolute rejected');
ok(!validPath('a b'), 'space rejected');
ok(!validPath('a/'), 'trailing slash rejected (normFolder before validate)');

console.log('== safeName (upload) ==');
eq(safeName('my photo.png'), 'my-photo.png', 'space replaced');
eq(safeName('assets/my photo.png'), 'assets/my-photo.png', 'folder kept');
eq(safeName('a  b.png'), 'a-b.png', 'double space collapsed');
eq(safeName('ឯកសារ/រូបភាព.png'), 'ឯកសារ/រូបភាព.png', 'khmer names preserved');
eq(safeName('a*b?.png'), 'a-b-.png', 'banned chars replaced');
ok(validPath(safeName('my photo.png')), 'sanitized name passes validPath');

console.log('== parentFolders ==');
eq(parentFolders('app/src/main/A.kt'), ['app', 'app/src', 'app/src/main'], 'nested');
eq(parentFolders('A.kt'), [], 'root file');
eq(parentFolders('assets/img/logo.png'), ['assets', 'assets/img'], 'image nested');

console.log('== folderIssue ==');
eq(folderIssue({}, 'assets'), '', 'free name');
eq(folderIssue({assets: 'x'}, 'assets'), 'file', 'file collision detected');
eq(folderIssue({'assets/a.png': 'x'}, 'assets'), '', 'folder may contain files');

console.log('== uniquePath ==');
eq(uniquePath({}, 'a.png'), 'a.png', 'no collision');
eq(uniquePath({'a.png': '1'}, 'a.png'), 'a (2).png', 'first duplicate');
eq(uniquePath({'a.png': '1', 'a (2).png': '1'}, 'a.png'), 'a (3).png', 'second duplicate');
eq(uniquePath({'assets/a.png': '1'}, 'assets/a.png'), 'assets/a (2).png', 'keeps folder');
eq(uniquePath({a: '1'}, 'a'), 'a (2)', 'no extension');

console.log('== folder set ដែលបង្កើតពេលបន្ថែម file ==');
const createdFolders = p => parentFolders(p).concat([p]);
eq(createdFolders('a/b/c'), ['a', 'a/b', 'a/b/c'], 'new folder includes parents');
eq(createdFolders('assets'), ['assets'], 'top-level folder');
const files = {};
const folders = [];
function addFile(p, content) {
  Object.keys(files).forEach(() => {});
  parentFolders(p).forEach(f => { if (folders.indexOf(f) < 0) folders.push(f); });
  files[p] = content;
}
addFile('assets/img/logo.png', 'data:image/png;base64,AA');
addFile('assets/css/style.css', 'body{}');
addFile('index.html', '<html>');
eq(folders.sort(), ['assets', 'assets/css', 'assets/img'], 'imported folders derived');
ok(Object.keys(files).every(p => p.indexOf('/') === -1 ||
  folders.indexOf(p.slice(0, p.lastIndexOf('/'))) >= 0),
  'every nested file has its folder registered');

console.log('== buildTree — Project Explorer បង្ហាញត្រឹមត្រូវ ==');
const tree = buildTree(['assets', 'assets/img'],
  {'index.html': '', 'assets/img/logo.png': '', 'App.py': ''});
eq(Object.keys(tree.dirs).sort(), ['assets'], 'root dirs only');
eq(tree.files.map(f => f.name), ['index.html', 'App.py'], 'root files នៅ root (មិនចូល folder)');
eq(tree.dirs.assets.dirs.img.files.map(f => f.name), ['logo.png'], 'file ជាន់ក្នុង');
eq(tree.dirs.assets.dirs.img.files[0].path, 'assets/img/logo.png', 'path ពេញត្រូវរក្សា');

const derived = buildTree([], {'App/new-file.js': ''});
eq(Object.keys(derived.dirs), ['App'], 'folder កើតពី file path (គ្មាន state.folders)');
eq(derived.dirs.App.files.map(f => f.name), ['new-file.js'], 'file ក្នុង folder ដែលកើតឡើង');

eq(Object.keys(buildTree(['empty'], {}).dirs), ['empty'], 'folder ទទេនៅតែបង្ហាញ');
eq(Object.keys(buildTree(['a'], {'A.py': ''}).dirs), ['a'], 'folder និង file ឈ្មោះផ្សេងគ្នានៅដាច់ពីគ្នា');

console.log('== detectMode — App type ពី file ==');
eq(detectMode({'lib/x.cpp': ''}), 'ndk', '.cpp → ndk');
eq(detectMode({'native.h': ''}), 'ndk', '.h → ndk');
eq(detectMode({'MainActivity.kt': ''}), 'kotlin', '.kt → kotlin');
eq(detectMode({'MainActivity.java': ''}), 'java', '.java → java');
eq(detectMode({'index.html': '', 'app.js': ''}), 'webview', 'web → webview');
eq(detectMode({}), 'webview', 'project ទទេ → webview');
eq(detectMode({'index.html': '', 'MainActivity.kt': ''}), 'kotlin', 'kotlin ឈ្នះ web');
eq(detectMode({'index.html': '', 'app.c': ''}), 'ndk', 'C ឈ្នះ web');

console.log('== folder → add file (wiring) ==');
ok(src.indexOf('function addRow(folder, depth)') > 0, 'addRow() សម្រាប់បន្ទាត់ ＋file');
ok(src.indexOf('wrap.appendChild(addRow(path, depth + 1))') > 0, 'ចុច folder → មាន ＋file ក្នុង folder');
ok(src.indexOf('data-act="file"') > 0 && src.indexOf('data-act="sub"') > 0 &&
  src.indexOf('data-act="upload"') > 0, 'menu folder មាន file / folder ថ្មី / upload');
ok(src.indexOf("if (state.selFolder && name.indexOf('/') < 0) name = state.selFolder + '/' + name;") > 0,
  'upload ដាក់ file ចូល folder ដែលជ្រើស');
ok(src.indexOf('function createFileIn(folder)') > 0 &&
  src.indexOf('createFileIn(state.selFolder)') > 0, 'toolbar ＋file → createFileIn(selFolder)');

console.log('== ZIP → APK wiring ==');
const ideHtml = fs.readFileSync(path.join(__dirname, '..', '..', 'templates', 'ide.html'), 'utf8');
ok(ideHtml.indexOf('id="zipApkBtn"') > 0, 'toolbar មានប៊ូតុង ZIP→APK');
ok(src.indexOf("$('zipApkBtn').onclick = zipApk") > 0, 'ប៊ូតុងភ្ជាប់ zipApk()');
ok(src.indexOf("fetch('/api/zip/build'") > 0, 'upload ទៅ /api/zip/build');
ok(src.indexOf("'/api/jobs/' + id + '/log'") > 0, 'ទាញ build log ពី job');
ok(src.indexOf("'/api/jobs/' + id") > 0, 'poll status របស់ job');
ok(src.indexOf("dl.href = '/dl/' + id") > 0, 'ប៊ូតុងទាញយក /dl/<id>');
ok(['upload', 'validate', 'build', 'download'].every(function (s) {
  return src.indexOf("['" + s + "',") > 0;
}), 'ជំហាន ៤ តាមលំដាប់ upload → validate → build → download');

console.log('');
if (fails) {
  console.log('FAILED ' + fails + ' checks');
  process.exit(1);
}
console.log('ALL FOLDER-PATH CHECKS PASSED');
