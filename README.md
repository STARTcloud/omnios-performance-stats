# omnios-performance-stats

## OmniOS Scripts

The `scripts` directory contains scripts that will be used to collect performance data on OmniOS hosts.

The scripts have a dependency on DTT and Java.

The Groovy scripts are currently run as .class files to remove the dependency on java.  Use this script to compile the Groovy classes:

    ./compile_groovy.sh


## Graphing scripts

The scripts in `gnuplot` are designed to convert the CSV files outputted above into graphs with gnuplot.

Dependencies:
- groovy
- gnuplot

Syntax

    groovy iostat_graph.groovy <iostat-csv>
    groovy rwtop_graph.groovy <rwtop-csv>
