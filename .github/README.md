# GitHub Configuration

This directory contains GitHub Actions workflows and configuration files for the payment-system-speckit project.

## Directory Structure

```
.github/
├── workflows/
│   ├── build-and-test.yml           # Main build and unit test pipeline
│   ├── integration-tests.yml        # Integration and load testing
│   ├── code-coverage.yml            # Code coverage reporting
│   ├── security-scan.yml            # Dependency vulnerability scanning
│   └── release.yml                  # Automated release builds
├── README.md                         # This file
├── CI_CD_SETUP.md                   # Detailed CI/CD documentation
└── WORKFLOW_QUICK_REFERENCE.md      # Quick reference for developers
```

## Quick Links

- **CI/CD Setup**: [Full documentation](CI_CD_SETUP.md)
- **Quick Reference**: [Developer quick guide](WORKFLOW_QUICK_REFERENCE.md)
- **GitHub Actions**: [View workflows](../actions)
- **Status Badges**: Add these to your README.md

## Status Badges

Add these badges to your main README.md:

```markdown
[![Build & Test](https://github.com/[owner]/payment-system-speckit/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/[owner]/payment-system-speckit/actions/workflows/build-and-test.yml)
[![Integration Tests](https://github.com/[owner]/payment-system-speckit/actions/workflows/integration-tests.yml/badge.svg?branch=main)](https://github.com/[owner]/payment-system-speckit/actions/workflows/integration-tests.yml)
[![codecov](https://codecov.io/gh/[owner]/payment-system-speckit/branch/main/graph/badge.svg)](https://codecov.io/gh/[owner]/payment-system-speckit)
```

## Workflows

### 1. Build & Test

- **File**: `build-and-test.yml`
- **Triggers**: Push to main/develop/001-\*, PR
- **Duration**: ~20 seconds
- **Outputs**: Test reports, coverage metrics

### 2. Integration Tests

- **File**: `integration-tests.yml`
- **Triggers**: Push, PR
- **Duration**: ~5 minutes (45 minutes for load tests on main/develop)
- **Outputs**: Integration test results, performance metrics

### 3. Code Coverage

- **File**: `code-coverage.yml`
- **Triggers**: Push to main/develop, PR
- **Duration**: ~3 minutes
- **Outputs**: JaCoCo reports, Codecov upload

### 4. Security Scan

- **File**: `security-scan.yml`
- **Triggers**: Push, PR, weekly at midnight UTC
- **Duration**: ~5 minutes
- **Outputs**: Vulnerability reports, SBOM

### 5. Release

- **File**: `release.yml`
- **Triggers**: Git tags matching `v*`
- **Duration**: ~2 minutes
- **Outputs**: GitHub Release with artifacts

## Recommended Setup

### 1. Enable Status Checks

Go to Settings → Branches → main/develop → Require status checks:

- ✅ Build & Test
- ✅ Integration Tests

### 2. Configure Codecov (Optional)

1. Visit [codecov.io](https://codecov.io)
2. Add your repository
3. Copy CODECOV_TOKEN to repository secrets

### 3. Add Branch Protection Rules

For `main` and `develop` branches:

### Isolated Workflows

#### Multi-Java Version Testing (Isolated)

- **File**: `multi-java-test.yml` (on `multi-java-testing` branch only)
- **Triggers**: Push/PR to `multi-java-testing` branch, daily schedule
- **Purpose**: Test compatibility with Java 21 and 25 (isolated due to test failures)
- **Duration**: ~4 minutes
- **Outputs**: Test results for Java 21 & 25

## Recommended Setup

### 1. Enable Status Checks

Go to Settings → Branches → main/develop → Require status checks:

- ✅ Build & Test
- ✅ Multi-Java Version Testing
- ✅ Integration Tests

### 2. Configure Codecov (Optional)

1. Visit [codecov.io](https://codecov.io)
2. Add your repository
3. Copy CODECOV_TOKEN to repository secrets

### 3. Add Branch Protection Rules

For `main` and `develop` branches:

- Require status checks to pass
- Require PR reviews
- Require branches to be up to date
- Restrict who can push to matching branches

### 4. Add Status Badges

Update main README.md with workflow badges (see Status Badges above)

## GitHub Actions Secrets

### Required Secrets

None (uses auto-generated GITHUB_TOKEN)

### Optional Secrets

- **CODECOV_TOKEN**: For Codecov integration
- **SLACK_WEBHOOK**: For Slack notifications (future enhancement)

To add secrets:

1. Go to Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add name and value

## Artifact Management

| Artifact            | Retention | Size    | Usage                  |
| ------------------- | --------- | ------- | ---------------------- |
| Test Reports        | 30 days   | ~5 MB   | Debugging failed tests |
| Coverage Reports    | 30 days   | ~20 MB  | Coverage tracking      |
| Integration Results | 7 days    | ~10 MB  | Performance metrics    |
| SBOM                | 90 days   | ~1 MB   | Compliance             |
| Release Artifacts   | 90 days   | ~100 MB | Distribution           |

## Local Development

To test workflows locally before pushing:

### Using act (GitHub Actions locally)

```bash
# Install act
brew install act

# List available workflows
act -l

# Run specific workflow
act push -j build
```

### Manual Testing

```bash
# Test build and unit tests
mvn clean test

# Test with coverage
mvn clean verify jacoco:report

# Test integration
mvn clean verify -Dgroups=integration
```

## Troubleshooting

### Workflows Not Running?

- Check `.github/workflows/` directory exists
- Verify workflow YAML syntax (use act or linter)
- Check GitHub Actions is enabled in Settings

### Tests Failing in CI but Passing Locally?

- Check Java version (21 vs 25)
- Check environment variables
- Review workflow logs for full error messages

### Slow Workflows?

- Check GitHub Actions runner availability
- Review Maven cache hit rate
- Consider splitting jobs

## Further Reading

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)
- [Starter Workflows](https://github.com/actions/starter-workflows)
- [Maven Plugin Documentation](https://maven.apache.org/guides/)

---

**Last Updated**: May 8, 2026
**Maintainer**: Payment Systems Team
