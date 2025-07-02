#!/bin/bash

cd /home/gpaga

./schermo_on.sh
python backup_monitor.py &

sleep 60
./schermo_off.sh

