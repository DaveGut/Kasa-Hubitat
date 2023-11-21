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
	definition (name: "Kasa Dimming Switch",
				namespace: nameSpace(),
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Configuration"
		capability "Switch Level"
		capability "Level Preset"
		capability "Change Level"
		command "ledOn"
		command "ledOff"
		attribute "led", "string"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
						  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
						  "30 minutes"],
			type: "ENUM"]]
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
		input ("gentleOn", "number",
			   title: "Gentle On (max 7000 msec)",
			   defaultValue:5000,
			   range: 0 .. 7100)
		input ("gentleOff", "number",
			   title: "Gentle Off (max 7000 msec)",
			   defaultValue:5000,
			   range: 0 .. 7100)
		def fadeOpts = [0: "Instant",  1000: "Fast",
						2000: "Medium", 3000: "Slow"]
		input ("fadeOn", "enum",
			   title: "Fade On",
			   defaultValue:"Fast",
			   options: fadeOpts)
		input ("fadeOff", "enum",
			   title: "Fade Off",
			   defaultValue:"Fast",
			   options: fadeOpts)
		def pressOpts = ["none",  "instant_on_off", "gentle_on_off",
						 "Preset 0", "Preset 1", "Preset 2", "Preset 3"]
		input ("longPress", "enum", title: "Long Press Action",
			   defaultValue: "gentle_on_off",
			   options: pressOpts)
		input ("doubleClick", "enum", title: "Double Tap Action",
			   defaultValue: "Preset 1",
			   options: pressOpts)
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
	def instStatus = installCommon()
	pauseExecution(3000)
	getDimmerConfiguration()
	logInfo("installed: ${instStatus}")
}

def updated() {
	def updStatus = updateCommon()
	configureDimmer()
	logInfo("updated: ${updStatus}")
	refresh()
}

def configureDimmer() {
	logDebug("configureDimmer")
	if (longPress == null || doubleClick == null || gentleOn == null
	    || gentleOff == null || fadeOff == null || fadeOn == null) {
		def dimmerSet = getDimmerConfiguration()
		pauseExecution(2000)
	}
	sendCmd("""{"smartlife.iot.dimmer":{"set_gentle_on_time":{"duration": ${gentleOn}}, """ +
			""""set_gentle_off_time":{"duration": ${gentleOff}}, """ +
			""""set_fade_on_time":{"fadeTime": ${fadeOn}}, """ +
			""""set_fade_off_time":{"fadeTime": ${fadeOff}}}}""")
	pauseExecution(2000)

	def action1 = """{"mode":"${longPress}"}"""
	if (longPress.contains("Preset")) {
		action1 = """{"mode":"customize_preset","index":${longPress[-1].toInteger()}}"""
	}
	def action2 = """{"mode":"${doubleClick}"}"""
	if (doubleClick.contains("Preset")) {
		action2 = """{"mode":"customize_preset","index":${doubleClick[-1].toInteger()}}"""
	}
	sendCmd("""{"smartlife.iot.dimmer":{"set_double_click_action":${action2}, """ +
			""""set_long_press_action":${action1}}}""")

	runIn(1, getDimmerConfiguration)
}

def setDimmerConfig(response) {
	logDebug("setDimmerConfiguration: ${response}")
	def params
	def dimmerConfig = [:]
	if (response["get_dimmer_parameters"]) {
		params = response["get_dimmer_parameters"]
		if (params.err_code == "0") {
			logWarn("setDimmerConfig: Error in getDimmerParams: ${params}")
		} else {
			def fadeOn = getFade(params.fadeOnTime.toInteger())
			def fadeOff = getFade(params.fadeOffTime.toInteger())
			device.updateSetting("fadeOn", [type:"integer", value: fadeOn])
			device.updateSetting("fadeOff", [type:"integer", value: fadeOff])
			device.updateSetting("gentleOn", [type:"integer", value: params.gentleOnTime])
			device.updateSetting("gentleOff", [type:"integer", value: params.gentleOffTime])
			dimmerConfig << [fadeOn: fadeOn, fadeOff: fadeOff,
							 genleOn: gentleOn, gentleOff: gentleOff]
		}
	}
	if (response["get_default_behavior"]) {
		params = response["get_default_behavior"]
		if (params.err_code == "0") {
			logWarn("setDimmerConfig: Error in getDefaultBehavior: ${params}")
		} else {
			def longPress = params.long_press.mode
			if (params.long_press.index != null) { longPress = "Preset ${params.long_press.index}" }
			device.updateSetting("longPress", [type:"enum", value: longPress])
			def doubleClick = params.double_click.mode
			if (params.double_click.index != null) { doubleClick = "Preset ${params.double_click.index}" }
			device.updateSetting("doubleClick", [type:"enum", value: doubleClick])
			dimmerConfig << [longPress: longPress, doubleClick: doubleClick]
		}
	}
	logInfo("setDimmerConfig: ${dimmerConfig}")
}

def getFade(fadeTime) {
	def fadeSpeed = "Instant"
	if (fadeTime == 1000) {
		fadeSpeed = "Fast"
	} else if (fadeTime == 2000) {
		fadeSpeed = "Medium"
	} else if (fadeTime == 3000) {
		fadeSpeed = "Slow"
	}
	return fadeSpeed
}

def setLevel(level, transTime = gentleOn/1000) {
	setDimmerTransition(level, transTime)
	def updates = [:]
	updates << [switch: "on", level: level]
	sendEvent(name: "switch", value: "on", type: "digital")
	sendEvent(name: "level", value: level, type: "digital")
	logInfo("setLevel: ${updates}")
	runIn(9, getSysinfo)
}

def presetLevel(level) {
	presetBrightness(level)
}

def startLevelChange(direction) {
	logDebug("startLevelChange: [level: ${device.currentValue("level")}, direction: ${direction}]")
	if (device.currentValue("switch") == "off") {
		setRelayState(1)
		pauseExecution(1000)
	}
	if (direction == "up") { levelUp() }
	else { levelDown() }
}

def stopLevelChange() {
	logDebug("startLevelChange: [level: ${device.currentValue("level")}]")
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 100) { return }
	def newLevel = curLevel + 4
	if (newLevel > 100) { newLevel = 100 }
	presetBrightness(newLevel)
	runIn(1, levelUp)
}

def levelDown() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 0 || device.currentValue("switch") == "off") { return }
	def newLevel = curLevel - 4
	if (newLevel <= 0) { off() }
	else {
		presetBrightness(newLevel)
		runIn(1, levelDown)
	}
}

def setSysInfo(status) {
	def switchStatus = status.relay_state
	def logData = [:]
	def onOff = "on"
	if (switchStatus == 0) { onOff = "off" }
	if (device.currentValue("switch") != onOff) {
		sendEvent(name: "switch", value: onOff, type: "digital")
		logData << [switch: onOff]
	}
	if (device.currentValue("level") != status.brightness) {
		sendEvent(name: "level", value: status.brightness, type: "digital")
		logData << [level: status.brightness]
	}
	def ledStatus = status.led_off
	def ledOnOff = "on"
	if (ledStatus == 1) { ledOnOff = "off" }
	if (device.currentValue("led") != ledOnOff) {
		sendEvent(name: "led", value: ledOnOff)
		logData << [led: ledOnOff]
	}

	if (logData != [:]) {
		logInfo("setSysinfo: ${logData}")
	}
	if (nameSync == "device") {
		updateName(status)
	}
}

def checkTransTime(transTime) {
	if (transTime == null || transTime < 0.001) {
		transTime = gentleOn
	} else if (transTime == 0) {
		transTime = 50
	} else {
		transTime = transTime * 1000
	}
	
	if (transTime > 8000) { transTime = 8000 }
	return transTime.toInteger()
}

def checkLevel(level) {
	if (level == null || level < 0) {
		level = device.currentValue("level")
		logWarn("checkLevel: Entered level null or negative. Level set to ${level}")
	} else if (level > 100) {
		level = 100
		logWarn("checkLevel: Entered level > 100.  Level set to ${level}")
	}
	return level
}

def setDimmerTransition(level, transTime) {
	level = checkLevel(level)
	transTime = checkTransTime(transTime)
	logDebug("setDimmerTransition: [level: ${level}, transTime: ${transTime}]")
	if (level == 0) {
		setRelayState(0)
	} else {
		sendCmd("""{"smartlife.iot.dimmer":{"set_dimmer_transition":{"brightness":${level},""" +
				""""duration":${transTime}}}}""")
	}
}

def presetBrightness(level) {
	level = checkLevel(level)
	logDebug("presetLevel: [level: ${level}]")
	sendCmd("""{"smartlife.iot.dimmer":{"set_brightness":{"brightness":${level}}},""" +
			""""system" :{"get_sysinfo" :{}}}""")
}

def getDimmerConfiguration() {
	logDebug("getDimmerConfiguration")
	sendCmd("""{"smartlife.iot.dimmer":{"get_dimmer_parameters":{}, """ +
			""""get_default_behavior":{}}}""")
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

// ~~~~~ start include (7) davegut.kasaPlugs ~~~~~
library ( // library marker davegut.kasaPlugs, line 1
	name: "kasaPlugs", // library marker davegut.kasaPlugs, line 2
	namespace: "davegut", // library marker davegut.kasaPlugs, line 3
	author: "Dave Gutheinz", // library marker davegut.kasaPlugs, line 4
	description: "Kasa Plug and Switches Common Methods", // library marker davegut.kasaPlugs, line 5
	category: "utilities", // library marker davegut.kasaPlugs, line 6
	documentationLink: "" // library marker davegut.kasaPlugs, line 7
) // library marker davegut.kasaPlugs, line 8

def on() { setRelayState(1) } // library marker davegut.kasaPlugs, line 10

def off() { setRelayState(0) } // library marker davegut.kasaPlugs, line 12

def ledOn() { setLedOff(0) } // library marker davegut.kasaPlugs, line 14

def ledOff() { setLedOff(1) } // library marker davegut.kasaPlugs, line 16

def distResp(response) { // library marker davegut.kasaPlugs, line 18
	if (response.system) { // library marker davegut.kasaPlugs, line 19
		if (response.system.get_sysinfo) { // library marker davegut.kasaPlugs, line 20
			setSysInfo(response.system.get_sysinfo) // library marker davegut.kasaPlugs, line 21
		} else if (response.system.set_relay_state || // library marker davegut.kasaPlugs, line 22
				   response.system.set_led_off) { // library marker davegut.kasaPlugs, line 23
			if (getDataValue("model") == "HS210") { // library marker davegut.kasaPlugs, line 24
				runIn(2, getSysinfo) // library marker davegut.kasaPlugs, line 25
			} else { // library marker davegut.kasaPlugs, line 26
				getSysinfo() // library marker davegut.kasaPlugs, line 27
			} // library marker davegut.kasaPlugs, line 28
		} else if (response.system.reboot) { // library marker davegut.kasaPlugs, line 29
			logWarn("distResp: Rebooting device.") // library marker davegut.kasaPlugs, line 30
		} else if (response.system.set_dev_alias) { // library marker davegut.kasaPlugs, line 31
			updateName(response.system.set_dev_alias) // library marker davegut.kasaPlugs, line 32
		} else { // library marker davegut.kasaPlugs, line 33
			logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 34
		} // library marker davegut.kasaPlugs, line 35
	} else if (response["smartlife.iot.dimmer"]) { // library marker davegut.kasaPlugs, line 36
		if (response["smartlife.iot.dimmer"].get_dimmer_parameters) { // library marker davegut.kasaPlugs, line 37
			setDimmerConfig(response["smartlife.iot.dimmer"]) // library marker davegut.kasaPlugs, line 38
		} else { // library marker davegut.kasaPlugs, line 39
			logDebug("distResp: Unhandled response: ${response["smartlife.iot.dimmer"]}") // library marker davegut.kasaPlugs, line 40
		} // library marker davegut.kasaPlugs, line 41
	} else if (response.emeter) { // library marker davegut.kasaPlugs, line 42
		distEmeter(response.emeter) // library marker davegut.kasaPlugs, line 43
	} else if (response.cnCloud) { // library marker davegut.kasaPlugs, line 44
		setBindUnbind(response.cnCloud) // library marker davegut.kasaPlugs, line 45
	} else { // library marker davegut.kasaPlugs, line 46
		logDebug("distResp: Unhandled response = ${response}") // library marker davegut.kasaPlugs, line 47
	} // library marker davegut.kasaPlugs, line 48
} // library marker davegut.kasaPlugs, line 49

def setRelayState(onOff) { // library marker davegut.kasaPlugs, line 51
	logDebug("setRelayState: [switch: ${onOff}]") // library marker davegut.kasaPlugs, line 52
	if (getDataValue("plugNo") == null) { // library marker davegut.kasaPlugs, line 53
		sendCmd("""{"system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 54
	} else { // library marker davegut.kasaPlugs, line 55
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaPlugs, line 56
				""""system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 57
	} // library marker davegut.kasaPlugs, line 58
} // library marker davegut.kasaPlugs, line 59

def setLedOff(onOff) { // library marker davegut.kasaPlugs, line 61
	logDebug("setLedOff: [ledOff: ${onOff}]") // library marker davegut.kasaPlugs, line 62
		sendCmd("""{"system":{"set_led_off":{"off":${onOff}}}}""") // library marker davegut.kasaPlugs, line 63
} // library marker davegut.kasaPlugs, line 64

// ~~~~~ end include (7) davegut.kasaPlugs ~~~~~
