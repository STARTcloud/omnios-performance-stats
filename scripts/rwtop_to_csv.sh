#!/bin/bash

export PATH=/usr/bin:/usr/sbin:/sbin:/usr/gnu/bin
cd /opt/prominic/performance-stats/bin
groovy_sdk/bin/groovy rwtop_to_csv.groovy 10 >/tmp/rwtop_to_csv.log 2>&1 || true
