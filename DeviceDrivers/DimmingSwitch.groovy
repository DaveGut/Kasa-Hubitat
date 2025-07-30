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
	definition (name: "Kasa Dimming Switch",
				namespace: nameSpace(),
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Switch Level"
		capability "Level Preset"
		capability "Change Level"
		command "ledOn"
		command "ledOff"
		attribute "led", "string"
	}
	preferences {
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
		commonPrefs()
	}
}

def installed() {
	def instStatus = installCommon()
	runIn(1, getDimmerConfiguration)
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
	logDebug("stopLevelChange: [level: ${device.currentValue("level")}]")
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
		sendEvent(name: "switch", value: onOff, type: state.eventType)
		logData << [switch: onOff]
	}
	if (device.currentValue("level") != status.brightness) {
		sendEvent(name: "level", value: status.brightness, type: state.eventType)
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
	state.eventType = "physical"
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
	state.eventType = "digital"
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
	state.eventType = "digital"
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

// ~~~~~ start include (324) davegut.kasaPlugs ~~~~~
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
	state.eventType = "digital" // library marker davegut.kasaPlugs, line 52
	logDebug("setRelayState: [switch: ${onOff}]") // library marker davegut.kasaPlugs, line 53
	if (getDataValue("plugNo") == null) { // library marker davegut.kasaPlugs, line 54
		sendCmd("""{"system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 55
	} else { // library marker davegut.kasaPlugs, line 56
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" + // library marker davegut.kasaPlugs, line 57
				""""system":{"set_relay_state":{"state":${onOff}}}}""") // library marker davegut.kasaPlugs, line 58
	} // library marker davegut.kasaPlugs, line 59
} // library marker davegut.kasaPlugs, line 60

def setLedOff(onOff) { // library marker davegut.kasaPlugs, line 62
	logDebug("setLedOff: [ledOff: ${onOff}]") // library marker davegut.kasaPlugs, line 63
		sendCmd("""{"system":{"set_led_off":{"off":${onOff}}}}""") // library marker davegut.kasaPlugs, line 64
} // library marker davegut.kasaPlugs, line 65

// ~~~~~ end include (324) davegut.kasaPlugs ~~~~~
