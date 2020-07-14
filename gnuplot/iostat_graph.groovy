/**
 * Display the iostat graph for a csv file with gnuplots
 */

import java.util.regex.Pattern
import java.util.regex.Matcher

String USAGE = "groovy iostat_graph.groovy <csv-file>"

if (args.length < 1) {
    println "Missing required argument for CSV file."
    println USAGE
    System.exit(1)
}

String inputFileName = args[0]

// Determine the title.
String title = "iostat %b and %s for $inputFileName"
// parse out the name of the array to improve the title
Pattern fileNamePattern = Pattern.compile(/^.*iostat_([^._]+)[._].*$/)
Matcher matcher = fileNamePattern.matcher(inputFileName)
if (matcher.matches()) {
    String array = matcher.group(1)
    title = "iostat %b and %s for $array"
}


File inputFile = new File(inputFileName)
File configFile = new File('conf/iostat_graph.gnuplot')
File templateFile = new File("templates/iostat_graph.gnuplot")


String output = templateFile.text
output = output.replaceAll('%TITLE%', title)
output = output.replaceAll('%FILE%', inputFileName)

if (configFile.exists()) {
    configFile.delete()
}
configFile << output


// run gnuplot
def gnuplot = "gnuplot -p $configFile".execute()
gnuplot.waitFor()

//configFile.delete()
