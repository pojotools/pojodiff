#!/bin/bash

# pojodiff SNAPSHOT Publishing Script
# Simplified SNAPSHOT publishing using Central Publishing Maven Plugin

set -eo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MODULE="pojodiff-jackson"
PROFILE="snapshot-deploy"

echo -e "${BLUE}===== pojodiff SNAPSHOT Publisher =====${NC}"
echo -e "${BLUE}Simplified Central Publishing Workflow${NC}"
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
if [[ ! -f "pom.xml" ]]; then
    echo -e "${RED}Error: No pom.xml found in current directory${NC}"
    echo "Please run this script from the directory containing the main pom.xml"
    echo "Current directory: $(pwd)"
    exit 1
fi

if ! grep -q "pojodiff-parent" pom.xml 2>/dev/null; then
    echo -e "${RED}Error: This doesn't appear to be the pojodiff project root${NC}"
    echo "Expected to find 'pojodiff-parent' in pom.xml"
    echo "Current directory: $(pwd)"
    exit 1
fi

echo -e "${GREEN}✓ Project directory verified${NC}"

# Check current version is SNAPSHOT
echo -e "${YELLOW}Checking project version...${NC}"
if ! CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout); then
    echo -e "${RED}Error: Failed to get project version from Maven${NC}"
    echo "Make sure Maven is installed and this is a valid Maven project"
    exit 1
fi

if [[ -z "$CURRENT_VERSION" ]]; then
    echo -e "${RED}Error: Maven returned empty version${NC}"
    exit 1
fi

if [[ ! "$CURRENT_VERSION" =~ -SNAPSHOT$ ]]; then
    echo -e "${RED}Error: Current version '$CURRENT_VERSION' is not a SNAPSHOT${NC}"
    echo "This script only publishes SNAPSHOT versions for testing"
    exit 1
fi

echo -e "${GREEN}✓ Current version: $CURRENT_VERSION${NC}"
echo

# Check and collect credentials if not set
echo -e "${YELLOW}Checking credentials...${NC}"

# Check if environment variables are already set
if [[ -z "$SONATYPE_USERNAME" ]] || [[ -z "$SONATYPE_PASSWORD" ]] || [[ -z "$GPG_KEY_ID" ]]; then
    echo -e "${BLUE}Environment variables not set, collecting credentials...${NC}"
    echo -e "${BLUE}Note: Use your Sonatype Central Portal tokens${NC}"

    # Temporarily disable bash history to avoid storing credentials
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
echo -e "${BLUE}Publishing SNAPSHOT to Central Portal...${NC}"
echo -e "${BLUE}Using Central Publishing Maven Plugin (0.9.0+) for automatic SNAPSHOT handling${NC}"

# Deploy SNAPSHOT - the central-publishing-maven-plugin handles everything automatically
if mvn clean deploy -pl "$MODULE" -P "$PROFILE"; then
    echo
    echo -e "${GREEN}✓ SNAPSHOT published successfully!${NC}"
    echo
    echo -e "${BLUE}SNAPSHOT Information:${NC}"
    echo "  Group ID: io.github.pojotools"
    echo "  Artifact ID: $MODULE"
    echo "  Version: $CURRENT_VERSION"
    echo
    echo -e "${BLUE}Repository URL:${NC}"
    echo "  https://central.sonatype.com/repository/maven-snapshots/io/github/pojotools/$MODULE/"
    echo
    echo -e "${BLUE}Monitoring:${NC}"
    echo "  Central Portal: https://central.sonatype.com/publishing/deployments"
    echo
    echo -e "${BLUE}Usage in other projects:${NC}"
    echo "  Add to pom.xml:"
    echo "  <repositories>"
    echo "    <repository>"
    echo "      <name>Central Portal Snapshots</name>"
    echo "      <id>central-portal-snapshots</id>"
    echo "      <url>https://central.sonatype.com/repository/maven-snapshots/</url>"
    echo "      <releases><enabled>false</enabled></releases>"
    echo "      <snapshots><enabled>true</enabled></snapshots>"
    echo "    </repository>"
    echo "  </repositories>"
    echo
    echo "  <dependency>"
    echo "    <groupId>io.github.pojotools</groupId>"
    echo "    <artifactId>$MODULE</artifactId>"
    echo "    <version>$CURRENT_VERSION</version>"
    echo "  </dependency>"
    echo
    echo -e "${GREEN}SNAPSHOT will be available shortly${NC}"
else
    echo -e "${RED}✗ SNAPSHOT publication failed${NC}"
    echo
    echo -e "${YELLOW}Troubleshooting tips:${NC}"
    echo "1. Verify your Central Portal credentials are correct"
    echo "2. Ensure your namespace is enabled for SNAPSHOT publishing"
    echo "3. Check that your GPG key is properly configured"
    echo "4. Verify your ~/.m2/settings.xml configuration:"
    echo
    echo "   <settings>"
    echo "     <servers>"
    echo "       <server>"
    echo "         <id>central</id>"
    echo "         <username>\${env.SONATYPE_USERNAME}</username>"
    echo "         <password>\${env.SONATYPE_PASSWORD}</password>"
    echo "       </server>"
    echo "     </servers>"
    echo "   </settings>"
    echo
    echo "5. Check Central Portal deployment logs: https://central.sonatype.com/publishing/deployments"
    exit 1
fi
