#!/bin/bash

export PATH=/usr/bin:/usr/sbin:/sbin:/usr/gnu/bin
cd /opt/prominic/performance-stats/bin
java -cp ".:groovy-3.0.3.jar" iostat_to_csv 10 >/tmp/iostat_to_csv.log 2>&1 || true
