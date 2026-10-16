const { exec } = require('child_process');
const fs = require('fs');

function handler(req, res, db) {
  const result = eval(req.query.expression);
  exec('ls ' + req.query.dir, () => {});
  db.query("SELECT * FROM users WHERE id = " + req.params.id);
  document.getElementById('out').innerHTML = req.body.html;
  const token = Math.random().toString(36);
  fs.readFile(req.params.file, () => {});
  return { result, token };
}
module.exports = handler;
