---
name: migraphe-errorprone
description: Fix ErrorProne/NullAway build warnings in the Migraphe project. Use when the build emits compiler warnings, or before a commit to confirm zero warnings.
---

# ErrorProne Warnings — MANDATORY

All ErrorProne warnings must be fixed by modifying source code. **Never use `@SuppressWarnings`** without explicit user permission.

| Warning | Fix |
|---------|-----|
| `MissingOverride` | Add `@Override` annotation |
| `UnusedVariable` / `ModifiedButNotUsed` | Remove unused variable and imports |
| `StringSplitter` | `split(regex)` → `split(regex, -1)` |
| `DefaultCharset` | Specify `StandardCharsets.UTF_8` explicitly |
| `StringCaseLocaleUsage` | `toUpperCase()` → `toUpperCase(Locale.ROOT)` |
| Other warnings | Fix root cause per warning message |

Verify with:

```bash
./gradlew clean build --warning-mode all 2>&1 | grep 警告
```
