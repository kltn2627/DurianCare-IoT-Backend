/* Read-only mongosh script; output contains only counts and hashes, never documents. */
const crypto = require("crypto");

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  }
  return value;
}

function fingerprint(documents) {
  const rows = documents.map(doc => JSON.stringify(canonical(doc))).sort();
  return { count: rows.length, sha256: crypto.createHash("sha256").update(JSON.stringify(rows)).digest("hex") };
}

if (typeof module !== "undefined") module.exports = { canonical, fingerprint };
if (typeof db !== "undefined") {
  const result = { algorithm: "canonical-ejson-v1", databases: {} };
  for (const name of ["duriancare_farm", "duriancare_cultivation"]) {
    const source = db.getSiblingDB(name);
    const collections = {};
    for (const collection of source.getCollectionNames().filter(n => !n.startsWith("system.")).sort()) {
      const documents = source.getCollection(collection).find().toArray()
        .map(doc => JSON.parse(EJSON.stringify(doc, { relaxed: false })));
      collections[collection] = fingerprint(documents);
    }
    result.databases[name] = collections;
  }
  print(JSON.stringify(result));
}
