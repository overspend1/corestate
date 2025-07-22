#!/system/bin/sh

# CoreState Backup Module - Service Script
# This script runs during late_start service stage

MODDIR=${0%/*}

# Start CoreState backup service daemon if needed
if [ -f "$MODDIR/system/bin/corestate_daemon" ]; then
    nohup $MODDIR/system/bin/corestate_daemon &
    echo "$(date): CoreState daemon started" >> /data/corestate/module.log
fi

# Set up backup environment
export CORESTATE_MODULE_PATH="$MODDIR"
export CORESTATE_DATA_PATH="/data/corestate"

echo "$(date): CoreState service script completed" >> /data/corestate/module.log