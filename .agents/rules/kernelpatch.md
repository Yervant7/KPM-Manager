---
trigger: always_on
---

# Development Environment

## Target Architecture

- ARM64 (aarch64)

### Restrictions

- Do not rely on complete local kernel tree headers from specific versions (KernelPatch is a patch designed to be compatible across multiple versions).

# Code Generation Guidelines

Style

- Pure C focused on the Linux kernel (Android 4.4 to 6.12).
