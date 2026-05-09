# Workflow Quick Reference

## Workflow Status Indicators

When you push code or create a pull request, you'll see workflow status checks:

```
✅ Build & Test          - Unit tests passed
✅ Multi-Java Test      - Tested on Java 21 & 25
✅ Integration Tests    - E2E and load tests passed
✅ Code Coverage        - Coverage reports generated
✅ Security Scan        - No critical vulnerabilities
```

## What Each Workflow Does

### Build & Test

- Runs every commit push
- Compiles code
- Runs all unit tests
- Generates coverage reports
- **Time**: ~20 seconds

### Multi-Java Version Testing

- Runs on push and daily
- Tests Java 21 and Java 25
- Validates Byte Buddy 1.18.8 and JaCoCo 0.8.14
- **Time**: ~2 minutes per version

### Integration Tests

- Runs on every commit
- Tests payment-bridge and mock-payment-api together
- Runs load tests on main/develop with dynamic scaling (1, 3, and 5 instance matrix)
- Validates load balancer Docker build separately from Maven module builds
- Uses service health checks to ensure Nginx only routes traffic after the Java backend is ready
- **Time**: ~5 minutes (45 minutes for load tests)

### Code Coverage

- Runs on push to main/develop
- Generates JaCoCo reports
- Posts coverage links to PRs
- **Time**: ~3 minutes

### Security Scan

- Runs on push and weekly
- Scans for dependency vulnerabilities
- Generates SBOM (Software Bill of Materials)
- **Time**: ~5 minutes

### Release

- Runs when you tag with `v*`
- Creates GitHub release
- Publishes compiled JARs
- **Time**: ~2 minutes

## Accessing Workflow Results

1. **GitHub Actions Dashboard**
   - Go to: Actions tab in repository
   - Click workflow name to see runs
   - Click run to see details and logs

2. **PR Status Checks**
   - Each check has a link to detailed logs
   - Click "Details" next to workflow name

3. **Artifacts**
   - Actions → Run → Artifacts
   - Download test reports and coverage data

## Common Tasks

### Create a Release

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
# Release workflow starts automatically
```

### Rerun a Failed Workflow

- GitHub Actions UI → Failed run → "Re-run failed jobs"
- Or: GitHub CLI: `gh run rerun [run-id]`

### Skip a Workflow

Add `[skip ci]` to commit message:

```bash
git commit -m "Update docs [skip ci]"
```

## Troubleshooting

### Tests Failing on Java 25?

- Check `pom.xml` for Byte Buddy 1.18.8 and JaCoCo 0.8.14
- Run locally: `mvn clean test`

### Codecov Integration Not Working?

- Verify token in repository secrets
- Check codecov action configuration

### Load Tests Timing Out?

- Increase timeout in `integration-tests.yml`
- Check runner memory/CPU availability
- Ensure service health startup semantics are used for load balancer dependencies to avoid premature traffic routing

### PR Comments Not Appearing?

- Check repository settings for Actions permissions
- Verify `pull-requests: write` scope

## Workflow Files Location

```
.github/workflows/
├── build-and-test.yml           # Core build & tests
├── multi-java-test.yml          # Java 21 testing
├── integration-tests.yml        # E2E and load tests
├── code-coverage.yml            # Coverage reporting
├── security-scan.yml            # Vulnerability scanning
└── release.yml                  # Release automation
```

## Environment & Caching

- **Java Cache**: Maven dependencies cached per OS and pom.xml checksum
- **Cache Location**: GitHub Actions Runner cache
- **Maven Memory**: 2GB heap allocation
- **Retention**: Artifacts kept 7-90 days based on type

## Performance Tips

1. **Reduce build time:**
   - Use branch-specific triggers
   - Cache dependencies aggressively
   - Run load tests only on main/develop

2. **Improve reliability:**
   - Use workflow timeouts
   - Set fail-fast to false for matrix jobs
   - Archive test results

3. **Cost optimization:**
   - Reduce concurrent job counts
   - Set appropriate artifact retention
   - Use resource limits

---

**For detailed information, see CI_CD_SETUP.md**
