/**
 * Read the I/O statistics with iostat, and write the required values to a file to each pool.
 */


import java.text.SimpleDateFormat
import java.util.Date


// Constants - move to configuration file?
String OUTPUT_DIRECTORY = '/performance-stats'
// The interval to use for iostat, in seconds
int INTERVAL_SECONDS = 10
// Timezone - used so that the files wrap at a consistent time
TimeZone TIME_ZONE = TimeZone.getTimeZone('GMT')

// The time that will be used for the output
long timestampMillis = System.currentTimeMillis()
SimpleDateFormat dateFormat = new SimpleDateFormat('MM/dd/yyyy HH:mm:ss a zzz')
dateFormat.setTimeZone(TIME_ZONE)
String timestamp = dateFormat.format(new Date(timestampMillis)) 
//println "Timestamp:  $timestamp"

// day-only format for log files
SimpleDateFormat todayFormat = new SimpleDateFormat('yyyyMMdd')
todayFormat.setTimeZone(TIME_ZONE)
String today = todayFormat.format(new Date(timestampMillis)) 

// make sure the output directory exists
//new File(OUTPUT_DIRECTORY).mkdirs()

def poolProcess = "zpool list -H".execute()
poolProcess.waitFor()
def pools = []
poolProcess.text.eachLine {line ->
	String[] tokens = line.split(/\s+/)
	String pool = tokens[0]
	pools << pool
}

def iostatCommand = "iostat -xn "
pools.each {String pool ->
	iostatCommand += "$pool "
}
// Need to collect two samples.  iostat always returns the same %w and %b for the output row, so we need a second row to see the correct output
iostatCommand += "$INTERVAL_SECONDS 2"  
//println "Command:  $iostatCommand"
def iostatProcess = iostatCommand.execute()
iostatProcess.waitFor()

String header = 'Timestamp, %w, %b\n'

// track the output rows
int rowNumber = 0
iostatProcess.text.eachLine { String line ->
	////println line
	def tokens = line.split(/\s+/)
	if (line.trim().equalsIgnoreCase('extended device statistics')) {
		rowNumber++
	}
	else if (rowNumber >=2 && tokens.length > 2 && tokens[1].isNumber()) {
		// skip the first row of data (rowNumber = 1)
		//println line
		if (tokens.length < 12) {
			//println "ERROR:  too few tokens in line '$line'"
		}
		else {
			String pool = tokens[11]
			String percentW = tokens[9]
			String percentB = tokens[10]
			//println "$pool: %w=$percentW, %b=$percentB"

			String outputLine = "$timestamp, $percentW, $percentB\n"
			//println header
			//println outputLine

			File outputFile = new File("$OUTPUT_DIRECTORY/${today}_iostat_${pool}.csv")
			if (!outputFile.exists()) {
				outputFile << header
			}
			outputFile << outputLine

		}
	}
	else {
		// ignore header line
	}
}
