# Scripts Directory

## Overview

This directory contains utility scripts for development workflow automation, testing, and repository maintenance.

## Files

### pre-commit

**Purpose**: Git pre-commit hook that runs tests and linters before allowing commits.

**What it does**:
1. Sets `AGENTIZE_HOME` to current worktree (critical for correct test execution)
2. Runs documentation linter (if available): `scripts/lint-documentation.sh`
3. Runs test suite: `tests/test-all.sh`
4. Blocks commit if tests or linting fail

**Installation**:

```bash
# From repository root
ln -s ../../scripts/pre-commit .git/hooks/pre-commit
```

Or for worktrees:
```bash
# From worktree root
ln -s ../../scripts/pre-commit .git/hooks/pre-commit
```

**Bypassing the hook**:

For milestone commits where documentation may be incomplete:
```bash
git commit --no-verify -m "milestone: intermediate implementation"
```

**Important**: The hook automatically sets `AGENTIZE_HOME` to prevent running tests from the wrong codebase when using git worktrees.

**Exit codes**:
- `0` - All checks passed, commit allowed
- `1` - Checks failed, commit blocked

## Installation Instructions

### Setting Up Pre-Commit Hook

1. **Navigate to repository root**:
   ```bash
   cd /path/to/Zircon-FloatPoint
   ```

2. **Create symbolic link**:
   ```bash
   ln -s ../../scripts/pre-commit .git/hooks/pre-commit
   ```

3. **Verify installation**:
   ```bash
   ls -la .git/hooks/pre-commit
   # Should show symlink to ../../scripts/pre-commit
   ```

4. **Test the hook**:
   ```bash
   # Make a trivial change
   echo "# test" >> README.md
   git add README.md
   git commit -m "test: verify pre-commit hook"
   # Should run tests before committing
   ```

### For Git Worktrees

When using git worktrees, each worktree needs the hook installed:

```bash
cd /path/to/worktree
ln -s ../../scripts/pre-commit .git/hooks/pre-commit
```

The hook handles `AGENTIZE_HOME` correctly for worktrees automatically.

## Script Development Guidelines

### Adding New Scripts

When adding new scripts to this directory:

1. **Use clear, descriptive names**: `lint-documentation.sh`, `run-benchmarks.sh`
2. **Make scripts executable**: `chmod +x scripts/new-script.sh`
3. **Add shebang line**: `#!/bin/bash` or `#!/usr/bin/env python3`
4. **Document in this README**: Add section describing purpose and usage
5. **Include usage information**: Add `--help` flag to display usage

### Script Template

```bash
#!/bin/bash
# Brief description of what this script does

set -e  # Exit on error

# Usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -h, --help    Display this help message"
    exit 1
}

# Main script logic
main() {
    # Implementation here
    echo "Script running..."
}

# Parse arguments
if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    usage
fi

main "$@"
```

## Future Scripts

Planned scripts for this directory:

### lint-documentation.sh
- Validate folder README.md files exist
- Check for source code interface documentation (`.md` companions)
- Verify test documentation
- Exit with error if documentation missing

### run-benchmarks.sh
- Execute hardware simulation benchmarks
- Measure cycle counts and resource usage
- Generate performance reports

### generate-verilog.sh
- Compile Chisel to Verilog
- Run synthesis checks
- Output to `generated/` directory

### setup-dev-env.sh
- Install Scala, sbt, Chisel dependencies
- Configure IDE integration
- Set up git hooks automatically

### clean-artifacts.sh
- Remove build artifacts (`target/`, `test_run_dir/`)
- Clean generated files
- Reset to clean repository state

## Related Documentation

- Pre-commit hook configuration: This README
- Git commit message standards: `docs/git-msg-tags.md`
- Testing guidelines: `src/test/scala/README.md`
- Documentation standards: Reference the review-standard skill

## Troubleshooting

### Pre-commit hook not running

**Symptom**: Commits succeed without running tests.

**Solutions**:
1. Check if hook is installed: `ls -la .git/hooks/pre-commit`
2. Verify hook is executable: `chmod +x .git/hooks/pre-commit`
3. Ensure symlink is correct: Should point to `../../scripts/pre-commit`

### Tests fail in pre-commit but pass manually

**Symptom**: `sbt test` passes, but pre-commit hook fails.

**Possible causes**:
1. `AGENTIZE_HOME` environment variable pointing to wrong worktree
2. Test dependencies not available in hook environment

**Solution**: The pre-commit hook sets `AGENTIZE_HOME` automatically. If still failing, check test dependencies.

### Hook too slow

**Symptom**: Pre-commit hook takes too long to run.

**Options**:
1. Use `--no-verify` for quick commits (not recommended for final commits)
2. Optimize test suite to run faster
3. Consider selective test execution for pre-commit

## Contributing

When modifying scripts:
1. Test changes thoroughly
2. Update this README with any changes to usage
3. Ensure scripts work in both regular clones and worktrees
4. Consider cross-platform compatibility (macOS, Linux)
