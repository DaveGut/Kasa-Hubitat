/*	Kasa Device Integration Drivers
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/tree/master/KasaDevices/Docs
==	Version 2.4.2
a.	Update to eliminate AltLan and Cloud Comms.
b.	Moved some data to libraries kasaCommon and kasaEnergyMonitor
c.	Clean-up past mod markups.
===================================================================================================*/
//	=====	NAMESPACE	============
def nameSpace() { return "davegut" }
//	================================

metadata {
	definition (name: "Kasa Mono Bulb",
				namespace: nameSpace(),
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Light"
		capability "Switch Level"
		capability "Change Level"
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
	}
	preferences {
		input ("emFunction", "bool", 
			   title: "Enable Energy Monitor", 
			   defaultValue: false)
		if (emFunction) {
			input ("energyPollInt", "enum",
				   title: "Energy Poll Interval (minutes)",
				   options: ["1 minute", "5 minutes", "30 minutes"],
				   defaultValue: "30 minutes")
		}
		commonPrefs()
	}
}

def installed() {
	def instStatus= installCommon()
	logInfo("installed: ${instStatus}")
}

def updated() {
	def updStatus = updateCommon()
	updStatus << [emFunction: setupEmFunction()]
	def transTime = transition_Time
	if (transTime == null) {
		transTime = 1
		device.updateSetting("transition_Time", [type:"number", value: 1])
	}
	updStatus << [transition_Time: transTime]
	logInfo("updated: ${updStatus}")
	refresh()
}

def distResp(response) {
	if (response.system) {
		if (response.system.get_sysinfo) {
			setSysInfo(response.system.get_sysinfo)
			if (nameSync == "device") {
				updateName(response.system.get_sysinfo)
			}
		} else if (response.system.set_dev_alias) {
			updateName(response.system.set_dev_alias)
		} else {
			logWarn("distResp: Unhandled response = ${response}")
		}
	} else if (response["smartlife.iot.smartbulb.lightingservice"]) {
		setSysInfo([light_state:response["smartlife.iot.smartbulb.lightingservice"].transition_light_state])
	} else if (response["smartlife.iot.common.emeter"]) {
		distEmeter(response["smartlife.iot.common.emeter"])
	} else if (response["smartlife.iot.common.cloud"]) {
		setBindUnbind(response["smartlife.iot.common.cloud"])
	} else if (response["smartlife.iot.common.system"]) {
		if (response["smartlife.iot.common.system"].reboot) {
			logWarn("distResp: Rebooting device")
		} else {
			logDebug("distResp: Unhandled reboot response: ${response}")
		}
	} else {
		logWarn("distResp: Unhandled response = ${response}")
	}
}

def setSysInfo(status) {
	def lightStatus = status.light_state
	if (state.lastStatus != lightStatus) {
		state.lastStatus = lightStatus
		logInfo("setSysinfo: [status: ${lightStatus}]")
		def onOff
		int level
		if (lightStatus.on_off == 0) {
			onOff = "off"
		} else {
			onOff = "on"
			level = lightStatus.brightness
		}
		sendEvent(name: "switch", value: onOff, type: "digital")
		if (device.currentValue("level") != level) {
			sendEvent(name: "level", value: level)
		}
	}
	runIn(1, getPower)
}







// ~~~~~ start include (320) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa Device Common Methods", // library marker davegut.kasaCommon, line 5
	category: "utilities", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

def getVer() { return "" } // library marker davegut.kasaCommon, line 10

capability "Switch" // library marker davegut.kasaCommon, line 12
capability "Refresh" // library marker davegut.kasaCommon, line 13
capability "Actuator" // library marker davegut.kasaCommon, line 14
capability "Configuration" // library marker davegut.kasaCommon, line 15
command "setPollInterval", [[ // library marker davegut.kasaCommon, line 16
	name: "Poll Interval in seconds", // library marker davegut.kasaCommon, line 17
	constraints: ["default", "5 seconds", "10 seconds", "15 seconds", // library marker davegut.kasaCommon, line 18
				  "30 seconds", "1 minute", "5 minutes",  "10 minutes", // library marker davegut.kasaCommon, line 19
				  "30 minutes"], // library marker davegut.kasaCommon, line 20
	type: "ENUM"]] // library marker davegut.kasaCommon, line 21
attribute "commsError", "string" // library marker davegut.kasaCommon, line 22

def commonPrefs() { // library marker davegut.kasaCommon, line 24
	input ("infoLog", "bool", title: "Enable descriptionText logging",  // library marker davegut.kasaCommon, line 25
		   defaultValue: true) // library marker davegut.kasaCommon, line 26
	input ("logEnable", "bool", title: "Enable debug logging", defaultValue: false) // library marker davegut.kasaCommon, line 27
	input ("nameSync", "enum", title: "Synchronize Names", defaultValue: "none", // library marker davegut.kasaCommon, line 28
		   options: ["none": "Don't synchronize", // library marker davegut.kasaCommon, line 29
					 "device" : "Kasa device name master", // library marker davegut.kasaCommon, line 30
					 "Hubitat" : "Hubitat label master"]) // library marker davegut.kasaCommon, line 31
	input ("manualIp", "string", title: "Manual IP Update <b>[Caution]</b>", // library marker davegut.kasaCommon, line 32
		   defaultValue: getDataValue("deviceIP")) // library marker davegut.kasaCommon, line 33
	input ("manualPort", "string", title: "Manual Port Update <b>[Caution]</b>", // library marker davegut.kasaCommon, line 34
		   defaultValue: getDataValue("devicePort")) // library marker davegut.kasaCommon, line 35
	input ("rebootDev", "bool", title: "Reboot device <b>[Caution]</b>", // library marker davegut.kasaCommon, line 36
		   defaultValue: false) // library marker davegut.kasaCommon, line 37
} // library marker davegut.kasaCommon, line 38

def installCommon() { // library marker davegut.kasaCommon, line 40
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 41
	state.errorCount = 0 // library marker davegut.kasaCommon, line 42
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 43
	runIn(5, updated) // library marker davegut.kasaCommon, line 44
	return [commsError: "false", pollInterval: "30 minutes", errorCount: 0] // library marker davegut.kasaCommon, line 45
} // library marker davegut.kasaCommon, line 46

def updateCommon() { // library marker davegut.kasaCommon, line 48
	//	Remove Kasa Cloud Access // library marker davegut.kasaCommon, line 49
	device.removeSetting("useCloud") // library marker davegut.kasaCommon, line 50
	device.removeSetting("bind") // library marker davegut.kasaCommon, line 51
	device.removeSetting("altLan") // library marker davegut.kasaCommon, line 52

	def updStatus = [:] // library marker davegut.kasaCommon, line 54
	if (rebootDev) { // library marker davegut.kasaCommon, line 55
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.kasaCommon, line 56
		return updStatus // library marker davegut.kasaCommon, line 57
	} // library marker davegut.kasaCommon, line 58
	unschedule() // library marker davegut.kasaCommon, line 59
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 60
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 61
	} // library marker davegut.kasaCommon, line 62
	if (logEnable) { runIn(1800, debugLogOff) } // library marker davegut.kasaCommon, line 63
	updStatus << [infoLog: infoLog, logEnable: logEnable] // library marker davegut.kasaCommon, line 64
	if (manualIp != getDataValue("deviceIP")) { // library marker davegut.kasaCommon, line 65
		updateDataValue("deviceIP", manualIp) // library marker davegut.kasaCommon, line 66
		updStatus << [ipUpdate: manualIp] // library marker davegut.kasaCommon, line 67
	} // library marker davegut.kasaCommon, line 68
	if (manualPort != getDataValue("devicePort")) { // library marker davegut.kasaCommon, line 69
		updateDataValue("devicePort", manualPort) // library marker davegut.kasaCommon, line 70
		updStatus << [portUpdate: manualPort] // library marker davegut.kasaCommon, line 71
	} // library marker davegut.kasaCommon, line 72
	state.errorCount = 0 // library marker davegut.kasaCommon, line 73
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 74

	def pollInterval = state.pollInterval // library marker davegut.kasaCommon, line 76
	if (pollInterval == null) { pollInterval = "30 minutes" } // library marker davegut.kasaCommon, line 77
	state.pollInterval = pollInterval // library marker davegut.kasaCommon, line 78
	runIn(15, setPollInterval) // library marker davegut.kasaCommon, line 79
	updStatus << [pollInterval: pollInterval] // library marker davegut.kasaCommon, line 80
	if (emFunction) { // library marker davegut.kasaCommon, line 81
		scheduleEnergyAttrs() // library marker davegut.kasaCommon, line 82
		state.getEnergy = "This Month" // library marker davegut.kasaCommon, line 83
		updStatus << [emFunction: "scheduled"] // library marker davegut.kasaCommon, line 84
	} // library marker davegut.kasaCommon, line 85
	return updStatus // library marker davegut.kasaCommon, line 86
} // library marker davegut.kasaCommon, line 87

def configure() { // library marker davegut.kasaCommon, line 89
	Map logData = [method: "configure"] // library marker davegut.kasaCommon, line 90
	logInfo logData // library marker davegut.kasaCommon, line 91
	if (parent == null) { // library marker davegut.kasaCommon, line 92
		logData << [error: "No Parent App.  Aborted"] // library marker davegut.kasaCommon, line 93
		logWarn(logData) // library marker davegut.kasaCommon, line 94
	} else { // library marker davegut.kasaCommon, line 95
		logData << [appData: parent.updateConfigurations()] // library marker davegut.kasaCommon, line 96
		logInfo(logData) // library marker davegut.kasaCommon, line 97
	} // library marker davegut.kasaCommon, line 98
} // library marker davegut.kasaCommon, line 99

def refresh() { sendCmd("""{"system":{"get_sysinfo":{}}}""") } // library marker davegut.kasaCommon, line 101

def poll() { sendCmd("""{"system":{"get_sysinfo":{}}}""") } // library marker davegut.kasaCommon, line 103

def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 105
	if (interval == "default" || interval == "off" || interval == null) { // library marker davegut.kasaCommon, line 106
		interval = "30 minutes" // library marker davegut.kasaCommon, line 107
	} else if (useCloud || altLan || getDataValue("altComms") == "true") { // library marker davegut.kasaCommon, line 108
		if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 109
			interval = "1 minute" // library marker davegut.kasaCommon, line 110
			logWarn("setPollInterval: Device using Cloud or rawSocket.  Poll interval reset to minimum value of 1 minute.") // library marker davegut.kasaCommon, line 111
		} // library marker davegut.kasaCommon, line 112
	} // library marker davegut.kasaCommon, line 113
	state.pollInterval = interval // library marker davegut.kasaCommon, line 114
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 115
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 116
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 117
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 118
		logWarn("setPollInterval: Polling intervals of less than one minute " + // library marker davegut.kasaCommon, line 119
				"can take high resources and may impact hub performance.") // library marker davegut.kasaCommon, line 120
	} else { // library marker davegut.kasaCommon, line 121
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 122
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 123
	} // library marker davegut.kasaCommon, line 124
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 125
	return interval // library marker davegut.kasaCommon, line 126
} // library marker davegut.kasaCommon, line 127

def rebootDevice() { // library marker davegut.kasaCommon, line 129
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 130
	reboot() // library marker davegut.kasaCommon, line 131
	pauseExecution(10000) // library marker davegut.kasaCommon, line 132
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 133
} // library marker davegut.kasaCommon, line 134

def xxxxxxxxxbindUnbind() { // library marker davegut.kasaCommon, line 136
	def message // library marker davegut.kasaCommon, line 137
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 138
		device.updateSetting("bind", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 139
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 140
		message = "No deviceIp.  Bind not modified." // library marker davegut.kasaCommon, line 141
	} else if (bind == null ||  getDataValue("feature") == "lightStrip") { // library marker davegut.kasaCommon, line 142
		message = "Getting current bind state" // library marker davegut.kasaCommon, line 143
		getBind() // library marker davegut.kasaCommon, line 144
	} else if (bind == true) { // library marker davegut.kasaCommon, line 145
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 146
			message = "Username/pwd not set." // library marker davegut.kasaCommon, line 147
			getBind() // library marker davegut.kasaCommon, line 148
		} else { // library marker davegut.kasaCommon, line 149
			message = "Binding device to the Kasa Cloud." // library marker davegut.kasaCommon, line 150
			setBind(parent.userName, parent.userPassword) // library marker davegut.kasaCommon, line 151
		} // library marker davegut.kasaCommon, line 152
	} else if (bind == false) { // library marker davegut.kasaCommon, line 153
		message = "Unbinding device from the Kasa Cloud." // library marker davegut.kasaCommon, line 154
		setUnbind() // library marker davegut.kasaCommon, line 155
	} // library marker davegut.kasaCommon, line 156
	pauseExecution(5000) // library marker davegut.kasaCommon, line 157
	return message // library marker davegut.kasaCommon, line 158
} // library marker davegut.kasaCommon, line 159

def syncName() { // library marker davegut.kasaCommon, line 161
	def message // library marker davegut.kasaCommon, line 162
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 163
		message = "Hubitat Label Sync" // library marker davegut.kasaCommon, line 164
		setDeviceAlias(device.getLabel()) // library marker davegut.kasaCommon, line 165
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 166
		message = "Device Alias Sync" // library marker davegut.kasaCommon, line 167
	} else { // library marker davegut.kasaCommon, line 168
		message = "Not Syncing" // library marker davegut.kasaCommon, line 169
	} // library marker davegut.kasaCommon, line 170
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.kasaCommon, line 171
	return message // library marker davegut.kasaCommon, line 172
} // library marker davegut.kasaCommon, line 173

def updateName(response) { // library marker davegut.kasaCommon, line 175
	def name = device.getLabel() // library marker davegut.kasaCommon, line 176
	if (response.alias) { // library marker davegut.kasaCommon, line 177
		name = response.alias // library marker davegut.kasaCommon, line 178
		device.setLabel(name) // library marker davegut.kasaCommon, line 179
	} else if (response.err_code != 0) { // library marker davegut.kasaCommon, line 180
		def msg = "updateName: Name Sync from Hubitat to Device returned an error." // library marker davegut.kasaCommon, line 181
		msg+= "\n\rNote: <b>Some devices do not support syncing name from the hub.</b>\n\r" // library marker davegut.kasaCommon, line 182
		logWarn(msg) // library marker davegut.kasaCommon, line 183
		return // library marker davegut.kasaCommon, line 184
	} // library marker davegut.kasaCommon, line 185
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}") // library marker davegut.kasaCommon, line 186
} // library marker davegut.kasaCommon, line 187

def getSysinfo() { // library marker davegut.kasaCommon, line 189
	sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 190
} // library marker davegut.kasaCommon, line 191

def sysService() { // library marker davegut.kasaCommon, line 193
	def service = "system" // library marker davegut.kasaCommon, line 194
	def feature = getDataValue("feature") // library marker davegut.kasaCommon, line 195
	if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaCommon, line 196
		service = "smartlife.iot.common.system" // library marker davegut.kasaCommon, line 197
	} // library marker davegut.kasaCommon, line 198
	return service // library marker davegut.kasaCommon, line 199
} // library marker davegut.kasaCommon, line 200

def reboot() { // library marker davegut.kasaCommon, line 202
	sendCmd("""{"${sysService()}":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 203
} // library marker davegut.kasaCommon, line 204

def setDeviceAlias(newAlias) { // library marker davegut.kasaCommon, line 206
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 207
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 208
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 209
	} else { // library marker davegut.kasaCommon, line 210
		sendCmd("""{"${sysService()}":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 211
	} // library marker davegut.kasaCommon, line 212
} // library marker davegut.kasaCommon, line 213

def updateAttr(attr, value) { // library marker davegut.kasaCommon, line 215
	if (device.currentValue(attr) != value) { // library marker davegut.kasaCommon, line 216
		sendEvent(name: attr, value: value) // library marker davegut.kasaCommon, line 217
	} // library marker davegut.kasaCommon, line 218
} // library marker davegut.kasaCommon, line 219


// ~~~~~ end include (320) davegut.kasaCommon ~~~~~

// ~~~~~ start include (321) davegut.kasaCommunications ~~~~~
library ( // library marker davegut.kasaCommunications, line 1
	name: "kasaCommunications", // library marker davegut.kasaCommunications, line 2
	namespace: "davegut", // library marker davegut.kasaCommunications, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommunications, line 4
	description: "Kasa Communications Methods", // library marker davegut.kasaCommunications, line 5
	category: "communications", // library marker davegut.kasaCommunications, line 6
	documentationLink: "" // library marker davegut.kasaCommunications, line 7
) // library marker davegut.kasaCommunications, line 8

import groovy.json.JsonSlurper // library marker davegut.kasaCommunications, line 10
import org.json.JSONObject // library marker davegut.kasaCommunications, line 11

def getPort() { // library marker davegut.kasaCommunications, line 13
	def port = 9999 // library marker davegut.kasaCommunications, line 14
	if (getDataValue("devicePort")) { // library marker davegut.kasaCommunications, line 15
		port = getDataValue("devicePort") // library marker davegut.kasaCommunications, line 16
	} // library marker davegut.kasaCommunications, line 17
	return port // library marker davegut.kasaCommunications, line 18
} // library marker davegut.kasaCommunications, line 19

def sendCmd(command) { // library marker davegut.kasaCommunications, line 21
	state.lastCommand = command // library marker davegut.kasaCommunications, line 22
	logDebug("sendCmd: [ip: ${getDataValue("deviceIP")}, cmd: ${command}]") // library marker davegut.kasaCommunications, line 23
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 24
		outputXOR(command), // library marker davegut.kasaCommunications, line 25
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 26
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 27
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.kasaCommunications, line 28
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 29
		 parseWarning: true, // library marker davegut.kasaCommunications, line 30
		 timeout: 3, // library marker davegut.kasaCommunications, line 31
		 ignoreResponse: false, // library marker davegut.kasaCommunications, line 32
		 callback: "parseUdp"]) // library marker davegut.kasaCommunications, line 33
	try { // library marker davegut.kasaCommunications, line 34
		runIn(5, udpTimeout) // library marker davegut.kasaCommunications, line 35
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 36
		state.errorCount += 1 // library marker davegut.kasaCommunications, line 37
	} catch (e) { // library marker davegut.kasaCommunications, line 38
		logWarn("sendLanCmd: [ip: ${getDataValue("deviceIP")}, error: ${e}]") // library marker davegut.kasaCommunications, line 39
		handleCommsError("Error", "sendLanCmd") // library marker davegut.kasaCommunications, line 40
	} // library marker davegut.kasaCommunications, line 41
} // library marker davegut.kasaCommunications, line 42

def udpTimeout() { // library marker davegut.kasaCommunications, line 44
	handleCommsError("Error", "udpTimeout") // library marker davegut.kasaCommunications, line 45
} // library marker davegut.kasaCommunications, line 46

def parseUdp(message) { // library marker davegut.kasaCommunications, line 48
	unschedule("udpTimeout") // library marker davegut.kasaCommunications, line 49
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 50
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 51
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 52
		if (clearResp.length() > 1023) { // library marker davegut.kasaCommunications, line 53
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 54
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 55
			} else if (clearResp.contains("child_num")) { // library marker davegut.kasaCommunications, line 56
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num") -2) + "}}}" // library marker davegut.kasaCommunications, line 57
			} else { // library marker davegut.kasaCommunications, line 58
				Map errorData = [method: "parseUdp", error: "udp msg can not be parsed",  // library marker davegut.kasaCommunications, line 59
								 respLength: clearResp.length(), clearResp: clearResp] // library marker davegut.kasaCommunications, line 60
				logWarn(errorData) // library marker davegut.kasaCommunications, line 61
				return // library marker davegut.kasaCommunications, line 62
			} // library marker davegut.kasaCommunications, line 63
		} // library marker davegut.kasaCommunications, line 64
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 65
		logDebug("parseUdp: ${cmdResp}") // library marker davegut.kasaCommunications, line 66
		handleCommsError("OK") // library marker davegut.kasaCommunications, line 67
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 68
	} else { // library marker davegut.kasaCommunications, line 69
		logWarn("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker davegut.kasaCommunications, line 70
		handleCommsError("Error", "parseUdp") // library marker davegut.kasaCommunications, line 71
	} // library marker davegut.kasaCommunications, line 72
} // library marker davegut.kasaCommunications, line 73

def handleCommsError(status, msg = "") { // library marker davegut.kasaCommunications, line 75
	if (status == "OK") { // library marker davegut.kasaCommunications, line 76
		state.errorCount = 0 // library marker davegut.kasaCommunications, line 77
		if (device.currentValue("commsError") == "true") { // library marker davegut.kasaCommunications, line 78
			sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommunications, line 79
			setPollInterval() // library marker davegut.kasaCommunications, line 80
		} // library marker davegut.kasaCommunications, line 81
	} else { // library marker davegut.kasaCommunications, line 82
		Map logData = [method: "handleCommsError", errCount: state.errorCount, msg: msg] // library marker davegut.kasaCommunications, line 83
		logData << [count: state.errorCount, command: state.lastCommand] // library marker davegut.kasaCommunications, line 84
		switch (state.errorCount) { // library marker davegut.kasaCommunications, line 85
			case 1: // library marker davegut.kasaCommunications, line 86
			case 2: // library marker davegut.kasaCommunications, line 87
				sendCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 88
				logDebug(logData) // library marker davegut.kasaCommunications, line 89
				break // library marker davegut.kasaCommunications, line 90
			case 3: // library marker davegut.kasaCommunications, line 91
				Map updConfig = parent.updateConfigurations() // library marker davegut.kasaCommunications, line 92
				logData << [updConfig: updConfig.configureEnabled] // library marker davegut.kasaCommunications, line 93
				if (updConfig.configureEnabled == true) { // library marker davegut.kasaCommunications, line 94
					pauseExecution(10000) // library marker davegut.kasaCommunications, line 95
				} // library marker davegut.kasaCommunications, line 96
				sendCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 97
				logInfo(logData) // library marker davegut.kasaCommunications, line 98
				break // library marker davegut.kasaCommunications, line 99
			case 4: // library marker davegut.kasaCommunications, line 100
				sendEvent(name: "commsError", value: "true") // library marker davegut.kasaCommunications, line 101
				runEvery5Minutes("poll") // library marker davegut.kasaCommunications, line 102
				logData << [setCommsError: true, status: "pollInterval set to 5 minutes"] // library marker davegut.kasaCommunications, line 103
				logData << [TRY: "<b>  CONFIGURE</b>"] // library marker davegut.kasaCommunications, line 104
				logWarn(logData) // library marker davegut.kasaCommunications, line 105
				break // library marker davegut.kasaCommunications, line 106
			default: // library marker davegut.kasaCommunications, line 107
				logData << [TRY: "<b>do CONFIGURE</b>"] // library marker davegut.kasaCommunications, line 108
				logWarn(logData) // library marker davegut.kasaCommunications, line 109
				break // library marker davegut.kasaCommunications, line 110
		} // library marker davegut.kasaCommunications, line 111
	} // library marker davegut.kasaCommunications, line 112
} // library marker davegut.kasaCommunications, line 113

private outputXOR(command) { // library marker davegut.kasaCommunications, line 115
	def str = "" // library marker davegut.kasaCommunications, line 116
	def encrCmd = "" // library marker davegut.kasaCommunications, line 117
 	def key = 0xAB // library marker davegut.kasaCommunications, line 118
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 119
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 120
		key = str // library marker davegut.kasaCommunications, line 121
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 122
	} // library marker davegut.kasaCommunications, line 123
   	return encrCmd // library marker davegut.kasaCommunications, line 124
} // library marker davegut.kasaCommunications, line 125

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 127
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 128
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 129
	def key = 0xAB // library marker davegut.kasaCommunications, line 130
	def nextKey // library marker davegut.kasaCommunications, line 131
	byte[] XORtemp // library marker davegut.kasaCommunications, line 132
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 133
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 134
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 135
		key = nextKey // library marker davegut.kasaCommunications, line 136
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 137
	} // library marker davegut.kasaCommunications, line 138
	return cmdResponse // library marker davegut.kasaCommunications, line 139
} // library marker davegut.kasaCommunications, line 140

// ~~~~~ end include (321) davegut.kasaCommunications ~~~~~

// ~~~~~ start include (325) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8

def label() { // library marker davegut.Logging, line 10
	if (device) { return device.displayName }  // library marker davegut.Logging, line 11
	else { return app.getLabel() } // library marker davegut.Logging, line 12
} // library marker davegut.Logging, line 13

def listAttributes() { // library marker davegut.Logging, line 15
	def attrData = device.getCurrentStates() // library marker davegut.Logging, line 16
	Map attrs = [:] // library marker davegut.Logging, line 17
	attrData.each { // library marker davegut.Logging, line 18
		attrs << ["${it.name}": it.value] // library marker davegut.Logging, line 19
	} // library marker davegut.Logging, line 20
	return attrs // library marker davegut.Logging, line 21
} // library marker davegut.Logging, line 22

def setLogsOff() { // library marker davegut.Logging, line 24
	def logData = [logEnable: logEnable] // library marker davegut.Logging, line 25
	if (logEnable) { // library marker davegut.Logging, line 26
		runIn(1800, debugLogOff) // library marker davegut.Logging, line 27
		logData << [debugLogOff: "scheduled"] // library marker davegut.Logging, line 28
	} // library marker davegut.Logging, line 29
	return logData // library marker davegut.Logging, line 30
} // library marker davegut.Logging, line 31

def logTrace(msg){ log.trace "${label()} ${getVer()}: ${msg}" } // library marker davegut.Logging, line 33

def logInfo(msg) {  // library marker davegut.Logging, line 35
	if (infoLog) { log.info "${label()} ${getVer()}: ${msg}" } // library marker davegut.Logging, line 36
} // library marker davegut.Logging, line 37

def debugLogOff() { // library marker davegut.Logging, line 39
	if (device) { // library marker davegut.Logging, line 40
		device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 41
	} else { // library marker davegut.Logging, line 42
		app.updateSetting("logEnable", false) // library marker davegut.Logging, line 43
	} // library marker davegut.Logging, line 44
	logInfo("debugLogOff") // library marker davegut.Logging, line 45
} // library marker davegut.Logging, line 46

def logDebug(msg) { // library marker davegut.Logging, line 48
	if (logEnable) { log.debug "${label()} ${getVer()}: ${msg}" } // library marker davegut.Logging, line 49
} // library marker davegut.Logging, line 50

def logWarn(msg) { log.warn "${label()} ${getVer()}: ${msg}" } // library marker davegut.Logging, line 52

def logError(msg) { log.error "${label()} ${getVer()}}: ${msg}" } // library marker davegut.Logging, line 54

// ~~~~~ end include (325) davegut.Logging ~~~~~

// ~~~~~ start include (323) davegut.kasaLights ~~~~~
library ( // library marker davegut.kasaLights, line 1
	name: "kasaLights", // library marker davegut.kasaLights, line 2
	namespace: "davegut", // library marker davegut.kasaLights, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaLights, line 4
	description: "Kasa Bulb and Light Common Methods", // library marker davegut.kasaLights, line 5
	category: "utilities", // library marker davegut.kasaLights, line 6
	documentationLink: "" // library marker davegut.kasaLights, line 7
) // library marker davegut.kasaLights, line 8

def on() { setLightOnOff(1, transition_Time) } // library marker davegut.kasaLights, line 10

def off() { setLightOnOff(0, transition_Time) } // library marker davegut.kasaLights, line 12

def setLevel(level, transTime = transition_Time) { // library marker davegut.kasaLights, line 14
	setLightLevel(level, transTime) // library marker davegut.kasaLights, line 15
} // library marker davegut.kasaLights, line 16

def startLevelChange(direction) { // library marker davegut.kasaLights, line 18
	unschedule(levelUp) // library marker davegut.kasaLights, line 19
	unschedule(levelDown) // library marker davegut.kasaLights, line 20
	if (direction == "up") { levelUp() } // library marker davegut.kasaLights, line 21
	else { levelDown() } // library marker davegut.kasaLights, line 22
} // library marker davegut.kasaLights, line 23

def stopLevelChange() { // library marker davegut.kasaLights, line 25
	unschedule(levelUp) // library marker davegut.kasaLights, line 26
	unschedule(levelDown) // library marker davegut.kasaLights, line 27
} // library marker davegut.kasaLights, line 28

def levelUp() { // library marker davegut.kasaLights, line 30
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.kasaLights, line 31
	if (curLevel == 100) { return } // library marker davegut.kasaLights, line 32
	def newLevel = curLevel + 4 // library marker davegut.kasaLights, line 33
	if (newLevel > 100) { newLevel = 100 } // library marker davegut.kasaLights, line 34
	setLevel(newLevel, 0) // library marker davegut.kasaLights, line 35
	runIn(1, levelUp) // library marker davegut.kasaLights, line 36
} // library marker davegut.kasaLights, line 37

def levelDown() { // library marker davegut.kasaLights, line 39
	def curLevel = device.currentValue("level").toInteger() // library marker davegut.kasaLights, line 40
	if (curLevel == 0 || device.currentValue("switch") == "off") { return } // library marker davegut.kasaLights, line 41
	def newLevel = curLevel - 4 // library marker davegut.kasaLights, line 42
	if (newLevel < 0) { off() } // library marker davegut.kasaLights, line 43
	else { // library marker davegut.kasaLights, line 44
		setLevel(newLevel, 0) // library marker davegut.kasaLights, line 45
		runIn(1, levelDown) // library marker davegut.kasaLights, line 46
	} // library marker davegut.kasaLights, line 47
} // library marker davegut.kasaLights, line 48

def service() { // library marker davegut.kasaLights, line 50
	def service = "smartlife.iot.smartbulb.lightingservice" // library marker davegut.kasaLights, line 51
	if (getDataValue("feature") == "lightStrip") { service = "smartlife.iot.lightStrip" } // library marker davegut.kasaLights, line 52
	return service // library marker davegut.kasaLights, line 53
} // library marker davegut.kasaLights, line 54

def method() { // library marker davegut.kasaLights, line 56
	def method = "transition_light_state" // library marker davegut.kasaLights, line 57
	if (getDataValue("feature") == "lightStrip") { method = "set_light_state" } // library marker davegut.kasaLights, line 58
	return method // library marker davegut.kasaLights, line 59
} // library marker davegut.kasaLights, line 60

def checkTransTime(transTime) { // library marker davegut.kasaLights, line 62
	if (transTime == null || transTime < 0) { transTime = 0 } // library marker davegut.kasaLights, line 63
	transTime = 1000 * transTime.toInteger() // library marker davegut.kasaLights, line 64
	if (transTime > 8000) { transTime = 8000 } // library marker davegut.kasaLights, line 65
	return transTime // library marker davegut.kasaLights, line 66
} // library marker davegut.kasaLights, line 67

def checkLevel(level) { // library marker davegut.kasaLights, line 69
	if (level == null || level < 0) { // library marker davegut.kasaLights, line 70
		level = device.currentValue("level") // library marker davegut.kasaLights, line 71
		logWarn("checkLevel: Entered level null or negative. Level set to ${level}") // library marker davegut.kasaLights, line 72
	} else if (level > 100) { // library marker davegut.kasaLights, line 73
		level = 100 // library marker davegut.kasaLights, line 74
		logWarn("checkLevel: Entered level > 100.  Level set to ${level}") // library marker davegut.kasaLights, line 75
	} // library marker davegut.kasaLights, line 76
	return level // library marker davegut.kasaLights, line 77
} // library marker davegut.kasaLights, line 78

def setLightOnOff(onOff, transTime = 0) { // library marker davegut.kasaLights, line 80
	state.eventType = "digital" // library marker davegut.kasaLights, line 81
	transTime = checkTransTime(transTime) // library marker davegut.kasaLights, line 82
	sendCmd("""{"${service()}":{"${method()}":{"on_off":${onOff},""" + // library marker davegut.kasaLights, line 83
			""""transition_period":${transTime}}}}""") // library marker davegut.kasaLights, line 84
} // library marker davegut.kasaLights, line 85

def setLightLevel(level, transTime = 0) { // library marker davegut.kasaLights, line 87
	state.eventType = "digital" // library marker davegut.kasaLights, line 88
	level = checkLevel(level) // library marker davegut.kasaLights, line 89
	if (level == 0) { // library marker davegut.kasaLights, line 90
		setLightOnOff(0, transTime) // library marker davegut.kasaLights, line 91
	} else { // library marker davegut.kasaLights, line 92
		transTime = checkTransTime(transTime) // library marker davegut.kasaLights, line 93
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" + // library marker davegut.kasaLights, line 94
				""""brightness":${level},"transition_period":${transTime}}}}""") // library marker davegut.kasaLights, line 95
	} // library marker davegut.kasaLights, line 96
} // library marker davegut.kasaLights, line 97

// ~~~~~ end include (323) davegut.kasaLights ~~~~~

// ~~~~~ start include (322) davegut.kasaEnergyMonitor ~~~~~
library ( // library marker davegut.kasaEnergyMonitor, line 1
	name: "kasaEnergyMonitor", // library marker davegut.kasaEnergyMonitor, line 2
	namespace: "davegut", // library marker davegut.kasaEnergyMonitor, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaEnergyMonitor, line 4
	description: "Kasa Device Energy Monitor Methods", // library marker davegut.kasaEnergyMonitor, line 5
	category: "energyMonitor", // library marker davegut.kasaEnergyMonitor, line 6
	documentationLink: "" // library marker davegut.kasaEnergyMonitor, line 7
) // library marker davegut.kasaEnergyMonitor, line 8

capability "Power Meter" // library marker davegut.kasaEnergyMonitor, line 10
capability "Energy Meter" // library marker davegut.kasaEnergyMonitor, line 11
attribute "currMonthTotal", "number" // library marker davegut.kasaEnergyMonitor, line 12
attribute "currMonthAvg", "number" // library marker davegut.kasaEnergyMonitor, line 13
attribute "lastMonthTotal", "number" // library marker davegut.kasaEnergyMonitor, line 14
attribute "lastMonthAvg", "number" // library marker davegut.kasaEnergyMonitor, line 15

def emPrefs() { // library marker davegut.kasaEnergyMonitor, line 17
	if (getDataValue("feature") == "TIM:ENE") { // library marker davegut.kasaEnergyMonitor, line 18
		input ("emFunction", "bool", title: "Enable Energy Monitor", // library marker davegut.kasaEnergyMonitor, line 19
			   defaultValue: false) // library marker davegut.kasaEnergyMonitor, line 20
		if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 21
			input ("energyPollInt", "enum", title: "Energy Poll Interval (minutes)", // library marker davegut.kasaEnergyMonitor, line 22
				   options: ["1 minute", "5 minutes", "30 minutes"],  // library marker davegut.kasaEnergyMonitor, line 23
				   defaultValue: "30 minutes") // library marker davegut.kasaEnergyMonitor, line 24
			} // library marker davegut.kasaEnergyMonitor, line 25
		} // library marker davegut.kasaEnergyMonitor, line 26
} // library marker davegut.kasaEnergyMonitor, line 27

def setupEmFunction() { // library marker davegut.kasaEnergyMonitor, line 29
	if (emFunction && device.currentValue("currMonthTotal") > 0) { // library marker davegut.kasaEnergyMonitor, line 30
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 31
		return "Continuing EM Function" // library marker davegut.kasaEnergyMonitor, line 32
	} else if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 33
		zeroizeEnergyAttrs() // library marker davegut.kasaEnergyMonitor, line 34
		state.response = "" // library marker davegut.kasaEnergyMonitor, line 35
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 36
		//	Run order / delay is critical for successful operation. // library marker davegut.kasaEnergyMonitor, line 37
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 38
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 39
		return "Initialized" // library marker davegut.kasaEnergyMonitor, line 40
	} else if (emFunction && device.currentValue("power") != null) { // library marker davegut.kasaEnergyMonitor, line 41
		//	for power != null, EM had to be enabled at one time.  Set values to 0. // library marker davegut.kasaEnergyMonitor, line 42
		zeroizeEnergyAttrs() // library marker davegut.kasaEnergyMonitor, line 43
		state.remove("getEnergy") // library marker davegut.kasaEnergyMonitor, line 44
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 45
	} else { // library marker davegut.kasaEnergyMonitor, line 46
		return "Not initialized" // library marker davegut.kasaEnergyMonitor, line 47
	} // library marker davegut.kasaEnergyMonitor, line 48
} // library marker davegut.kasaEnergyMonitor, line 49

def scheduleEnergyAttrs() { // library marker davegut.kasaEnergyMonitor, line 51
	schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 52
	schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 53
	switch(energyPollInt) { // library marker davegut.kasaEnergyMonitor, line 54
		case "1 minute": // library marker davegut.kasaEnergyMonitor, line 55
			runEvery1Minute(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 56
			break // library marker davegut.kasaEnergyMonitor, line 57
		case "5 minutes": // library marker davegut.kasaEnergyMonitor, line 58
			runEvery5Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 59
			break // library marker davegut.kasaEnergyMonitor, line 60
		default: // library marker davegut.kasaEnergyMonitor, line 61
			runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 62
	} // library marker davegut.kasaEnergyMonitor, line 63
} // library marker davegut.kasaEnergyMonitor, line 64

def zeroizeEnergyAttrs() { // library marker davegut.kasaEnergyMonitor, line 66
	sendEvent(name: "power", value: 0, unit: "W") // library marker davegut.kasaEnergyMonitor, line 67
	sendEvent(name: "energy", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 68
	sendEvent(name: "currMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 69
	sendEvent(name: "currMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 70
	sendEvent(name: "lastMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 71
	sendEvent(name: "lastMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 72
} // library marker davegut.kasaEnergyMonitor, line 73

def getDate() { // library marker davegut.kasaEnergyMonitor, line 75
	def currDate = new Date() // library marker davegut.kasaEnergyMonitor, line 76
	int year = currDate.format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 77
	int month = currDate.format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 78
	int day = currDate.format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 79
	return [year: year, month: month, day: day] // library marker davegut.kasaEnergyMonitor, line 80
} // library marker davegut.kasaEnergyMonitor, line 81

def distEmeter(emeterResp) { // library marker davegut.kasaEnergyMonitor, line 83
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 84
	logDebug("distEmeter: ${emeterResp}, ${date}, ${state.getEnergy}") // library marker davegut.kasaEnergyMonitor, line 85
	def lastYear = date.year - 1 // library marker davegut.kasaEnergyMonitor, line 86
	if (emeterResp.get_realtime) { // library marker davegut.kasaEnergyMonitor, line 87
		setPower(emeterResp.get_realtime) // library marker davegut.kasaEnergyMonitor, line 88
	} else if (emeterResp.get_monthstat) { // library marker davegut.kasaEnergyMonitor, line 89
		def monthList = emeterResp.get_monthstat.month_list // library marker davegut.kasaEnergyMonitor, line 90
		if (state.getEnergy == "Today") { // library marker davegut.kasaEnergyMonitor, line 91
			setEnergyToday(monthList, date) // library marker davegut.kasaEnergyMonitor, line 92
		} else if (state.getEnergy == "This Month") { // library marker davegut.kasaEnergyMonitor, line 93
			setThisMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 94
		} else if (state.getEnergy == "Last Month") { // library marker davegut.kasaEnergyMonitor, line 95
			setLastMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 96
		} else if (monthList == []) { // library marker davegut.kasaEnergyMonitor, line 97
			logDebug("distEmeter: monthList Empty. No data for year.") // library marker davegut.kasaEnergyMonitor, line 98
		} // library marker davegut.kasaEnergyMonitor, line 99
	} else { // library marker davegut.kasaEnergyMonitor, line 100
		logWarn("distEmeter: Unhandled response = ${emeterResp}") // library marker davegut.kasaEnergyMonitor, line 101
	} // library marker davegut.kasaEnergyMonitor, line 102
} // library marker davegut.kasaEnergyMonitor, line 103

def getPower() { // library marker davegut.kasaEnergyMonitor, line 105
	if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 106
		if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 107
			getRealtime() // library marker davegut.kasaEnergyMonitor, line 108
		} else if (device.currentValue("power") != 0) { // library marker davegut.kasaEnergyMonitor, line 109
			sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 110
		} // library marker davegut.kasaEnergyMonitor, line 111
	} // library marker davegut.kasaEnergyMonitor, line 112
} // library marker davegut.kasaEnergyMonitor, line 113

def setPower(response) { // library marker davegut.kasaEnergyMonitor, line 115
	logDebug("setPower: ${response}") // library marker davegut.kasaEnergyMonitor, line 116
	def power = response.power // library marker davegut.kasaEnergyMonitor, line 117
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaEnergyMonitor, line 118
	power = (power + 0.5).toInteger() // library marker davegut.kasaEnergyMonitor, line 119
	def curPwr = device.currentValue("power") // library marker davegut.kasaEnergyMonitor, line 120
	def pwrChange = false // library marker davegut.kasaEnergyMonitor, line 121
	if (curPwr != power) { // library marker davegut.kasaEnergyMonitor, line 122
		if (curPwr == null || (curPwr == 0 && power > 0)) { // library marker davegut.kasaEnergyMonitor, line 123
			pwrChange = true // library marker davegut.kasaEnergyMonitor, line 124
		} else { // library marker davegut.kasaEnergyMonitor, line 125
			def changeRatio = Math.abs((power - curPwr) / curPwr) // library marker davegut.kasaEnergyMonitor, line 126
			if (changeRatio > 0.03) { // library marker davegut.kasaEnergyMonitor, line 127
				pwrChange = true // library marker davegut.kasaEnergyMonitor, line 128
			} // library marker davegut.kasaEnergyMonitor, line 129
		} // library marker davegut.kasaEnergyMonitor, line 130
	} // library marker davegut.kasaEnergyMonitor, line 131
	if (pwrChange == true) { // library marker davegut.kasaEnergyMonitor, line 132
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 133
	} // library marker davegut.kasaEnergyMonitor, line 134
} // library marker davegut.kasaEnergyMonitor, line 135

def getEnergyToday() { // library marker davegut.kasaEnergyMonitor, line 137
	if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 138
		state.getEnergy = "Today" // library marker davegut.kasaEnergyMonitor, line 139
		def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 140
		logDebug("getEnergyToday: ${year}") // library marker davegut.kasaEnergyMonitor, line 141
		runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 142
	} // library marker davegut.kasaEnergyMonitor, line 143
} // library marker davegut.kasaEnergyMonitor, line 144

def setEnergyToday(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 146
	logDebug("setEnergyToday: ${date}, ${monthList}") // library marker davegut.kasaEnergyMonitor, line 147
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 148
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 149
	def energy = 0 // library marker davegut.kasaEnergyMonitor, line 150
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 151
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 152
	} else { // library marker davegut.kasaEnergyMonitor, line 153
		energy = data.energy // library marker davegut.kasaEnergyMonitor, line 154
		if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 155
		energy = Math.round(100*energy)/100 - device.currentValue("currMonthTotal") // library marker davegut.kasaEnergyMonitor, line 156
	} // library marker davegut.kasaEnergyMonitor, line 157
	if (device.currentValue("energy") != energy) { // library marker davegut.kasaEnergyMonitor, line 158
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 159
		status << [energy: energy] // library marker davegut.kasaEnergyMonitor, line 160
	} // library marker davegut.kasaEnergyMonitor, line 161
	if (status != [:]) { logInfo("setEnergyToday: ${status}") } // library marker davegut.kasaEnergyMonitor, line 162
	if (!state.getEnergy) { // library marker davegut.kasaEnergyMonitor, line 163
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 164
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 165
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 166
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 167
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 168
	} // library marker davegut.kasaEnergyMonitor, line 169
} // library marker davegut.kasaEnergyMonitor, line 170

def getEnergyThisMonth() { // library marker davegut.kasaEnergyMonitor, line 172
	state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 173
	def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 174
	logDebug("getEnergyThisMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 175
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 176
} // library marker davegut.kasaEnergyMonitor, line 177

def setThisMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 179
	logDebug("setThisMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 180
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 181
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 182
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 183
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 184
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 185
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 186
	} else { // library marker davegut.kasaEnergyMonitor, line 187
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 188
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 189
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 190
		if (date.day == 1) { // library marker davegut.kasaEnergyMonitor, line 191
			avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 192
		} else { // library marker davegut.kasaEnergyMonitor, line 193
			avgEnergy = totEnergy /(date.day - 1) // library marker davegut.kasaEnergyMonitor, line 194
		} // library marker davegut.kasaEnergyMonitor, line 195
	} // library marker davegut.kasaEnergyMonitor, line 196
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 197
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 198
	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 199
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 200
	status << [currMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 201
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 202
		 	 descriptionText: "KiloWatt Hours per Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 203
	status << [currMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 204
	getEnergyToday() // library marker davegut.kasaEnergyMonitor, line 205
	logInfo("setThisMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 206
} // library marker davegut.kasaEnergyMonitor, line 207

def getEnergyLastMonth() { // library marker davegut.kasaEnergyMonitor, line 209
	state.getEnergy = "Last Month" // library marker davegut.kasaEnergyMonitor, line 210
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 211
	def year = date.year // library marker davegut.kasaEnergyMonitor, line 212
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 213
		year = year - 1 // library marker davegut.kasaEnergyMonitor, line 214
	} // library marker davegut.kasaEnergyMonitor, line 215
	logDebug("getEnergyLastMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 216
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 217
} // library marker davegut.kasaEnergyMonitor, line 218

def setLastMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 220
	logDebug("setLastMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 221
	def lastMonthYear = date.year // library marker davegut.kasaEnergyMonitor, line 222
	def lastMonth = date.month - 1 // library marker davegut.kasaEnergyMonitor, line 223
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 224
		lastMonthYear -+ 1 // library marker davegut.kasaEnergyMonitor, line 225
		lastMonth = 12 // library marker davegut.kasaEnergyMonitor, line 226
	} // library marker davegut.kasaEnergyMonitor, line 227
	def data = monthList.find { it.month == lastMonth } // library marker davegut.kasaEnergyMonitor, line 228
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 229
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 230
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 231
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 232
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 233
	} else { // library marker davegut.kasaEnergyMonitor, line 234
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 235
		def monthLength // library marker davegut.kasaEnergyMonitor, line 236
		switch(lastMonth) { // library marker davegut.kasaEnergyMonitor, line 237
			case 4: // library marker davegut.kasaEnergyMonitor, line 238
			case 6: // library marker davegut.kasaEnergyMonitor, line 239
			case 9: // library marker davegut.kasaEnergyMonitor, line 240
			case 11: // library marker davegut.kasaEnergyMonitor, line 241
				monthLength = 30 // library marker davegut.kasaEnergyMonitor, line 242
				break // library marker davegut.kasaEnergyMonitor, line 243
			case 2: // library marker davegut.kasaEnergyMonitor, line 244
				monthLength = 28 // library marker davegut.kasaEnergyMonitor, line 245
				if (lastMonthYear == 2020 || lastMonthYear == 2024 || lastMonthYear == 2028) {  // library marker davegut.kasaEnergyMonitor, line 246
					monthLength = 29 // library marker davegut.kasaEnergyMonitor, line 247
				} // library marker davegut.kasaEnergyMonitor, line 248
				break // library marker davegut.kasaEnergyMonitor, line 249
			default: // library marker davegut.kasaEnergyMonitor, line 250
				monthLength = 31 // library marker davegut.kasaEnergyMonitor, line 251
		} // library marker davegut.kasaEnergyMonitor, line 252
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 253
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 254
		avgEnergy = totEnergy / monthLength // library marker davegut.kasaEnergyMonitor, line 255
	} // library marker davegut.kasaEnergyMonitor, line 256
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 257
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 258
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 259
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 260
	status << [lastMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 261
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 262
			  descriptionText: "KiloWatt Hoursper Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 263
	status << [lastMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 264
	logInfo("setLastMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 265
} // library marker davegut.kasaEnergyMonitor, line 266

def getRealtime() { // library marker davegut.kasaEnergyMonitor, line 268
	def feature = getDataValue("feature") // library marker davegut.kasaEnergyMonitor, line 269
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 270
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 271
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 272
	} else if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaEnergyMonitor, line 273
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 274
	} else { // library marker davegut.kasaEnergyMonitor, line 275
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 276
	} // library marker davegut.kasaEnergyMonitor, line 277
} // library marker davegut.kasaEnergyMonitor, line 278

def getMonthstat(year) { // library marker davegut.kasaEnergyMonitor, line 280
	def feature = getDataValue("feature") // library marker davegut.kasaEnergyMonitor, line 281
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 282
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 283
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 284
	} else if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaEnergyMonitor, line 285
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 286
	} else { // library marker davegut.kasaEnergyMonitor, line 287
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 288
	} // library marker davegut.kasaEnergyMonitor, line 289
} // library marker davegut.kasaEnergyMonitor, line 290

// ~~~~~ end include (322) davegut.kasaEnergyMonitor ~~~~~
