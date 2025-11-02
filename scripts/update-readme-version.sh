#!/bin/bash

# Script to update README.md with latest released version
# This ensures the dependency examples always show the latest stable release for users

set -eo pipefail

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}===== README Version Updater =====${NC}"

# Get latest released version from git tags
echo -e "${YELLOW}Getting latest released version...${NC}"
LATEST_TAG=$(git tag -l | grep -E "^v[0-9]" | sort -V | tail -1)

if [[ -z "$LATEST_TAG" ]]; then
    echo "Error: No release tags found"
    exit 1
fi

# Remove 'v' prefix to get version number
LATEST_VERSION=${LATEST_TAG#v}

echo -e "${YELLOW}Latest released version: $LATEST_VERSION${NC}"

# Check if README.md exists
if [[ ! -f "README.md" ]]; then
    echo "Error: README.md not found in current directory"
    exit 1
fi

# Update version in README.md dependency examples
echo -e "${YELLOW}Updating README.md dependency versions...${NC}"

# Create a backup of README.md
cp README.md README.md.bak

# Update all version tags in dependency examples
# This matches both SNAPSHOT and release versions
# macOS-compatible sed syntax
sed -i '' "s/<version>[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\(-SNAPSHOT\)\{0,1\}<\/version>/<version>$LATEST_VERSION<\/version>/g" README.md

# Check if any changes were made
if ! diff -q README.md README.md.bak > /dev/null 2>&1; then
    echo -e "${GREEN}✓ README.md updated with version $LATEST_VERSION${NC}"

    # Show what changed
    echo -e "${BLUE}Changes made:${NC}"
    diff README.md.bak README.md | grep "^>" | head -5

    # Remove backup
    rm README.md.bak
else
    echo -e "${GREEN}✓ README.md already up to date with version $LATEST_VERSION${NC}"
    rm README.md.bak
fi

echo -e "${GREEN}Version update complete!${NC}"
