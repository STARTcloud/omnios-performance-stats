#!/bin/bash

export PATH=/usr/bin:/usr/sbin:/sbin:/usr/gnu/bin
cd /opt/prominic/performance-stats/bin

GROOVY_JAR='/opt/ooce/groovy-3.0/lib/*'
java -cp ".:$GROOVY_JAR" iostat_to_csv 10 >/tmp/iostat_to_csv.log 2>&1 || true
