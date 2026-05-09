# CI/CD Pipeline Setup

This document describes the GitHub Actions workflows configured for the payment-system project, including payment-bridge and mock-payment-api modules.

## Overview

The CI/CD pipeline includes 6 automated workflows that handle building, testing, code coverage, security scanning, and releasing:

1. **Build & Test** - Core build and unit test pipeline
2. **Multi-Java Version Testing** - Tests against Java 21 and Java 25
3. **Integration Tests** - Integration and load testing
4. **Code Coverage** - Coverage reporting and metrics
5. **Security Scan** - Dependency vulnerability and SCA
6. **Release** - Automated release artifact publishing

## Workflows

### 1. Build & Test (`build-and-test.yml`)

**Triggers:** Push to main/develop/001-* branches, Pull requests

**What it does:**
- Checks out code
- Sets up Java 21
- Runs full Maven build and test suite
- Generates test reports
- Publishes test results
- Uploads code coverage to Codecov

**Configuration:**
- JVM Memory: 2GB
- Test suite: All unit tests via `mvn clean test`
- Coverage: JaCoCo reports uploaded to Codecov

**Artifacts:**
- Test reports (30 days retention)
- Coverage reports

### 2. Multi-Java Version Testing (`multi-java-test.yml`)

**Triggers:** Push to main/develop/001-* branches, Pull requests, Daily at 2 AM UTC

**What it does:**
- Tests against Java 21 LTS and Java 25
- Verifies bytecode manipulation compatibility
- Confirms Byte Buddy 1.18.8 is in place (Java 25 fix)
- Confirms JaCoCo 0.8.14 is in place (Java 25 fix)
- Posts results to PRs automatically

**Key Features:**
- Matrix strategy for multiple Java versions
- Automatic PR comments with test status
- Validates critical dependency versions

**Artifacts:**
- Test results per Java version (15 days retention)

### 3. Integration Tests (`integration-tests.yml`)

**Triggers:** Push to main/develop/001-* branches, Pull requests

**What it does:**
- Runs Payment Bridge integration tests
- Runs Mock Payment API integration tests
- Runs load and performance tests (on main/develop pushes)
- Timeout: 45 minutes for load tests

**Jobs:**
1. **payment-bridge-integration**: Full end-to-end tests with RabbitMQ
2. **mock-payment-api-integration**: API mock and failure scenario tests
3. **load-tests**: Performance and stress tests (main/develop only)

**Test Groups:**
- `@IntegrationTest`: Multi-component tests
- `*LoadTest`: Performance benchmarks
- `*FailureDistributionStatTest`: Reliability validation

**Artifacts:**
- Integration test results (7 days retention)

### 4. Code Coverage (`code-coverage.yml`)

**Triggers:** Push to main/develop, Pull requests

**What it does:**
- Runs full build with JaCoCo coverage enabled
- Generates JaCoCo HTML reports for both modules
- Uploads coverage to Codecov
- Creates PR comments with coverage links
- Publishes coverage artifacts

**Coverage Tools:**
- JaCoCo 0.8.14 (Java 25 compatible)
- Codecov integration
- HTML reports with line-by-line coverage

**Artifacts:**
- JaCoCo HTML reports (30 days retention)

### 5. Security Scan (`security-scan.yml`)

**Triggers:** Push to main/develop, Pull requests, Weekly at midnight UTC

**What it does:**
- **Dependency Check**: OWASP vulnerability scanning
- **Maven Security**: Dependency tree audit
- **SCA**: Software Composition Analysis with SBOM generation

**Tools Used:**
- OWASP Dependency-Check
- CycloneDX Maven plugin
- Maven dependency tree analysis

**Artifacts:**
- Dependency check reports (30 days)
- SBOM files (90 days)

### 6. Release (`release.yml`)

**Triggers:** Git tags matching `v*` (e.g., `v1.0.0`)

**What it does:**
- Builds release artifacts (JARs)
- Creates GitHub Release with detailed notes
- Attaches compiled JARs to release
- Archives artifacts (90 days)

**Release Artifacts:**
- `payment-bridge-*.jar`
- `mock-payment-api-*.jar`

## Workflow Triggers

| Event | Build & Test | Multi-Java | Integration | Coverage | Security | Release |
|-------|:---:|:---:|:---:|:---:|:---:|:---:|
| Push main | ✅ | ✅ | ✅ | ✅ | ✅ | - |
| Push develop | ✅ | ✅ | ✅ | ✅ | ✅ | - |
| Push 001-* | ✅ | ✅ | ✅ | - | - | - |
| Pull Request | ✅ | ✅ | ✅ | ✅ | ✅ | - |
| Tag v* | - | - | - | - | - | ✅ |
| Schedule | - | ✅ (daily) | - | - | ✅ (weekly) | - |

## Java Version Compatibility

### Java 21 (LTS - Primary)
- Source compilation target
- Default test version
- Full feature support

### Java 25 (Current)
- Runtime compatibility testing
- Requires special handling:
  - **Byte Buddy 1.18.8**: For Mockito mocking on Java 25 bytecode
  - **JaCoCo 0.8.14**: For code coverage instrumentation on Java 25

### Critical Dependency Versions

```
Byte Buddy: 1.18.8 (test scope) - Java 25 bytecode manipulation support
JaCoCo: 0.8.14 - Java 25 code coverage instrumentation
Spring Boot: 3.4.0 - Java 21 source, 25 runtime support
Mockito: 5.14.2 - Transitive Byte Buddy override via parent POM
```

## Performance Characteristics

### Build Times
- **Unit Tests**: ~20 seconds (payment-bridge)
- **Mock API Tests**: ~2 minutes (48 tests including load tests)
- **Integration Tests**: ~5 minutes (depends on RabbitMQ/PostgreSQL)
- **Full Suite**: ~2:30 minutes (all modules, all tests)

### Load Tests
- **FailureDistributionStatTest**: Validates 90% ±4% success rate across 500+ concurrent requests
- **LoadTest**: Ensures transaction persistence under load
- **DelaySimulatorTest**: Verifies 2000ms ±100ms timing with microsecond precision

## Environment Variables & Secrets

### Codecov (Optional)
If using Codecov for coverage tracking:
```
CODECOV_TOKEN: GitHub repository secret (auto-generated)
```

### GitHub Token
- Automatically available as `${{ secrets.GITHUB_TOKEN }}`
- Used for PR comments and release creation

## Branch Protection Rules (Recommended)

Add these protection rules to `main` and `develop` branches:

1. **Require status checks to pass:**
   - ✅ Build & Test
   - ✅ Multi-Java Version Testing
   - ✅ Integration Tests
   - ✅ Code Coverage

2. **Require code reviews:** 1 approval

3. **Require branches to be up to date:** Yes

4. **Require status checks to pass before merging:** Yes

## Running Tests Locally

### All Tests
```bash
mvn clean test
```

### Specific Module
```bash
mvn clean test -pl payment-bridge
mvn clean test -pl mock-payment-api
```

### With Coverage
```bash
mvn clean test jacoco:report
```

### Integration Tests Only
```bash
mvn clean verify -Dgroups=integration
```

### Load Tests Only
```bash
mvn clean test -Dtest=*LoadTest,*FailureDistributionStatTest
```

## Troubleshooting

### Tests Fail on Java 25

**Issue**: Byte Buddy or JaCoCo errors on Java 25
**Solution**: Verify versions in pom.xml:
```bash
grep -A 2 "byte-buddy" pom.xml  # Should show 1.18.8
grep "jacoco-maven-plugin" pom.xml  # Should show 0.8.14
```

### Coverage Reports Not Generated

**Issue**: JaCoCo XML files not found
**Solution**:
```bash
mvn jacoco:report
find . -name "jacoco.xml" -type f
```

### Load Tests Timeout

**Issue**: Tests exceed 45-minute limit
**Solution**:
- Check system resources (CPU, memory)
- Reduce concurrent request count in load tests
- Increase timeout in `integration-tests.yml`

### PR Comments Not Appearing

**Issue**: Test results not posted to PRs
**Solution**:
- Verify repository has PR comment permissions
- Check GitHub Actions workflow permissions in repository settings
- Ensure `pull-requests: write` permission is enabled

## CI/CD Monitoring

### GitHub Actions Dashboard
- View workflows: https://github.com/\[owner\]/payment-system-speckit/actions
- Check recent runs and logs
- Monitor job duration and artifact storage

### Codecov Dashboard (if enabled)
- Coverage trends: https://app.codecov.io/gh/\[owner\]/payment-system-speckit
- Coverage reports per module
- Historical data and comparisons

### Release Dashboard
- Releases: https://github.com/\[owner\]/payment-system-speckit/releases
- Download artifacts
- View release notes and assets

## Creating Releases

To create a new release:

1. **Tag the release:**
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. **Workflow automatically:**
   - Builds artifacts
   - Creates GitHub Release
   - Attaches JARs
   - Archives for 90 days

3. **Download artifacts:**
   - From GitHub Release page
   - Or from Actions > Release > Artifacts

## Maintenance

### Regular Tasks
- Monitor artifact storage (may hit limits with many releases)
- Review security scan reports weekly
- Update Java versions when new LTS released
- Update dependencies monthly

### Annual Review
- Check for deprecated actions
- Review workflow performance
- Consider optimization opportunities
- Update documentation

---

**Last Updated**: May 8, 2026
**Status**: Complete and operational
