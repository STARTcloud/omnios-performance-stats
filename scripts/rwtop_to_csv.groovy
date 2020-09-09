/* ###########################
Read the process I/O statistics with rwtop, link to zones and pools, and append
the totals to a .csv file

#############################*/

import java.util.regex.Pattern
import java.util.regex.Matcher

import java.text.DecimalFormat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

import java.util.ArrayList
import java.util.Map


// Constants
// newline character to use for output files
NEWLINE = "\n"
// base path for output files
String OUTPUT_DIRECTORY = '/performance-stats'
// expected path to rwtop command
String RWTOP_COMMAND = '/opt/DTT/rwtop'
// Default interval for rwtop command, in seconds 
int DEFAULT_INTERVAL_SECONDS = 10
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

String formatNumber(def number,boolean withDecimals=false){
	String format=withDecimals?"#,###.00":"#,###"
	DecimalFormat formatter = new DecimalFormat(format)
	//formatter.setDecimalFormatSymbols(new DecimalFormatSymbols(new Locale("es")))
	return formatter.format(number)
}


// parameters
int interval = DEFAULT_INTERVAL_SECONDS
if (args.length >= 1) {
	// fail on non-number.  TODO:  cleaner error reporting?
	interval = Integer.parseInt(args[0])
}

// list all zones, so that we can save a spot for the halted zones
def zoneadmProc = "/usr/sbin/zoneadm list -p -c".execute()
zoneadmProc.waitFor()

////println zoneadmProc.text
////println ""
////println "Exit code:  ${zoneadmProc.exitValue()}"

String output = zoneadmProc.text
def zoneMap = [:]
output.eachLine { line ->
	String[] tokens = line.split(':')
	if (tokens.length >= 4) {
		String zoneID = tokens[0]
		String zoneName = tokens[1]
		String path = tokens[3]
		if (zoneID == '0') {
			// skip the global zone?
			zoneMap[zoneID] = [zoneID:zoneID, name:zoneName, pool:'global']
		}
		else {
			String[] pathTokens = path.split('/')
			String pool = pathTokens[1]  // first entry will be blank
			////println "$zoneID:  $zoneName on $pool"
			zoneMap[zoneID] = [zoneID:zoneID, name:zoneName, pool:pool]
		}
	}
	else {
		//println "ERROR:  invalid number of tokens in following line:"
		//println line
	}
}


// List all VirtualBox VMs and pools
// This assumes that the VM disk paths match the VM name.  If this is not accurate, then 
// Only lists the mapping for running VMs - change to "list vms" for a full map.
def vboxArrayCmd = "pfexec VBoxManage list vms -l | grep '): /' | grep -v /templates/ | sed 's:^.*\\: /\\([^/]*\\)/[^/]*/\\([0-9]*\\)\\(--[^/]*\\)/.*\$:\\2 \\1 \\2\\3:' | sort | uniq"
// get the full VM name instead - I felt that it would be more reliable to match the ID
//def vboxArrayProc = "pfexec VBoxManage list vms -l | grep '): /' | sed 's:^.*\\: /\\([^/]*\\)/[^/]*/\\([^/]*\\)/.*\$:\\2 \\1:' | sort | uniq".execute()
// Use this API to run complicated commands
def vboxArrayProc = ['/bin/bash', '-c', vboxArrayCmd].execute()
vboxArrayProc.waitFor()

def vboxMap = [:]
if (vboxArrayProc.exitValue() == 0) {
    // build a map
    vboxArrayProc.text.eachLine { line ->
        String[] tokens = line.split(' ')
        if (tokens.length != 3) {
            println "ERROR:  Unparsed VirtualBox disk line:  '$line'"
        }

        String vmID = tokens[0]
        String pool = tokens[1]
        String vmName = tokens[2]
        if (vboxMap.keySet().contains(vmID)) {
            println "WARNING:  VM '$vmID' has multiple pools.  Only '${vboxMap.get(vmID)}' will be used"
        }
        else {
            // match names of zoneMap
            vboxMap.put(vmID, [zoneID:vmID, pool:pool, name:vmName])
        }
    }
}
else {
    // Assume no VirtualBox VMs on this host
    // TODO:  report an error if there are VBoxHeadless instances?
    //println "DEBUG:  Failed to run VirtualBox pool command.  Error code ${vboxArrayProc.exitValue()}, Output:  '${vboxArrayProc.getErrorStream().text}'"
}
// debug
//println "## VM ID, Name, Pool"
//vboxMap.each {k, v ->
//    println "$k, ${v.name}, ${v.pool}"
//}


// Build a map from pid to VM ID for VBoxHeadless commands
// I get the VM ID instead of the full name because the full name is cut off in some examples
def vboxPidCmd = "ps -e -o pid,args | grep VBoxHeadless | grep -v grep | sed 's/^ *\\([0-9]*\\) *.*--comment *\\([0-9]*\\)--.*\$/\\1 \\2/'"
// Use this API to run complicated commands
def vboxPidProc = ['/bin/bash', '-c', vboxPidCmd].execute()
vboxPidProc.waitFor()

def vboxPidMap = [:]
if (vboxPidProc.exitValue() == 0) {
    vboxPidProc.text.eachLine { line ->
        String[] tokens = line.split(' ')
        if (tokens.length != 2) {
            println "ERROR:  Unparsed VBoxHeadless process line:  '$line'"
        }

        String vmID = tokens[0]
        String pool = tokens[1]
        if (vboxPidMap.keySet().contains(vmID)) {
            println "WARNING:  VM '$vmID' has multiple pools.  Only '${vboxPidMap.get(vmID)}' will be used"
        }
        else {
            vboxPidMap.put(vmID, pool)
        }
    }
}
else {
    println "Error on VBoxHeadless PID command:  Error code:  ${vboxPidProc.exitValue()}, Output: '${vboxPidProc.getErrorStream().text}'."
}
// debug
//println "## PID, VM ID"
//vboxPidMap.each {k, v ->
//    println "$k, $v"
//}

//println "Collecting $interval seconds of data"
// Example:  pfexec /opt/DTT/rwtop -C -Z 10 1
rwtopProcess = "pfexec $RWTOP_COMMAND -C -Z $interval 1".execute()
rwtopProcess.waitFor()
if (rwtopProcess.exitValue() != 0) {
	//println "rwtop failed with exit code ${rwtopProcess.exitValue()}"
}

// flat list of processes
def processes = []
// categorized by zone
def byZone = [:]
rwtopProcess.text.eachLine {String line ->
	String[] tokens = line.trim().split(/\s+/)
	String zoneID = tokens[0].trim()
	if (line.trim().isEmpty() || line.startsWith('Tracing')) {
		// ignore blank lines
	}
	else if (tokens.length < 6) {
		//println "ERROR:  invalid number of tokens in following line:"
		//println line
	}
	else if (zoneID.isNumber() && tokens[1]?.isNumber()) {
//		if ('0'.equals(zoneID)) {
//			// ignore non-zone line
//		}
//		else {
			def zone = zoneMap[zoneID]
			String zoneName = zone ? zone['name'] : 'Unknown'
			String pool = zone ? zone['pool'] : 'Unknown'
                        if (zoneID == '0') {
                            // check if this is a VirtualBox instance
                            String pid = tokens[1]
                            String cmd = tokens[3]
                            if (cmd == "VBoxHeadless") {
                                // This is a VirtualBox instance
                                // Try to lookup the VM info, and report as "UnknownVirtualBox" if not found
                                String vmID = vboxPidMap[pid]
                                def vm = vmID ? vboxMap[vmID] : null
                                zoneName = vm ? vm['name'] : 'UnknownVirtualBox'
                                pool = vm ? vm['pool'] : 'UnknownVirtualBox'

                                //println "DEBUG:  Process $pid is VM $vmID ($zoneName, $pool)"

                            }
                            // else - treat as normal process
                        }
			////println "${zoneName}:"
			////println line

			def entry = [full:line, zone:zoneName, pool:pool, pid:tokens[1], cmd:tokens[3], device:'unknown', direction:tokens[4], bytes:tokens[5] ]
			processes << entry

			String key = "$zoneName ($pool)"
			def list = byZone[key]
			if (!list) {
				list = []
				byZone[key] = list
			}
			list << entry
//		}
	}
	else {
		// header line
		//println line
	}
}

// closure to compute a total
def readTotalClosure = {process ->
	long bytes = 0L
	if (process['direction']?.equalsIgnoreCase('r')) {
		// return the bytes value
		if (process['bytes'].isNumber()) {
			bytes = Long.parseLong(process['bytes'])
		}
		else {
			//println "Invalid bytes value for process ${process['pid']}:  '${process['bytes']}"
		}
	}
	// else:  ignore other direction
	bytes
}
def writeTotalClosure = {process ->
	long bytes = 0L
	if (!process['direction']?.equalsIgnoreCase('r')) {  
		// treat anything other than 'r' as 'w'.  TODO: change this?
		// return the bytes value
		if (process['bytes'].isNumber()) {
			bytes = Long.parseLong(process['bytes'])
		}
		else {
			//println "Invalid bytes value for process ${process['pid']}:  '${process['bytes']}"
		}
	}
	// else:  ignore other direction
	bytes
}

def byPool = processes.groupBy {process -> process['pool']}
// organize zones and VMs by pool
def allVMs = new ArrayList<Map>(zoneMap.values())
allVMs.addAll(vboxMap.values())
def allZonesByPool = allVMs.groupBy {zone -> zone['pool']}


byPool.each {pool, poolProcesses ->
	// total the bytes read or written for the pool
	long readTotal = poolProcesses.sum(readTotalClosure)
	long writeTotal = poolProcesses.sum(writeTotalClosure)
	//println "$pool: ${formatNumber(readTotal)} bytes read, ${formatNumber(writeTotal)} bytes written"


	// full list of zones to output
	def zones = allZonesByPool[pool].sort { zone -> zone['name'] }

	// the processes for each zone
	def poolProcessesByZone = poolProcesses.groupBy{process -> process['zone']}

	StringBuilder header = new StringBuilder()
	header.append('Timestamp')
	StringBuilder outputLine = new StringBuilder()
	outputLine.append(timestamp)

	// pool totals - not required, but easy for me to add
	//header.append(", Total Read, Total Written")
	//outputLine.append(", $readTotal, $writeTotal")


	zones.each { zoneEntry ->
		String zone = zoneEntry['name']
		def zoneProcesses = poolProcessesByZone[zone]
		int zoneReadTotal = 0
		int zoneWriteTotal = 0
		if (zoneProcesses) {
			zoneReadTotal = zoneProcesses.sum(readTotalClosure)
			zoneWriteTotal = zoneProcesses.sum(writeTotalClosure)
			//println "$zone: ${formatNumber(zoneReadTotal)} bytes read, ${formatNumber(zoneWriteTotal)} bytes written"
		}
		else {
			// no processes for this zone - it is probably not running
			//println " No running proceses for zone '$zone'."
		}

		header.append(", $zone Read, $zone Written")
		outputLine.append(", $zoneReadTotal, $zoneWriteTotal")
	}
	//println header.toString()
	//println outputLine.toString()

	// write line to file for pool
	File poolFile = filename = new File("$OUTPUT_DIRECTORY/${today}_rwtop_${pool}.csv")
	if (!poolFile.exists()) {
		// write the header
		poolFile << header.toString() + NEWLINE
	}
	poolFile << outputLine.toString() + NEWLINE

	//println ""  // separate pools
}
