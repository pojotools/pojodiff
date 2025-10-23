# Release Process

Modern simplified release workflow for pojodiff using Sonatype Central Portal.

## Prerequisites (One-time setup)

1. **Central Portal Account**: Register at [central.sonatype.com](https://central.sonatype.com)
2. **Namespace**: Claim your namespace (e.g., `io.github.pojotools`)
3. **GPG Key**: Generate and upload to key servers
4. **Maven Settings**: Configure `~/.m2/settings.xml` with environment variables
5. **Enable SNAPSHOTS**: Enable SNAPSHOT publishing for your namespace in Central Portal

### Maven Settings Configuration

Create or update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.SONATYPE_USERNAME}</username>
      <password>${env.SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

### GPG Setup for Signing

```bash
# Generate key if needed
gpg --gen-key

# Test GPG signing works
echo "test" | gpg --armor --sign

# If you get "Inappropriate ioctl for device", configure GPG agent:
echo 'use-agent' >> ~/.gnupg/gpg.conf
echo 'pinentry-program /usr/local/bin/pinentry-mac' >> ~/.gnupg/gpg-agent.conf  # macOS
# or for Linux:
# echo 'pinentry-program /usr/bin/pinentry-gtk-2' >> ~/.gnupg/gpg-agent.conf

# Restart GPG agent
gpgconf --kill gpg-agent

# Get your key ID
gpg --list-secret-keys --keyid-format=long
```

## Modern Release Workflow

### SNAPSHOT Publishing (for testing)

```bash
./scripts/publish-snapshot.sh
```

**What it does:**
- Validates current version is a SNAPSHOT
- Collects credentials if not set in environment
- Runs `mvn clean deploy` with Central Publishing Maven Plugin
- Plugin automatically handles SNAPSHOT upload to Central Portal

### Production Release

```bash
./scripts/publish-release.sh
```

**What it does:**
1. Validates git working directory is clean
2. Validates current version is SNAPSHOT
3. Prompts for release version (removes -SNAPSHOT)
4. Collects credentials if not set in environment
5. Updates version to release version
6. Commits release version
7. Creates and pushes git tag (e.g., `v0.1.0`)
8. Installs parent POM with release version
9. Builds and installs dependencies with release version
10. Deploys to Central Portal using Maven plugin
11. Updates to next development version
12. Pushes changes to git

**Key Benefits:**
- Git tag-based versioning
- Automatic rollback on failure
- Uses Central Publishing Maven Plugin 0.9.0+
- Simplified workflow

## Environment Variables

The scripts can use pre-set environment variables to avoid prompting:

```bash
export SONATYPE_USERNAME="your_username_token"
export SONATYPE_PASSWORD="your_password_token"
export GPG_KEY_ID="your_gpg_key_id"

# Then run scripts without prompts
./scripts/publish-snapshot.sh
./scripts/publish-release.sh
```

## Central Publishing Maven Plugin

The modern workflow uses `central-publishing-maven-plugin` version 0.9.0+ which:

- **Automatically detects SNAPSHOT versions** and uploads to snapshot repository
- **Handles all Maven Central requirements** (checksums, signatures, metadata)
- **Provides unified deployment** for both snapshots and releases
- **Supports autoPublish** for automatic publication without manual approval

### Plugin Configuration

```xml
<plugin>
  <groupId>org.sonatype.central</groupId>
  <artifactId>central-publishing-maven-plugin</artifactId>
  <version>0.9.0</version>
  <extensions>true</extensions>
  <configuration>
    <publishingServerId>central</publishingServerId>
    <tokenAuth>true</tokenAuth>
    <autoPublish>false</autoPublish> <!-- for releases -->
  </configuration>
</plugin>
```

## Repository URLs (Updated for Central Portal)

- **SNAPSHOT Repository**: https://central.sonatype.com/repository/maven-snapshots/
- **Release Repository**: https://central.sonatype.com
- **Deployment Monitoring**: https://central.sonatype.com/publishing/deployments

## Verification

### SNAPSHOT Releases
- **Central Portal**: https://central.sonatype.com/repository/maven-snapshots/io/github/pojotools/
- **Deployment Logs**: https://central.sonatype.com/publishing/deployments

### Production Releases
- **Maven Central Search**: https://search.maven.org/search?q=g:io.github.pojotools
- **Central Portal**: https://central.sonatype.com/artifact/io.github.pojotools/pojodiff-jackson
- **Deployment Logs**: https://central.sonatype.com/publishing/deployments

## Migration from Legacy OSSRH

This setup uses the **new Central Portal** (required for all projects since Feb 1, 2024):

- ✅ **Modern**: Central Portal with `central-publishing-maven-plugin`
- ❌ **Legacy**: OSSRH with `nexus-staging-maven-plugin` (sunset June 30, 2025)

## Manual Commands (Emergency/Debugging)

```bash
# Check current version
mvn help:evaluate -Dexpression=project.version -q -DforceStdout

# Manual SNAPSHOT deployment
mvn clean deploy -pl pojodiff-jackson -P snapshot-deploy

# Manual release deployment (after version update and git tag)
mvn clean deploy -pl pojodiff-jackson -P release-signing

# Check deployment status
curl -H "Authorization: Bearer $TOKEN" https://central.sonatype.com/api/v1/publisher/deployments
```

## Troubleshooting

### Common Issues

1. **Plugin not found**: Ensure Maven can download the Central Publishing Maven Plugin
2. **Authentication failed**: Verify your Central Portal tokens are correct
3. **GPG signing failed**: Check GPG agent configuration and key availability
4. **Namespace not enabled**: Enable SNAPSHOT publishing in Central Portal UI
5. **Version conflicts**: Ensure no duplicate versions exist

### Debug Commands

```bash
# Test credentials
mvn help:effective-settings | grep -A5 -B5 central

# Test GPG signing
echo "test" | gpg --armor --sign

# Check plugin resolution
mvn dependency:resolve-sources -Dclassifier=sources
```

Keep it simple. The new workflow eliminates most complexity.

## Related Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) - Version history and release notes
- [DEVELOPMENT.md](DEVELOPMENT.md) - Development environment setup
