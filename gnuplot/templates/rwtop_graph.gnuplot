set title "%TITLE%"

set xlabel "Timestamp"
set ylabel "Percentage"

set format x "%H:%M:%S"

set xdata time
set timefmt "%m/%d/%Y %H:%M:%S"

set mxtics 4 #a small tic every five minute

set key autotitle columnhead # use the first line as title

set datafile separator ','
plot %LINES%


