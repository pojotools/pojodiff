#!/bin/bash

# pojodiff Release Publishing Script
# Modern git tag-based release workflow using Central Publishing Maven Plugin

set -eo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MODULE="pojodiff-jackson"
PROFILE="release-signing"

echo -e "${BLUE}===== pojodiff Release Publisher =====${NC}"
echo -e "${BLUE}Modern Git Tag-Based Release Workflow${NC}"
echo

# Function to cleanup sensitive variables
cleanup() {
    echo -e "${YELLOW}Cleaning up environment variables...${NC}"
    unset SONATYPE_USERNAME SONATYPE_PASSWORD GPG_KEY_ID 2>/dev/null || true
}

# Set trap to cleanup on exit
trap cleanup EXIT

# Verify we're in the right directory
echo -e "${YELLOW}Checking project directory...${NC}"
if [[ ! -f "pom.xml" ]] || ! grep -q "pojodiff-parent" pom.xml; then
    echo -e "${RED}Error: Not in pojodiff project root directory${NC}"
    echo "Please run this script from the directory containing the main pom.xml"
    exit 1
fi
echo -e "${GREEN}✓ Project directory verified${NC}"

# Check if git working directory is clean
echo -e "${YELLOW}Checking git status...${NC}"
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}Error: Git working directory is not clean${NC}"
    echo "Please commit or stash your changes before releasing"
    git status --porcelain
    exit 1
fi
echo -e "${GREEN}✓ Git working directory is clean${NC}"

# Get current version from pom.xml
echo -e "${YELLOW}Getting current version...${NC}"
if ! CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout); then
    echo -e "${RED}Error: Failed to get project version from Maven${NC}"
    exit 1
fi

if [[ -z "$CURRENT_VERSION" ]]; then
    echo -e "${RED}Error: Maven returned empty version${NC}"
    exit 1
fi

echo -e "${YELLOW}Current version: $CURRENT_VERSION${NC}"

# Check if current version is SNAPSHOT
if [[ ! "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
    echo -e "${RED}Error: Current version '$CURRENT_VERSION' is not a SNAPSHOT${NC}"
    echo "Release should be created from a SNAPSHOT version"
    exit 1
fi

# Propose release version (remove -SNAPSHOT)
RELEASE_VERSION=${CURRENT_VERSION%-SNAPSHOT}
echo -e "${BLUE}Proposed release version: $RELEASE_VERSION${NC}"

# Confirm release version
read -r -p "Enter release version [$RELEASE_VERSION]: " USER_VERSION
if [[ -n "$USER_VERSION" ]]; then
    RELEASE_VERSION="$USER_VERSION"
fi

# Validate release version format
if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}Error: Invalid version format '$RELEASE_VERSION'${NC}"
    echo "Expected format: X.Y.Z (e.g., 1.0.0)"
    exit 1
fi

echo -e "${GREEN}✓ Release version: $RELEASE_VERSION${NC}"

# Check if tag already exists
if git tag -l | grep -q "^v$RELEASE_VERSION$"; then
    echo -e "${RED}Error: Tag v$RELEASE_VERSION already exists${NC}"
    echo "Existing tags:"
    git tag -l | grep -E "^v[0-9]" | sort -V | tail -5
    exit 1
fi

echo
echo -e "${YELLOW}Release Summary:${NC}"
echo "  Current version: $CURRENT_VERSION"
echo "  Release version: $RELEASE_VERSION"
echo "  Git tag: v$RELEASE_VERSION"
echo "  Module to publish: $MODULE"
echo

read -r -p "Proceed with release? (yes/no): " CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
    echo "Release cancelled"
    exit 0
fi

# Check and collect credentials if not set
echo -e "${YELLOW}Checking credentials...${NC}"

# Check if environment variables are already set
if [[ -z "$SONATYPE_USERNAME" ]] || [[ -z "$SONATYPE_PASSWORD" ]] || [[ -z "$GPG_KEY_ID" ]]; then
    echo -e "${BLUE}Environment variables not set, collecting credentials...${NC}"

    # Temporarily disable bash history
    set +o history

    # Collect missing credentials
    if [[ -z "$SONATYPE_USERNAME" ]]; then
        read -r -p "Sonatype Username Token: " SONATYPE_USERNAME
    fi

    if [[ -z "$SONATYPE_PASSWORD" ]]; then
        read -r -s -p "Sonatype Password Token: " SONATYPE_PASSWORD
        echo  # Add newline after hidden input
    fi

    if [[ -z "$GPG_KEY_ID" ]]; then
        read -r -p "GPG Key ID: " GPG_KEY_ID
    fi

    # Export variables for Maven
    export SONATYPE_USERNAME
    export SONATYPE_PASSWORD
    export GPG_KEY_ID

    # Re-enable bash history
    set -o history
else
    echo -e "${GREEN}✓ Using existing environment variables${NC}"
fi

# Verify credentials are set
if [[ -z "$SONATYPE_USERNAME" ]] || [[ -z "$SONATYPE_PASSWORD" ]] || [[ -z "$GPG_KEY_ID" ]]; then
    echo -e "${RED}Error: Credentials not properly set${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Credentials configured${NC}"

# Configure GPG for non-interactive use
export GPG_TTY=$(tty)

echo
echo -e "${BLUE}Starting simplified release process...${NC}"

# Step 1: Update to release version
echo -e "${YELLOW}Step 1: Setting release version ${RELEASE_VERSION}...${NC}"
if ! mvn versions:set -DnewVersion="$RELEASE_VERSION" -q; then
    echo -e "${RED}Failed to set release version${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Version updated to $RELEASE_VERSION${NC}"

# Step 2: Commit release version
echo -e "${YELLOW}Step 2: Committing release version...${NC}"
git add .
git commit -m "Release version $RELEASE_VERSION"
echo -e "${GREEN}✓ Release version committed${NC}"

# Step 3: Create and push release tag
echo -e "${YELLOW}Step 3: Creating and pushing release tag...${NC}"
git tag "v$RELEASE_VERSION" -m "Release version $RELEASE_VERSION"
git push origin "v$RELEASE_VERSION"
echo -e "${GREEN}✓ Release tag v$RELEASE_VERSION created and pushed${NC}"

# Step 3b: Update README.md with new release version
echo -e "${YELLOW}Step 3b: Updating README.md with new release version...${NC}"
if [[ -x "./scripts/update-readme-version.sh" ]]; then
    if ./scripts/update-readme-version.sh; then
        # Commit the README update if changes were made
        if ! git diff --quiet README.md; then
            git add README.md
            git commit -m "Update README.md with release version $RELEASE_VERSION"
            git push origin main
            echo -e "${GREEN}✓ README.md updated and committed with version $RELEASE_VERSION${NC}"
        else
            echo -e "${GREEN}✓ README.md already up to date${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ README update script failed, continuing anyway${NC}"
    fi
else
    echo -e "${YELLOW}⚠ README update script not found or not executable${NC}"
fi

# Step 4: Install parent POM and build dependencies
echo -e "${YELLOW}Step 4a: Installing parent POM with release version...${NC}"
if ! mvn clean install -N -q; then
    echo -e "${RED}Failed to install parent POM with release version!${NC}"
    RELEASE_SUCCESS=false
else
    echo -e "${GREEN}✓ Parent POM installed${NC}"

    echo -e "${YELLOW}Step 4b: Building and installing dependencies...${NC}"
    if ! mvn clean install -pl pojodiff-spi,pojodiff-core -q; then
        echo -e "${RED}Failed to build dependencies with release version!${NC}"
        RELEASE_SUCCESS=false
    else
        echo -e "${GREEN}✓ Dependencies built and installed${NC}"

        # Step 4c: Deploy release using Central Publishing Plugin
        echo -e "${YELLOW}Step 4c: Deploying release to Central Portal...${NC}"
        echo -e "${BLUE}Using Central Publishing Maven Plugin with reactor build...${NC}"

        # Deploy with reactor build to ensure all dependencies are available
        if mvn clean deploy -pl "$MODULE" -am -P "$PROFILE"; then
            echo -e "${GREEN}✓ Release deployed successfully!${NC}"
            RELEASE_SUCCESS=true
        else
            echo -e "${RED}✗ Release deployment failed${NC}"
            RELEASE_SUCCESS=false
        fi
    fi
fi

# Step 5: Update to next development version
if [[ "$RELEASE_SUCCESS" == "true" ]]; then
    echo -e "${YELLOW}Step 5: Setting next development version...${NC}"

    # Calculate next development version
    IFS='.' read -ra VERSION_PARTS <<< "$RELEASE_VERSION"
    NEXT_MINOR=$((VERSION_PARTS[1] + 1))
    NEXT_DEV_VERSION="${VERSION_PARTS[0]}.$NEXT_MINOR.0-SNAPSHOT"

    mvn versions:set -DnewVersion="$NEXT_DEV_VERSION" -q

    # Clean up Maven versions backup files before committing
    find . -name "*.versionsBackup" -delete 2>/dev/null || true

    git add .
    git commit -m "Prepare for next development iteration $NEXT_DEV_VERSION"
    git push origin main

    echo -e "${GREEN}✓ Next development version: $NEXT_DEV_VERSION${NC}"
    echo -e "${GREEN}✓ Cleaned up Maven versions backup files${NC}"
else
    echo -e "${YELLOW}Rolling back due to deployment failure...${NC}"

    # Count commits made since starting release process
    # We need to roll back: 1) release version commit, 2) README update commit (if made)
    COMMITS_TO_ROLLBACK=1

    # Check if README was updated (look for recent README commit)
    if git log --oneline -2 | grep -q "Update README.md with release version"; then
        COMMITS_TO_ROLLBACK=2
        echo -e "${YELLOW}Rolling back README update commit as well...${NC}"
    fi

    # Reset to before the release commits
    git reset --hard HEAD~$COMMITS_TO_ROLLBACK

    # Delete the release tag
    git tag -d "v$RELEASE_VERSION" 2>/dev/null || true
    git push origin ":refs/tags/v$RELEASE_VERSION" 2>/dev/null || true

    # Force push to revert the commits on remote
    git push --force-with-lease origin main

    # Revert Maven version changes
    mvn versions:revert -q 2>/dev/null || true

    # Clean up any backup files created during the failed release
    find . -name "*.versionsBackup" -delete 2>/dev/null || true

    echo -e "${RED}Release rolled back (reverted $COMMITS_TO_ROLLBACK commit(s))${NC}"
    exit 1
fi

echo
echo -e "${GREEN}🎉 Release $RELEASE_VERSION published successfully!${NC}"
echo
echo -e "${BLUE}Release Information:${NC}"
echo "  Group ID: io.github.pojotools"
echo "  Artifact ID: $MODULE"
echo "  Version: $RELEASE_VERSION"
echo "  Git tag: v$RELEASE_VERSION"
echo
echo -e "${BLUE}Monitoring URLs:${NC}"
echo "  Central Portal Deployments: https://central.sonatype.com/publishing/deployments"
echo "  Maven Central Search: https://search.maven.org/search?q=g:io.github.pojotools"
echo "  Artifact Page: https://central.sonatype.com/artifact/io.github.pojotools/$MODULE/$RELEASE_VERSION"
echo
echo -e "${BLUE}Usage in other projects:${NC}"
echo "  <dependency>"
echo "    <groupId>io.github.pojotools</groupId>"
echo "    <artifactId>$MODULE</artifactId>"
echo "    <version>$RELEASE_VERSION</version>"
echo "  </dependency>"
echo
echo -e "${GREEN}Release will be available in Maven Central within 30 minutes${NC}"
echo -e "${BLUE}Current development version: $NEXT_DEV_VERSION${NC}"
