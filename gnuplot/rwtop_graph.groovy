/**
 * Display a graph for the rwtop csv file with gnuplot
 */

import java.util.regex.Pattern
import java.util.regex.Matcher

String USAGE = "groovy iostat_graph.groovy <csv-file>"
int ENTRIES_TO_HIGHLIGHT = 4

if (args.length < 1) {
    println "Missing required argument for CSV file."
    println USAGE
    System.exit(1)
}

String inputFileName = args[0]

// Determine the title.
String title = "Reads/Writes for file $inputFileName"
// parse out the name of the array to improve the title
Pattern fileNamePattern = Pattern.compile(/^.*iostat_([^._]+)[._].*$/)
Matcher matcher = fileNamePattern.matcher(inputFileName)
if (matcher.matches()) {
    String array = matcher.group(1)
    title = "Reads/Writes for $array"
}


File inputFile = new File(inputFileName)
File configFile = new File('conf/rwtop_graph.gnuplot')
File templateFile = new File("templates/rwtop_graph.gnuplot")

// build the plot lines
boolean header = true;
def columns = []
inputFile.text.eachLine { String line ->
    // the first column is column 1

    String[] tokens = line.split(',')
    if (header) {
        int columnIndex = 0;
        tokens.each { String zoneName ->
            columnIndex++
            columns << [name:zoneName.trim(), max:0, columnIndex:columnIndex]
        }
        header=false
    }
    else {
        if (tokens.length != columns.size()) {
            println "Inconsistent number of tokens in line - zone list on pool may have changed."
            println "Header count:  ${columns.size()}, Line count:  ${tokens.length}"
            println line
            System.exit(1)
        }
        for (int i = 1; i < tokens.length; i++) {
            int curValue = Integer.parseInt(tokens[i].trim())
            if (curValue > columns[i]['max']) {
                columns[i]['max'] = curValue
            }
        }
    }

}

columns.remove(0)  // remove Timestamp placeholder

def plotLines = []
(columns.sort {x -> -x['max']}).eachWithIndex { column, index ->
    println "${column['name']}:  max=${column['max']}"

    String plotLine = "\"$inputFileName\" using 1:${column['columnIndex']} with lines"
    if (index < ENTRIES_TO_HIGHLIGHT) {
        // increase the line width
        plotLine += ' lw 4'
    }

    println plotLine
    
    plotLines << plotLine
}

String plotLinesRaw = plotLines.join(', \\\\\n     ')


String output = templateFile.text
output = output.replaceAll('%TITLE%', title)
output = output.replaceAll('%FILE%', inputFileName)
output = output.replaceAll('%LINES%', plotLinesRaw)

if (configFile.exists()) {
    configFile.delete()
}
configFile << output


// run gnuplot
def gnuplot = "gnuplot -p $configFile".execute()
gnuplot.waitFor()

//configFile.delete()
