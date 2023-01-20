'use strict';

// TODO(joyeecheung): remove the flag when it is turned on by default in V8.
// Flags: --experimental-async-stack-tagging-api
// This tests the console works in the deserialized snapshot.

const common = require('../common');
common.skipIfInspectorDisabled();

const assert = require('assert');
const { spawnSync } = require('child_process');
const tmpdir = require('../common/tmpdir');
const fixtures = require('../common/fixtures');
const path = require('path');
const fs = require('fs');

tmpdir.refresh();
const blobPath = path.join(tmpdir.path, 'snapshot.blob');
const entry = fixtures.path('snapshot', 'console.js');

{
  const child = spawnSync(process.execPath, [
    '--experimental-async-stack-tagging-api',
    '--snapshot-blob',
    blobPath,
    '--build-snapshot',
    entry,
  ], {
    cwd: tmpdir.path
  });
  const stdout = child.stdout.toString();
  if (child.status !== 0) {
    console.log(stdout);
    console.log(child.stderr.toString());
    assert.strictEqual(child.status, 0);
  }
  assert.deepStrictEqual(Object.keys(console), JSON.parse(stdout));
  const stats = fs.statSync(path.join(tmpdir.path, 'snapshot.blob'));
  assert(stats.isFile());
}

{
  const child = spawnSync(process.execPath, [
    '--experimental-async-stack-tagging-api',
    '--snapshot-blob',
    blobPath,
  ], {
    cwd: tmpdir.path,
    env: {
      ...process.env,
    }
  });

  const stdout = child.stdout.toString();
  if (child.status !== 0) {
    console.log(stdout);
    console.log(child.stderr.toString());
    assert.strictEqual(child.status, 0);
  }
  assert.deepStrictEqual(Object.keys(console), JSON.parse(stdout));
  assert.strictEqual(child.status, 0);
}
