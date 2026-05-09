# Repository Setup Checklist

Complete the following steps to fully activate the CI/CD pipeline in your GitHub repository.

## Step 1: Push CI/CD Configuration to Repository

```bash
# From the project root
git add .github/
git add CI_CD_*.md
git add REPOSITORY_SETUP_CHECKLIST.md
git commit -m "Add GitHub Actions CI/CD workflows and documentation"
git push origin 001-resilient-payment-bridge
```

**Verification**: 
- [ ] `.github/workflows/` directory appears in GitHub
- [ ] All 6 workflow files are present
- [ ] Documentation files are pushed

---

## Step 2: Enable GitHub Actions (if not already enabled)

1. [ ] Go to repository Settings
2. [ ] Navigate to "Actions" → "General"
3. [ ] Under "Actions permissions", select "Allow all actions and reusable workflows"
4. [ ] Save changes

**Verification**:
- [ ] GitHub Actions tab is visible
- [ ] "Allow all actions" is selected

---

## Step 3: Configure Branch Protection Rules for `main`

1. [ ] Go to Settings → Branches
2. [ ] Click "Add rule" under "Branch protection rules"
3. [ ] Enter branch name: `main`
4. [ ] Enable the following:
   - [ ] "Require a pull request before merging"
   - [ ] "Require approvals" (set to 1)
   - [ ] "Require status checks to pass before merging"
   - [ ] "Require branches to be up to date before merging"
   - [ ] "Require code reviews before merging"

5. [ ] Under "Status checks that are required to pass before merging", add:
   - [ ] `Build & Test`
   - [ ] `Test Java 21` (from Multi-Java-Version-Testing)
   - [ ] `Test Java 25` (from Multi-Java-Version-Testing)
   - [ ] `Payment Bridge Integration Tests`
   - [ ] `Mock Payment API Integration Tests`

6. [ ] Click "Create" to save

**Verification**:
- [ ] `main` branch protection appears in Settings
- [ ] Status checks are listed
- [ ] "Require branches to be up to date" is checked

---

## Step 4: Configure Branch Protection Rules for `develop`

1. [ ] Go to Settings → Branches
2. [ ] Click "Add rule" under "Branch protection rules"
3. [ ] Enter branch name: `develop`
4. [ ] Enable:
   - [ ] "Require a pull request before merging"
   - [ ] "Require approvals" (set to 1)
   - [ ] "Require status checks to pass before merging"

5. [ ] Under "Status checks that are required to pass before merging", add:
   - [ ] `Build & Test`
   - [ ] `Test Java 21`
   - [ ] `Test Java 25`

6. [ ] Click "Create" to save

**Verification**:
- [ ] `develop` branch protection appears in Settings
- [ ] Status checks are less strict than `main`

---

## Step 5: Set Up Codecov (Optional but Recommended)

### Part A: Add Repository to Codecov

1. [ ] Visit https://codecov.io
2. [ ] Sign in with your GitHub account
3. [ ] Click "New repository"
4. [ ] Select your GitHub organization and repository
5. [ ] Click "Authorize codecov"
6. [ ] Copy the repository token

### Part B: Add Token to GitHub Secrets

1. [ ] Go to Settings → Secrets and variables → Actions
2. [ ] Click "New repository secret"
3. [ ] Name: `CODECOV_TOKEN`
4. [ ] Value: (paste the token from Codecov)
5. [ ] Click "Add secret"

**Verification**:
- [ ] CODECOV_TOKEN appears in repository secrets
- [ ] Codecov dashboard shows your repository
- [ ] Next workflow run will upload coverage

---

## Step 6: Configure PR Permissions

1. [ ] Go to Settings → Actions → General
2. [ ] Under "Workflow permissions":
   - [ ] Select "Read and write permissions"
   - [ ] Check "Allow GitHub Actions to create and approve pull requests"
3. [ ] Save changes

**Verification**:
- [ ] Workflow permissions are set to "Read and write"
- [ ] PR creation is allowed

---

## Step 7: Test the CI/CD Pipeline

### Option A: Create a Test PR

```bash
# Create a test branch
git checkout -b test/cicd-validation
echo "# CI/CD Test" >> README.md
git add README.md
git commit -m "Test CI/CD pipeline"
git push origin test/cicd-validation
```

Then create a PR from `test/cicd-validation` to `develop`.

### Option B: Create a Release Tag

```bash
# Tag for release
git tag -a v0.1.0 -m "Test release"
git push origin v0.1.0
```

**Verification**:
- [ ] Workflows appear in GitHub Actions tab
- [ ] Build & Test workflow completes
- [ ] Multi-Java workflow shows Java 21 and 25 results
- [ ] PR shows status checks (if created via PR)
- [ ] Codecov report appears (if token configured)

---

## Step 8: Add Status Badges to README.md

Edit your main `README.md` and add workflow status badges:

```markdown
## CI/CD Status

[![Build & Test](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/build-and-test.yml)
[![Multi-Java Testing](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/multi-java-test.yml/badge.svg?branch=main)](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/multi-java-test.yml)
[![Integration Tests](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/integration-tests.yml/badge.svg?branch=main)](https://github.com/[OWNER]/payment-system-speckit/actions/workflows/integration-tests.yml)
[![codecov](https://codecov.io/gh/[OWNER]/payment-system-speckit/branch/main/graph/badge.svg)](https://codecov.io/gh/[OWNER]/payment-system-speckit)
```

Replace `[OWNER]` with your GitHub username or organization.

**Verification**:
- [ ] Badges appear in main README.md
- [ ] Badges link to workflow runs and Codecov

---

## Step 9: Review Documentation

1. [ ] Read [CI_CD_SETUP.md](CI_CD_SETUP.md) for detailed workflow documentation
2. [ ] Review [WORKFLOW_QUICK_REFERENCE.md](.github/WORKFLOW_QUICK_REFERENCE.md) for quick reference
3. [ ] Check [CI_CD_IMPLEMENTATION_SUMMARY.md](CI_CD_IMPLEMENTATION_SUMMARY.md) for complete overview
4. [ ] Share [.github/README.md](.github/README.md) with team members

**Verification**:
- [ ] Team understands the workflows
- [ ] Documentation is accessible and clear
- [ ] Troubleshooting guides are known

---

## Step 10: Monitor First Workflow Runs

1. [ ] Watch the GitHub Actions tab
2. [ ] Review logs for any issues
3. [ ] Verify all workflows complete successfully
4. [ ] Check coverage reports on Codecov (if enabled)

**Common Issues to Check**:
- [ ] All jobs pass (no red X marks)
- [ ] Build completes in expected time
- [ ] Tests show appropriate results
- [ ] Coverage reports are generated

---

## Step 11: Create Production Releases

Once everything is working:

```bash
# Create a release
git tag -a v1.0.0 -m "Production Release 1.0.0"
git push origin v1.0.0

# The release workflow will automatically:
# - Build all artifacts
# - Create GitHub Release
# - Attach compiled JARs
# - Archive for 90 days
```

**Verification**:
- [ ] Release workflow runs automatically
- [ ] GitHub Release is created
- [ ] JAR artifacts are attached
- [ ] Release notes are populated

---

## Step 12: Cleanup (Remove Test Artifacts)

```bash
# Delete test branch and tag
git branch -d test/cicd-validation
git push origin --delete test/cicd-validation
git tag -d v0.1.0
git push origin --delete v0.1.0
```

**Verification**:
- [ ] Test branch is removed
- [ ] Test tag is removed
- [ ] Repository is clean

---

## Maintenance Tasks

### Weekly
- [ ] Review GitHub Actions usage/costs
- [ ] Check for any failed workflow runs
- [ ] Monitor build times

### Monthly
- [ ] Review security scan reports
- [ ] Update dependencies if needed
- [ ] Check coverage trends on Codecov

### Quarterly
- [ ] Review and update Java versions
- [ ] Check for deprecated GitHub Actions
- [ ] Optimize workflow performance

---

## Troubleshooting Checklist

### Workflows Not Running
- [ ] GitHub Actions is enabled in Settings
- [ ] Branch name matches trigger conditions (main/develop/001-*)
- [ ] YAML files are valid (all verified ✅)
- [ ] Repository has sufficient permissions

### Build Failures
- [ ] Check Java version (21 vs 25)
- [ ] Verify dependencies are not corrupted
- [ ] Run `mvn clean test` locally to verify
- [ ] Check workflow logs for specific errors

### PR Checks Not Appearing
- [ ] Confirm branch protection rules are set
- [ ] Verify workflow triggers include `pull_request`
- [ ] Check GitHub Actions permissions

### Coverage Not Uploading
- [ ] Verify Codecov token is correct
- [ ] Check that JaCoCo reports are generated
- [ ] Review Codecov integration in workflow

---

## Support & Documentation

| Document | Purpose |
|----------|---------|
| [CI_CD_SETUP.md](CI_CD_SETUP.md) | Detailed CI/CD documentation |
| [WORKFLOW_QUICK_REFERENCE.md](.github/WORKFLOW_QUICK_REFERENCE.md) | Quick reference guide |
| [CI_CD_IMPLEMENTATION_SUMMARY.md](CI_CD_IMPLEMENTATION_SUMMARY.md) | Implementation overview |
| [.github/README.md](.github/README.md) | GitHub configuration guide |

---

## Completion Checklist

- [ ] Step 1: CI/CD files pushed to repository
- [ ] Step 2: GitHub Actions enabled
- [ ] Step 3: `main` branch protection configured
- [ ] Step 4: `develop` branch protection configured
- [ ] Step 5: Codecov setup (optional)
- [ ] Step 6: PR permissions configured
- [ ] Step 7: CI/CD pipeline tested
- [ ] Step 8: Status badges added to README
- [ ] Step 9: Documentation reviewed
- [ ] Step 10: First workflow runs monitored
- [ ] Step 11: Production release created
- [ ] Step 12: Test artifacts cleaned up

---

**All Done!** 🎉

Your CI/CD pipeline is now fully operational. Workflows will automatically run on every push and PR, validating your code quality, testing across Java versions, scanning for security issues, and automating releases.

**Questions?** Refer to the CI/CD documentation files or GitHub Actions documentation.

---

**Checklist Completed**: ___________
**Date**: ___________
