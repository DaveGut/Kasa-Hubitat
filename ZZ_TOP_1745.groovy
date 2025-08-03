/*	ZZ-TOP
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== TEST KASA IOT Devices for connectivity to Hubitat
Command:  Test device.  Enter IP address to test.
Logging:

c.	Clean-up past mod markups.
===================================================================================================*/
//	=====	NAMESPACE	============
def nameSpace() { return "davegut" }
//	================================
import groovy.json.JsonSlurper

metadata {
	definition (name: "ZZ-TOP", namespace: nameSpace(), author: "Dave Gutheinz") {
		command "testIP", [[name: "IP (ex: 102.68.50.22)", type: "STRING"]]
	}
}

def installed() { updated() }

def updated() { }

def testIP(devIp) {
	Map logData = [method: "testIP", IP: devIp]
	def action = sendPing(devIp)
	action = sendLanCmd(devIp)
}						   

def sendPing(ip, count = 5) {
	hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ip, count)
	def success = "nullResults"
	def minTime = "n/a"
	def maxTime = "n/a"
	if (pingData) {
		success = (100 * pingData.packetsReceived.toInteger()  / count).toInteger()
		minTime = pingData.rttMin
		maxTime = pingData.rttMax
	}
	def pingResult = [ip: ip, min: minTime, max: maxTime, success: success]
	if (success == 100) {
		log.info "pingResult: ${pingResult}"
	} else if (success == 0) {
		log.warn "pingResult: ${pingResult}"
	} else {
		log.debug "pingResult: ${pingResult}"
	}
	return
}

def sendLanCmd(devIp, command = """{"system":{"get_sysinfo":{}}}""") {
	Map logData = [:]
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${devIp}:9999",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 9,
		 ignoreResponse: false,
		 callback: "parseUdp"])
	try {
		runIn(10, udpTimeout)
		sendHubCommand(myHubAction)
		logData << [status: "UDP Command Sent"]
	} catch (err) {
		logData << [status: "UDP Command Failed", error: err]
	}
}
def udpTimeout() {
	Map logData = [method: "udpTimeout", status:" IOT Device Not found"]
	log.warn logData
}
def parseUdp(message) {
	unschedule("udpTimeout")
	def resp = parseLanMessage(message)
	Map logData = [method: "parseUdp", type: resp.type]
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def clearResp = inputXOR(resp.payload)
		if (clearResp.length() > 1023) {
			if (clearResp.contains("preferred")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
			} else if (clearResp.contains("child_num")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num") -2) + "}}}"
			}
		}
		def cmdResp = new JsonSlurper().parseText(clearResp)
		logData << [cmdResp: cmdResp]
	} else {
		logData << [status: "FAILED"]
	}
	log.info logData
}

private outputXOR(command) {
	def str = ""
	def encrCmd = ""
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}

private inputXOR(encrResponse) {
	String[] strBytes = encrResponse.split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}

