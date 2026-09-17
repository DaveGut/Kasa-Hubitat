library (
	name: "kasaCommunications",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Kasa Communications Methods",
	category: "communications",
	documentationLink: ""
)

import groovy.json.JsonSlurper
import org.json.JSONObject

def getPort() {
	def port = 9999
	if (getDataValue("devicePort")) {
		port = getDataValue("devicePort")
	}
	return port
}

def sendCmd(command) {
	state.lastCommand = command
	logDebug("sendCmd: [ip: ${getDataValue("deviceIP")}, cmd: ${command}]")
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: 3,
		 ignoreResponse: false,
		 callback: "parseUdp"])
	try {
		runIn(5, udpTimeout)
		sendHubCommand(myHubAction)
		state.errorCount += 1
	} catch (e) {
		logWarn("sendLanCmd: [ip: ${getDataValue("deviceIP")}, error: ${e}]")
		handleCommsError("Error", "sendLanCmd")
	}
}

def udpTimeout() {
	handleCommsError("Error", "udpTimeout")
}

def parseUdp(message) {
	unschedule("udpTimeout")
	def resp = parseLanMessage(message)
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def clearResp = inputXOR(resp.payload)
		if (clearResp.length() > 1023) {
			if (clearResp.contains("preferred")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
			} else if (clearResp.contains("child_num")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num") -2) + "}}}"
			} else {
				Map errorData = [method: "parseUdp", error: "udp msg can not be parsed", 
								 respLength: clearResp.length(), clearResp: clearResp]
				logWarn(errorData)
				return
			}
		}
		def cmdResp = new JsonSlurper().parseText(clearResp)
		logDebug("parseUdp: ${cmdResp}")
		handleCommsError("OK")
		distResp(cmdResp)
	} else {
		logWarn("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]")
		handleCommsError("Error", "parseUdp")
	}
}

def handleCommsError(status, msg = "") {
	if (status == "OK") {
		state.errorCount = 0
		if (device.currentValue("commsError") == "true") {
			sendEvent(name: "commsError", value: "false")
			setPollInterval()
		}
	} else {
		Map logData = [method: "handleCommsError", errCount: state.errorCount, msg: msg]
		logData << [count: state.errorCount, command: state.lastCommand]
		switch (state.errorCount) {
			case 1:
			case 2:
				sendCmd(state.lastCommand)
				logDebug(logData)
				break
			case 3:
				Map updConfig = parent.updateConfigurations()
				logData << [updConfig: updConfig.configureEnabled]
				if (updConfig.configureEnabled == true) {
					pauseExecution(10000)
				}
				sendCmd(state.lastCommand)
				logInfo(logData)
				break
			case 4:
				sendEvent(name: "commsError", value: "true")
				runEvery5Minutes("poll")
				logData << [setCommsError: true, status: "pollInterval set to 5 minutes"]
				logData << [TRY: "<b>  CONFIGURE</b>"]
				logWarn(logData)
				break
			default:
				logData << [TRY: "<b>do CONFIGURE</b>"]
				logWarn(logData)
				break
		}
	}
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
