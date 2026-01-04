.PHONY: install

# Install the app on a connected Android device
# Usage:
#   make install           - Install latest release
#   make install V=v1.0.5  - Install specific version
install:
	@./scripts/install_apk.sh $(V)
