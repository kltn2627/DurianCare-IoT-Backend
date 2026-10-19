const { test } = require("node:test");
const assert = require("node:assert/strict");
const { fingerprint } = require("./database-fingerprint.js");

test("document order and field insertion order do not change the fingerprint", () => {
  assert.deepEqual(fingerprint([{ a: 1, nested: { b: 2, c: 3 } }, { a: 2 }]),
    fingerprint([{ a: 2 }, { nested: { c: 3, b: 2 }, a: 1 }]));
});
test("changed values, array order and duplicate documents are detected", () => {
  const original = fingerprint([{ values: [1, 2] }]);
  assert.notEqual(original.sha256, fingerprint([{ values: [2, 1] }]).sha256);
  assert.notEqual(original.sha256, fingerprint([{ values: [1, 3] }]).sha256);
  assert.notEqual(original.sha256, fingerprint([{ values: [1, 2] }, { values: [1, 2] }]).sha256);
});
test("only count and digest leave the fingerprint function", () => {
  assert.deepEqual(Object.keys(fingerprint([{ privateField: "fixture-only" }])), ["count", "sha256"]);
});
