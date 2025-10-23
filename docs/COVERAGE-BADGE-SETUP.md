# Coverage Badge & Report Setup

This project uses:
- **GitHub Gist + shields.io** to display the coverage badge
- **GitHub Pages** to host the detailed JaCoCo coverage report

## One-Time Setup

### Step 0: Enable GitHub Pages

1. Go to your repository → **Settings** → **Pages**
2. Under "Build and deployment":
   - Source: **Deploy from a branch**
   - Branch: **gh-pages** (will be created automatically on first deployment)
   - Folder: **/ (root)**
3. Click **Save**

After the first deployment (after pushing to main), your coverage report will be available at:
**https://pojotools.github.io/pojodiff/coverage/**

### Step 1: Create a GitHub Personal Access Token

1. Go to https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Name it: `pojodiff-coverage-badge`
4. Select scope: **gist** (only this one is needed)
5. Click "Generate token"
6. **Copy the token** (you won't see it again!)

### Step 2: Create a Gist

1. Go to https://gist.github.com/
2. Create a new gist:
   - Filename: `pojodiff-coverage.json`
   - Content:
     ```json
     {
       "schemaVersion": 1,
       "label": "coverage",
       "message": "0%",
       "color": "red"
     }
     ```
3. Click "Create public gist"
4. **Copy the Gist ID** from the URL (e.g., `https://gist.github.com/YOUR_USERNAME/abc123def456` → ID is `abc123def456`)

### Step 3: Add GitHub Secrets

Go to your repository → Settings → Secrets and variables → Actions → "New repository secret"

Add two secrets:
- Name: `GIST_SECRET`, Value: [paste your token from step 1]
- Name: `GIST_ID`, Value: [paste your gist ID from step 2]

### Step 4: Update README.md

Replace `YOUR_GIST_ID` in README.md with your actual Gist ID:

```markdown
[![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/YOUR_USERNAME/YOUR_GIST_ID/raw/pojodiff-coverage.json)](https://pojotools.github.io/pojodiff/coverage/)
```

## How It Works

After every successful build on `main` branch:

1. **Coverage Badge**:
   - JaCoCo generates coverage reports
   - CI extracts the coverage percentage from `jacoco.csv`
   - CI determines badge color based on coverage:
     - ≥80%: bright green
     - ≥60%: yellow
     - ≥40%: orange
     - <40%: red
   - CI updates your Gist with the new coverage data
   - shields.io reads from the Gist and generates the badge

2. **Coverage Report**:
   - The full HTML JaCoCo report is deployed to GitHub Pages
   - Available at: `https://pojotools.github.io/pojodiff/coverage/`
   - Auto-updates on every push to main
   - No authentication required - publicly accessible!

## Troubleshooting

### Badge Issues
- **Badge shows "invalid"**: Check that the Gist ID in README.md is correct
- **Badge doesn't update**: Check that `GIST_SECRET` and `GIST_ID` secrets are set correctly
- **Build succeeds but badge not updated**: Check the "Extract and upload coverage" step in CI logs

### GitHub Pages Issues
- **Coverage report not accessible**: Make sure GitHub Pages is enabled in Settings → Pages
- **Report shows old coverage**: Check the "Deploy coverage report to GitHub Pages" step in CI logs
- **404 error**: Wait a few minutes after first deployment for GitHub Pages to initialize
