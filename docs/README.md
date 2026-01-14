# Documentation

## Overview

This directory contains project documentation including design documents, implementation plans, and development guidelines.

## Files

### farpath-test-plan.md

**Purpose**: Test strategy and implementation plan for FAddFarPath module.

**Contents**:
- Problem statement for far-path floating-point addition
- Test coverage strategy
- Test case descriptions
- Debug process and validation criteria
- Known limitations and constraints

**Audience**: Developers implementing or debugging the FAddFarPath module.

### git-msg-tags.md

**Purpose**: Standardized tags for git commit messages.

**Contents**:
- Tag definitions (feat, bugfix, test, docs, refactor, etc.)
- Usage guidelines for each tag
- Special tags for agent-related changes

**Usage**: Reference when writing commit messages to ensure consistency.

**Audience**: All contributors to the repository.

## Documentation Categories

### Design Documents

Documents describing architectural decisions and module designs:
- Currently: `farpath-test-plan.md` (includes design context)
- Future: Design docs for other floating-point operations

### Process Documents

Documents describing development processes and conventions:
- `git-msg-tags.md` - Commit message standards

### Implementation Plans

Documents outlining implementation strategies for features:
- `farpath-test-plan.md` - Test-driven implementation approach for FAddFarPath

## Creating New Documentation

### When to Add Documentation

Add documentation for:
1. **New subsystems or major features** - Design documents explaining architecture
2. **Complex algorithms** - Implementation notes with mathematical background
3. **Development processes** - Guidelines for workflows, testing, review
4. **API or interface changes** - Migration guides or compatibility notes

### Documentation Standards

- Use Markdown format (`.md`)
- Include clear headings and table of contents for long documents
- Link to related source code files
- Update this README when adding new documents
- Focus on current design rationale, not historical comparisons

### File Naming Conventions

- Use lowercase with hyphens: `floating-point-design.md`
- Be descriptive: `farpath-test-plan.md` not `test-plan.md`
- Group related docs with common prefixes: `farpath-*.md`, `git-*.md`

## Integration with Source Code

Source code files should have companion `.md` files documenting interfaces:
- `src/main/scala/FAdd.scala` → `src/main/scala/FAdd.md`

This `docs/` directory contains higher-level design and process documentation.

## Future Documentation

Planned documentation additions:
- `floating-point-architecture.md` - Overall FPU design
- `ieee754-implementation.md` - IEEE 754 compliance notes
- `performance-optimization.md` - Hardware optimization strategies
- `testing-guidelines.md` - Comprehensive testing standards
- `contributing.md` - Contribution guidelines for external developers
