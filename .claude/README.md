# Claude Code Configuration

## Overview

This directory contains configuration files and instructions for Claude Code (Anthropic's CLI tool) when working with the Zircon-FloatPoint project.

## Files

### CLAUDE.md

**Purpose**: Project-specific instructions that Claude Code reads to understand development conventions and guidelines.

**Contents**:
- Documentation lookup instructions (`docs/llms.txt`)
- Naming conventions for commands, skills, and agents
- Guidelines for concise descriptions
- System prompt considerations for commands/skills/agents

**When to modify**:
- Adding new project-wide coding standards
- Defining repository-specific workflows
- Documenting AI agent interaction patterns
- Specifying file availability assumptions

## Usage

Claude Code automatically reads `CLAUDE.md` when working in this repository. The instructions help ensure:
- Consistent code style and documentation
- Proper naming of custom commands and skills
- Efficient use of Claude Code features
- Avoidance of redundant or verbose descriptions

## Best Practices

### When Adding Instructions

1. **Be concise**: Claude Code processes these instructions frequently
2. **Be specific**: Provide clear, actionable guidelines
3. **Avoid redundancy**: Don't duplicate information available elsewhere
4. **Think context**: Distinguish between user-facing notes and AI internal understanding

### Example Good Instructions

```markdown
- Use IEEE 754 terminology consistently in floating-point code
- Document all hardware modules in companion .md files
- Test files must include inline comments for test case descriptions
```

### Example Poor Instructions

```markdown
- Please make sure to always write good code with proper documentation
- Be careful when writing code to avoid bugs
- Think about the user experience when designing modules
```

## Integration with Development Workflow

Claude Code uses these instructions when:
- Generating new code or documentation
- Reviewing existing code
- Creating or modifying commands/skills/agents
- Answering questions about the project

## Related Documentation

- Main project documentation: `docs/`
- LLM-specific docs: `docs/llms.txt` (if exists)
- Git commit standards: `docs/git-msg-tags.md`

## Future Additions

Potential future files in this directory:
- Custom skills definitions
- Repository-specific commands
- Agent configurations
- Workflow automation scripts
