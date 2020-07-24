#!/bin/bash

#top_output=$(/opt/ooce/bin/top -b -n 0)

#loads=$(cat $top_output | grep "load averages"}
#cpu_states=$(cat $top_output | grep "CPU states"}

# Generate the times in GMT
TZ=GMT
FILE_TIMESTAMP=$(date '+%Y%m%d')
CSV_TIMESTAMP=$(date '+%m/%d/%Y %I:%M:%S %p %Z')

STATS_DIR=/performance-stats
LOAD_FILE=$STATS_DIR/${FILE_TIMESTAMP}_top_load.csv
CPU_FILE=$STATS_DIR/${FILE_TIMESTAMP}_top_cpustates.csv

#echo $LOAD_FILE
#echo $CPU_FILE

# add the headers to the files if they don't exist yet
if [ ! -e "$LOAD_FILE" ]; then 
	echo "Timestamp,1 Min. Load,5 Min. Load,15 Min. Load" > "$LOAD_FILE"
fi
if [ ! -e "$CPU_FILE" ]; then 
	echo "Timestamp,Idle,User,Kernel,IOWait,swap" > "$CPU_FILE"
fi



pfexec /opt/ooce/bin/top -b -n 0 | while read -r line
do
	if [[ "$line" == *load* ]]; then 
		#echo "line: $line" 
		echo "$CSV_TIMESTAMP,$line"  | sed 's/last pid: *[0-9]*; *load avg: *\([^,]*\), *\([^,]*\), *\([^;]*\);.*$/\1,\2,\3/' >> "$LOAD_FILE"
		#echo "$line" | sed "s/^load averages: *\([^,]*\), *\([^,]*\), *\([^;]*\);.*$/$CSV_TIMESTAMP,\1,\2,\3/"
	elif [[ "$line" == *states* ]]; then
		echo "$CSV_TIMESTAMP,$line" | sed 's/CPU states: *\([^%]*\)% idle, *\([^%]*\)% user, *\([^%]*\)% kernel, *\([^%]*\)% iowait, *\([^%]*\)% swap.*$/\1,\2,\3,\4,\5/' >> "$CPU_FILE"

	# memory is a problem because the units (K, M, G) are inconsistent
	#elif [[ "$line" == *Memory* ]]; then
	#	echo "$CSV_TIMESTAMP,$line" | sed 's/Memory: *TODO''
	fi
		

done
