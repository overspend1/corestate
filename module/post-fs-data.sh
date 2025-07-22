#!/system/bin/sh

# CoreState Backup Module - Post FS Data Script
# This script runs after the file system is mounted but before any app starts

MODDIR=${0%/*}

# Set permissions for CoreState app to access backup functions
chmod 755 $MODDIR/system/bin/corestate_backup
chown 0:0 $MODDIR/system/bin/corestate_backup

# Create necessary directories
mkdir -p /data/corestate
chmod 755 /data/corestate

# Log module loading
echo "$(date): CoreState module loaded successfully" >> /data/corestate/module.log