# Contributing

- Test on the mock or the off-car tests first (`tools/test-all.sh`), and say which cars a change was tested on.
- Anything that writes to `/system` keeps a stock backup and an uninstall that restores the exact stock bytes.
- The repo holds no manufacturer apps or libraries: only hooks, hash-only recipes, delta patch sets, checksums and our own code.
- Never collect or publish per-car secrets (VIN, serials, keys, account tokens, `/data` contents).
- Commit messages describe the change in plain words.

By contributing you agree your work is licensed under the project's AGPL-3.0.
