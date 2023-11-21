/*	Kasa Device Driver Series
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/tree/master/KasaDevices/Docs
Version 2.3.7a.
	NOTE:  Namespace Change.  At top of code for app and each driver.
	a.	Revert to legacy Kasa Device support only.  Removed all code related to Kasa
		Matter devices.
	b.	Removed version info and logging (it is part of the Hubitat version).
	c.	Fixed commsError process to detect error in UDP messages,  (HubAction
		parameter parseWarning = true no longer works).
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
		capability "Switch"
		capability "Switch Level"
		capability "Change Level"
		capability "Refresh"
		capability "Actuator"
		capability "Configuration"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
		capability "Power Meter"
		capability "Energy Meter"
		attribute "currMonthTotal", "number"
		attribute "currMonthAvg", "number"
		attribute "lastMonthTotal", "number"
		attribute "lastMonthAvg", "number"
		attribute "connection", "string"
		attribute "commsError", "string"
	}
	preferences {
		input ("infoLog", "bool", 
			   title: "Enable descriptionText logging",
			   defaultValue: true)
		input ("logEnable", "bool",
			   title: "Enable debug logging",
			   defaultValue: false)
		input ("emFunction", "bool", 
			   title: "Enable Energy Monitor", 
			   defaultValue: false)
		if (emFunction) {
			input ("energyPollInt", "enum",
				   title: "Energy Poll Interval (minutes)",
				   options: ["1 minute", "5 minutes", "30 minutes"],
				   defaultValue: "30 minutes")
		}
		input ("bind", "bool",
			   title: "Kasa Cloud Binding",
			   defalutValue: true)
		input ("useCloud", "bool",
		 	  title: "Use Kasa Cloud for device control",
		 	  defaultValue: false)
		input ("nameSync", "enum", title: "Synchronize Names",
			   defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "Kasa device name master", 
						 "Hubitat" : "Hubitat label master"])
		input ("manualIp", "string",
			   title: "Manual IP Update <b>[Caution]</b>",
			   defaultValue: getDataValue("deviceIP"))
		input ("manualPort", "string",
			   title: "Manual Port Update <b>[Caution]</b>",
			   defaultValue: getDataValue("devicePort"))
		input ("rebootDev", "bool",
			   title: "Reboot device <b>[Caution]</b>",
			   defaultValue: false)
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







// ~~~~~ start include (3) davegut.kasaCommon ~~~~~
library ( // library marker davegut.kasaCommon, line 1
	name: "kasaCommon", // library marker davegut.kasaCommon, line 2
	namespace: "davegut", // library marker davegut.kasaCommon, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaCommon, line 4
	description: "Kasa Device Common Methods", // library marker davegut.kasaCommon, line 5
	category: "utilities", // library marker davegut.kasaCommon, line 6
	documentationLink: "" // library marker davegut.kasaCommon, line 7
) // library marker davegut.kasaCommon, line 8

def installCommon() { // library marker davegut.kasaCommon, line 10
	pauseExecution(3000) // library marker davegut.kasaCommon, line 11
	def instStatus = [:] // library marker davegut.kasaCommon, line 12
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 13
		sendEvent(name: "connection", value: "CLOUD") // library marker davegut.kasaCommon, line 14
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 15
		instStatus << [useCloud: true, connection: "CLOUD"] // library marker davegut.kasaCommon, line 16
	} else { // library marker davegut.kasaCommon, line 17
		sendEvent(name: "connection", value: "LAN") // library marker davegut.kasaCommon, line 18
		device.updateSetting("useCloud", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 19
		instStatus << [useCloud: false, connection: "LAN"] // library marker davegut.kasaCommon, line 20
	} // library marker davegut.kasaCommon, line 21

	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 23
	state.errorCount = 0 // library marker davegut.kasaCommon, line 24
	state.pollInterval = "30 minutes" // library marker davegut.kasaCommon, line 25
	runIn(1, updated) // library marker davegut.kasaCommon, line 26
	return instStatus // library marker davegut.kasaCommon, line 27
} // library marker davegut.kasaCommon, line 28

def updateCommon() { // library marker davegut.kasaCommon, line 30
	def updStatus = [:] // library marker davegut.kasaCommon, line 31
	if (rebootDev) { // library marker davegut.kasaCommon, line 32
		updStatus << [rebootDev: rebootDevice()] // library marker davegut.kasaCommon, line 33
		return updStatus // library marker davegut.kasaCommon, line 34
	} // library marker davegut.kasaCommon, line 35
	unschedule() // library marker davegut.kasaCommon, line 36
	updStatus << [bind: bindUnbind()] // library marker davegut.kasaCommon, line 37
	if (nameSync != "none") { // library marker davegut.kasaCommon, line 38
		updStatus << [nameSync: syncName()] // library marker davegut.kasaCommon, line 39
	} // library marker davegut.kasaCommon, line 40
	if (logEnable) { runIn(1800, debugLogOff) } // library marker davegut.kasaCommon, line 41
	updStatus << [infoLog: infoLog, logEnable: logEnable] // library marker davegut.kasaCommon, line 42
	if (manualIp != getDataValue("deviceIP")) { // library marker davegut.kasaCommon, line 43
		updateDataValue("deviceIP", manualIp) // library marker davegut.kasaCommon, line 44
		updStatus << [ipUpdate: manualIp] // library marker davegut.kasaCommon, line 45
	} // library marker davegut.kasaCommon, line 46
	if (manualPort != getDataValue("devicePort")) { // library marker davegut.kasaCommon, line 47
		updateDataValue("devicePort", manualPort) // library marker davegut.kasaCommon, line 48
		updStatus << [portUpdate: manualPort] // library marker davegut.kasaCommon, line 49
	} // library marker davegut.kasaCommon, line 50
	state.errorCount = 0 // library marker davegut.kasaCommon, line 51
	sendEvent(name: "commsError", value: "false") // library marker davegut.kasaCommon, line 52

	def pollInterval = state.pollInterval // library marker davegut.kasaCommon, line 54
	if (pollInterval == null) { pollInterval = "30 minutes" } // library marker davegut.kasaCommon, line 55
	state.pollInterval = pollInterval // library marker davegut.kasaCommon, line 56
	runIn(15, setPollInterval) // library marker davegut.kasaCommon, line 57
	updStatus << [pollInterval: pollInterval] // library marker davegut.kasaCommon, line 58
	if (emFunction) { // library marker davegut.kasaCommon, line 59
		scheduleEnergyAttrs() // library marker davegut.kasaCommon, line 60
		state.getEnergy = "This Month" // library marker davegut.kasaCommon, line 61
		updStatus << [emFunction: "scheduled"] // library marker davegut.kasaCommon, line 62
	} // library marker davegut.kasaCommon, line 63
	return updStatus // library marker davegut.kasaCommon, line 64
} // library marker davegut.kasaCommon, line 65

def configure() { // library marker davegut.kasaCommon, line 67
	if (parent == null) { // library marker davegut.kasaCommon, line 68
		logWarn("configure: No Parent Detected.  Configure function ABORTED.  Use Save Preferences instead.") // library marker davegut.kasaCommon, line 69
	} else { // library marker davegut.kasaCommon, line 70
		def confStatus = parent.updateConfigurations() // library marker davegut.kasaCommon, line 71
		logInfo("configure: ${confStatus}") // library marker davegut.kasaCommon, line 72
	} // library marker davegut.kasaCommon, line 73
} // library marker davegut.kasaCommon, line 74

def refresh() { poll() } // library marker davegut.kasaCommon, line 76

def poll() { getSysinfo() } // library marker davegut.kasaCommon, line 78

def setPollInterval(interval = state.pollInterval) { // library marker davegut.kasaCommon, line 80
	if (interval == "default" || interval == "off" || interval == null) { // library marker davegut.kasaCommon, line 81
		interval = "30 minutes" // library marker davegut.kasaCommon, line 82
	} else if (useCloud || altLan || getDataValue("altComms") == "true") { // library marker davegut.kasaCommon, line 83
		if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 84
			interval = "1 minute" // library marker davegut.kasaCommon, line 85
			logWarn("setPollInterval: Device using Cloud or rawSocket.  Poll interval reset to minimum value of 1 minute.") // library marker davegut.kasaCommon, line 86
		} // library marker davegut.kasaCommon, line 87
	} // library marker davegut.kasaCommon, line 88
	state.pollInterval = interval // library marker davegut.kasaCommon, line 89
	def pollInterval = interval.substring(0,2).toInteger() // library marker davegut.kasaCommon, line 90
	if (interval.contains("sec")) { // library marker davegut.kasaCommon, line 91
		def start = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker davegut.kasaCommon, line 92
		schedule("${start}/${pollInterval} * * * * ?", "poll") // library marker davegut.kasaCommon, line 93
		logWarn("setPollInterval: Polling intervals of less than one minute " + // library marker davegut.kasaCommon, line 94
				"can take high resources and may impact hub performance.") // library marker davegut.kasaCommon, line 95
	} else { // library marker davegut.kasaCommon, line 96
		def start = Math.round(59 * Math.random()).toInteger() // library marker davegut.kasaCommon, line 97
		schedule("${start} */${pollInterval} * * * ?", "poll") // library marker davegut.kasaCommon, line 98
	} // library marker davegut.kasaCommon, line 99
	logDebug("setPollInterval: interval = ${interval}.") // library marker davegut.kasaCommon, line 100
	return interval // library marker davegut.kasaCommon, line 101
} // library marker davegut.kasaCommon, line 102

def rebootDevice() { // library marker davegut.kasaCommon, line 104
	device.updateSetting("rebootDev", [type:"bool", value: false]) // library marker davegut.kasaCommon, line 105
	reboot() // library marker davegut.kasaCommon, line 106
	pauseExecution(10000) // library marker davegut.kasaCommon, line 107
	return "REBOOTING DEVICE" // library marker davegut.kasaCommon, line 108
} // library marker davegut.kasaCommon, line 109

def bindUnbind() { // library marker davegut.kasaCommon, line 111
	def message // library marker davegut.kasaCommon, line 112
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 113
		device.updateSetting("bind", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 114
		device.updateSetting("useCloud", [type:"bool", value: true]) // library marker davegut.kasaCommon, line 115
		message = "No deviceIp.  Bind not modified." // library marker davegut.kasaCommon, line 116
	} else if (bind == null ||  getDataValue("feature") == "lightStrip") { // library marker davegut.kasaCommon, line 117
		message = "Getting current bind state" // library marker davegut.kasaCommon, line 118
		getBind() // library marker davegut.kasaCommon, line 119
	} else if (bind == true) { // library marker davegut.kasaCommon, line 120
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) { // library marker davegut.kasaCommon, line 121
			message = "Username/pwd not set." // library marker davegut.kasaCommon, line 122
			getBind() // library marker davegut.kasaCommon, line 123
		} else { // library marker davegut.kasaCommon, line 124
			message = "Binding device to the Kasa Cloud." // library marker davegut.kasaCommon, line 125
			setBind(parent.userName, parent.userPassword) // library marker davegut.kasaCommon, line 126
		} // library marker davegut.kasaCommon, line 127
	} else if (bind == false) { // library marker davegut.kasaCommon, line 128
		message = "Unbinding device from the Kasa Cloud." // library marker davegut.kasaCommon, line 129
		setUnbind() // library marker davegut.kasaCommon, line 130
	} // library marker davegut.kasaCommon, line 131
	pauseExecution(5000) // library marker davegut.kasaCommon, line 132
	return message // library marker davegut.kasaCommon, line 133
} // library marker davegut.kasaCommon, line 134

def setBindUnbind(cmdResp) { // library marker davegut.kasaCommon, line 136
	def bindState = true // library marker davegut.kasaCommon, line 137
	if (cmdResp.get_info) { // library marker davegut.kasaCommon, line 138
		if (cmdResp.get_info.binded == 0) { bindState = false } // library marker davegut.kasaCommon, line 139
		logInfo("setBindUnbind: Bind status set to ${bindState}") // library marker davegut.kasaCommon, line 140
		setCommsType(bindState) // library marker davegut.kasaCommon, line 141
	} else if (cmdResp.bind.err_code == 0){ // library marker davegut.kasaCommon, line 142
		getBind() // library marker davegut.kasaCommon, line 143
	} else { // library marker davegut.kasaCommon, line 144
		logWarn("setBindUnbind: Unhandled response: ${cmdResp}") // library marker davegut.kasaCommon, line 145
	} // library marker davegut.kasaCommon, line 146
} // library marker davegut.kasaCommon, line 147

def setCommsType(bindState) { // library marker davegut.kasaCommon, line 149
	def commsType = "LAN" // library marker davegut.kasaCommon, line 150
	def cloudCtrl = false // library marker davegut.kasaCommon, line 151
	if (bindState == false && useCloud == true) { // library marker davegut.kasaCommon, line 152
		logWarn("setCommsType: Can not use cloud.  Device is not bound to Kasa cloud.") // library marker davegut.kasaCommon, line 153
	} else if (bindState == true && useCloud == true && parent.kasaToken) { // library marker davegut.kasaCommon, line 154
		commsType = "CLOUD" // library marker davegut.kasaCommon, line 155
		cloudCtrl = true // library marker davegut.kasaCommon, line 156
	} else if (altLan == true) { // library marker davegut.kasaCommon, line 157
		commsType = "AltLAN" // library marker davegut.kasaCommon, line 158
		state.response = "" // library marker davegut.kasaCommon, line 159
	} // library marker davegut.kasaCommon, line 160
	def commsSettings = [bind: bindState, useCloud: cloudCtrl, commsType: commsType] // library marker davegut.kasaCommon, line 161
	device.updateSetting("bind", [type:"bool", value: bindState]) // library marker davegut.kasaCommon, line 162
	device.updateSetting("useCloud", [type:"bool", value: cloudCtrl]) // library marker davegut.kasaCommon, line 163
	sendEvent(name: "connection", value: "${commsType}") // library marker davegut.kasaCommon, line 164
	logInfo("setCommsType: ${commsSettings}") // library marker davegut.kasaCommon, line 165
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 166
		def coordData = [:] // library marker davegut.kasaCommon, line 167
		coordData << [bind: bindState] // library marker davegut.kasaCommon, line 168
		coordData << [useCloud: cloudCtrl] // library marker davegut.kasaCommon, line 169
		coordData << [connection: commsType] // library marker davegut.kasaCommon, line 170
		coordData << [altLan: altLan] // library marker davegut.kasaCommon, line 171
		parent.coordinate("commsData", coordData, getDataValue("deviceId"), getDataValue("plugNo")) // library marker davegut.kasaCommon, line 172
	} // library marker davegut.kasaCommon, line 173
	pauseExecution(1000) // library marker davegut.kasaCommon, line 174
} // library marker davegut.kasaCommon, line 175

def syncName() { // library marker davegut.kasaCommon, line 177
	def message // library marker davegut.kasaCommon, line 178
	if (nameSync == "Hubitat") { // library marker davegut.kasaCommon, line 179
		message = "Hubitat Label Sync" // library marker davegut.kasaCommon, line 180
		setDeviceAlias(device.getLabel()) // library marker davegut.kasaCommon, line 181
	} else if (nameSync == "device") { // library marker davegut.kasaCommon, line 182
		message = "Device Alias Sync" // library marker davegut.kasaCommon, line 183
	} else { // library marker davegut.kasaCommon, line 184
		message = "Not Syncing" // library marker davegut.kasaCommon, line 185
	} // library marker davegut.kasaCommon, line 186
	device.updateSetting("nameSync",[type:"enum", value:"none"]) // library marker davegut.kasaCommon, line 187
	return message // library marker davegut.kasaCommon, line 188
} // library marker davegut.kasaCommon, line 189

def updateName(response) { // library marker davegut.kasaCommon, line 191
	def name = device.getLabel() // library marker davegut.kasaCommon, line 192
	if (response.alias) { // library marker davegut.kasaCommon, line 193
		name = response.alias // library marker davegut.kasaCommon, line 194
		device.setLabel(name) // library marker davegut.kasaCommon, line 195
	} else if (response.err_code != 0) { // library marker davegut.kasaCommon, line 196
		def msg = "updateName: Name Sync from Hubitat to Device returned an error." // library marker davegut.kasaCommon, line 197
		msg+= "\n\rNote: <b>Some devices do not support syncing name from the hub.</b>\n\r" // library marker davegut.kasaCommon, line 198
		logWarn(msg) // library marker davegut.kasaCommon, line 199
		return // library marker davegut.kasaCommon, line 200
	} // library marker davegut.kasaCommon, line 201
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}") // library marker davegut.kasaCommon, line 202
} // library marker davegut.kasaCommon, line 203

def getSysinfo() { // library marker davegut.kasaCommon, line 205
	if (getDataValue("altComms") == "true") { // library marker davegut.kasaCommon, line 206
		sendTcpCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 207
	} else { // library marker davegut.kasaCommon, line 208
		sendCmd("""{"system":{"get_sysinfo":{}}}""") // library marker davegut.kasaCommon, line 209
	} // library marker davegut.kasaCommon, line 210
} // library marker davegut.kasaCommon, line 211

def bindService() { // library marker davegut.kasaCommon, line 213
	def service = "cnCloud" // library marker davegut.kasaCommon, line 214
	def feature = getDataValue("feature") // library marker davegut.kasaCommon, line 215
	if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaCommon, line 216
		service = "smartlife.iot.common.cloud" // library marker davegut.kasaCommon, line 217
	} // library marker davegut.kasaCommon, line 218
	return service // library marker davegut.kasaCommon, line 219
} // library marker davegut.kasaCommon, line 220

def getBind() { // library marker davegut.kasaCommon, line 222
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 223
		logDebug("getBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 224
	} else { // library marker davegut.kasaCommon, line 225
		sendLanCmd("""{"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 226
	} // library marker davegut.kasaCommon, line 227
} // library marker davegut.kasaCommon, line 228

def setBind(userName, password) { // library marker davegut.kasaCommon, line 230
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 231
		logDebug("setBind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 232
	} else { // library marker davegut.kasaCommon, line 233
		sendLanCmd("""{"${bindService()}":{"bind":{"username":"${userName}",""" + // library marker davegut.kasaCommon, line 234
				   """"password":"${password}"}},""" + // library marker davegut.kasaCommon, line 235
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 236
	} // library marker davegut.kasaCommon, line 237
} // library marker davegut.kasaCommon, line 238

def setUnbind() { // library marker davegut.kasaCommon, line 240
	if (getDataValue("deviceIP") == "CLOUD") { // library marker davegut.kasaCommon, line 241
		logDebug("setUnbind: [status: notRun, reason: [deviceIP: CLOUD]]") // library marker davegut.kasaCommon, line 242
	} else { // library marker davegut.kasaCommon, line 243
		sendLanCmd("""{"${bindService()}":{"unbind":""},""" + // library marker davegut.kasaCommon, line 244
				   """"${bindService()}":{"get_info":{}}}""") // library marker davegut.kasaCommon, line 245
	} // library marker davegut.kasaCommon, line 246
} // library marker davegut.kasaCommon, line 247

def sysService() { // library marker davegut.kasaCommon, line 249
	def service = "system" // library marker davegut.kasaCommon, line 250
	def feature = getDataValue("feature") // library marker davegut.kasaCommon, line 251
	if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaCommon, line 252
		service = "smartlife.iot.common.system" // library marker davegut.kasaCommon, line 253
	} // library marker davegut.kasaCommon, line 254
	return service // library marker davegut.kasaCommon, line 255
} // library marker davegut.kasaCommon, line 256

def reboot() { // library marker davegut.kasaCommon, line 258
	sendCmd("""{"${sysService()}":{"reboot":{"delay":1}}}""") // library marker davegut.kasaCommon, line 259
} // library marker davegut.kasaCommon, line 260

def setDeviceAlias(newAlias) { // library marker davegut.kasaCommon, line 262
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaCommon, line 263
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaCommon, line 264
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 265
	} else { // library marker davegut.kasaCommon, line 266
		sendCmd("""{"${sysService()}":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""") // library marker davegut.kasaCommon, line 267
	} // library marker davegut.kasaCommon, line 268
} // library marker davegut.kasaCommon, line 269

def updateAttr(attr, value) { // library marker davegut.kasaCommon, line 271
	if (device.currentValue(attr) != value) { // library marker davegut.kasaCommon, line 272
		sendEvent(name: attr, value: value) // library marker davegut.kasaCommon, line 273
	} // library marker davegut.kasaCommon, line 274
} // library marker davegut.kasaCommon, line 275


// ~~~~~ end include (3) davegut.kasaCommon ~~~~~

// ~~~~~ start include (4) davegut.kasaCommunications ~~~~~
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
	def connection = device.currentValue("connection") // library marker davegut.kasaCommunications, line 23
	if (connection == "LAN") { // library marker davegut.kasaCommunications, line 24
		sendLanCmd(command) // library marker davegut.kasaCommunications, line 25
	} else if (connection == "CLOUD") { // library marker davegut.kasaCommunications, line 26
		sendKasaCmd(command) // library marker davegut.kasaCommunications, line 27
	} else if (connection == "AltLAN") { // library marker davegut.kasaCommunications, line 28
		sendTcpCmd(command) // library marker davegut.kasaCommunications, line 29
	} else { // library marker davegut.kasaCommunications, line 30
		logWarn("sendCmd: attribute connection is not set.") // library marker davegut.kasaCommunications, line 31
	} // library marker davegut.kasaCommunications, line 32
} // library marker davegut.kasaCommunications, line 33

def sendLanCmd(command) { // library marker davegut.kasaCommunications, line 35
	logDebug("sendLanCmd: [ip: ${getDataValue("deviceIP")}, cmd: ${command}]") // library marker davegut.kasaCommunications, line 36
	def myHubAction = new hubitat.device.HubAction( // library marker davegut.kasaCommunications, line 37
		outputXOR(command), // library marker davegut.kasaCommunications, line 38
		hubitat.device.Protocol.LAN, // library marker davegut.kasaCommunications, line 39
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker davegut.kasaCommunications, line 40
		 destinationAddress: "${getDataValue("deviceIP")}:${getPort()}", // library marker davegut.kasaCommunications, line 41
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker davegut.kasaCommunications, line 42
		 parseWarning: true, // library marker davegut.kasaCommunications, line 43
		 timeout: 8, // library marker davegut.kasaCommunications, line 44
		 ignoreResponse: false, // library marker davegut.kasaCommunications, line 45
		 callback: "parseUdp"]) // library marker davegut.kasaCommunications, line 46
	try { // library marker davegut.kasaCommunications, line 47
		sendHubCommand(myHubAction) // library marker davegut.kasaCommunications, line 48
		if (state.errorCount > 0 && state.errorCount < 4) { // library marker davegut.kasaCommunications, line 49
			runIn(9, handleCommsError, [overwrite: false]) // library marker davegut.kasaCommunications, line 50
		} // library marker davegut.kasaCommunications, line 51
		state.errorCount += 1 // library marker davegut.kasaCommunications, line 52
	} catch (e) { // library marker davegut.kasaCommunications, line 53
		logWarn("sendLanCmd: [ip: ${getDataValue("deviceIP")}, error: ${e}]") // library marker davegut.kasaCommunications, line 54
		handleCommsError() // library marker davegut.kasaCommunications, line 55
	} // library marker davegut.kasaCommunications, line 56
} // library marker davegut.kasaCommunications, line 57
def parseUdp(message) { // library marker davegut.kasaCommunications, line 58
	def resp = parseLanMessage(message) // library marker davegut.kasaCommunications, line 59
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker davegut.kasaCommunications, line 60
		def clearResp = inputXOR(resp.payload) // library marker davegut.kasaCommunications, line 61
		if (clearResp.length() > 1023) { // library marker davegut.kasaCommunications, line 62
			if (clearResp.contains("preferred")) { // library marker davegut.kasaCommunications, line 63
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}" // library marker davegut.kasaCommunications, line 64
			} else if (clearResp.contains("child_num")) { // library marker davegut.kasaCommunications, line 65
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num") -2) + "}}}" // library marker davegut.kasaCommunications, line 66
			} else { // library marker davegut.kasaCommunications, line 67
				logWarn("parseUdp: [status: converting to altComms, error: udp msg can not be parsed]") // library marker davegut.kasaCommunications, line 68
				logDebug("parseUdp: [messageData: ${clearResp}]") // library marker davegut.kasaCommunications, line 69
				updateDataValue("altComms", "true") // library marker davegut.kasaCommunications, line 70
				state.errorCount = 0 // library marker davegut.kasaCommunications, line 71
				sendTcpCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 72
				return // library marker davegut.kasaCommunications, line 73
			} // library marker davegut.kasaCommunications, line 74
		} // library marker davegut.kasaCommunications, line 75
		def cmdResp = new JsonSlurper().parseText(clearResp) // library marker davegut.kasaCommunications, line 76
		logDebug("parseUdp: ${cmdResp}") // library marker davegut.kasaCommunications, line 77
		state.errorCount = 0 // library marker davegut.kasaCommunications, line 78
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 79
	} else { // library marker davegut.kasaCommunications, line 80
		logWarn("parseUdp: [error: error, reason: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker davegut.kasaCommunications, line 81
		handleCommsError() // library marker davegut.kasaCommunications, line 82
	} // library marker davegut.kasaCommunications, line 83
} // library marker davegut.kasaCommunications, line 84

def sendKasaCmd(command) { // library marker davegut.kasaCommunications, line 86
	logDebug("sendKasaCmd: ${command}") // library marker davegut.kasaCommunications, line 87
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 88
	def cmdBody = [ // library marker davegut.kasaCommunications, line 89
		method: "passthrough", // library marker davegut.kasaCommunications, line 90
		params: [ // library marker davegut.kasaCommunications, line 91
			deviceId: getDataValue("deviceId"), // library marker davegut.kasaCommunications, line 92
			requestData: "${command}" // library marker davegut.kasaCommunications, line 93
		] // library marker davegut.kasaCommunications, line 94
	] // library marker davegut.kasaCommunications, line 95
	if (!parent.kasaCloudUrl || !parent.kasaToken) { // library marker davegut.kasaCommunications, line 96
		logWarn("sendKasaCmd: Cloud interface not properly set up.") // library marker davegut.kasaCommunications, line 97
		return // library marker davegut.kasaCommunications, line 98
	} // library marker davegut.kasaCommunications, line 99
	def sendCloudCmdParams = [ // library marker davegut.kasaCommunications, line 100
		uri: "${parent.kasaCloudUrl}/?token=${parent.kasaToken}", // library marker davegut.kasaCommunications, line 101
		requestContentType: 'application/json', // library marker davegut.kasaCommunications, line 102
		contentType: 'application/json', // library marker davegut.kasaCommunications, line 103
		headers: ['Accept':'application/json; version=1, */*; q=0.01'], // library marker davegut.kasaCommunications, line 104
		timeout: 10, // library marker davegut.kasaCommunications, line 105
		body : new groovy.json.JsonBuilder(cmdBody).toString() // library marker davegut.kasaCommunications, line 106
	] // library marker davegut.kasaCommunications, line 107
	try { // library marker davegut.kasaCommunications, line 108
		asynchttpPost("cloudParse", sendCloudCmdParams) // library marker davegut.kasaCommunications, line 109
	} catch (e) { // library marker davegut.kasaCommunications, line 110
		def msg = "sendKasaCmd: <b>Error in Cloud Communications.</b> The Kasa Cloud is unreachable." // library marker davegut.kasaCommunications, line 111
		msg += "\nAdditional Data: Error = ${e}\n\n" // library marker davegut.kasaCommunications, line 112
		logWarn(msg) // library marker davegut.kasaCommunications, line 113
	} // library marker davegut.kasaCommunications, line 114
} // library marker davegut.kasaCommunications, line 115
def cloudParse(resp, data = null) { // library marker davegut.kasaCommunications, line 116
	try { // library marker davegut.kasaCommunications, line 117
		response = new JsonSlurper().parseText(resp.data) // library marker davegut.kasaCommunications, line 118
	} catch (e) { // library marker davegut.kasaCommunications, line 119
		response = [error_code: 9999, data: e] // library marker davegut.kasaCommunications, line 120
	} // library marker davegut.kasaCommunications, line 121
	if (resp.status == 200 && response.error_code == 0 && resp != []) { // library marker davegut.kasaCommunications, line 122
		def cmdResp = new JsonSlurper().parseText(response.result.responseData) // library marker davegut.kasaCommunications, line 123
		logDebug("cloudParse: ${cmdResp}") // library marker davegut.kasaCommunications, line 124
		distResp(cmdResp) // library marker davegut.kasaCommunications, line 125
	} else { // library marker davegut.kasaCommunications, line 126
		def msg = "cloudParse:\n<b>Error from the Kasa Cloud.</b> Most common cause is " // library marker davegut.kasaCommunications, line 127
		msg += "your Kasa Token has expired.  Run Kasa Login and Token update and try again." // library marker davegut.kasaCommunications, line 128
		msg += "\nAdditional Data: Error = ${resp.data}\n\n" // library marker davegut.kasaCommunications, line 129
		logDebug(msg) // library marker davegut.kasaCommunications, line 130
	} // library marker davegut.kasaCommunications, line 131
} // library marker davegut.kasaCommunications, line 132

def sendTcpCmd(command) { // library marker davegut.kasaCommunications, line 134
	logDebug("sendTcpCmd: ${command}") // library marker davegut.kasaCommunications, line 135
	try { // library marker davegut.kasaCommunications, line 136
		interfaces.rawSocket.connect("${getDataValue("deviceIP")}", // library marker davegut.kasaCommunications, line 137
									 getPort().toInteger(), byteInterface: true) // library marker davegut.kasaCommunications, line 138
	} catch (error) { // library marker davegut.kasaCommunications, line 139
		logDebug("SendTcpCmd: [connectFailed: [ip: ${getDataValue("deviceIP")}, Error = ${error}]]") // library marker davegut.kasaCommunications, line 140
	} // library marker davegut.kasaCommunications, line 141
	state.response = "" // library marker davegut.kasaCommunications, line 142
	interfaces.rawSocket.sendMessage(outputXorTcp(command)) // library marker davegut.kasaCommunications, line 143
} // library marker davegut.kasaCommunications, line 144
def close() { interfaces.rawSocket.close() } // library marker davegut.kasaCommunications, line 145
def socketStatus(message) { // library marker davegut.kasaCommunications, line 146
	if (message != "receive error: Stream closed.") { // library marker davegut.kasaCommunications, line 147
		logDebug("socketStatus: Socket Established") // library marker davegut.kasaCommunications, line 148
	} else { // library marker davegut.kasaCommunications, line 149
		logWarn("socketStatus = ${message}") // library marker davegut.kasaCommunications, line 150
	} // library marker davegut.kasaCommunications, line 151
} // library marker davegut.kasaCommunications, line 152
def parse(message) { // library marker davegut.kasaCommunications, line 153
	if (message != null || message != "") { // library marker davegut.kasaCommunications, line 154
		def response = state.response.concat(message) // library marker davegut.kasaCommunications, line 155
		state.response = response // library marker davegut.kasaCommunications, line 156
		extractTcpResp(response) // library marker davegut.kasaCommunications, line 157
	} // library marker davegut.kasaCommunications, line 158
} // library marker davegut.kasaCommunications, line 159
def extractTcpResp(response) { // library marker davegut.kasaCommunications, line 160
	def cmdResp // library marker davegut.kasaCommunications, line 161
	def clearResp = inputXorTcp(response) // library marker davegut.kasaCommunications, line 162
	if (clearResp.endsWith("}}}")) { // library marker davegut.kasaCommunications, line 163
		interfaces.rawSocket.close() // library marker davegut.kasaCommunications, line 164
		try { // library marker davegut.kasaCommunications, line 165
			cmdResp = parseJson(clearResp) // library marker davegut.kasaCommunications, line 166
			distResp(cmdResp) // library marker davegut.kasaCommunications, line 167
		} catch (e) { // library marker davegut.kasaCommunications, line 168
			logWarn("extractTcpResp: [length: ${clearResp.length()}, clearResp: ${clearResp}, comms error: ${e}]") // library marker davegut.kasaCommunications, line 169
		} // library marker davegut.kasaCommunications, line 170
	} else if (clearResp.length() > 2000) { // library marker davegut.kasaCommunications, line 171
		interfaces.rawSocket.close() // library marker davegut.kasaCommunications, line 172
	} // library marker davegut.kasaCommunications, line 173
} // library marker davegut.kasaCommunications, line 174

def handleCommsError() { // library marker davegut.kasaCommunications, line 176
	Map logData = [method: "handleCommsError"] // library marker davegut.kasaCommunications, line 177
	if (state.errorCount > 0 && state.lastCommand != "") { // library marker davegut.kasaCommunications, line 178
		unschedule("poll") // library marker davegut.kasaCommunications, line 179
		runIn(60, setPollInterval) // library marker davegut.kasaCommunications, line 180
		logData << [count: state.errorCount, command: state.lastCommand] // library marker davegut.kasaCommunications, line 181
		switch (state.errorCount) { // library marker davegut.kasaCommunications, line 182
			case 1: // library marker davegut.kasaCommunications, line 183
			case 2: // library marker davegut.kasaCommunications, line 184
			case 3: // library marker davegut.kasaCommunications, line 185
				if (getDataValue("altComms") == "true") { // library marker davegut.kasaCommunications, line 186
					sendTcpCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 187
				} else { // library marker davegut.kasaCommunications, line 188
					sendCmd(state.lastCommand) // library marker davegut.kasaCommunications, line 189
				} // library marker davegut.kasaCommunications, line 190
				logDebug(logData) // library marker davegut.kasaCommunications, line 191
				break // library marker davegut.kasaCommunications, line 192
			case 4: // library marker davegut.kasaCommunications, line 193
				updateAttr("commsError", "true") // library marker davegut.kasaCommunications, line 194
				logData << [setCommsError: true, status: "retriesDisabled"] // library marker davegut.kasaCommunications, line 195
				logData << [TRY: "<b>do CONFIGURE</b>"] // library marker davegut.kasaCommunications, line 196
				logData << [commonERROR: "IP Address not static in Router"] // library marker davegut.kasaCommunications, line 197
				logWarn(logData) // library marker davegut.kasaCommunications, line 198
				break // library marker davegut.kasaCommunications, line 199
			default: // library marker davegut.kasaCommunications, line 200
				logData << [TRY: "<b>do CONFIGURE</b>"] // library marker davegut.kasaCommunications, line 201
				logData << [commonERROR: "IP Address not static in Router"] // library marker davegut.kasaCommunications, line 202
				logWarn(logData) // library marker davegut.kasaCommunications, line 203
				break // library marker davegut.kasaCommunications, line 204
		} // library marker davegut.kasaCommunications, line 205
	} // library marker davegut.kasaCommunications, line 206
} // library marker davegut.kasaCommunications, line 207

private outputXOR(command) { // library marker davegut.kasaCommunications, line 209
	def str = "" // library marker davegut.kasaCommunications, line 210
	def encrCmd = "" // library marker davegut.kasaCommunications, line 211
 	def key = 0xAB // library marker davegut.kasaCommunications, line 212
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 213
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 214
		key = str // library marker davegut.kasaCommunications, line 215
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 216
	} // library marker davegut.kasaCommunications, line 217
   	return encrCmd // library marker davegut.kasaCommunications, line 218
} // library marker davegut.kasaCommunications, line 219

private inputXOR(encrResponse) { // library marker davegut.kasaCommunications, line 221
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 222
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 223
	def key = 0xAB // library marker davegut.kasaCommunications, line 224
	def nextKey // library marker davegut.kasaCommunications, line 225
	byte[] XORtemp // library marker davegut.kasaCommunications, line 226
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 227
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 228
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 229
		key = nextKey // library marker davegut.kasaCommunications, line 230
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 231
	} // library marker davegut.kasaCommunications, line 232
	return cmdResponse // library marker davegut.kasaCommunications, line 233
} // library marker davegut.kasaCommunications, line 234

private outputXorTcp(command) { // library marker davegut.kasaCommunications, line 236
	def str = "" // library marker davegut.kasaCommunications, line 237
	def encrCmd = "000000" + Integer.toHexString(command.length())  // library marker davegut.kasaCommunications, line 238
 	def key = 0xAB // library marker davegut.kasaCommunications, line 239
	for (int i = 0; i < command.length(); i++) { // library marker davegut.kasaCommunications, line 240
		str = (command.charAt(i) as byte) ^ key // library marker davegut.kasaCommunications, line 241
		key = str // library marker davegut.kasaCommunications, line 242
		encrCmd += Integer.toHexString(str) // library marker davegut.kasaCommunications, line 243
	} // library marker davegut.kasaCommunications, line 244
   	return encrCmd // library marker davegut.kasaCommunications, line 245
} // library marker davegut.kasaCommunications, line 246

private inputXorTcp(resp) { // library marker davegut.kasaCommunications, line 248
	String[] strBytes = resp.substring(8).split("(?<=\\G.{2})") // library marker davegut.kasaCommunications, line 249
	def cmdResponse = "" // library marker davegut.kasaCommunications, line 250
	def key = 0xAB // library marker davegut.kasaCommunications, line 251
	def nextKey // library marker davegut.kasaCommunications, line 252
	byte[] XORtemp // library marker davegut.kasaCommunications, line 253
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.kasaCommunications, line 254
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative // library marker davegut.kasaCommunications, line 255
		XORtemp = nextKey ^ key // library marker davegut.kasaCommunications, line 256
		key = nextKey // library marker davegut.kasaCommunications, line 257
		cmdResponse += new String(XORtemp) // library marker davegut.kasaCommunications, line 258
	} // library marker davegut.kasaCommunications, line 259
	return cmdResponse // library marker davegut.kasaCommunications, line 260
} // library marker davegut.kasaCommunications, line 261

// ~~~~~ end include (4) davegut.kasaCommunications ~~~~~

// ~~~~~ start include (1) davegut.kasa_logging ~~~~~
library ( // library marker davegut.kasa_logging, line 1
	name: "kasa_logging", // library marker davegut.kasa_logging, line 2
	namespace: "davegut", // library marker davegut.kasa_logging, line 3
	author: "Dave Gutheinz", // library marker davegut.kasa_logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.kasa_logging, line 5
	category: "utilities", // library marker davegut.kasa_logging, line 6
	documentationLink: "" // library marker davegut.kasa_logging, line 7
) // library marker davegut.kasa_logging, line 8

def listAttributes() { // library marker davegut.kasa_logging, line 10
	def attrData = device.getCurrentStates() // library marker davegut.kasa_logging, line 11
	Map attrs = [:] // library marker davegut.kasa_logging, line 12
	attrData.each { // library marker davegut.kasa_logging, line 13
		attrs << ["${it.name}": it.value] // library marker davegut.kasa_logging, line 14
	} // library marker davegut.kasa_logging, line 15
	return attrs // library marker davegut.kasa_logging, line 16
} // library marker davegut.kasa_logging, line 17

def setLogsOff() { // library marker davegut.kasa_logging, line 19
	def logData = [logEnable: logEnable] // library marker davegut.kasa_logging, line 20
	if (logEnable) { // library marker davegut.kasa_logging, line 21
		runIn(1800, debugLogOff) // library marker davegut.kasa_logging, line 22
		logData << [debugLogOff: "scheduled"] // library marker davegut.kasa_logging, line 23
	} // library marker davegut.kasa_logging, line 24
	return logData // library marker davegut.kasa_logging, line 25
} // library marker davegut.kasa_logging, line 26

def logTrace(msg){ log.trace "${device.displayName}: ${msg}" } // library marker davegut.kasa_logging, line 28

def logInfo(msg) {  // library marker davegut.kasa_logging, line 30
	if (infoLog) { // library marker davegut.kasa_logging, line 31
		log.info "${device.displayName}: ${msg}" // library marker davegut.kasa_logging, line 32
	} // library marker davegut.kasa_logging, line 33
} // library marker davegut.kasa_logging, line 34

def debugLogOff() { // library marker davegut.kasa_logging, line 36
	device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.kasa_logging, line 37
	logInfo("debugLogOff") // library marker davegut.kasa_logging, line 38
} // library marker davegut.kasa_logging, line 39

def logDebug(msg) { // library marker davegut.kasa_logging, line 41
	if (logEnable) { // library marker davegut.kasa_logging, line 42
		log.debug "${device.displayName}: ${msg}" // library marker davegut.kasa_logging, line 43
	} // library marker davegut.kasa_logging, line 44
} // library marker davegut.kasa_logging, line 45

def logWarn(msg) { log.warn "${device.displayName}: ${msg}" } // library marker davegut.kasa_logging, line 47

def logError(msg) { log.error "${device.displayName}: ${msg}" } // library marker davegut.kasa_logging, line 49

// ~~~~~ end include (1) davegut.kasa_logging ~~~~~

// ~~~~~ start include (6) davegut.kasaLights ~~~~~
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
	transTime = checkTransTime(transTime) // library marker davegut.kasaLights, line 81
	sendCmd("""{"${service()}":{"${method()}":{"on_off":${onOff},""" + // library marker davegut.kasaLights, line 82
			""""transition_period":${transTime}}}}""") // library marker davegut.kasaLights, line 83
} // library marker davegut.kasaLights, line 84

def setLightLevel(level, transTime = 0) { // library marker davegut.kasaLights, line 86
	level = checkLevel(level) // library marker davegut.kasaLights, line 87
	if (level == 0) { // library marker davegut.kasaLights, line 88
		setLightOnOff(0, transTime) // library marker davegut.kasaLights, line 89
	} else { // library marker davegut.kasaLights, line 90
		transTime = checkTransTime(transTime) // library marker davegut.kasaLights, line 91
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" + // library marker davegut.kasaLights, line 92
				""""brightness":${level},"transition_period":${transTime}}}}""") // library marker davegut.kasaLights, line 93
	} // library marker davegut.kasaLights, line 94
} // library marker davegut.kasaLights, line 95

// ~~~~~ end include (6) davegut.kasaLights ~~~~~

// ~~~~~ start include (5) davegut.kasaEnergyMonitor ~~~~~
library ( // library marker davegut.kasaEnergyMonitor, line 1
	name: "kasaEnergyMonitor", // library marker davegut.kasaEnergyMonitor, line 2
	namespace: "davegut", // library marker davegut.kasaEnergyMonitor, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaEnergyMonitor, line 4
	description: "Kasa Device Energy Monitor Methods", // library marker davegut.kasaEnergyMonitor, line 5
	category: "energyMonitor", // library marker davegut.kasaEnergyMonitor, line 6
	documentationLink: "" // library marker davegut.kasaEnergyMonitor, line 7
) // library marker davegut.kasaEnergyMonitor, line 8

def setupEmFunction() { // library marker davegut.kasaEnergyMonitor, line 10
	if (emFunction && device.currentValue("currMonthTotal") > 0) { // library marker davegut.kasaEnergyMonitor, line 11
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 12
		return "Continuing EM Function" // library marker davegut.kasaEnergyMonitor, line 13
	} else if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 14
		zeroizeEnergyAttrs() // library marker davegut.kasaEnergyMonitor, line 15
		state.response = "" // library marker davegut.kasaEnergyMonitor, line 16
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 17
		//	Run order / delay is critical for successful operation. // library marker davegut.kasaEnergyMonitor, line 18
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 19
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 20
		return "Initialized" // library marker davegut.kasaEnergyMonitor, line 21
	} else if (emFunction && device.currentValue("power") != null) { // library marker davegut.kasaEnergyMonitor, line 22
		//	for power != null, EM had to be enabled at one time.  Set values to 0. // library marker davegut.kasaEnergyMonitor, line 23
		zeroizeEnergyAttrs() // library marker davegut.kasaEnergyMonitor, line 24
		state.remove("getEnergy") // library marker davegut.kasaEnergyMonitor, line 25
		return "Disabled" // library marker davegut.kasaEnergyMonitor, line 26
	} else { // library marker davegut.kasaEnergyMonitor, line 27
		return "Not initialized" // library marker davegut.kasaEnergyMonitor, line 28
	} // library marker davegut.kasaEnergyMonitor, line 29
} // library marker davegut.kasaEnergyMonitor, line 30

def scheduleEnergyAttrs() { // library marker davegut.kasaEnergyMonitor, line 32
	schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 33
	schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 34
	switch(energyPollInt) { // library marker davegut.kasaEnergyMonitor, line 35
		case "1 minute": // library marker davegut.kasaEnergyMonitor, line 36
			runEvery1Minute(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 37
			break // library marker davegut.kasaEnergyMonitor, line 38
		case "5 minutes": // library marker davegut.kasaEnergyMonitor, line 39
			runEvery5Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 40
			break // library marker davegut.kasaEnergyMonitor, line 41
		default: // library marker davegut.kasaEnergyMonitor, line 42
			runEvery30Minutes(getEnergyToday) // library marker davegut.kasaEnergyMonitor, line 43
	} // library marker davegut.kasaEnergyMonitor, line 44
} // library marker davegut.kasaEnergyMonitor, line 45

def zeroizeEnergyAttrs() { // library marker davegut.kasaEnergyMonitor, line 47
	sendEvent(name: "power", value: 0, unit: "W") // library marker davegut.kasaEnergyMonitor, line 48
	sendEvent(name: "energy", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 49
	sendEvent(name: "currMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 50
	sendEvent(name: "currMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 51
	sendEvent(name: "lastMonthTotal", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 52
	sendEvent(name: "lastMonthAvg", value: 0, unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 53
} // library marker davegut.kasaEnergyMonitor, line 54

def getDate() { // library marker davegut.kasaEnergyMonitor, line 56
	def currDate = new Date() // library marker davegut.kasaEnergyMonitor, line 57
	int year = currDate.format("yyyy").toInteger() // library marker davegut.kasaEnergyMonitor, line 58
	int month = currDate.format("M").toInteger() // library marker davegut.kasaEnergyMonitor, line 59
	int day = currDate.format("d").toInteger() // library marker davegut.kasaEnergyMonitor, line 60
	return [year: year, month: month, day: day] // library marker davegut.kasaEnergyMonitor, line 61
} // library marker davegut.kasaEnergyMonitor, line 62

def distEmeter(emeterResp) { // library marker davegut.kasaEnergyMonitor, line 64
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 65
	logDebug("distEmeter: ${emeterResp}, ${date}, ${state.getEnergy}") // library marker davegut.kasaEnergyMonitor, line 66
	def lastYear = date.year - 1 // library marker davegut.kasaEnergyMonitor, line 67
	if (emeterResp.get_realtime) { // library marker davegut.kasaEnergyMonitor, line 68
		setPower(emeterResp.get_realtime) // library marker davegut.kasaEnergyMonitor, line 69
	} else if (emeterResp.get_monthstat) { // library marker davegut.kasaEnergyMonitor, line 70
		def monthList = emeterResp.get_monthstat.month_list // library marker davegut.kasaEnergyMonitor, line 71
		if (state.getEnergy == "Today") { // library marker davegut.kasaEnergyMonitor, line 72
			setEnergyToday(monthList, date) // library marker davegut.kasaEnergyMonitor, line 73
		} else if (state.getEnergy == "This Month") { // library marker davegut.kasaEnergyMonitor, line 74
			setThisMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 75
		} else if (state.getEnergy == "Last Month") { // library marker davegut.kasaEnergyMonitor, line 76
			setLastMonth(monthList, date) // library marker davegut.kasaEnergyMonitor, line 77
		} else if (monthList == []) { // library marker davegut.kasaEnergyMonitor, line 78
			logDebug("distEmeter: monthList Empty. No data for year.") // library marker davegut.kasaEnergyMonitor, line 79
		} // library marker davegut.kasaEnergyMonitor, line 80
	} else { // library marker davegut.kasaEnergyMonitor, line 81
		logWarn("distEmeter: Unhandled response = ${emeterResp}") // library marker davegut.kasaEnergyMonitor, line 82
	} // library marker davegut.kasaEnergyMonitor, line 83
} // library marker davegut.kasaEnergyMonitor, line 84

def getPower() { // library marker davegut.kasaEnergyMonitor, line 86
	if (emFunction) { // library marker davegut.kasaEnergyMonitor, line 87
		if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 88
			getRealtime() // library marker davegut.kasaEnergyMonitor, line 89
		} else if (device.currentValue("power") != 0) { // library marker davegut.kasaEnergyMonitor, line 90
			sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 91
		} // library marker davegut.kasaEnergyMonitor, line 92
	} // library marker davegut.kasaEnergyMonitor, line 93
} // library marker davegut.kasaEnergyMonitor, line 94

def setPower(response) { // library marker davegut.kasaEnergyMonitor, line 96
	logDebug("setPower: ${response}") // library marker davegut.kasaEnergyMonitor, line 97
	def power = response.power // library marker davegut.kasaEnergyMonitor, line 98
	if (power == null) { power = response.power_mw / 1000 } // library marker davegut.kasaEnergyMonitor, line 99
	power = (power + 0.5).toInteger() // library marker davegut.kasaEnergyMonitor, line 100
	def curPwr = device.currentValue("power") // library marker davegut.kasaEnergyMonitor, line 101
	def pwrChange = false // library marker davegut.kasaEnergyMonitor, line 102
	if (curPwr != power) { // library marker davegut.kasaEnergyMonitor, line 103
		if (curPwr == null || (curPwr == 0 && power > 0)) { // library marker davegut.kasaEnergyMonitor, line 104
			pwrChange = true // library marker davegut.kasaEnergyMonitor, line 105
		} else { // library marker davegut.kasaEnergyMonitor, line 106
			def changeRatio = Math.abs((power - curPwr) / curPwr) // library marker davegut.kasaEnergyMonitor, line 107
			if (changeRatio > 0.03) { // library marker davegut.kasaEnergyMonitor, line 108
				pwrChange = true // library marker davegut.kasaEnergyMonitor, line 109
			} // library marker davegut.kasaEnergyMonitor, line 110
		} // library marker davegut.kasaEnergyMonitor, line 111
	} // library marker davegut.kasaEnergyMonitor, line 112
	if (pwrChange == true) { // library marker davegut.kasaEnergyMonitor, line 113
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital") // library marker davegut.kasaEnergyMonitor, line 114
	} // library marker davegut.kasaEnergyMonitor, line 115
} // library marker davegut.kasaEnergyMonitor, line 116

def getEnergyToday() { // library marker davegut.kasaEnergyMonitor, line 118
	if (device.currentValue("switch") == "on") { // library marker davegut.kasaEnergyMonitor, line 119
		state.getEnergy = "Today" // library marker davegut.kasaEnergyMonitor, line 120
		def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 121
		logDebug("getEnergyToday: ${year}") // library marker davegut.kasaEnergyMonitor, line 122
		runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 123
	} // library marker davegut.kasaEnergyMonitor, line 124
} // library marker davegut.kasaEnergyMonitor, line 125

def setEnergyToday(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 127
	logDebug("setEnergyToday: ${date}, ${monthList}") // library marker davegut.kasaEnergyMonitor, line 128
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 129
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 130
	def energy = 0 // library marker davegut.kasaEnergyMonitor, line 131
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 132
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 133
	} else { // library marker davegut.kasaEnergyMonitor, line 134
		energy = data.energy // library marker davegut.kasaEnergyMonitor, line 135
		if (energy == null) { energy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 136
		energy = Math.round(100*energy)/100 - device.currentValue("currMonthTotal") // library marker davegut.kasaEnergyMonitor, line 137
	} // library marker davegut.kasaEnergyMonitor, line 138
	if (device.currentValue("energy") != energy) { // library marker davegut.kasaEnergyMonitor, line 139
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 140
		status << [energy: energy] // library marker davegut.kasaEnergyMonitor, line 141
	} // library marker davegut.kasaEnergyMonitor, line 142
	if (status != [:]) { logInfo("setEnergyToday: ${status}") } // library marker davegut.kasaEnergyMonitor, line 143
	if (!state.getEnergy) { // library marker davegut.kasaEnergyMonitor, line 144
		schedule("10 0 0 * * ?", getEnergyThisMonth) // library marker davegut.kasaEnergyMonitor, line 145
		schedule("15 2 0 1 * ?", getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 146
		state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 147
		getEnergyThisMonth() // library marker davegut.kasaEnergyMonitor, line 148
		runIn(10, getEnergyLastMonth) // library marker davegut.kasaEnergyMonitor, line 149
	} // library marker davegut.kasaEnergyMonitor, line 150
} // library marker davegut.kasaEnergyMonitor, line 151

def getEnergyThisMonth() { // library marker davegut.kasaEnergyMonitor, line 153
	state.getEnergy = "This Month" // library marker davegut.kasaEnergyMonitor, line 154
	def year = getDate().year // library marker davegut.kasaEnergyMonitor, line 155
	logDebug("getEnergyThisMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 156
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 157
} // library marker davegut.kasaEnergyMonitor, line 158

def setThisMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 160
	logDebug("setThisMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 161
	def data = monthList.find { it.month == date.month && it.year == date.year} // library marker davegut.kasaEnergyMonitor, line 162
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 163
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 164
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 165
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 166
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 167
	} else { // library marker davegut.kasaEnergyMonitor, line 168
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 169
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 170
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 171
		if (date.day == 1) { // library marker davegut.kasaEnergyMonitor, line 172
			avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 173
		} else { // library marker davegut.kasaEnergyMonitor, line 174
			avgEnergy = totEnergy /(date.day - 1) // library marker davegut.kasaEnergyMonitor, line 175
		} // library marker davegut.kasaEnergyMonitor, line 176
	} // library marker davegut.kasaEnergyMonitor, line 177
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 178
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 179
	sendEvent(name: "currMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 180
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 181
	status << [currMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 182
	sendEvent(name: "currMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 183
		 	 descriptionText: "KiloWatt Hours per Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 184
	status << [currMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 185
	getEnergyToday() // library marker davegut.kasaEnergyMonitor, line 186
	logInfo("setThisMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 187
} // library marker davegut.kasaEnergyMonitor, line 188

def getEnergyLastMonth() { // library marker davegut.kasaEnergyMonitor, line 190
	state.getEnergy = "Last Month" // library marker davegut.kasaEnergyMonitor, line 191
	def date = getDate() // library marker davegut.kasaEnergyMonitor, line 192
	def year = date.year // library marker davegut.kasaEnergyMonitor, line 193
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 194
		year = year - 1 // library marker davegut.kasaEnergyMonitor, line 195
	} // library marker davegut.kasaEnergyMonitor, line 196
	logDebug("getEnergyLastMonth: ${year}") // library marker davegut.kasaEnergyMonitor, line 197
	runIn(5, getMonthstat, [data: year]) // library marker davegut.kasaEnergyMonitor, line 198
} // library marker davegut.kasaEnergyMonitor, line 199

def setLastMonth(monthList, date) { // library marker davegut.kasaEnergyMonitor, line 201
	logDebug("setLastMonth: ${date} // ${monthList}") // library marker davegut.kasaEnergyMonitor, line 202
	def lastMonthYear = date.year // library marker davegut.kasaEnergyMonitor, line 203
	def lastMonth = date.month - 1 // library marker davegut.kasaEnergyMonitor, line 204
	if (date.month == 1) { // library marker davegut.kasaEnergyMonitor, line 205
		lastMonthYear -+ 1 // library marker davegut.kasaEnergyMonitor, line 206
		lastMonth = 12 // library marker davegut.kasaEnergyMonitor, line 207
	} // library marker davegut.kasaEnergyMonitor, line 208
	def data = monthList.find { it.month == lastMonth } // library marker davegut.kasaEnergyMonitor, line 209
	def status = [:] // library marker davegut.kasaEnergyMonitor, line 210
	def totEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 211
	def avgEnergy = 0 // library marker davegut.kasaEnergyMonitor, line 212
	if (data == null) { // library marker davegut.kasaEnergyMonitor, line 213
		status << [msgError: "Return Data Null"] // library marker davegut.kasaEnergyMonitor, line 214
	} else { // library marker davegut.kasaEnergyMonitor, line 215
		status << [msgError: "OK"] // library marker davegut.kasaEnergyMonitor, line 216
		def monthLength // library marker davegut.kasaEnergyMonitor, line 217
		switch(lastMonth) { // library marker davegut.kasaEnergyMonitor, line 218
			case 4: // library marker davegut.kasaEnergyMonitor, line 219
			case 6: // library marker davegut.kasaEnergyMonitor, line 220
			case 9: // library marker davegut.kasaEnergyMonitor, line 221
			case 11: // library marker davegut.kasaEnergyMonitor, line 222
				monthLength = 30 // library marker davegut.kasaEnergyMonitor, line 223
				break // library marker davegut.kasaEnergyMonitor, line 224
			case 2: // library marker davegut.kasaEnergyMonitor, line 225
				monthLength = 28 // library marker davegut.kasaEnergyMonitor, line 226
				if (lastMonthYear == 2020 || lastMonthYear == 2024 || lastMonthYear == 2028) {  // library marker davegut.kasaEnergyMonitor, line 227
					monthLength = 29 // library marker davegut.kasaEnergyMonitor, line 228
				} // library marker davegut.kasaEnergyMonitor, line 229
				break // library marker davegut.kasaEnergyMonitor, line 230
			default: // library marker davegut.kasaEnergyMonitor, line 231
				monthLength = 31 // library marker davegut.kasaEnergyMonitor, line 232
		} // library marker davegut.kasaEnergyMonitor, line 233
		totEnergy = data.energy // library marker davegut.kasaEnergyMonitor, line 234
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 } // library marker davegut.kasaEnergyMonitor, line 235
		avgEnergy = totEnergy / monthLength // library marker davegut.kasaEnergyMonitor, line 236
	} // library marker davegut.kasaEnergyMonitor, line 237
	totEnergy = Math.round(100*totEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 238
	avgEnergy = Math.round(100*avgEnergy)/100 // library marker davegut.kasaEnergyMonitor, line 239
	sendEvent(name: "lastMonthTotal", value: totEnergy,  // library marker davegut.kasaEnergyMonitor, line 240
			  descriptionText: "KiloWatt Hours", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 241
	status << [lastMonthTotal: totEnergy] // library marker davegut.kasaEnergyMonitor, line 242
	sendEvent(name: "lastMonthAvg", value: avgEnergy,  // library marker davegut.kasaEnergyMonitor, line 243
			  descriptionText: "KiloWatt Hoursper Day", unit: "KWH") // library marker davegut.kasaEnergyMonitor, line 244
	status << [lastMonthAvg: avgEnergy] // library marker davegut.kasaEnergyMonitor, line 245
	logInfo("setLastMonth: ${status}") // library marker davegut.kasaEnergyMonitor, line 246
} // library marker davegut.kasaEnergyMonitor, line 247

def getRealtime() { // library marker davegut.kasaEnergyMonitor, line 249
	def feature = getDataValue("feature") // library marker davegut.kasaEnergyMonitor, line 250
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 251
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 252
				""""emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 253
	} else if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaEnergyMonitor, line 254
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 255
	} else { // library marker davegut.kasaEnergyMonitor, line 256
		sendCmd("""{"emeter":{"get_realtime":{}}}""") // library marker davegut.kasaEnergyMonitor, line 257
	} // library marker davegut.kasaEnergyMonitor, line 258
} // library marker davegut.kasaEnergyMonitor, line 259

def getMonthstat(year) { // library marker davegut.kasaEnergyMonitor, line 261
	def feature = getDataValue("feature") // library marker davegut.kasaEnergyMonitor, line 262
	if (getDataValue("plugNo") != null) { // library marker davegut.kasaEnergyMonitor, line 263
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaEnergyMonitor, line 264
				""""emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 265
	} else if (feature.contains("Bulb") || feature == "lightStrip") { // library marker davegut.kasaEnergyMonitor, line 266
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 267
	} else { // library marker davegut.kasaEnergyMonitor, line 268
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""") // library marker davegut.kasaEnergyMonitor, line 269
	} // library marker davegut.kasaEnergyMonitor, line 270
} // library marker davegut.kasaEnergyMonitor, line 271

// ~~~~~ end include (5) davegut.kasaEnergyMonitor ~~~~~
