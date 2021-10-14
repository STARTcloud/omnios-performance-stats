#!/bin/bash

export PATH=/usr/bin:/usr/sbin:/sbin:/usr/gnu/bin
cd /opt/prominic/performance-stats/bin

# Kill any existing instances of this process.  Example:
#     root  8218  8215   0   Jun 30 ?          65:39 java -cp .:groovy-3.0.3.jar rwtop_to_csv 10
# These processes are handing on a dtrace command.
# Use a regular expression to make it less likely that I match an unrelated process
pkill -f 'java.*rwtop_to_csv'


java -cp ".:groovy-3.0.3.jar" rwtop_to_csv 10 >/tmp/rwtop_to_csv.log 2>&1 || true
