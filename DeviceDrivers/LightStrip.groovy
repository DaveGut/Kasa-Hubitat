/*	Kasa/Tapo Integration Driver: TpLink Lightstrip
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
*/ 

String nameSpace() { return "davegut" }
String version() { return "2.5.3" }


import groovy.transform.Field
@Field static final String DEV_TYPE = "Light Strip"
@Field static final String EM_SYS = "smartlife.iot.common.emeter"
@Field static final Boolean IS_CHILD = false
@Field static final String LIGHT_TRANS_TYPE = "transition_period"
@Field static final String SYSTEM = "smartlife.iot.lightStrip"
@Field static final String LIGHT_CMD = "set_light_state"

metadata {
	definition (name: "Kasa Light Strip", 
				namespace: nameSpace(), 
				author: "Dave Gutheinz", 
				singleThreaded: true
			   )
	{
		capability "Light"
	}
	preferences {
		emPrefs()
		levelPrefs()
		commonPrefs()
	}
}

def installed() {
	Map instStatus = [method: "installed"]
	instStatus << commonInstalled()
	logInfo(instStatus)
	runIn(5, updated)
	deviceHandshake()
}

def updated() {
	Map updStatus = [method: "updated"]
	unschedule()
	updStatus << [commonUpdated: commonUpdated()]
	updStatus << [levelUpdated: levelUpdated()]
	if (getDataValue("isEm")) { updStatus << [emUpdated: emUpdated()] }
	logInfo(updStatus)
}

def setColorLight(onOff, level, colorTemp, hue, saturation) {
	Map logData = [method: "setColorLight", onOff: onOff, level: level,
				   colorTemp: colorTemp, hue: hue, satuartion: saturation]
	sendEvent(name: "switch", value: onOff, type: state.eventType)
	if (onOff == "on") {
		sendEvent(name: "level", value: level)
		sendEvent(name: "colorTemperature", value: colorTemp)
		int hubHue = (hue / 3.6).toInteger()
		sendEvent(name: "hue", value: hubHue)
		sendEvent(name: "saturation", value: saturation)
		if (colorTemp > 0) {
			sendEvent(name: "colorMode", value: "CT")
			def colorName = convertTemperatureToGenericColorName(colorTemp)
			sendEvent(name: "colorName", value: colorName)
			sendEvent(name: "color", value: [:])
			sendEvent(name: "RGB", value: [])
		} else {
			sendEvent(name: "colorMode", value: "COLOR")
			def colorName = convertHueToGenericColorName(hubHue)
			sendEvent(name: "colorName", value: colorName)
			Map color = [hue: hubHue, saturation: saturation, level: level]
			sendEvent(name: "color", value: color)
			rgb = hubitat.helper.ColorUtils.hsvToRGB([hubHue, saturation, level])
			sendEvent(name: "RGB", value: rgb)
		}
	}
	logDebug(logData)
}

//	===== Smart Methods =====
def parse_get_device_info(devData) {
	def onOff = "off"
	if (devData.device_on == true ) { onOff = "on" }
	int level = devData.brightness.toInteger()
	def colorTemp = devData.color_temp.toInteger()
	int hue = devData.hue.toInteger()
	int saturation = devData.saturation.toInteger()
	setColorLight(onOff, level, colorTemp, hue, saturation)
}

//	===== Iot Methods =====
def parse_system(devData) {
	def onOff = "on"
	if (devData.on_off == 0) {
		onOff = "off"
		devData = devData.dft_on_state
	}
	int level = devData.brightness.toInteger()
	int hue = devData.hue.toInteger()
	int saturation = devData.saturation.toInteger()
	int colorTemp = devData.color_temp.toInteger()
	setColorLight(onOff, level, colorTemp, hue, saturation)
}

//	===== Include Libraries =====















// ~~~~~ start include (1074) tpLink.tpl_cap_switch ~~~~~
library ( // library marker tpLink.tpl_cap_switch, line 1
	name: "tpl_cap_switch", // library marker tpLink.tpl_cap_switch, line 2
	namespace: "tpLink", // library marker tpLink.tpl_cap_switch, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_cap_switch, line 4
	description: "Kasa-Tapo switch on/off. Hub V 2.5.3", // library marker tpLink.tpl_cap_switch, line 5
	category: "utilities", // library marker tpLink.tpl_cap_switch, line 6
	documentationLink: "" // library marker tpLink.tpl_cap_switch, line 7
) // library marker tpLink.tpl_cap_switch, line 8

capability "Switch" // library marker tpLink.tpl_cap_switch, line 10

def on() { setOnOff("on") } // library marker tpLink.tpl_cap_switch, line 12

def off() { setOnOff("off") } // library marker tpLink.tpl_cap_switch, line 14

def setOnOff(onOff) { // library marker tpLink.tpl_cap_switch, line 16
	def api = getDataValue("api") // library marker tpLink.tpl_cap_switch, line 17
	Map logData = [method: "setOnOff", api:api, switch: onOff] // library marker tpLink.tpl_cap_switch, line 18
	if (api == "smart") { // library marker tpLink.tpl_cap_switch, line 19
		setSmartOnOff(onOff) // library marker tpLink.tpl_cap_switch, line 20
	} else { // library marker tpLink.tpl_cap_switch, line 21
		setIotOnOff(onOff) // library marker tpLink.tpl_cap_switch, line 22
	} // library marker tpLink.tpl_cap_switch, line 23
	logDebug(logData) // library marker tpLink.tpl_cap_switch, line 24
} // library marker tpLink.tpl_cap_switch, line 25

//	===== Smart API Calls ===== // library marker tpLink.tpl_cap_switch, line 27
def setSmartOnOff(onOff) { // library marker tpLink.tpl_cap_switch, line 28
	def smartOnOff = true // library marker tpLink.tpl_cap_switch, line 29
	if (onOff == "off") { smartOnOff = false } // library marker tpLink.tpl_cap_switch, line 30
	Map params = [device_on: smartOnOff] // library marker tpLink.tpl_cap_switch, line 31
	if (transTime) { // library marker tpLink.tpl_cap_switch, line 32
		int tTime = 1000 * transTime.toInteger() // library marker tpLink.tpl_cap_switch, line 33
		if (tTime == 0) { tTime = 10 } // library marker tpLink.tpl_cap_switch, line 34
		params << [transition_period: tTime] // library marker tpLink.tpl_cap_switch, line 35
	} // library marker tpLink.tpl_cap_switch, line 36
	List requests = [[method: "set_device_info", params: params], // library marker tpLink.tpl_cap_switch, line 37
					 [method: "get_device_info"]] // library marker tpLink.tpl_cap_switch, line 38
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_switch, line 39
	if (IS_CHILD) { // library marker tpLink.tpl_cap_switch, line 40
		state.eventType = "digital" // library marker tpLink.tpl_cap_switch, line 41
		Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_switch, line 42
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_switch, line 43
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_switch, line 44
	} else { // library marker tpLink.tpl_cap_switch, line 45
		sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_cap_switch, line 46
	} // library marker tpLink.tpl_cap_switch, line 47
	if (emEnable) { runIn(5, getSmartPower) } // library marker tpLink.tpl_cap_switch, line 48
} // library marker tpLink.tpl_cap_switch, line 49

//	===== IOT API Calls	===== // library marker tpLink.tpl_cap_switch, line 51
//	All IOT Hubitat child devices are childPlugSwitch. // library marker tpLink.tpl_cap_switch, line 52
def setIotOnOff(onOff) { // library marker tpLink.tpl_cap_switch, line 53
	state.eventType = "digital" // library marker tpLink.tpl_cap_switch, line 54
	def onOffState = 1 // library marker tpLink.tpl_cap_switch, line 55
	if (onOff == "off") { onOffState = 0 } // library marker tpLink.tpl_cap_switch, line 56
	Map cmdBody = [:] // library marker tpLink.tpl_cap_switch, line 57
	if (getDataValue("plugNo") || DEV_TYPE == "Child Plug Switch") { // library marker tpLink.tpl_cap_switch, line 58
		//	Handle legacy multiplug (plugNo) and new installs (Child Plug Switch) // library marker tpLink.tpl_cap_switch, line 59
		cmdBody << [context: [child_ids:[getDataValue("deviceId")]]] // library marker tpLink.tpl_cap_switch, line 60
	} // library marker tpLink.tpl_cap_switch, line 61
	if (DEV_TYPE == "Plug Switch" || DEV_TYPE == "Dimming Switch" || IS_CHILD) { // library marker tpLink.tpl_cap_switch, line 62
			cmdBody << [system: [set_relay_state: [state: onOffState], get_sysinfo: []]] // library marker tpLink.tpl_cap_switch, line 63
	} else { // library marker tpLink.tpl_cap_switch, line 64
		int tTime = 1000 * transTime.toInteger() // library marker tpLink.tpl_cap_switch, line 65
		if (tTime == 0) { tTime = 10 } // library marker tpLink.tpl_cap_switch, line 66
		Map transData = [on_off: onOffState, "${LIGHT_TRANS_TYPE}": tTime] // library marker tpLink.tpl_cap_switch, line 67
		cmdBody = ["${SYSTEM}": ["${LIGHT_CMD}": transData]] // library marker tpLink.tpl_cap_switch, line 68
	} // library marker tpLink.tpl_cap_switch, line 69
	if (IS_CHILD) { // library marker tpLink.tpl_cap_switch, line 70
		Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_switch, line 71
						   dni: device.deviceNetworkId, api: "iot"] // library marker tpLink.tpl_cap_switch, line 72
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_switch, line 73
	} else { // library marker tpLink.tpl_cap_switch, line 74
		sendIotCmd(cmdBody, true) // library marker tpLink.tpl_cap_switch, line 75
	} // library marker tpLink.tpl_cap_switch, line 76
	if (emEnable) { runIn(5, iotGetRealtime) } // library marker tpLink.tpl_cap_switch, line 77
} // library marker tpLink.tpl_cap_switch, line 78

// ~~~~~ end include (1074) tpLink.tpl_cap_switch ~~~~~

// ~~~~~ start include (1075) tpLink.tpl_cap_level ~~~~~
library ( // library marker tpLink.tpl_cap_level, line 1
	name: "tpl_cap_level", // library marker tpLink.tpl_cap_level, line 2
	namespace: "tpLink", // library marker tpLink.tpl_cap_level, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_cap_level, line 4
	description: "Kasa-taop switch level and change level. Hub V 2.5.3", // library marker tpLink.tpl_cap_level, line 5
	category: "utilities", // library marker tpLink.tpl_cap_level, line 6
	documentationLink: "" // library marker tpLink.tpl_cap_level, line 7
) // library marker tpLink.tpl_cap_level, line 8

capability "Switch Level" // library marker tpLink.tpl_cap_level, line 10
capability "Change Level" // library marker tpLink.tpl_cap_level, line 11

def levelPrefs() { // library marker tpLink.tpl_cap_level, line 13
	input ("transTime", "number", defaultValue: 1, range: [0..10], // library marker tpLink.tpl_cap_level, line 14
		   title: "lighting transition time (seconds, 1->10)") // library marker tpLink.tpl_cap_level, line 15
} // library marker tpLink.tpl_cap_level, line 16

def levelUpdated() { // library marker tpLink.tpl_cap_level, line 18
	Map logData = [:] // library marker tpLink.tpl_cap_level, line 19
	if (transTime < 0 || transTime > 10 || transTime == null) { // library marker tpLink.tpl_cap_level, line 20
		device.updateSetting("transTime", [type: "number", value: 1]) // library marker tpLink.tpl_cap_level, line 21
		logData << [transTimeError: "out of range", updatedTo: 1] // library marker tpLink.tpl_cap_level, line 22
	} else { // library marker tpLink.tpl_cap_level, line 23
		logData << [transTime: transTime] // library marker tpLink.tpl_cap_level, line 24
	} // library marker tpLink.tpl_cap_level, line 25
	if (getDataValue("gradOnOff")) { logData << setDimmerTransition() } // library marker tpLink.tpl_cap_level, line 26
	return [levelUpdated: logData] // library marker tpLink.tpl_cap_level, line 27
} // library marker tpLink.tpl_cap_level, line 28

def setDimmerTransition() { // library marker tpLink.tpl_cap_level, line 30
	//	Sets the onOff Transition Time in the actual device. // library marker tpLink.tpl_cap_level, line 31
	//	All kasa/tapo dimming devices support this. // library marker tpLink.tpl_cap_level, line 32
	Map cmdBody = [:] // library marker tpLink.tpl_cap_level, line 33
	Map logData = [:] // library marker tpLink.tpl_cap_level, line 34
	int onOffTime = transTime.toInteger() // library marker tpLink.tpl_cap_level, line 35
	def api = getDataValue("api") // library marker tpLink.tpl_cap_level, line 36
	if (api == "iot") { // library marker tpLink.tpl_cap_level, line 37
		setIotDimmerTrans(onOffTime) // library marker tpLink.tpl_cap_level, line 38
	} else if (api == "smart") { // library marker tpLink.tpl_cap_level, line 39
		setSmartDimmerTrans(onOffTime) // library marker tpLink.tpl_cap_level, line 40
	} // library marker tpLink.tpl_cap_level, line 41
	return [setDimmerTransition: [fadeOn: transTime, fadeOff: transTime]] // library marker tpLink.tpl_cap_level, line 42
} // library marker tpLink.tpl_cap_level, line 43

def setLevel(level, transSecs = transTime) { // library marker tpLink.tpl_cap_level, line 45
	if (level.toInteger() == 0) { // library marker tpLink.tpl_cap_level, line 46
		off() // library marker tpLink.tpl_cap_level, line 47
		return // library marker tpLink.tpl_cap_level, line 48
	} // library marker tpLink.tpl_cap_level, line 49
	def api = getDataValue("api") // library marker tpLink.tpl_cap_level, line 50
	Map logData = [method: "setLevel", api: api, level: level, transTime:transTime] // library marker tpLink.tpl_cap_level, line 51
	Integer fadeTime = (transSecs ?: 1).toInteger() // library marker tpLink.tpl_cap_level, line 52
	int tTime = 1000 * fadeTime // library marker tpLink.tpl_cap_level, line 53
	if (tTime == 0) { tTime = 10 } // library marker tpLink.tpl_cap_level, line 54
	Map params = [brightness: level, "${LIGHT_TRANS_TYPE}": tTime] // library marker tpLink.tpl_cap_level, line 55
	if (api == "smart") { // library marker tpLink.tpl_cap_level, line 56
		params << [device_on: true] // library marker tpLink.tpl_cap_level, line 57
		setSmartLevel(params) // library marker tpLink.tpl_cap_level, line 58
	} else { // library marker tpLink.tpl_cap_level, line 59
		params << [ignore_default: 1, on_off: 1] // library marker tpLink.tpl_cap_level, line 60
		setIotLevel(params) // library marker tpLink.tpl_cap_level, line 61
	} // library marker tpLink.tpl_cap_level, line 62
	logDebug(logData) // library marker tpLink.tpl_cap_level, line 63
} // library marker tpLink.tpl_cap_level, line 64

def startLevelChange(direction) { // library marker tpLink.tpl_cap_level, line 66
	int curLevel = (device.currentValue("level") ?: 0).toInteger() // library marker tpLink.tpl_cap_level, line 67
	unschedule(levelUp) // library marker tpLink.tpl_cap_level, line 68
	unschedule(levelDown) // library marker tpLink.tpl_cap_level, line 69
	if (direction == "up") {  // library marker tpLink.tpl_cap_level, line 70
		levelUp(curLevel) // library marker tpLink.tpl_cap_level, line 71
	} else {  // library marker tpLink.tpl_cap_level, line 72
		levelDown(curLevel) // library marker tpLink.tpl_cap_level, line 73
	} // library marker tpLink.tpl_cap_level, line 74
} // library marker tpLink.tpl_cap_level, line 75

def stopLevelChange() { // library marker tpLink.tpl_cap_level, line 77
	unschedule(levelUp) // library marker tpLink.tpl_cap_level, line 78
	unschedule(levelDown) // library marker tpLink.tpl_cap_level, line 79
	refresh() // library marker tpLink.tpl_cap_level, line 80
} // library marker tpLink.tpl_cap_level, line 81

def levelUp(curLevel) { // library marker tpLink.tpl_cap_level, line 83
	if (curLevel == 100) {  // library marker tpLink.tpl_cap_level, line 84
		refresh() // library marker tpLink.tpl_cap_level, line 85
	} else { // library marker tpLink.tpl_cap_level, line 86
		def newLevel = curLevel + 4 // library marker tpLink.tpl_cap_level, line 87
		if (newLevel > 100) { newLevel = 100 } // library marker tpLink.tpl_cap_level, line 88
		if (getDataValue("api") == "smart") {  // library marker tpLink.tpl_cap_level, line 89
			smartTransCmd(newLevel) // library marker tpLink.tpl_cap_level, line 90
		} else { // library marker tpLink.tpl_cap_level, line 91
			iotQuickLevel(newLevel) // library marker tpLink.tpl_cap_level, line 92
		} // library marker tpLink.tpl_cap_level, line 93
		sendEvent(name: "level", value: newLevel, type: "levelUpDown") // library marker tpLink.tpl_cap_level, line 94
		runIn(1, levelUp, [data: newLevel]) // library marker tpLink.tpl_cap_level, line 95
	} // library marker tpLink.tpl_cap_level, line 96
} // library marker tpLink.tpl_cap_level, line 97

def levelDown(curLevel) { // library marker tpLink.tpl_cap_level, line 99
	if (curLevel == 0 || device.currentValue("switch") == "off") { // library marker tpLink.tpl_cap_level, line 100
		refresh() // library marker tpLink.tpl_cap_level, line 101
	} else { // library marker tpLink.tpl_cap_level, line 102
		def newLevel = curLevel - 4 // library marker tpLink.tpl_cap_level, line 103
		if (getDataValue("api") == "smart") {  // library marker tpLink.tpl_cap_level, line 104
			smartTransCmd(newLevel)  // library marker tpLink.tpl_cap_level, line 105
		} else { // library marker tpLink.tpl_cap_level, line 106
			iotQuickLevel(newLevel) // library marker tpLink.tpl_cap_level, line 107
		} // library marker tpLink.tpl_cap_level, line 108
		sendEvent(name: "level", value: newLevel, type: "levelUpDown") // library marker tpLink.tpl_cap_level, line 109
		if (newLevel < 4) {  // library marker tpLink.tpl_cap_level, line 110
			off() // library marker tpLink.tpl_cap_level, line 111
		} // library marker tpLink.tpl_cap_level, line 112
		else { runIn(1, levelDown, [data: newLevel]) } // library marker tpLink.tpl_cap_level, line 113
	} // library marker tpLink.tpl_cap_level, line 114
} // library marker tpLink.tpl_cap_level, line 115

//	===== Smart API Calls ===== // library marker tpLink.tpl_cap_level, line 117
def setSmartDimmerTrans(onOffTime) { // library marker tpLink.tpl_cap_level, line 118
	Map params = [:] // library marker tpLink.tpl_cap_level, line 119
	if (onOffTime == 0) { // library marker tpLink.tpl_cap_level, line 120
		params << [off_state: [enable: false], on_state: [enable: false]] // library marker tpLink.tpl_cap_level, line 121
	} else { // library marker tpLink.tpl_cap_level, line 122
		params << [off_state: [duration: onOffTime, enable: true], // library marker tpLink.tpl_cap_level, line 123
				   on_state: [duration: onOffTime, enable: true]] // library marker tpLink.tpl_cap_level, line 124
	} // library marker tpLink.tpl_cap_level, line 125
	List requests = [[method: "set_on_off_gradually_info", params: params], // library marker tpLink.tpl_cap_level, line 126
					 [method: "get_on_off_gradually_info"]] // library marker tpLink.tpl_cap_level, line 127
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_level, line 128
	if (IS_CHILD) { // library marker tpLink.tpl_cap_level, line 129
		Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_level, line 130
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_level, line 131
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_level, line 132
	} else { // library marker tpLink.tpl_cap_level, line 133
		sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_cap_level, line 134
	} // library marker tpLink.tpl_cap_level, line 135
} // library marker tpLink.tpl_cap_level, line 136

def parse_get_on_off_gradually_info(resp) { // library marker tpLink.tpl_cap_level, line 138
	def onOffDef = resp.on_state.duration // library marker tpLink.tpl_cap_level, line 139
	device.updateSetting("onOffDef", [type:"number", value: onOffDef]) // library marker tpLink.tpl_cap_level, line 140
	Map logData = [method: "setSmartDimConfig", onOffDef: onOffDef, enable: resp.on_state.enable] // library marker tpLink.tpl_cap_level, line 141
	logDebug(logData) // library marker tpLink.tpl_cap_level, line 142
} // library marker tpLink.tpl_cap_level, line 143

def setSmartLevel(params) { // library marker tpLink.tpl_cap_level, line 145
	List requests = [[method: "set_device_info", params: params], // library marker tpLink.tpl_cap_level, line 146
					 [method: "get_device_info"]] // library marker tpLink.tpl_cap_level, line 147
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_level, line 148
	if (IS_CHILD) { // library marker tpLink.tpl_cap_level, line 149
		Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_level, line 150
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_level, line 151
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_level, line 152
	} else { // library marker tpLink.tpl_cap_level, line 153
		sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_cap_level, line 154
		if (emEnable) { runIn(5, getSmartPower) } // library marker tpLink.tpl_cap_level, line 155
	} // library marker tpLink.tpl_cap_level, line 156
} // library marker tpLink.tpl_cap_level, line 157

def smartTransCmd(currLevel) { // library marker tpLink.tpl_cap_level, line 159
	//	always uses tTime of 10 // library marker tpLink.tpl_cap_level, line 160
	Map params = [device_on: true, brightness: currLevel, "${LIGHT_TRANS_TYPE}": 10] // library marker tpLink.tpl_cap_level, line 161
	List requests = [[method: "set_device_info", params: params]] // library marker tpLink.tpl_cap_level, line 162
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_level, line 163
	if (IS_CHILD) { // library marker tpLink.tpl_cap_level, line 164
		Map commandData = [cmdBody: cmdBody, retry: false, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_level, line 165
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_level, line 166
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_level, line 167
	} else { // library marker tpLink.tpl_cap_level, line 168
		sendSmartCmd(cmdBody, false, "smartNullParse") // library marker tpLink.tpl_cap_level, line 169
	} // library marker tpLink.tpl_cap_level, line 170
} // library marker tpLink.tpl_cap_level, line 171

//	===== IOT API Calls	===== // library marker tpLink.tpl_cap_level, line 173
def setIotDimmerTrans(onOffTime) { // library marker tpLink.tpl_cap_level, line 174
	Map cmdBody = ["smartlife.iot.dimmer": [ // library marker tpLink.tpl_cap_level, line 175
		set_fade_on_time: [fadeTime: onOffTime * 1000], // library marker tpLink.tpl_cap_level, line 176
		set_fade_off_time: [fadeTime: onOffTime * 1000], // library marker tpLink.tpl_cap_level, line 177
		get_dimmer_parameters :[]]] // library marker tpLink.tpl_cap_level, line 178
	sendIotCmd(cmdBody, true) // library marker tpLink.tpl_cap_level, line 179
} // library marker tpLink.tpl_cap_level, line 180

def parse_get_dimmer_parameters(resp) { // library marker tpLink.tpl_cap_level, line 182
	logDebug([method: "setIotDimConfig", resp: resp]) // library marker tpLink.tpl_cap_level, line 183
	if (resp.err_code != 0) { // library marker tpLink.tpl_cap_level, line 184
		log.warn "setIotDimConfig: Error in return" // library marker tpLink.tpl_cap_level, line 185
	} else { // library marker tpLink.tpl_cap_level, line 186
		def onOffDef = (0.5 + resp.fadeOffTime / 1000).toInteger() // library marker tpLink.tpl_cap_level, line 187
		device.updateSetting("onOffDef", [type:"number", value: onOffDef]) // library marker tpLink.tpl_cap_level, line 188
	} // library marker tpLink.tpl_cap_level, line 189
} // library marker tpLink.tpl_cap_level, line 190

def setIotLevel(params) { // library marker tpLink.tpl_cap_level, line 192
	Map cmdBody = ["${SYSTEM}": ["${LIGHT_CMD}": params]] // library marker tpLink.tpl_cap_level, line 193
	sendIotCmd(cmdBody, true) // library marker tpLink.tpl_cap_level, line 194
	//	Dimming switch return actual level, so wait for transition to complete. // library marker tpLink.tpl_cap_level, line 195
	if (DEV_TYPE == "Dimming Switch") { runIn(transTime + 1, refresh) } // library marker tpLink.tpl_cap_level, line 196
	if (emEnable) { runIn(5, iotGetRealtime) } // library marker tpLink.tpl_cap_level, line 197
} // library marker tpLink.tpl_cap_level, line 198

def iotQuickLevel(level) { // library marker tpLink.tpl_cap_level, line 200
	Map transData = [ignore_default: 1, on_off: 1, brightness: level, // library marker tpLink.tpl_cap_level, line 201
					 "${LIGHT_TRANS_TYPE}": 10] // library marker tpLink.tpl_cap_level, line 202
	Map cmdBody = ["${SYSTEM}": ["${LIGHT_CMD}": transData]] // library marker tpLink.tpl_cap_level, line 203
	if (getDataValue("protocol") == "xor") { // library marker tpLink.tpl_cap_level, line 204
		sendIotCmd(cmdBody, false, "iotNullParse") // library marker tpLink.tpl_cap_level, line 205
	} else { // library marker tpLink.tpl_cap_level, line 206
		sendSmartCmd(cmdBody, false, "smartNullParse") // library marker tpLink.tpl_cap_level, line 207
	} // library marker tpLink.tpl_cap_level, line 208
} // library marker tpLink.tpl_cap_level, line 209

// ~~~~~ end include (1075) tpLink.tpl_cap_level ~~~~~

// ~~~~~ start include (1071) tpLink.tpl_cap_color ~~~~~
library ( // library marker tpLink.tpl_cap_color, line 1
	name: "tpl_cap_color", // library marker tpLink.tpl_cap_color, line 2
	namespace: "tpLink", // library marker tpLink.tpl_cap_color, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_cap_color, line 4
	description: "kasa-tapo color control. Hub V 2.5.3", // library marker tpLink.tpl_cap_color, line 5
	category: "utilities", // library marker tpLink.tpl_cap_color, line 6
	documentationLink: "" // library marker tpLink.tpl_cap_color, line 7
) // library marker tpLink.tpl_cap_color, line 8

capability "Color Mode" // library marker tpLink.tpl_cap_color, line 10
capability "Color Control" // library marker tpLink.tpl_cap_color, line 11

def setHue(hue) { // library marker tpLink.tpl_cap_color, line 13
	Map color = [level: device.currentValue("level").toInteger(), // library marker tpLink.tpl_cap_color, line 14
				 hue: hue,  // library marker tpLink.tpl_cap_color, line 15
				 saturation: device.currentValue("saturation").toInteger()] // library marker tpLink.tpl_cap_color, line 16
	setColor(color) // library marker tpLink.tpl_cap_color, line 17
} // library marker tpLink.tpl_cap_color, line 18

def setSaturation(saturation) {  // library marker tpLink.tpl_cap_color, line 20
	Map color = [level: device.currentValue("level").toInteger(), // library marker tpLink.tpl_cap_color, line 21
				 hue: device.currentValue("hue").toInteger(),  // library marker tpLink.tpl_cap_color, line 22
				 saturation: saturation] // library marker tpLink.tpl_cap_color, line 23
	setColor(color) // library marker tpLink.tpl_cap_color, line 24
} // library marker tpLink.tpl_cap_color, line 25

def setRGB(rgb) { // library marker tpLink.tpl_cap_color, line 27
	def rgbArray = rgb.split('\\,') // library marker tpLink.tpl_cap_color, line 28
	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([rgbArray[0].toInteger(), rgbArray[1].toInteger(), rgbArray[2].toInteger()]) // library marker tpLink.tpl_cap_color, line 29
	def hue = (0.5 + hsvData[0]).toInteger() // library marker tpLink.tpl_cap_color, line 30
	def saturation = (0.5 + hsvData[1]).toInteger() // library marker tpLink.tpl_cap_color, line 31
	def level = (0.5 + hsvData[2]).toInteger() // library marker tpLink.tpl_cap_color, line 32
	def Map color = [ // library marker tpLink.tpl_cap_color, line 33
		hue: hue, // library marker tpLink.tpl_cap_color, line 34
		saturation: saturation, // library marker tpLink.tpl_cap_color, line 35
		level: level // library marker tpLink.tpl_cap_color, line 36
		] // library marker tpLink.tpl_cap_color, line 37
	setColor(color) // library marker tpLink.tpl_cap_color, line 38
} // library marker tpLink.tpl_cap_color, line 39

def setColor(Map color, transTime = transTime) { // library marker tpLink.tpl_cap_color, line 41
	def api = getDataValue("api") // library marker tpLink.tpl_cap_color, line 42
	Map logData = [method: "setColor", api: api,  // library marker tpLink.tpl_cap_color, line 43
				   params: [brightness: color.level, hue: color.hue, saturation: color.saturation]] // library marker tpLink.tpl_cap_color, line 44
	int hubHue = Math.round(0.49 + color.hue.toInteger() * 3.6).toInteger() // library marker tpLink.tpl_cap_color, line 45
	int level = color.level.toInteger() // library marker tpLink.tpl_cap_color, line 46
	int saturation = color.saturation.toInteger() // library marker tpLink.tpl_cap_color, line 47
	int tTime = 1000 * transTime.toInteger() // library marker tpLink.tpl_cap_color, line 48
	if (color.rate) { tTime = 1000 * color.rate.toInteger() } // library marker tpLink.tpl_cap_color, line 49
	if (tTime == 0) { tTime = 10 } // library marker tpLink.tpl_cap_color, line 50
	Map params = [brightness: level, color_temp: 0, hue: hubHue,  // library marker tpLink.tpl_cap_color, line 51
				  saturation: saturation, "${LIGHT_TRANS_TYPE}": tTime] // library marker tpLink.tpl_cap_color, line 52
	if (getDataValue("api") == "smart") { // library marker tpLink.tpl_cap_color, line 53
		params << [device_on: true] // library marker tpLink.tpl_cap_color, line 54
		setSmartColor(params) // library marker tpLink.tpl_cap_color, line 55
	} else { // library marker tpLink.tpl_cap_color, line 56
		params << [ignore_default: 1, on_off: 1] // library marker tpLink.tpl_cap_color, line 57
		setIotColor(params) // library marker tpLink.tpl_cap_color, line 58
	} // library marker tpLink.tpl_cap_color, line 59
	logDebug(logData) // library marker tpLink.tpl_cap_color, line 60
} // library marker tpLink.tpl_cap_color, line 61

@Field static Map ANIM_LIMITS = [ // library marker tpLink.tpl_cap_color, line 63
	level: [min: 5,    max: 100], // library marker tpLink.tpl_cap_color, line 64
	hue:  [min: 0,    max: 100], // library marker tpLink.tpl_cap_color, line 65
	saturation:  [min: 0,    max: 100], // library marker tpLink.tpl_cap_color, line 66
	colorTemp:  [min: 2700,    max: 6500], // library marker tpLink.tpl_cap_color, line 67
	rate:  [min: 0,    max: 10000], // library marker tpLink.tpl_cap_color, line 68
	onOff: [min: 0, max: 1] // library marker tpLink.tpl_cap_color, line 69
] // library marker tpLink.tpl_cap_color, line 70

command "setLightWithTransition", [[ // library marker tpLink.tpl_cap_color, line 72
	name: "Custom transition for lighting", // library marker tpLink.tpl_cap_color, line 73
	type: "STRING", // library marker tpLink.tpl_cap_color, line 74
	title: "enter key:value pairs followed by commas", // library marker tpLink.tpl_cap_color, line 75
	description: "enter key:value pairs followed by commas"]] // library marker tpLink.tpl_cap_color, line 76

def setLightWithTransition(inputs) { // library marker tpLink.tpl_cap_color, line 78
	Map logData = [method: "setLightWithTransition", input: inputs] // library marker tpLink.tpl_cap_color, line 79
	Boolean paramsOK = true // library marker tpLink.tpl_cap_color, line 80
	Map inputParams = [:] // library marker tpLink.tpl_cap_color, line 81
	inputs.split(',').each { item -> // library marker tpLink.tpl_cap_color, line 82
		def (k, v) = item.split(':', 2)*.trim() // library marker tpLink.tpl_cap_color, line 83
		inputParams[k] = v.isInteger() ? v.toInteger() : v // library marker tpLink.tpl_cap_color, line 84
	} // library marker tpLink.tpl_cap_color, line 85
	//	range-check entered params. // library marker tpLink.tpl_cap_color, line 86
	inputParams.each { key, v -> // library marker tpLink.tpl_cap_color, line 87
		def r = ANIM_LIMITS[key] // library marker tpLink.tpl_cap_color, line 88
		if (r && (v < r.min || v > r.max)) { // library marker tpLink.tpl_cap_color, line 89
			logData << ["${key}": "${v} out of limits"] // library marker tpLink.tpl_cap_color, line 90
				paramsOK = false // library marker tpLink.tpl_cap_color, line 91
		} // library marker tpLink.tpl_cap_color, line 92
	} // library marker tpLink.tpl_cap_color, line 93
	if (paramsOK == true) { // library marker tpLink.tpl_cap_color, line 94
		Map params = [:] // library marker tpLink.tpl_cap_color, line 95
		//	If rate/level not sent, use currentValue // library marker tpLink.tpl_cap_color, line 96
		int rate = inputParams.rate // library marker tpLink.tpl_cap_color, line 97
		int onOff = 1 // library marker tpLink.tpl_cap_color, line 98
		if (inputParams.onOff == 0) { onOff = 0 } // library marker tpLink.tpl_cap_color, line 99
		if (onOff != 0) { // library marker tpLink.tpl_cap_color, line 100
			int level = device.currentValue("level").toInteger() // library marker tpLink.tpl_cap_color, line 101
			if (inputParams.level) { level = inputParams.level } // library marker tpLink.tpl_cap_color, line 102
				params = [brightness: level] // library marker tpLink.tpl_cap_color, line 103
			if (inputParams.color_temp && inputParams.color_temp >= 2700)  { // library marker tpLink.tpl_cap_color, line 104
				//	if color_temp is >= 2700, do a colorTemperature transition. // library marker tpLink.tpl_cap_color, line 105
				params << [hue: 0, saturation: 0, color_temp: inputParams.color_temp] // library marker tpLink.tpl_cap_color, line 106
			} else { // library marker tpLink.tpl_cap_color, line 107
				//	if hue or saturation is null, use currentValue // library marker tpLink.tpl_cap_color, line 108
				int hue = device.currentValue("hue") // library marker tpLink.tpl_cap_color, line 109
				if(inputParams.hue) { hue = inputParams.hue } // library marker tpLink.tpl_cap_color, line 110
				int hubHue = Math.round(0.49 + hue.toInteger() * 3.6).toInteger() // library marker tpLink.tpl_cap_color, line 111
				int saturation = device.currentValue("saturation") // library marker tpLink.tpl_cap_color, line 112
				if(inputParams.saturation) { saturation = inputParams.saturation } // library marker tpLink.tpl_cap_color, line 113
				params << [hue: hubHue, saturation: saturation, color_temp: 0] // library marker tpLink.tpl_cap_color, line 114
			} // library marker tpLink.tpl_cap_color, line 115
		} // library marker tpLink.tpl_cap_color, line 116
		if (getDataValue("api") == "smart") { // library marker tpLink.tpl_cap_color, line 117
			deviceOn = true // library marker tpLink.tpl_cap_color, line 118
			if (onOff == 0) { deviceOn = false } // library marker tpLink.tpl_cap_color, line 119
			params << [device_on: deviceOn, "${LIGHT_TRANS_TYPE}": rate] // library marker tpLink.tpl_cap_color, line 120
			logData << [params: params] // library marker tpLink.tpl_cap_color, line 121
			setSmartColor(params) // library marker tpLink.tpl_cap_color, line 122
		} else { // library marker tpLink.tpl_cap_color, line 123
			params << [ignore_default: 1, on_off: onOff, "${LIGHT_TRANS_TYPE}": rate] // library marker tpLink.tpl_cap_color, line 124
			logData << [params: params] // library marker tpLink.tpl_cap_color, line 125
			setIotColor(params) // library marker tpLink.tpl_cap_color, line 126
		} // library marker tpLink.tpl_cap_color, line 127
	} // library marker tpLink.tpl_cap_color, line 128
	logDebug(logData) // library marker tpLink.tpl_cap_color, line 129
} // library marker tpLink.tpl_cap_color, line 130

// ~~~~~ end include (1071) tpLink.tpl_cap_color ~~~~~

// ~~~~~ start include (1072) tpLink.tpl_cap_ct ~~~~~
library ( // library marker tpLink.tpl_cap_ct, line 1
	name: "tpl_cap_ct", // library marker tpLink.tpl_cap_ct, line 2
	namespace: "tpLink", // library marker tpLink.tpl_cap_ct, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_cap_ct, line 4
	description: "kasa-tapo color temperature control. Hub V 2.5.3", // library marker tpLink.tpl_cap_ct, line 5
	category: "utilities", // library marker tpLink.tpl_cap_ct, line 6
	documentationLink: "" // library marker tpLink.tpl_cap_ct, line 7
) // library marker tpLink.tpl_cap_ct, line 8

capability "Color Temperature" // library marker tpLink.tpl_cap_ct, line 10

def setColorTemperature(colorTemp, level=device.currentValue("level"), transSecs = transTime) { // library marker tpLink.tpl_cap_ct, line 12
	level = level ?: device.currentValue("level") ?: 100 // library marker tpLink.tpl_cap_ct, line 13
	if (level.toInteger() == 0) { // library marker tpLink.tpl_cap_ct, line 14
		off() // library marker tpLink.tpl_cap_ct, line 15
		return // library marker tpLink.tpl_cap_ct, line 16
	} // library marker tpLink.tpl_cap_ct, line 17
	if (colorTemp < getDataValue("ctLow").toInteger()) { // library marker tpLink.tpl_cap_ct, line 18
		colorTemp = getDataValue("ctLow").toInteger() // library marker tpLink.tpl_cap_ct, line 19
	} else if (colorTemp > getDataValue("ctHigh").toInteger()) { // library marker tpLink.tpl_cap_ct, line 20
		colorTemp = getDataValue("ctHigh").toInteger() // library marker tpLink.tpl_cap_ct, line 21
	} // library marker tpLink.tpl_cap_ct, line 22
	int fadeTime = (transSecs ?: settings.transTime ?: 1).toInteger() // library marker tpLink.tpl_cap_ct, line 23
	int tTime = fadeTime * 1000	 // library marker tpLink.tpl_cap_ct, line 24
	if (tTime == 0) { tTime = 10 } // library marker tpLink.tpl_cap_ct, line 25
	String api = getDataValue("api") // library marker tpLink.tpl_cap_ct, line 26
	Map logData = [method: "setColorTemperature", api: api,  // library marker tpLink.tpl_cap_ct, line 27
				   params: [level: level, colorTemp: colorTemp, transTime: transTime]] // library marker tpLink.tpl_cap_ct, line 28
	Map params = [brightness: level, color_temp: colorTemp, // library marker tpLink.tpl_cap_ct, line 29
				  hue: 0, saturation: 0, "${LIGHT_TRANS_TYPE}": tTime] // library marker tpLink.tpl_cap_ct, line 30
	if (api == "smart") { // library marker tpLink.tpl_cap_ct, line 31
		params << [device_on: true] // library marker tpLink.tpl_cap_ct, line 32
		setSmartColor(params) // library marker tpLink.tpl_cap_ct, line 33
	} else { // library marker tpLink.tpl_cap_ct, line 34
		params << [ignore_default: 1, on_off: 1] // library marker tpLink.tpl_cap_ct, line 35
		setIotColor(params) // library marker tpLink.tpl_cap_ct, line 36
	} // library marker tpLink.tpl_cap_ct, line 37
	logDebug(logData) // library marker tpLink.tpl_cap_ct, line 38
} // library marker tpLink.tpl_cap_ct, line 39

// ~~~~~ end include (1072) tpLink.tpl_cap_ct ~~~~~

// ~~~~~ start include (1073) tpLink.tpl_cap_em ~~~~~
library ( // library marker tpLink.tpl_cap_em, line 1
	name: "tpl_cap_em", // library marker tpLink.tpl_cap_em, line 2
	namespace: "tpLink", // library marker tpLink.tpl_cap_em, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_cap_em, line 4
	description: "IOT/Smart Energy Monitor Methods. Hub V 2.5.3", // library marker tpLink.tpl_cap_em, line 5
	category: "energyMonitor", // library marker tpLink.tpl_cap_em, line 6
	documentationLink: "" // library marker tpLink.tpl_cap_em, line 7
) // library marker tpLink.tpl_cap_em, line 8

capability "Power Meter" // library marker tpLink.tpl_cap_em, line 10
capability "Energy Meter" // library marker tpLink.tpl_cap_em, line 11

def emPrefs() { // library marker tpLink.tpl_cap_em, line 13
	if (getDataValue("isEm")) { // library marker tpLink.tpl_cap_em, line 14
		input ("emEnable", "bool", title: "Enable Energy Monitoring", defaultValue: false) // library marker tpLink.tpl_cap_em, line 15
	} // library marker tpLink.tpl_cap_em, line 16
} // library marker tpLink.tpl_cap_em, line 17

def emUpdated() { // library marker tpLink.tpl_cap_em, line 19
	def updStatus = [:] // library marker tpLink.tpl_cap_em, line 20
	if (getDataValue("api") == "iot") { // library marker tpLink.tpl_cap_em, line 21
		if (!state.energyYesterday) { // library marker tpLink.tpl_cap_em, line 22
			state.energyYesterday = 0 // library marker tpLink.tpl_cap_em, line 23
			updStatus << [initialized: "energyYesterday",  // library marker tpLink.tpl_cap_em, line 24
				NOTE: "FOR TODAY, energy WILL BE FROM DEVICE INSTALLATION"] // library marker tpLink.tpl_cap_em, line 25
			setTotWhPrev() // library marker tpLink.tpl_cap_em, line 26
		} // library marker tpLink.tpl_cap_em, line 27
		schedule("0 1 0 * * ?", "setTotWhPrev") // library marker tpLink.tpl_cap_em, line 28
	} // library marker tpLink.tpl_cap_em, line 29
	if (emEnable) { // library marker tpLink.tpl_cap_em, line 30
		runEvery30Minutes("energyRefresh") // library marker tpLink.tpl_cap_em, line 31
		runIn(10, energyRefresh) // library marker tpLink.tpl_cap_em, line 32
	} // library marker tpLink.tpl_cap_em, line 33
	return updStatus // library marker tpLink.tpl_cap_em, line 34
} // library marker tpLink.tpl_cap_em, line 35

def setTotWhPrev() { // library marker tpLink.tpl_cap_em, line 37
	state.settingWhPrev = true // library marker tpLink.tpl_cap_em, line 38
	int delay = 1 // library marker tpLink.tpl_cap_em, line 39
	if (IS_CHILD) { // library marker tpLink.tpl_cap_em, line 40
		//	Vary delay to avoid collisions at actual device. // library marker tpLink.tpl_cap_em, line 41
		delay = getDataValue("deviceId")[-1].toInteger() // library marker tpLink.tpl_cap_em, line 42
	} // library marker tpLink.tpl_cap_em, line 43
	runIn(2*delay, iotEnergyRefresh) // library marker tpLink.tpl_cap_em, line 44
	logDebug([method: "setTotWhPrev", status: "Updating yesterday's total watt hours"]) // library marker tpLink.tpl_cap_em, line 45
} // library marker tpLink.tpl_cap_em, line 46

def energyRefresh() { // library marker tpLink.tpl_cap_em, line 48
	if (getDataValue("api") == "smart") {  // library marker tpLink.tpl_cap_em, line 49
		smartEnergyRefresh() // library marker tpLink.tpl_cap_em, line 50
	} else { // library marker tpLink.tpl_cap_em, line 51
		iotEnergyRefresh() // library marker tpLink.tpl_cap_em, line 52
	} // library marker tpLink.tpl_cap_em, line 53
} // library marker tpLink.tpl_cap_em, line 54

//	===== Smart API Calls ===== // library marker tpLink.tpl_cap_em, line 56
def smartPowerRefresh() { // library marker tpLink.tpl_cap_em, line 57
	state.eventType = "digital" // library marker tpLink.tpl_cap_em, line 58
	getSmartPower() // library marker tpLink.tpl_cap_em, line 59
} // library marker tpLink.tpl_cap_em, line 60

def smartPowerPoll() { // library marker tpLink.tpl_cap_em, line 62
	state.eventType = "manual" // library marker tpLink.tpl_cap_em, line 63
	getSmartPower() // library marker tpLink.tpl_cap_em, line 64
} // library marker tpLink.tpl_cap_em, line 65

def getSmartPower() { // library marker tpLink.tpl_cap_em, line 67
	List requests = [[method: "get_current_power"]] // library marker tpLink.tpl_cap_em, line 68
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_em, line 69
	if (IS_CHILD) { // library marker tpLink.tpl_cap_em, line 70
		Map commandData = [cmdBody: cmdBody, retry: false, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_em, line 71
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_em, line 72
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_em, line 73
	} else { // library marker tpLink.tpl_cap_em, line 74
		sendSmartCmd(cmdBody, false) // library marker tpLink.tpl_cap_em, line 75
	} // library marker tpLink.tpl_cap_em, line 76
} // library marker tpLink.tpl_cap_em, line 77

def parse_get_current_power(methResp) { // library marker tpLink.tpl_cap_em, line 79
	Map logData = [method: "smartPowerParse", result: methResp] // library marker tpLink.tpl_cap_em, line 80
	logDebug(logData) // library marker tpLink.tpl_cap_em, line 81
	sendEvent(name: "power", value: methResp.current_power) // library marker tpLink.tpl_cap_em, line 82
} // library marker tpLink.tpl_cap_em, line 83

def smartEnergyRefresh() { // library marker tpLink.tpl_cap_em, line 85
	List requests = [[method: "get_device_usage"]] // library marker tpLink.tpl_cap_em, line 86
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_cap_em, line 87
	if (IS_CHILD) { // library marker tpLink.tpl_cap_em, line 88
		Map commandData = [cmdBody: cmdBody, retry: false, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_em, line 89
						   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_cap_em, line 90
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_em, line 91
	} else { // library marker tpLink.tpl_cap_em, line 92
		sendSmartCmd(cmdBody, false) // library marker tpLink.tpl_cap_em, line 93
	} // library marker tpLink.tpl_cap_em, line 94
} // library marker tpLink.tpl_cap_em, line 95

def parse_get_device_usage(methResp) { // library marker tpLink.tpl_cap_em, line 97
	Map logData = [method: "smartEnergyParse", methResp: methResp] // library marker tpLink.tpl_cap_em, line 98
	def pwrKwh = methResp.power_usage.today / 1000 // library marker tpLink.tpl_cap_em, line 99
	sendEvent(name: "energy", value: pwrKwh, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker tpLink.tpl_cap_em, line 100
	logDebug(logData) // library marker tpLink.tpl_cap_em, line 101
} // library marker tpLink.tpl_cap_em, line 102

//	===== IOT API Calls	===== // library marker tpLink.tpl_cap_em, line 104
def iotGetRealtime() { // library marker tpLink.tpl_cap_em, line 105
	state.eventType = "digital" // library marker tpLink.tpl_cap_em, line 106
	getIotPoll() // library marker tpLink.tpl_cap_em, line 107
} // library marker tpLink.tpl_cap_em, line 108

def iotRealtimePoll() { // library marker tpLink.tpl_cap_em, line 110
	state.eventType = "manual" // library marker tpLink.tpl_cap_em, line 111
	getIotPoll() // library marker tpLink.tpl_cap_em, line 112
} // library marker tpLink.tpl_cap_em, line 113

def getIotPoll() { // library marker tpLink.tpl_cap_em, line 115
	Map cmdBody = [:] // library marker tpLink.tpl_cap_em, line 116
	if (getDataValue("plugNo") || IS_CHILD) {  // library marker tpLink.tpl_cap_em, line 117
		cmdBody << [context: [child_ids:[getDataValue("deviceId")]]] // library marker tpLink.tpl_cap_em, line 118
	} // library marker tpLink.tpl_cap_em, line 119
	cmdBody << ["${EM_SYS}": [get_realtime:[]]] // library marker tpLink.tpl_cap_em, line 120
	if (IS_CHILD) { // library marker tpLink.tpl_cap_em, line 121
		Map commandData = [cmdBody: cmdBody, retry: false, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_em, line 122
						   dni: device.deviceNetworkId, api: "iot"] // library marker tpLink.tpl_cap_em, line 123
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_em, line 124
	} else { // library marker tpLink.tpl_cap_em, line 125
		sendIotCmd(cmdBody, false) // library marker tpLink.tpl_cap_em, line 126
	} // library marker tpLink.tpl_cap_em, line 127
} // library marker tpLink.tpl_cap_em, line 128

def iotEnergyRefresh() { // library marker tpLink.tpl_cap_em, line 130
	def currDate = new Date() // library marker tpLink.tpl_cap_em, line 131
	int year = currDate.format("yyyy").toInteger() // library marker tpLink.tpl_cap_em, line 132
	Map cmdBody = [:] // library marker tpLink.tpl_cap_em, line 133
	def plugId = getDataValue("plugId") // library marker tpLink.tpl_cap_em, line 134
	if (plugId) { cmdBody << [context: [child_ids:[plugId]]] } // library marker tpLink.tpl_cap_em, line 135
	cmdBody << ["${EM_SYS}": [get_monthstat: [year: year]]] // library marker tpLink.tpl_cap_em, line 136
	if (IS_CHILD) { // library marker tpLink.tpl_cap_em, line 137
		Map commandData = [cmdBody: cmdBody, retry: false, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_cap_em, line 138
						   dni: device.deviceNetworkId, api: "iot"] // library marker tpLink.tpl_cap_em, line 139
		parent.processChildCmd(commandData) // library marker tpLink.tpl_cap_em, line 140
	} else { // library marker tpLink.tpl_cap_em, line 141
		sendIotCmd(cmdBody, false) // library marker tpLink.tpl_cap_em, line 142
	} // library marker tpLink.tpl_cap_em, line 143
} // library marker tpLink.tpl_cap_em, line 144

def parse_emeter(devData) { // library marker tpLink.tpl_cap_em, line 146
	Map logData = [method: "iotEmeterParse"] // library marker tpLink.tpl_cap_em, line 147
	if (devData.get_realtime) { // library marker tpLink.tpl_cap_em, line 148
		def realtime = devData.get_realtime // library marker tpLink.tpl_cap_em, line 149
		def power = realtime.power // library marker tpLink.tpl_cap_em, line 150
		if (power == null) { power = realtime.power_mw / 1000 } // library marker tpLink.tpl_cap_em, line 151
		power = (power + 0.5).toInteger() // library marker tpLink.tpl_cap_em, line 152
		sendEvent(name: "power", value: power) // library marker tpLink.tpl_cap_em, line 153
		logData << [power: power] // library marker tpLink.tpl_cap_em, line 154
		if (state.totWhPrev > 0) { // library marker tpLink.tpl_cap_em, line 155
			def whToday = (realtime.total_wh - state.totWhPrev) / 1000 // library marker tpLink.tpl_cap_em, line 156
			sendEvent(name: "energy", value: whToday, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker tpLink.tpl_cap_em, line 157
			logData << [energy: whToday] // library marker tpLink.tpl_cap_em, line 158
		} // library marker tpLink.tpl_cap_em, line 159
	} // library marker tpLink.tpl_cap_em, line 160
	if (devData.get_monthstat) { // library marker tpLink.tpl_cap_em, line 161
		def currDate = new Date() // library marker tpLink.tpl_cap_em, line 162
		int month = currDate.format("M").toInteger() // library marker tpLink.tpl_cap_em, line 163
		def monthData = devData.get_monthstat.month_list.find { it.month == month } // library marker tpLink.tpl_cap_em, line 164
		if (!monthData) { // library marker tpLink.tpl_cap_em, line 165
			logWarn("parse_emeter: No monthstat data for month ${month}") // library marker tpLink.tpl_cap_em, line 166
			return // library marker tpLink.tpl_cap_em, line 167
		}		 // library marker tpLink.tpl_cap_em, line 168
		def energyMonth = monthData.energy // library marker tpLink.tpl_cap_em, line 169
		if (energyMonth == null) { energyMonth = monthData.energy_wh / 1000 } // library marker tpLink.tpl_cap_em, line 170
		energyMonth = Math.round(1000 * energyMonth) / 1000 // library marker tpLink.tpl_cap_em, line 171
		if (state.settingWhPrev) { // library marker tpLink.tpl_cap_em, line 172
			state.energyYesterday = energyMonth // library marker tpLink.tpl_cap_em, line 173
			logData << [energyYesterday: energyMonth] // library marker tpLink.tpl_cap_em, line 174
			state.remove("settingWhPrev") // library marker tpLink.tpl_cap_em, line 175
		} else { // library marker tpLink.tpl_cap_em, line 176
			def energyToday = energyMonth - state.energyYesterday // library marker tpLink.tpl_cap_em, line 177
			sendEvent(name: "energy", value: energyToday, descriptionText: "KiloWatt Hours", unit: "KWH") // library marker tpLink.tpl_cap_em, line 178
			logData << [energy: energyToday] // library marker tpLink.tpl_cap_em, line 179
		} // library marker tpLink.tpl_cap_em, line 180
	} // library marker tpLink.tpl_cap_em, line 181
	logDebug(logData) // library marker tpLink.tpl_cap_em, line 182
} // library marker tpLink.tpl_cap_em, line 183

// ~~~~~ end include (1073) tpLink.tpl_cap_em ~~~~~

// ~~~~~ start include (1076) tpLink.tpl_dev_common ~~~~~
library ( // library marker tpLink.tpl_dev_common, line 1
	name: "tpl_dev_common", // library marker tpLink.tpl_dev_common, line 2
	namespace: "tpLink", // library marker tpLink.tpl_dev_common, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_dev_common, line 4
	description: "kasa-tapo devices shared methods", // library marker tpLink.tpl_dev_common, line 5
	category: "utilities", // library marker tpLink.tpl_dev_common, line 6
	documentationLink: "" // library marker tpLink.tpl_dev_common, line 7
) // library marker tpLink.tpl_dev_common, line 8

capability "Refresh" // library marker tpLink.tpl_dev_common, line 10
capability "Actuator" // library marker tpLink.tpl_dev_common, line 11
capability "Configuration" // library marker tpLink.tpl_dev_common, line 12
attribute "commsError", "string" // library marker tpLink.tpl_dev_common, line 13

def commonPrefs() { // library marker tpLink.tpl_dev_common, line 15
	input("systemPoll", "enum", title: "System poll interval",  // library marker tpLink.tpl_dev_common, line 16
		  options: ["5 seconds", "10 seconds", "30 seconds", "1 minute",  // library marker tpLink.tpl_dev_common, line 17
					"10 minutes", "15 minutes", "30 minutes"],  // library marker tpLink.tpl_dev_common, line 18
		  defaultValue: "30 minutes") // library marker tpLink.tpl_dev_common, line 19
	if (getDataValue("hasLed") == "true") { // library marker tpLink.tpl_dev_common, line 20
		if (getDataValue("api") == "smart") { // library marker tpLink.tpl_dev_common, line 21
			input ("ledRule", "enum", title: "LED Mode (if night mode, set type and times in phone app)", // library marker tpLink.tpl_dev_common, line 22
			   	   options: ["auto", "never", "night_mode"], defaultValue: "always") // library marker tpLink.tpl_dev_common, line 23
		} else { // library marker tpLink.tpl_dev_common, line 24
			input ("ledRule", "enum", title: "LED On/OFF", options: ["on", "off"],  // library marker tpLink.tpl_dev_common, line 25
				   defaultValue: "on") // library marker tpLink.tpl_dev_common, line 26
		} // library marker tpLink.tpl_dev_common, line 27
	} // library marker tpLink.tpl_dev_common, line 28
	input ("nameSync", "enum", title: "Synchronize Names", // library marker tpLink.tpl_dev_common, line 29
		   options: ["hubMaster", "deviceMaster", "no"], defaultValue: "no") // library marker tpLink.tpl_dev_common, line 30
	input ("infoLog", "bool", title: "Enable descriptionText logging", defaultValue: true) // library marker tpLink.tpl_dev_common, line 31
	input ("logEnable", "bool", title: "Enable debug logging", defaultValue: false) // library marker tpLink.tpl_dev_common, line 32
	if (getDataValue("api") == "iot" && parent.localHash) { // library marker tpLink.tpl_dev_common, line 33
		input ("changeProt", "bool", defaultValue: false, title: "CAUTION! If device " + // library marker tpLink.tpl_dev_common, line 34
			   "does not work, try using the other Protocol.") // library marker tpLink.tpl_dev_common, line 35
	} // library marker tpLink.tpl_dev_common, line 36
} // library marker tpLink.tpl_dev_common, line 37

def commonInstalled() { // library marker tpLink.tpl_dev_common, line 39
	sendEvent(name: "commsError", value: "false") // library marker tpLink.tpl_dev_common, line 40
	state.errorCount = 0 // library marker tpLink.tpl_dev_common, line 41
	return [commsError: "false", errorCount: 0] // library marker tpLink.tpl_dev_common, line 42
} // library marker tpLink.tpl_dev_common, line 43

def commonUpdated() { // library marker tpLink.tpl_dev_common, line 45
	state.errorCount = 0 // library marker tpLink.tpl_dev_common, line 46
	sendEvent(name: "commsError", value: "false") // library marker tpLink.tpl_dev_common, line 47
	if (logEnable) { runIn(1800, debugLogOff) } // library marker tpLink.tpl_dev_common, line 48
	Map updStatus = [infoLog: infoLog, logEnable: logEnable, commsError: "false", errorCount: 0] // library marker tpLink.tpl_dev_common, line 49
	if (changeProt) { updStatus << changeProtocol() } // library marker tpLink.tpl_dev_common, line 50
	if (getDataValue("protocol") != "xor") { updStatus << scheduleLogin() } // library marker tpLink.tpl_dev_common, line 51
	updStatus << setPollInterval(systemPoll) // library marker tpLink.tpl_dev_common, line 52
	if (nameSync != "no") { updStatus << [syncName: syncName()] } // library marker tpLink.tpl_dev_common, line 53
	if (ledRule != null) { updStatus << [ledUpdate: updateLed()] } // library marker tpLink.tpl_dev_common, line 54
	runIn(5, deviceHandshake) // library marker tpLink.tpl_dev_common, line 55
	return updStatus // library marker tpLink.tpl_dev_common, line 56
} // library marker tpLink.tpl_dev_common, line 57

/*	To be deleted after 2.5.3 // library marker tpLink.tpl_dev_common, line 59
	Error on transition due to scheduled events running prior to startUpHandler. // library marker tpLink.tpl_dev_common, line 60
	Explicitly, this was pollInterval (although others may be involved). // library marker tpLink.tpl_dev_common, line 61
	When poll is called, update the new systemPoll setting and unschedule poll. // library marker tpLink.tpl_dev_common, line 62
	also did same on getEnergyToday. The nightly scheduled jobs are not covered. // library marker tpLink.tpl_dev_common, line 63
*/ // library marker tpLink.tpl_dev_common, line 64
def poll() { verUpdate_2_5_3() } // library marker tpLink.tpl_dev_common, line 65
def getEnergyToday() { verUpdate_2_5_3() } // library marker tpLink.tpl_dev_common, line 66

def changeProtocol() { // library marker tpLink.tpl_dev_common, line 68
	Map respData = [:] // library marker tpLink.tpl_dev_common, line 69
	device.updateSetting("changeProt", [type: "bool", value: false]) // library marker tpLink.tpl_dev_common, line 70
	String warnTxt = "<b>changeProtocol: You have chosen to change the protocol. This may be " // library marker tpLink.tpl_dev_common, line 71
	warnTxt += "used as a part of troubleshooting. THIS ACTION IS REVERSIBLE BY SELECTING " // library marker tpLink.tpl_dev_common, line 72
	warnTxt += "CHANGE PROTOCOL again. Allow 20 seconds before testing.</b>" // library marker tpLink.tpl_dev_common, line 73
	log.warn warnTxt // library marker tpLink.tpl_dev_common, line 74
	def baseUrl = getDataValue("baseUrl") // library marker tpLink.tpl_dev_common, line 75
	if (getDataValue("protocol") == "xor") { // library marker tpLink.tpl_dev_common, line 76
		def (ip, port) = baseUrl.tokenize(':') // library marker tpLink.tpl_dev_common, line 77
		String newBaseUrl = "http://${ip}:80/app" // library marker tpLink.tpl_dev_common, line 78
		updateDataValue("protocol", "klap") // library marker tpLink.tpl_dev_common, line 79
		updateDataValue("baseUrl", newBaseUrl) // library marker tpLink.tpl_dev_common, line 80
		respData << [protocol: "xor -> klap", baseUrl: "${baseUrl} -> ${newBaseUrl}"] // library marker tpLink.tpl_dev_common, line 81
	} else { // library marker tpLink.tpl_dev_common, line 82
		String ip = baseUrl.replace("http://", "").replace(":80/app","") // library marker tpLink.tpl_dev_common, line 83
		String newBaseUrl = "${ip}:9999" // library marker tpLink.tpl_dev_common, line 84
		updateDataValue("protocol", "xor") // library marker tpLink.tpl_dev_common, line 85
		updateDataValue("baseUrl", newBaseUrl) // library marker tpLink.tpl_dev_common, line 86
		respData << [protocol: "klap -> xor", baseUrl: "${baseUrl} -> ${newBaseUrl}"] // library marker tpLink.tpl_dev_common, line 87
	} // library marker tpLink.tpl_dev_common, line 88
	runIn(5, configure) // library marker tpLink.tpl_dev_common, line 89
	return [changeProtocol: respData] // library marker tpLink.tpl_dev_common, line 90
} // library marker tpLink.tpl_dev_common, line 91

def updateLed() { // library marker tpLink.tpl_dev_common, line 93
	if (getDataValue("api") == "smart") { // library marker tpLink.tpl_dev_common, line 94
		List requests = [[method: "get_led_info"]] // library marker tpLink.tpl_dev_common, line 95
		cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_dev_common, line 96
		sendSmartCmd(cmdBody, false) // library marker tpLink.tpl_dev_common, line 97
	} else { // library marker tpLink.tpl_dev_common, line 98
		int onOff = 1 // library marker tpLink.tpl_dev_common, line 99
		if (ledRule == "on") { onOff = 0 } // library marker tpLink.tpl_dev_common, line 100
		Map cmdBody = [system: [set_led_off: [off: onOff]]] // library marker tpLink.tpl_dev_common, line 101
		sendIotCmd(cmdBody, false) // library marker tpLink.tpl_dev_common, line 102
	} // library marker tpLink.tpl_dev_common, line 103
	return ledRule // library marker tpLink.tpl_dev_common, line 104
} // library marker tpLink.tpl_dev_common, line 105

def parse_get_led_info(result) { // library marker tpLink.tpl_dev_common, line 107
	//	If not the same as ledRule, update. // library marker tpLink.tpl_dev_common, line 108
	Map logData = [method: "parse_get_led_info"] // library marker tpLink.tpl_dev_common, line 109
	log.trace result.night_mode // library marker tpLink.tpl_dev_common, line 110
	if (ledRule != result.led_rule) { // library marker tpLink.tpl_dev_common, line 111
		Map params = [led_rule: ledRule] // library marker tpLink.tpl_dev_common, line 112
		if (ledRule == "night_mode") { // library marker tpLink.tpl_dev_common, line 113
			params << [night_mode: result.night_mode] // library marker tpLink.tpl_dev_common, line 114
		} // library marker tpLink.tpl_dev_common, line 115
		List requests = [[method: "set_led_info",params: params]] // library marker tpLink.tpl_dev_common, line 116
		logData << [status: "updatingLedRule"] // library marker tpLink.tpl_dev_common, line 117
		cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_dev_common, line 118
		sendSmartCmd(cmdBody, false) // library marker tpLink.tpl_dev_common, line 119
	} // library marker tpLink.tpl_dev_common, line 120
	logData << [ledRule: ledRule] // library marker tpLink.tpl_dev_common, line 121
	logDebug(logData) // library marker tpLink.tpl_dev_common, line 122
} // library marker tpLink.tpl_dev_common, line 123

def syncName() { // library marker tpLink.tpl_dev_common, line 125
	String status = "no update requested" // library marker tpLink.tpl_dev_common, line 126
	switch(nameSync) { // library marker tpLink.tpl_dev_common, line 127
		case "hubMaster": // library marker tpLink.tpl_dev_common, line 128
			status = "update device's name to hub device name" // library marker tpLink.tpl_dev_common, line 129
			setDeviceAlias() // library marker tpLink.tpl_dev_common, line 130
			break // library marker tpLink.tpl_dev_common, line 131
		case "deviceMaster": // library marker tpLink.tpl_dev_common, line 132
			status = "update hub device's name to device name" // library marker tpLink.tpl_dev_common, line 133
			break // library marker tpLink.tpl_dev_common, line 134
		default:  // library marker tpLink.tpl_dev_common, line 135
			break // library marker tpLink.tpl_dev_common, line 136
	} // library marker tpLink.tpl_dev_common, line 137
	return [syncName: status] // library marker tpLink.tpl_dev_common, line 138
} // library marker tpLink.tpl_dev_common, line 139

def setDeviceAlias() { // library marker tpLink.tpl_dev_common, line 141
	Map cmdBody = [:] // library marker tpLink.tpl_dev_common, line 142
	if (getDataValue("api") == "iot") { // library marker tpLink.tpl_dev_common, line 143
		if (getDataValue("plugNo") || DEV_TYPE == "Child Plug Switch") { // library marker tpLink.tpl_dev_common, line 144
			cmdBody << [context: [child_ids:[getDataValue("deviceId")]]] // library marker tpLink.tpl_dev_common, line 145
		} // library marker tpLink.tpl_dev_common, line 146
		cmdBody << [system: [set_dev_alias: [alias: device.getLabel()]]] // library marker tpLink.tpl_dev_common, line 147
		if (IS_CHILD) { // library marker tpLink.tpl_dev_common, line 148
			Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),  // library marker tpLink.tpl_dev_common, line 149
						   	   dni: device.deviceNetworkId, api: "iot"] // library marker tpLink.tpl_dev_common, line 150
			parent.processChildCmd(commandData) // library marker tpLink.tpl_dev_common, line 151
		} else { // library marker tpLink.tpl_dev_common, line 152
			sendIotCmd(cmdBody, true) // library marker tpLink.tpl_dev_common, line 153
		} // library marker tpLink.tpl_dev_common, line 154
	} else { // library marker tpLink.tpl_dev_common, line 155
		String nickname = device.getLabel().bytes.encodeBase64().toString() // library marker tpLink.tpl_dev_common, line 156
		List requests = [[method: "set_device_info", params: [nickname: nickname]]] // library marker tpLink.tpl_dev_common, line 157
		cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_dev_common, line 158
		if (IS_CHILD) { // library marker tpLink.tpl_dev_common, line 159
			Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"), // library marker tpLink.tpl_dev_common, line 160
							   dni: device.deviceNetworkId, api: "smart"] // library marker tpLink.tpl_dev_common, line 161
			parent.processChildCmd(commandData) // library marker tpLink.tpl_dev_common, line 162
		} else { // library marker tpLink.tpl_dev_common, line 163
			sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_dev_common, line 164
		} // library marker tpLink.tpl_dev_common, line 165
	} // library marker tpLink.tpl_dev_common, line 166
} // library marker tpLink.tpl_dev_common, line 167

def parseNameUpdate(response) { // library marker tpLink.tpl_dev_common, line 169
	String name = device.getLabel() // library marker tpLink.tpl_dev_common, line 170
	if (getDataValue("api") == "iot") { // library marker tpLink.tpl_dev_common, line 171
		if (getDataValue("plugNo") != null) { // library marker tpLink.tpl_dev_common, line 172
			Map newResp = response.children.find { it.id == getDataValue("plugNo") } // library marker tpLink.tpl_dev_common, line 173
			if (newResp == null) { // library marker tpLink.tpl_dev_common, line 174
				newResp = response.children.find { it.id == getDataValue("deviceId") } // library marker tpLink.tpl_dev_common, line 175
			} // library marker tpLink.tpl_dev_common, line 176
			response = newResp // library marker tpLink.tpl_dev_common, line 177
		} // library marker tpLink.tpl_dev_common, line 178
		name = response.alias // library marker tpLink.tpl_dev_common, line 179
	} else { // library marker tpLink.tpl_dev_common, line 180
		byte[] plainBytes = response.nickname.decodeBase64() // library marker tpLink.tpl_dev_common, line 181
		name = new String(plainBytes) // library marker tpLink.tpl_dev_common, line 182
	} // library marker tpLink.tpl_dev_common, line 183
	device.setLabel(name) // library marker tpLink.tpl_dev_common, line 184
	logData = [method: "updateNames", nameSync: nameSync, label: name] // library marker tpLink.tpl_dev_common, line 185
	logDebug(logData) // library marker tpLink.tpl_dev_common, line 186
	device.updateSetting("nameSync", [type: "enum", value: "no"]) // library marker tpLink.tpl_dev_common, line 187
} // library marker tpLink.tpl_dev_common, line 188

def refresh() { // library marker tpLink.tpl_dev_common, line 190
	if (getDataValue("api") == "smart") { // library marker tpLink.tpl_dev_common, line 191
		if (DEV_TYPE == "Parent") { // library marker tpLink.tpl_dev_common, line 192
			//	Smart parent works differently than iot parent using a specific // library marker tpLink.tpl_dev_common, line 193
			//	refresh command to get child info. // library marker tpLink.tpl_dev_common, line 194
			smartParentRefresh() // library marker tpLink.tpl_dev_common, line 195
		} else { // library marker tpLink.tpl_dev_common, line 196
			smartRefresh() // library marker tpLink.tpl_dev_common, line 197
		} // library marker tpLink.tpl_dev_common, line 198
	} else { // library marker tpLink.tpl_dev_common, line 199
		iotRefresh() // library marker tpLink.tpl_dev_common, line 200
	} // library marker tpLink.tpl_dev_common, line 201
} // library marker tpLink.tpl_dev_common, line 202

def scheduleLogin() { // library marker tpLink.tpl_dev_common, line 204
	def startS = Math.round((59) * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 205
	def startM = Math.round((59) * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 206
	def startH = Math.round((11) * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 207
	schedule("${startS} ${startM} ${startH}/8 * * ?", "deviceHandshake") // library marker tpLink.tpl_dev_common, line 208
	return [scheduleLogin: "8 hours"] // library marker tpLink.tpl_dev_common, line 209
} // library marker tpLink.tpl_dev_common, line 210

def setPollInterval(systemPoll) { // library marker tpLink.tpl_dev_common, line 212
	int pollInterval = (systemPoll =~ /\d+/)[0].toInteger() // library marker tpLink.tpl_dev_common, line 213
	String pollMethod // library marker tpLink.tpl_dev_common, line 214
	if (getDataValue("api") == "smart") { // library marker tpLink.tpl_dev_common, line 215
		if (DEV_TYPE == "Parent") { // library marker tpLink.tpl_dev_common, line 216
			pollMethod = "smartParentPoll" // library marker tpLink.tpl_dev_common, line 217
		} else { // library marker tpLink.tpl_dev_common, line 218
			pollMethod = "smartPoll" // library marker tpLink.tpl_dev_common, line 219
		} // library marker tpLink.tpl_dev_common, line 220
	} else { // library marker tpLink.tpl_dev_common, line 221
		pollMethod = "iotPoll" // library marker tpLink.tpl_dev_common, line 222
	} // library marker tpLink.tpl_dev_common, line 223
	if (systemPoll.contains("sec")) { // library marker tpLink.tpl_dev_common, line 224
		state.retry = false // library marker tpLink.tpl_dev_common, line 225
		def startS = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 226
		schedule("${startS}/${pollInterval} * * * * ?", pollMethod) // library marker tpLink.tpl_dev_common, line 227
		logWarn("setPollInterval: Polling intervals of less than one minute " + // library marker tpLink.tpl_dev_common, line 228
				"can take high resources and may impact hub performance.") // library marker tpLink.tpl_dev_common, line 229
	} else { // library marker tpLink.tpl_dev_common, line 230
		state.retry = true // library marker tpLink.tpl_dev_common, line 231
		def startS = Math.round(59 * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 232
		def startM = Math.round((pollInterval-1) * Math.random()).toInteger() // library marker tpLink.tpl_dev_common, line 233
		schedule("${startS} ${startM}/${pollInterval} * * * ?", pollMethod) // library marker tpLink.tpl_dev_common, line 234
	} // library marker tpLink.tpl_dev_common, line 235
	return [setPollInterval: [systemPoll: systemPoll, pollMethod: pollMethod]] // library marker tpLink.tpl_dev_common, line 236
} // library marker tpLink.tpl_dev_common, line 237

def deviceHandshake(protocol = getDataValue("protocol")) { // library marker tpLink.tpl_dev_common, line 239
	if (protocol == "klap") { // library marker tpLink.tpl_dev_common, line 240
		klapHandshake(getDataValue("baseUrl"), parent.localHash) // library marker tpLink.tpl_dev_common, line 241
	} else if (protocol == "aes") { // library marker tpLink.tpl_dev_common, line 242
		aesHandshake(getDataValue("baseUrl")) // library marker tpLink.tpl_dev_common, line 243
	} else { // library marker tpLink.tpl_dev_common, line 244
		iotRefresh() // library marker tpLink.tpl_dev_common, line 245
	} // library marker tpLink.tpl_dev_common, line 246
} // library marker tpLink.tpl_dev_common, line 247

def configure() { // library marker tpLink.tpl_dev_common, line 249
	//	If version update not completed, both the // library marker tpLink.tpl_dev_common, line 250
	//	getDataProtocol() would return null. // library marker tpLink.tpl_dev_common, line 251
	//	Only check xor device if it is already xor. // library marker tpLink.tpl_dev_common, line 252
	if (getDataValue("protocol") == "xor") { // library marker tpLink.tpl_dev_common, line 253
		sendUdp(xorDiscCmd(), "parseCheckXor", getDataValue("baseUrl"), 5) // library marker tpLink.tpl_dev_common, line 254
	} else if (getDataValue("protocol") == "klap") { // library marker tpLink.tpl_dev_common, line 255
		checkSecure() // library marker tpLink.tpl_dev_common, line 256
	} else if (settings.version == null) { // library marker tpLink.tpl_dev_common, line 257
		verUpdate_2_5_3()  // library marker tpLink.tpl_dev_common, line 258
	} // library marker tpLink.tpl_dev_common, line 259
} // library marker tpLink.tpl_dev_common, line 260

def parseCheckXor(message) { // library marker tpLink.tpl_dev_common, line 262
	Map logData = [method: "parseCheckXor"] // library marker tpLink.tpl_dev_common, line 263
	if (message == "udpTimeout") { // library marker tpLink.tpl_dev_common, line 264
		logData << [status: "noXorDeviceFound", action: "checkSecure"] // library marker tpLink.tpl_dev_common, line 265
		checkSecure() // library marker tpLink.tpl_dev_common, line 266
	} else { // library marker tpLink.tpl_dev_common, line 267
		unschedule("udpTimeout") // library marker tpLink.tpl_dev_common, line 268
		Map respData = parseUdp(message, "xor") // library marker tpLink.tpl_dev_common, line 269
		if (respData.status == "ok") { // library marker tpLink.tpl_dev_common, line 270
			Map cmdResp = respData.cmdResp.system.get_sysinfo // library marker tpLink.tpl_dev_common, line 271
			def foundDni // library marker tpLink.tpl_dev_common, line 272
			if (cmdResp.mic_mac) { // library marker tpLink.tpl_dev_common, line 273
				foundDni = cmdResp.mic_mac // library marker tpLink.tpl_dev_common, line 274
			} else { // library marker tpLink.tpl_dev_common, line 275
				foundDni = cmdResp.mac.replace(/:/, "") // library marker tpLink.tpl_dev_common, line 276
			} // library marker tpLink.tpl_dev_common, line 277
			if (getDataValue("plugNo")) { // library marker tpLink.tpl_dev_common, line 278
				foundDni = foundDni + getDataValue("plugNo") // library marker tpLink.tpl_dev_common, line 279
			} // library marker tpLink.tpl_dev_common, line 280
			if (foundDni == device.deviceNetworkId) { // library marker tpLink.tpl_dev_common, line 281
				logData << [status: "deviceFound", action: "updated"] // library marker tpLink.tpl_dev_common, line 282
				runIn(1, deviceHandshake) // library marker tpLink.tpl_dev_common, line 283
			} else { // library marker tpLink.tpl_dev_common, line 284
				logData << [status: "foundDifferentDevice", action: "quickPoll"] // library marker tpLink.tpl_dev_common, line 285
				parent.pollForDevices("poll") // library marker tpLink.tpl_dev_common, line 286
			} // library marker tpLink.tpl_dev_common, line 287
		} else { // library marker tpLink.tpl_dev_common, line 288
			logData << [status: "invalidUdpResponse", action: "checkSecure"] // library marker tpLink.tpl_dev_common, line 289
			checkSecure() // library marker tpLink.tpl_dev_common, line 290
		} // library marker tpLink.tpl_dev_common, line 291
	} // library marker tpLink.tpl_dev_common, line 292
	logInfo(logData) // library marker tpLink.tpl_dev_common, line 293
} // library marker tpLink.tpl_dev_common, line 294

def checkSecure() { // library marker tpLink.tpl_dev_common, line 296
	//	If XOR Fails or device is already Secure..... // library marker tpLink.tpl_dev_common, line 297
	def ip = (getDataValue("baseUrl") =~ /\b\d{1,3}(?:\.\d{1,3}){3}\b/)[0] // library marker tpLink.tpl_dev_common, line 298
	def ipPort = ip + ":20002" // library marker tpLink.tpl_dev_common, line 299
	sendUdp(secureDiscCmd(), "parseCheckSecure", ipPort) // library marker tpLink.tpl_dev_common, line 300
} // library marker tpLink.tpl_dev_common, line 301

def parseCheckSecure(message) { // library marker tpLink.tpl_dev_common, line 303
//	If an XOR device is found here (therefore, not found in the XOR Check), this // library marker tpLink.tpl_dev_common, line 304
//	will transform the device from XOR to KLAP by updating the protocol and // library marker tpLink.tpl_dev_common, line 305
//	baseURL values determined herein. // library marker tpLink.tpl_dev_common, line 306
	Map logData = [method: "parseCheckSecure"] // library marker tpLink.tpl_dev_common, line 307
	if (message == "udpTimeout") { // library marker tpLink.tpl_dev_common, line 308
		logData << [status: "noSecureDeviceFound", next: "quickFind"] // library marker tpLink.tpl_dev_common, line 309
		parent.pollForDevices("quickFind", 5) // library marker tpLink.tpl_dev_common, line 310
	} else { // library marker tpLink.tpl_dev_common, line 311
		unschedule("udpTimeout") // library marker tpLink.tpl_dev_common, line 312
		Map respData = parseUdp(message, "secure") // library marker tpLink.tpl_dev_common, line 313
		if (respData.status == "ok") { // library marker tpLink.tpl_dev_common, line 314
			Map cmdResp = respData.cmdResp.result // library marker tpLink.tpl_dev_common, line 315
			String foundProt = cmdResp.mgt_encrypt_schm.encrypt_type.toLowerCase() // library marker tpLink.tpl_dev_common, line 316
			def foundDni = cmdResp.mac.replace(/-/, "") // library marker tpLink.tpl_dev_common, line 317
			if (getDataValue("plugNo")) { foundDni = foundDni + getDataValue("plugNo") } // library marker tpLink.tpl_dev_common, line 318
			if (foundDni == device.deviceNetworkId) { // library marker tpLink.tpl_dev_common, line 319
				if (foundProt == "tpap") { // library marker tpLink.tpl_dev_common, line 320
					logData << [foundProt: "TPAP", action: "NOT UPDATED. TPAP not supported.", // library marker tpLink.tpl_dev_common, line 321
								note: "divice will likely work as specified"] // library marker tpLink.tpl_dev_common, line 322
				} else if (foundProt != getDataValue("protocol") && foundProt != "tpap") { // library marker tpLink.tpl_dev_common, line 323
					String httpPort = cmdResp.mgt_encrypt_schm.http_port // library marker tpLink.tpl_dev_common, line 324
					String httpStr = "http://" // library marker tpLink.tpl_dev_common, line 325
					String httpPath = "/app" // library marker tpLink.tpl_dev_common, line 326
					String baseUrl = httpStr + respData.ip + ":" + httpPort + httpPath // library marker tpLink.tpl_dev_common, line 327
					logData << [update: "protocolUpdated", newProtocol: foundProt, baseUrl: baseUrl] // library marker tpLink.tpl_dev_common, line 328
					updateDataValue("protocol", foundProt) // library marker tpLink.tpl_dev_common, line 329
					updateDataValue("baseUrl", baseUrl) // library marker tpLink.tpl_dev_common, line 330
				} // library marker tpLink.tpl_dev_common, line 331
				logData << [status: "deviceFound", next: "deviceHandshake"] // library marker tpLink.tpl_dev_common, line 332
				runIn(1, deviceHandshake) // library marker tpLink.tpl_dev_common, line 333
			} else { // library marker tpLink.tpl_dev_common, line 334
				logData << [status: "foundDifferentDevice", next: "pollForDevices"] // library marker tpLink.tpl_dev_common, line 335
			} // library marker tpLink.tpl_dev_common, line 336
		} else { // library marker tpLink.tpl_dev_common, line 337
			logData << [status: "invalidUdpResponse", next: "pollForDevices"] // library marker tpLink.tpl_dev_common, line 338
		} // library marker tpLink.tpl_dev_common, line 339
	} // library marker tpLink.tpl_dev_common, line 340
	logInfo(logData) // library marker tpLink.tpl_dev_common, line 341
} // library marker tpLink.tpl_dev_common, line 342

/* // library marker tpLink.tpl_dev_common, line 344
In the app during a find (discovery or quick) device data is generated // library marker tpLink.tpl_dev_common, line 345
and sent to this method. XOR data should always (by design) comes first. // library marker tpLink.tpl_dev_common, line 346
To preclude multiple processing, set state.updProcessed to true the first // library marker tpLink.tpl_dev_common, line 347
time an update is processed and skip the remaining. // library marker tpLink.tpl_dev_common, line 348
So if several finds are done rapidly, only the FIRST until the state times out. // library marker tpLink.tpl_dev_common, line 349
Future updates: if there are changes in dataValues, an update section will // library marker tpLink.tpl_dev_common, line 350
need to be added. // library marker tpLink.tpl_dev_common, line 351
*/ // library marker tpLink.tpl_dev_common, line 352
void updateDeviceData(Map devData) { // library marker tpLink.tpl_dev_common, line 353
	Map logData = [method: "updateDeviceData", devDataSize: devData.size()] // library marker tpLink.tpl_dev_common, line 354
//	Instrumentation	 // library marker tpLink.tpl_dev_common, line 355
//log.trace "updateDeviceData: [devData: $devData]" // library marker tpLink.tpl_dev_common, line 356
	if (devData != [:]) { // library marker tpLink.tpl_dev_common, line 357
		/*	Second path to transition from Kasa to KasaTapo in case (for any // library marker tpLink.tpl_dev_common, line 358
			reason) the hubStartupHandler is not successful. By running quickFind // library marker tpLink.tpl_dev_common, line 359
			or discovery in the app, data is sent to child devices. // library marker tpLink.tpl_dev_common, line 360
			Other paths are in methods poll() and configure().*/ // library marker tpLink.tpl_dev_common, line 361
		if (version == null) { // library marker tpLink.tpl_dev_common, line 362
			verUpdate_2_5_3() // library marker tpLink.tpl_dev_common, line 363
			return // library marker tpLink.tpl_dev_common, line 364
		} // library marker tpLink.tpl_dev_common, line 365
		//	During an app.quickFind, only the protocol, baseUrl, and version are // library marker tpLink.tpl_dev_common, line 366
		//	provided. // library marker tpLink.tpl_dev_common, line 367
		String protocol = getDataValue("protocol") // library marker tpLink.tpl_dev_common, line 368
		String baseUrl = getDataValue("baseUrl") // library marker tpLink.tpl_dev_common, line 369
		String version = getDataValue("version") // library marker tpLink.tpl_dev_common, line 370
		//	Device will update to KLAP once it is discovered via klap but  // library marker tpLink.tpl_dev_common, line 371
		//	not via XOR. // library marker tpLink.tpl_dev_common, line 372
		//	deviceHandshake is only run if something as changed. // library marker tpLink.tpl_dev_common, line 373
		String notice = "Protocol or baseUrl are changed. CHECK DEVICE" // library marker tpLink.tpl_dev_common, line 374
		boolean newData = false // library marker tpLink.tpl_dev_common, line 375
		if (state.updProcessed == true) { // library marker tpLink.tpl_dev_common, line 376
			logData << [notProcessed: "XOR data previously processed"] // library marker tpLink.tpl_dev_common, line 377
		} else if (devData.protocol == protocol) { // library marker tpLink.tpl_dev_common, line 378
			//	protocol not processed. Only xor or klap baseUrl processed. // library marker tpLink.tpl_dev_common, line 379
			if (baseUrl != devData.baseUrl) { // library marker tpLink.tpl_dev_common, line 380
				updateDataValue("baseUrl", devData.baseUrl) // library marker tpLink.tpl_dev_common, line 381
				logData << [newBaseUrl: devData.baseUrl, NOTICE: notice] // library marker tpLink.tpl_dev_common, line 382
				newData = true // library marker tpLink.tpl_dev_common, line 383
			} // library marker tpLink.tpl_dev_common, line 384
			state.updProcessed = true // library marker tpLink.tpl_dev_common, line 385
		} else if (protocol == "xor" && devData.protocol != "xor") { // library marker tpLink.tpl_dev_common, line 386
			//	protocol and baseUrl updated. This is the big case. // library marker tpLink.tpl_dev_common, line 387
			//	only goes from xor -> klap. Never from klap -> xor. // library marker tpLink.tpl_dev_common, line 388
			//	Note that preference "changeProt" provides the user // library marker tpLink.tpl_dev_common, line 389
			//	a method to manually change protocol. // library marker tpLink.tpl_dev_common, line 390
			updateDataValue("protocol", devData.protocol) // library marker tpLink.tpl_dev_common, line 391
			updateDataValue("baseUrl", devData.baseUrl) // library marker tpLink.tpl_dev_common, line 392
			logData << [newProtocol: devData.protocol, newBaseUrl: devData.baseUrl, // library marker tpLink.tpl_dev_common, line 393
						NOTICE: notice] // library marker tpLink.tpl_dev_common, line 394
			newData = true // library marker tpLink.tpl_dev_common, line 395
			state.updProcessed = true // library marker tpLink.tpl_dev_common, line 396
		} else { // library marker tpLink.tpl_dev_common, line 397
			logData << [notProcessed: "ignored xor find of a klap protocol device."] // library marker tpLink.tpl_dev_common, line 398
		} // library marker tpLink.tpl_dev_common, line 399

		if (newData) { runIn(5, configure) } // library marker tpLink.tpl_dev_common, line 401
		runIn(300, clearUpdProcessed) // library marker tpLink.tpl_dev_common, line 402
		logInfo(logData) // library marker tpLink.tpl_dev_common, line 403
	} // library marker tpLink.tpl_dev_common, line 404
} // library marker tpLink.tpl_dev_common, line 405

void clearUpdProcessed() { state.remove("xorReceived") } // library marker tpLink.tpl_dev_common, line 407

//	Initiated by app.hubStartupHandler. // library marker tpLink.tpl_dev_common, line 409
//	Does an immediated update of the device data followed by an // library marker tpLink.tpl_dev_common, line 410
//	updated to reset settings and kick-start the device. Only // library marker tpLink.tpl_dev_common, line 411
//	executes changes if version is null (the major transition // library marker tpLink.tpl_dev_common, line 412
//	signature). // library marker tpLink.tpl_dev_common, line 413
//	Rationale for separate method. This method does not rely on // library marker tpLink.tpl_dev_common, line 414
//	UDP - XOR communications from the device to execute successfully // library marker tpLink.tpl_dev_common, line 415
//	(a frequent issue) and will update the device to it's current // library marker tpLink.tpl_dev_common, line 416
//	settings for UDP-XOR. If UDP failure is persistent, configure will // library marker tpLink.tpl_dev_common, line 417
//	catch this (part of error handling) as well an app discover action. // library marker tpLink.tpl_dev_common, line 418
def verUpdate_2_5_3() { // library marker tpLink.tpl_dev_common, line 419
	if (settings.version == null) { // library marker tpLink.tpl_dev_common, line 420
		unschedule() // library marker tpLink.tpl_dev_common, line 421
		logInfo("EXECUTING ONE-TIME CONVERSION: kasa Integration to kasaTapoIntegration") // library marker tpLink.tpl_dev_common, line 422
		log.warn "This update will not correct IP issues if it is a local LAN issue." // library marker tpLink.tpl_dev_common, line 423
		log.warn "CHECK YOUR DEVICE AND RUN CONFIGURATION IF THERE ARE ISSUES." // library marker tpLink.tpl_dev_common, line 424
		Map respData = [method: "version_2_5_3_update"] // library marker tpLink.tpl_dev_common, line 425
		updateDataValue("protocol", "xor") // library marker tpLink.tpl_dev_common, line 426
		updateDataValue("api", "iot") // library marker tpLink.tpl_dev_common, line 427
		Map currDataValues = device.getData() // library marker tpLink.tpl_dev_common, line 428
		Map currSettings = settings // library marker tpLink.tpl_dev_common, line 429
//	Instrumentation	for test // library marker tpLink.tpl_dev_common, line 430
//log.debug "currDataValues: $currDataValues" // library marker tpLink.tpl_dev_common, line 431
//log.debug "currSettings: $currSettings" // library marker tpLink.tpl_dev_common, line 432
		if (currDataValues.deviceIP != null && currDataValues.devicePort != null) { // library marker tpLink.tpl_dev_common, line 433
			String baseUrl = "${currDataValues.deviceIP}:${currDataValues.devicePort}" // library marker tpLink.tpl_dev_common, line 434
			updateDataValue("baseUrl", baseUrl) // library marker tpLink.tpl_dev_common, line 435
		} // library marker tpLink.tpl_dev_common, line 436
		if (currDataValues.plugId != null) { // library marker tpLink.tpl_dev_common, line 437
			updateDataValue("deviceId", currDataValues.plugId) // library marker tpLink.tpl_dev_common, line 438
		} // library marker tpLink.tpl_dev_common, line 439
		if (DEV_TYPE == "Dimming Switch" || DEV_TYPE == "Parent" // library marker tpLink.tpl_dev_common, line 440
		    || DEV_TYPE == "Plug Switch") { // library marker tpLink.tpl_dev_common, line 441
			updateDataValue("hasLed", "true") // library marker tpLink.tpl_dev_common, line 442
			String ledRule = device.currentValue("led") // library marker tpLink.tpl_dev_common, line 443
			device.updateSetting("ledRule", [type: "enum", value: ledRule]) // library marker tpLink.tpl_dev_common, line 444
		} // library marker tpLink.tpl_dev_common, line 445
		updateDataValue("manufacturer", "kasa") // library marker tpLink.tpl_dev_common, line 446
		String feature = currDataValues.feature // library marker tpLink.tpl_dev_common, line 447
		if (feature != null && (feature.contains("color") || feature.contains("light"))) { // library marker tpLink.tpl_dev_common, line 448
			updateDataValue("ctHigh", "6500") // library marker tpLink.tpl_dev_common, line 449
			updateDataValue("ctLow", "2500") // library marker tpLink.tpl_dev_common, line 450
		} // library marker tpLink.tpl_dev_common, line 451
		if (currSettings.emFunction) { // library marker tpLink.tpl_dev_common, line 452
			def emEnable = false // library marker tpLink.tpl_dev_common, line 453
			updateDataValue("isEm", "true") // library marker tpLink.tpl_dev_common, line 454
			if (currSettings.emFunction == true) { emEnable = true } // library marker tpLink.tpl_dev_common, line 455
			device.updateSetting("emEnable", [type: "bool", value: emEnable]) // library marker tpLink.tpl_dev_common, line 456
		} // library marker tpLink.tpl_dev_common, line 457
	//	Create new single call baseUrl. // library marker tpLink.tpl_dev_common, line 458
		String pollInt = state.pollInterval	//	Value x minutes, x seconds // library marker tpLink.tpl_dev_common, line 459
		device.updateSetting("systemPoll", [type: "enum", value: pollInt]) // library marker tpLink.tpl_dev_common, line 460
		if (currSettings.transition_Time) { // library marker tpLink.tpl_dev_common, line 461
			device.updateSetting("transTime", [type: "number", value: currSettings.transition_Time]) // library marker tpLink.tpl_dev_common, line 462
		} // library marker tpLink.tpl_dev_common, line 463
		if (currSettings.gentleOn) { // library marker tpLink.tpl_dev_common, line 464
			def gentOn = currSettings.gentleOn.toInteger() // library marker tpLink.tpl_dev_common, line 465
			int newTransTime = ((500 + gentOn) / 1000).toInteger() // library marker tpLink.tpl_dev_common, line 466
			device.updateSetting("transTime", [type: "number", value: newTransTime]) // library marker tpLink.tpl_dev_common, line 467
		} // library marker tpLink.tpl_dev_common, line 468
		respData << [dataValue_settings: "updated to 2.5.3"] // library marker tpLink.tpl_dev_common, line 469

		//	Remove reduntant dataValues // library marker tpLink.tpl_dev_common, line 471
		removeDataValue("deviceIP") // library marker tpLink.tpl_dev_common, line 472
		removeDataValue("devicePort") // library marker tpLink.tpl_dev_common, line 473
		removeDataValue("plugId") // library marker tpLink.tpl_dev_common, line 474
		removeDataValue("feature") // library marker tpLink.tpl_dev_common, line 475
		removeDataValue("emFunction") // library marker tpLink.tpl_dev_common, line 476
		respData << [redundantDataValues: "removed"] // library marker tpLink.tpl_dev_common, line 477
		//	Remove redundnat settings // library marker tpLink.tpl_dev_common, line 478
		device.removeSetting("transition_Time") // library marker tpLink.tpl_dev_common, line 479
		device.removeSetting("gentleOn") // library marker tpLink.tpl_dev_common, line 480
		device.removeSetting("gentleOff") // library marker tpLink.tpl_dev_common, line 481
		device.removeSetting("syncBulbs") // library marker tpLink.tpl_dev_common, line 482
		device.removeSetting("manualIp") // library marker tpLink.tpl_dev_common, line 483
		device.removeSetting("manualPort") // library marker tpLink.tpl_dev_common, line 484
		device.removeSetting("nameSync") // library marker tpLink.tpl_dev_common, line 485
		device.removeSetting("rebootDev") // library marker tpLink.tpl_dev_common, line 486
		device.removeSetting("fadeOn") // library marker tpLink.tpl_dev_common, line 487
		device.removeSetting("fadeOff") // library marker tpLink.tpl_dev_common, line 488
		device.removeSetting("longPress") // library marker tpLink.tpl_dev_common, line 489
		device.removeSetting("doubleClick")	 // library marker tpLink.tpl_dev_common, line 490
		respData << [redundantSettings: "removed"] // library marker tpLink.tpl_dev_common, line 491
		//	Remove redundated states // library marker tpLink.tpl_dev_common, line 492
		state.remove("pollInterval") // library marker tpLink.tpl_dev_common, line 493
		respData << [redundantStates: "removed"] // library marker tpLink.tpl_dev_common, line 494
		//	Done at end to force run with a configure if there is // library marker tpLink.tpl_dev_common, line 495
		//	a logic error causing failure. // library marker tpLink.tpl_dev_common, line 496
		updateDataValue("version", version()) // library marker tpLink.tpl_dev_common, line 497

		runIn(5, updated) // library marker tpLink.tpl_dev_common, line 499
		logInfo(respData) // library marker tpLink.tpl_dev_common, line 500
	} // library marker tpLink.tpl_dev_common, line 501
} // library marker tpLink.tpl_dev_common, line 502

// ~~~~~ end include (1076) tpLink.tpl_dev_common ~~~~~

// ~~~~~ start include (1079) tpLink.tpl_api_smart ~~~~~
library ( // library marker tpLink.tpl_api_smart, line 1
	name: "tpl_api_smart", // library marker tpLink.tpl_api_smart, line 2
	namespace: "tpLink", // library marker tpLink.tpl_api_smart, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_api_smart, line 4
	description: "kasa-tapo color control. Hub V 2.5.3", // library marker tpLink.tpl_api_smart, line 5
	category: "utilities", // library marker tpLink.tpl_api_smart, line 6
	documentationLink: "" // library marker tpLink.tpl_api_smart, line 7
) // library marker tpLink.tpl_api_smart, line 8

//	===== Device Capability Commands ===== // library marker tpLink.tpl_api_smart, line 10
def smartRefresh() { // library marker tpLink.tpl_api_smart, line 11
	state.eventType = "digital" // library marker tpLink.tpl_api_smart, line 12
	List requests = [[method: "get_device_info"]] // library marker tpLink.tpl_api_smart, line 13
	if (emEnable) {  // library marker tpLink.tpl_api_smart, line 14
		requests << [method: "get_current_power"] // library marker tpLink.tpl_api_smart, line 15
	} // library marker tpLink.tpl_api_smart, line 16
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_api_smart, line 17
	sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_api_smart, line 18
} // library marker tpLink.tpl_api_smart, line 19

def smartPoll() { // library marker tpLink.tpl_api_smart, line 21
	state.eventType = "manual" // library marker tpLink.tpl_api_smart, line 22
	List requests = [[method: "get_device_info"]] // library marker tpLink.tpl_api_smart, line 23
	if (emEnable) {  // library marker tpLink.tpl_api_smart, line 24
		requests << [method: "get_current_power"] // library marker tpLink.tpl_api_smart, line 25
	} // library marker tpLink.tpl_api_smart, line 26
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_api_smart, line 27
	sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_api_smart, line 28
} // library marker tpLink.tpl_api_smart, line 29

//	setColor is used in CT, Color bulbs and light strip. // library marker tpLink.tpl_api_smart, line 31
def setSmartColor(params) { // library marker tpLink.tpl_api_smart, line 32
	List requests = [[method: "set_device_info", params: params], // library marker tpLink.tpl_api_smart, line 33
					 [method: "get_device_info"]] // library marker tpLink.tpl_api_smart, line 34
	Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_api_smart, line 35
	sendSmartCmd(cmdBody, true) // library marker tpLink.tpl_api_smart, line 36
	if (emEnable) { runIn(5, getSmartPower) } // library marker tpLink.tpl_api_smart, line 37
} // library marker tpLink.tpl_api_smart, line 38

//	===== Smart Implementation Interface ===== // library marker tpLink.tpl_api_smart, line 40
def sendSmartCmd(cmdBody, retry = false, parseMeth = "parseSmartResp", dni = null) { // library marker tpLink.tpl_api_smart, line 41
//	Insturmentation point for testing	 // library marker tpLink.tpl_api_smart, line 42
//log.info "cmdBody: $cmdBody" // library marker tpLink.tpl_api_smart, line 43
	if (retry == true) { // library marker tpLink.tpl_api_smart, line 44
		Map cmdData = [cmdBody: cmdBody, action: parseMeth, retryMeth: "sendSmartCmd"] // library marker tpLink.tpl_api_smart, line 45
		state.cmdData = cmdData // library marker tpLink.tpl_api_smart, line 46
	} // library marker tpLink.tpl_api_smart, line 47
	String cmd = new groovy.json.JsonBuilder(cmdBody).toString() // library marker tpLink.tpl_api_smart, line 48
	Map reqParams = [:] // library marker tpLink.tpl_api_smart, line 49
	logDebug("sendSmartCmd: $cmdBody") // library marker tpLink.tpl_api_smart, line 50
	if (getDataValue("protocol") == "klap") { // library marker tpLink.tpl_api_smart, line 51
		byte[] keyByte = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_api_smart, line 52
		byte[] ivByte = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_api_smart, line 53
		byte[] sigByte = new JsonSlurper().parseText(encSig) // library marker tpLink.tpl_api_smart, line 54
		String uri = "${getDataValue("baseUrl")}/request?seq=${state.seqNo + 1}" // library marker tpLink.tpl_api_smart, line 55
		reqParams = getKlapParams(cmd, keyByte, ivByte, sigByte, state.seqNo, uri, cookie) // library marker tpLink.tpl_api_smart, line 56
	} else { // library marker tpLink.tpl_api_smart, line 57
		byte[] encKey = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_api_smart, line 58
		byte[] encIv = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_api_smart, line 59
		String uri = "${getDataValue("baseUrl")}?token=${token}" // library marker tpLink.tpl_api_smart, line 60
		reqParams = getAesParams(cmd, encKey, encIv, uri, cookie) // library marker tpLink.tpl_api_smart, line 61
	} // library marker tpLink.tpl_api_smart, line 62
	asynchttpPost(parseMeth, reqParams, [dni: dni, retry: retry]) // library marker tpLink.tpl_api_smart, line 63
} // library marker tpLink.tpl_api_smart, line 64

def smartNullParse(resp, data) { } // library marker tpLink.tpl_api_smart, line 66

def parseSmartResp(resp, data) { // library marker tpLink.tpl_api_smart, line 68
	Map logData = [method: "parseSmartResp"] // library marker tpLink.tpl_api_smart, line 69
	def respData = parseKlapAesResp(resp) // library marker tpLink.tpl_api_smart, line 70
	if (respData.cmdResp) { // library marker tpLink.tpl_api_smart, line 71
		def result = respData.cmdResp.result // library marker tpLink.tpl_api_smart, line 72
		result.responses.each { // library marker tpLink.tpl_api_smart, line 73
			if (it.error_code == 0) { // library marker tpLink.tpl_api_smart, line 74
				distSmartData(it) // library marker tpLink.tpl_api_smart, line 75
			} else { // library marker tpLink.tpl_api_smart, line 76
				logData << [devRespError: it] // library marker tpLink.tpl_api_smart, line 77
				logDebug(logData) // library marker tpLink.tpl_api_smart, line 78
			} // library marker tpLink.tpl_api_smart, line 79
		} // library marker tpLink.tpl_api_smart, line 80
	} else { // library marker tpLink.tpl_api_smart, line 81
		logData << respData // library marker tpLink.tpl_api_smart, line 82
		if (data.retry == true && systemPoll.contains("min")) { // library marker tpLink.tpl_api_smart, line 83
			handleCommsError([status: respData.status, cryptoStatus: respData.cryptoStatus]) // library marker tpLink.tpl_api_smart, line 84
		} // library marker tpLink.tpl_api_smart, line 85
	} // library marker tpLink.tpl_api_smart, line 86
	logDebug(logData) // library marker tpLink.tpl_api_smart, line 87
} // library marker tpLink.tpl_api_smart, line 88

def parseKlapAesResp(resp) { // library marker tpLink.tpl_api_smart, line 90
	Map logData = [method: "parseKlapAesResp", status: resp.status] // library marker tpLink.tpl_api_smart, line 91
	if (resp.status == 200) { // library marker tpLink.tpl_api_smart, line 92
		Map respData = [:] // library marker tpLink.tpl_api_smart, line 93
		if (getDataValue("protocol") == "klap") { // library marker tpLink.tpl_api_smart, line 94
			logData << [dataLength: resp.data.length()] // library marker tpLink.tpl_api_smart, line 95
			respData << parseKlapData(resp) // library marker tpLink.tpl_api_smart, line 96
		} else {  // library marker tpLink.tpl_api_smart, line 97
			respData << parseAesData(resp) // library marker tpLink.tpl_api_smart, line 98
		} // library marker tpLink.tpl_api_smart, line 99
		logData << [cryptoStatus: respData.cryptoStatus, respData: respData] // library marker tpLink.tpl_api_smart, line 100
		if (respData.cryptoStatus == "OK" && respData.cmdResp != [:]) { // library marker tpLink.tpl_api_smart, line 101
			setCommsError("OK") // library marker tpLink.tpl_api_smart, line 102
			return respData // library marker tpLink.tpl_api_smart, line 103
		} // library marker tpLink.tpl_api_smart, line 104
	} else { // library marker tpLink.tpl_api_smart, line 105
		logData << [respStatus: resp.status, headers: resp.headers] // library marker tpLink.tpl_api_smart, line 106
		logDebug(logData) // library marker tpLink.tpl_api_smart, line 107
	} // library marker tpLink.tpl_api_smart, line 108
	return logData // library marker tpLink.tpl_api_smart, line 109
} // library marker tpLink.tpl_api_smart, line 110

def distSmartData(devResp) { // library marker tpLink.tpl_api_smart, line 112
	def key = devResp.method // library marker tpLink.tpl_api_smart, line 113
	def methResp = devResp.result // library marker tpLink.tpl_api_smart, line 114
	logDebug("[method: distSmartData, $key: $methResp]") // library marker tpLink.tpl_api_smart, line 115
//	Insturmentation point for testing	 // library marker tpLink.tpl_api_smart, line 116
//log.info "[method: distSmartData, $key: $methResp]" // library marker tpLink.tpl_api_smart, line 117
	switch(key) { // library marker tpLink.tpl_api_smart, line 118
		case "get_device_info": // library marker tpLink.tpl_api_smart, line 119
			parse_get_device_info(methResp) // library marker tpLink.tpl_api_smart, line 120
			parseNameUpdate(methResp) // library marker tpLink.tpl_api_smart, line 121
			break // library marker tpLink.tpl_api_smart, line 122
		case "get_current_power": // library marker tpLink.tpl_api_smart, line 123
			parse_get_current_power(methResp) // library marker tpLink.tpl_api_smart, line 124
			break // library marker tpLink.tpl_api_smart, line 125
		case "get_device_usage": // library marker tpLink.tpl_api_smart, line 126
			parse_get_device_usage(methResp) // library marker tpLink.tpl_api_smart, line 127
			break // library marker tpLink.tpl_api_smart, line 128
		case "get_on_off_gradually_info": // library marker tpLink.tpl_api_smart, line 129
			parse_get_on_off_gradually_info(methResp) // library marker tpLink.tpl_api_smart, line 130
			break // library marker tpLink.tpl_api_smart, line 131
		case "get_child_device_list": // library marker tpLink.tpl_api_smart, line 132
			parse_get_child_device_list(methResp) // library marker tpLink.tpl_api_smart, line 133
			break // library marker tpLink.tpl_api_smart, line 134
		case "get_led_info": // library marker tpLink.tpl_api_smart, line 135
			parse_get_led_info(methResp) // library marker tpLink.tpl_api_smart, line 136
			break // library marker tpLink.tpl_api_smart, line 137
		case "device_reboot": // library marker tpLink.tpl_api_smart, line 138
			runIn(10, configure) // library marker tpLink.tpl_api_smart, line 139
		default: // library marker tpLink.tpl_api_smart, line 140
			if (!devResp.method.contains("set_")) { // library marker tpLink.tpl_api_smart, line 141
				Map logData = [method: "distGetData", key: key,  // library marker tpLink.tpl_api_smart, line 142
							   methResp: methResp, status: "unprocessed"] // library marker tpLink.tpl_api_smart, line 143
				logDebug(logData) // library marker tpLink.tpl_api_smart, line 144
			} // library marker tpLink.tpl_api_smart, line 145
	} // library marker tpLink.tpl_api_smart, line 146
} // library marker tpLink.tpl_api_smart, line 147

// ~~~~~ end include (1079) tpLink.tpl_api_smart ~~~~~

// ~~~~~ start include (1078) tpLink.tpl_api_iot ~~~~~
library ( // library marker tpLink.tpl_api_iot, line 1
	name: "tpl_api_iot", // library marker tpLink.tpl_api_iot, line 2
	namespace: "tpLink", // library marker tpLink.tpl_api_iot, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_api_iot, line 4
	description: "IOT Protocol-specific methods. Hub V 2.5.3", // library marker tpLink.tpl_api_iot, line 5
	category: "utilities", // library marker tpLink.tpl_api_iot, line 6
	documentationLink: "" // library marker tpLink.tpl_api_iot, line 7
)  // library marker tpLink.tpl_api_iot, line 8

//	===== Device Capability Commands ===== // library marker tpLink.tpl_api_iot, line 10
void iotRefresh() { // library marker tpLink.tpl_api_iot, line 11
	state.eventType = "digital" // library marker tpLink.tpl_api_iot, line 12
	Map cmdBody = [system: [get_sysinfo: []]] // library marker tpLink.tpl_api_iot, line 13
	sendIotCmd(cmdBody, true) // library marker tpLink.tpl_api_iot, line 14
	if (emEnable) {runIn(1, iotGetRealtime) } // library marker tpLink.tpl_api_iot, line 15
} // library marker tpLink.tpl_api_iot, line 16

void iotPoll() { // library marker tpLink.tpl_api_iot, line 18
	state.eventType = "manual" // library marker tpLink.tpl_api_iot, line 19
	Map cmdBody = [system: [get_sysinfo: []]] // library marker tpLink.tpl_api_iot, line 20
	sendIotCmd(cmdBody, true) // library marker tpLink.tpl_api_iot, line 21
	if (emEnable) {runIn(1, iotGetRealtime) } // library marker tpLink.tpl_api_iot, line 22
} // library marker tpLink.tpl_api_iot, line 23

//	Used in multiple drivers / capabilities // library marker tpLink.tpl_api_iot, line 25
def setIotColor(params) { // library marker tpLink.tpl_api_iot, line 26
	Map cmdBody = ["${SYSTEM}": ["${LIGHT_CMD}": params]] // library marker tpLink.tpl_api_iot, line 27
	sendIotCmd(cmdBody, true) // library marker tpLink.tpl_api_iot, line 28
	if (emEnable) { runIn(5, iotGetRealtime) } // library marker tpLink.tpl_api_iot, line 29
} // library marker tpLink.tpl_api_iot, line 30

//	===== Iot Implementation Interface ===== // library marker tpLink.tpl_api_iot, line 32
def sendIotCmd(cmdBody, retry = false, action = null, dni = null) { // library marker tpLink.tpl_api_iot, line 33
	String cmd = new groovy.json.JsonBuilder(cmdBody).toString() // library marker tpLink.tpl_api_iot, line 34
	if (retry == true) { // library marker tpLink.tpl_api_iot, line 35
		Map cmdData = [cmdBody: cmdBody, action: action, retryMeth: "sendIotCmd"] // library marker tpLink.tpl_api_iot, line 36
		state.cmdData = cmdData // library marker tpLink.tpl_api_iot, line 37
	} // library marker tpLink.tpl_api_iot, line 38
	if (getDataValue("protocol") == "xor") { // library marker tpLink.tpl_api_iot, line 39
		logDebug("[method: sendIotCmd(XOR), cmdBody: $cmdBody, RETRY: $retry]") // library marker tpLink.tpl_api_iot, line 40
//	Insturmentation point for testing	 // library marker tpLink.tpl_api_iot, line 41
//log.info "[method: sendIotCmd(XOR), cmdBody: $cmdBody, RETRY: $retry]" // library marker tpLink.tpl_api_iot, line 42
		if (action == null) { action = "parseIotXorResp" } // library marker tpLink.tpl_api_iot, line 43
		if (retry == true) { // library marker tpLink.tpl_api_iot, line 44
			runIn(10, udpTimeout) // library marker tpLink.tpl_api_iot, line 45
		} // library marker tpLink.tpl_api_iot, line 46
		sendUdp(outputXOR(cmd), action, getDataValue("baseUrl")) // library marker tpLink.tpl_api_iot, line 47
	} else { // library marker tpLink.tpl_api_iot, line 48
		logDebug("[method: sendIotCmd(SMART), cmdBody: $cmdBody, RETRY: $retry]") // library marker tpLink.tpl_api_iot, line 49
//	Insturmentation point for testing	 // library marker tpLink.tpl_api_iot, line 50
//log.info "[method: sendIotCmd(SMART), cmdBody: $cmdBody, RETRY: $retry]" // library marker tpLink.tpl_api_iot, line 51
		if (action == null) { action = "parseIotKlapResp" } // library marker tpLink.tpl_api_iot, line 52
		if (encKey == null) { // library marker tpLink.tpl_api_iot, line 53
			//	Only encountered on transition from XOR to UPD first time // library marker tpLink.tpl_api_iot, line 54
			//	and does not impact transition. // library marker tpLink.tpl_api_iot, line 55
			return // library marker tpLink.tpl_api_iot, line 56
		} // library marker tpLink.tpl_api_iot, line 57
		byte[] keyByte = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_api_iot, line 58
		byte[] ivByte = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_api_iot, line 59
		byte[] sigByte = new JsonSlurper().parseText(encSig) // library marker tpLink.tpl_api_iot, line 60
		String uri = "${getDataValue("baseUrl")}/request?seq=${state.seqNo + 1}" // library marker tpLink.tpl_api_iot, line 61
		Map reqParams = getKlapParams(cmd, keyByte, ivByte, sigByte, state.seqNo, uri, cookie) // library marker tpLink.tpl_api_iot, line 62
		asynchttpPost(action, reqParams, [data: [dni:dni, retry: retry]]) // library marker tpLink.tpl_api_iot, line 63
	} // library marker tpLink.tpl_api_iot, line 64
} // library marker tpLink.tpl_api_iot, line 65

def iotNullParse(message) {} // library marker tpLink.tpl_api_iot, line 67

def parseIotXorResp(message) { // library marker tpLink.tpl_api_iot, line 69
	if (message == "udpTimeout") { // library marker tpLink.tpl_api_iot, line 70
		if (systemPoll.contains("min")) { // library marker tpLink.tpl_api_iot, line 71
			handleCommsError(message) // library marker tpLink.tpl_api_iot, line 72
			Map logData = [method: "parseXorResp", error: message] // library marker tpLink.tpl_api_iot, line 73
			logDebug(logData) // library marker tpLink.tpl_api_iot, line 74
		} // library marker tpLink.tpl_api_iot, line 75
	} else { // library marker tpLink.tpl_api_iot, line 76
		Map respData = parseUdp(message, "xor") // library marker tpLink.tpl_api_iot, line 77
		if (respData.status == "ok") { // library marker tpLink.tpl_api_iot, line 78
			setCommsError("OK") // library marker tpLink.tpl_api_iot, line 79
			respData.cmdResp.each { key, devData -> // library marker tpLink.tpl_api_iot, line 80
				distIotData(key, devData) // library marker tpLink.tpl_api_iot, line 81
			} // library marker tpLink.tpl_api_iot, line 82
		} else { // library marker tpLink.tpl_api_iot, line 83
			Map logData = [method: "parseXorResp", respData: respData] // library marker tpLink.tpl_api_iot, line 84
			logWarn(logData) // library marker tpLink.tpl_api_iot, line 85
		} // library marker tpLink.tpl_api_iot, line 86
	} // library marker tpLink.tpl_api_iot, line 87
} // library marker tpLink.tpl_api_iot, line 88

def parseIotKlapResp(resp, data) { // library marker tpLink.tpl_api_iot, line 90
	def respData = parseKlapAesResp(resp) // library marker tpLink.tpl_api_iot, line 91
	def cryptoStatus = respData.cryptoStatus // library marker tpLink.tpl_api_iot, line 92
	if (resp.status == 200 && cryptoStatus == "OK") { // library marker tpLink.tpl_api_iot, line 93
		setCommsError("OK") // library marker tpLink.tpl_api_iot, line 94
		respData.cmdResp.each { key, devData -> // library marker tpLink.tpl_api_iot, line 95
			distIotData(key, devData) // library marker tpLink.tpl_api_iot, line 96
		} // library marker tpLink.tpl_api_iot, line 97
	} else { // library marker tpLink.tpl_api_iot, line 98
		Map logData = [method: "parseIotKlapResp", retry: data.retry,  // library marker tpLink.tpl_api_iot, line 99
					   httpStatus: resp.status, cryptoStatus: cryptoStatus] // library marker tpLink.tpl_api_iot, line 100
		if (data.retry == true && systemPoll.contains("min")) { // library marker tpLink.tpl_api_iot, line 101
			handleCommsError([httpStatus: resp.status, cryptoStatus: cryptoStatus]) // library marker tpLink.tpl_api_iot, line 102
		} // library marker tpLink.tpl_api_iot, line 103
		logDebug(logData) // library marker tpLink.tpl_api_iot, line 104
	} // library marker tpLink.tpl_api_iot, line 105
} // library marker tpLink.tpl_api_iot, line 106

void distIotData(key, devData) { // library marker tpLink.tpl_api_iot, line 108
	logDebug("[method: distIotData: $key: $devData]") // library marker tpLink.tpl_api_iot, line 109
//	Insturmentation point for testing	 // library marker tpLink.tpl_api_iot, line 110
//log.info "[method: distIotData: $key: $devData]" // library marker tpLink.tpl_api_iot, line 111
	switch(key) { // library marker tpLink.tpl_api_iot, line 112
		case "system": // library marker tpLink.tpl_api_iot, line 113
			if (devData.get_sysinfo) { // library marker tpLink.tpl_api_iot, line 114
				devData = devData.get_sysinfo // library marker tpLink.tpl_api_iot, line 115
				if (nameSync != "no") { // library marker tpLink.tpl_api_iot, line 116
					parseNameUpdate(devData) // library marker tpLink.tpl_api_iot, line 117
				} // library marker tpLink.tpl_api_iot, line 118
				if (devData.light_state) { // library marker tpLink.tpl_api_iot, line 119
					devData = devData.light_state // library marker tpLink.tpl_api_iot, line 120
				} // library marker tpLink.tpl_api_iot, line 121
				parse_system(devData) // library marker tpLink.tpl_api_iot, line 122
			} // library marker tpLink.tpl_api_iot, line 123
			break // library marker tpLink.tpl_api_iot, line 124
		case "get_light_state": // library marker tpLink.tpl_api_iot, line 125
			parse_system(devData) // library marker tpLink.tpl_api_iot, line 126
			break // library marker tpLink.tpl_api_iot, line 127
		case "smartlife.iot.smartbulb.lightingservice": // library marker tpLink.tpl_api_iot, line 128
			parse_system(devData.transition_light_state) // library marker tpLink.tpl_api_iot, line 129
			break // library marker tpLink.tpl_api_iot, line 130
		case "smartlife.iot.lightStrip": // library marker tpLink.tpl_api_iot, line 131
			iotRefresh() // library marker tpLink.tpl_api_iot, line 132
			break // library marker tpLink.tpl_api_iot, line 133
		case "smartlife.iot.dimmer": // library marker tpLink.tpl_api_iot, line 134
			if(devData.get_dimmer_parameters) { // library marker tpLink.tpl_api_iot, line 135
				parse_get_dimmer_parameters(devData.get_dimmer_parameters) // library marker tpLink.tpl_api_iot, line 136
			} // library marker tpLink.tpl_api_iot, line 137
			break // library marker tpLink.tpl_api_iot, line 138
		case "smartlife.iot.common.emeter": // library marker tpLink.tpl_api_iot, line 139
		case "emeter": // library marker tpLink.tpl_api_iot, line 140
			parse_emeter(devData) // library marker tpLink.tpl_api_iot, line 141
			break // library marker tpLink.tpl_api_iot, line 142
		case "smartlife.iot.common.system": // library marker tpLink.tpl_api_iot, line 143
			if (devData.reboot) { // library marker tpLink.tpl_api_iot, line 144
				runIn(10, updated)  // library marker tpLink.tpl_api_iot, line 145
			} // library marker tpLink.tpl_api_iot, line 146
			break // library marker tpLink.tpl_api_iot, line 147
		default: // library marker tpLink.tpl_api_iot, line 148
			Map logData = [method: "distIotData", key: key, devData: devData, status: "unprocessed"] // library marker tpLink.tpl_api_iot, line 149
			logDebug(logData) // library marker tpLink.tpl_api_iot, line 150
	} // library marker tpLink.tpl_api_iot, line 151
} // library marker tpLink.tpl_api_iot, line 152

// ~~~~~ end include (1078) tpLink.tpl_api_iot ~~~~~

// ~~~~~ start include (1081) tpLink.tpl_prot_klap ~~~~~
library ( // library marker tpLink.tpl_prot_klap, line 1
	name: "tpl_prot_klap", // library marker tpLink.tpl_prot_klap, line 2
	namespace: "tpLink", // library marker tpLink.tpl_prot_klap, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_prot_klap, line 4
	description: "KLAP protocol. Hub V 2.5.3", // library marker tpLink.tpl_prot_klap, line 5
	category: "utilities", // library marker tpLink.tpl_prot_klap, line 6
	documentationLink: "" // library marker tpLink.tpl_prot_klap, line 7
) // library marker tpLink.tpl_prot_klap, line 8

//	Imports for both Klap and AES // library marker tpLink.tpl_prot_klap, line 10
import org.json.JSONObject // library marker tpLink.tpl_prot_klap, line 11
import groovy.json.JsonOutput // library marker tpLink.tpl_prot_klap, line 12
import groovy.json.JsonBuilder // library marker tpLink.tpl_prot_klap, line 13
import groovy.json.JsonSlurper // library marker tpLink.tpl_prot_klap, line 14
import java.security.spec.PKCS8EncodedKeySpec // library marker tpLink.tpl_prot_klap, line 15
import javax.crypto.Cipher // library marker tpLink.tpl_prot_klap, line 16
import java.security.KeyFactory // library marker tpLink.tpl_prot_klap, line 17
import java.util.Random // library marker tpLink.tpl_prot_klap, line 18
import javax.crypto.spec.SecretKeySpec // library marker tpLink.tpl_prot_klap, line 19
import javax.crypto.spec.IvParameterSpec // library marker tpLink.tpl_prot_klap, line 20
import java.security.MessageDigest // library marker tpLink.tpl_prot_klap, line 21

//	===== KLAP synchttp command ===== // library marker tpLink.tpl_prot_klap, line 23
void sendKlapSyncCmd(cmdBody, retry = false, parseMeth = "parseSmartResp", dni = null) { // library marker tpLink.tpl_prot_klap, line 24
	if (retry == true) { // library marker tpLink.tpl_prot_klap, line 25
		Map cmdData = [cmdBody: cmdBody, action: parseMeth, retryMeth: "sendKlapSyncCmd"] // library marker tpLink.tpl_prot_klap, line 26
		state.cmdData = cmdData // library marker tpLink.tpl_prot_klap, line 27
	} // library marker tpLink.tpl_prot_klap, line 28
	String cmd = new groovy.json.JsonBuilder(cmdBody).toString() // library marker tpLink.tpl_prot_klap, line 29
	byte[] keyByte = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_prot_klap, line 30
	byte[] ivByte = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_prot_klap, line 31
	byte[] sigByte = new JsonSlurper().parseText(encSig) // library marker tpLink.tpl_prot_klap, line 32
	String uri = "${getDataValue("baseUrl")}/request?seq=${state.seqNo + 1}" // library marker tpLink.tpl_prot_klap, line 33
	Map reqParams = getKlapParams(cmd, keyByte, ivByte, sigByte, state.seqNo, uri, cookie) // library marker tpLink.tpl_prot_klap, line 34
	httpPost(reqParams) { resp -> // library marker tpLink.tpl_prot_klap, line 35
		if (resp.status == 200 && resp.data != null) { // library marker tpLink.tpl_prot_klap, line 36
			byte[] keyBytes = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_prot_klap, line 37
			byte[] ivBytes = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_prot_klap, line 38
			int seqNo = state.seqNo // library marker tpLink.tpl_prot_klap, line 39
			byte[] encoded = resp.data.readAllBytes()[32..-1] // library marker tpLink.tpl_prot_klap, line 40
			String decoded // library marker tpLink.tpl_prot_klap, line 41
			try { // library marker tpLink.tpl_prot_klap, line 42
				decoded = klapDecrypt(encoded, keyBytes, ivBytes, seqNo) // library marker tpLink.tpl_prot_klap, line 43
			} catch (err) { // library marker tpLink.tpl_prot_klap, line 44
				logDebug("sendKlapSyncCmd: [cryptoError: ${err}]") // library marker tpLink.tpl_prot_klap, line 45
				runInMillis(200, handleCommsError, [data:"cryptoDecodeError"]) // library marker tpLink.tpl_prot_klap, line 46
			} // library marker tpLink.tpl_prot_klap, line 47
			Map cmdResp = new JsonSlurper().parseText(decoded) // library marker tpLink.tpl_prot_klap, line 48
			if (parseMeth == "parseSmartInstData") { // library marker tpLink.tpl_prot_klap, line 49
				parseSmartInstData(cmdResp, "httpSync") // library marker tpLink.tpl_prot_klap, line 50
			} // library marker tpLink.tpl_prot_klap, line 51
		} else { // library marker tpLink.tpl_prot_klap, line 52
			logDebug("sendKlapSyncCmd: [httpReturnData: resp.properties]") // library marker tpLink.tpl_prot_klap, line 53
			runInMillis(200, handleCommsError, [data: "httpRespError"]) // library marker tpLink.tpl_prot_klap, line 54
		} // library marker tpLink.tpl_prot_klap, line 55
	} // library marker tpLink.tpl_prot_klap, line 56
} // library marker tpLink.tpl_prot_klap, line 57

//	===== KLAP Smart Support ===== // library marker tpLink.tpl_prot_klap, line 59
def klapHandshake(baseUrl, localHash, devData = [:]) { // library marker tpLink.tpl_prot_klap, line 60
	byte[] localSeed = getSeed(16) // library marker tpLink.tpl_prot_klap, line 61
	devData << [localSeed: localSeed, localHash: localHash] // library marker tpLink.tpl_prot_klap, line 62
	Map reqParams = [uri: "${baseUrl}/handshake1", // library marker tpLink.tpl_prot_klap, line 63
					 headers: ["Connection": "close"], // library marker tpLink.tpl_prot_klap, line 64
					 body: localSeed, // library marker tpLink.tpl_prot_klap, line 65
					 contentType: "application/octet-stream", // library marker tpLink.tpl_prot_klap, line 66
					 requestContentType: "application/octet-stream", // library marker tpLink.tpl_prot_klap, line 67
					 timeout: 5 // library marker tpLink.tpl_prot_klap, line 68
					] // library marker tpLink.tpl_prot_klap, line 69
	asynchttpPost("parseKlapHandshake", reqParams, devData) // library marker tpLink.tpl_prot_klap, line 70
} // library marker tpLink.tpl_prot_klap, line 71

def parseKlapHandshake(resp, devData) { // library marker tpLink.tpl_prot_klap, line 73
	String hs1Status = devData.hs1Status // library marker tpLink.tpl_prot_klap, line 74
	def tryStatus // library marker tpLink.tpl_prot_klap, line 75
	if (resp.status == 200 && resp.data != null) { // library marker tpLink.tpl_prot_klap, line 76
		byte[] localSeed = devData.localSeed // library marker tpLink.tpl_prot_klap, line 77
		byte[] seedData = resp.data.decodeBase64() // library marker tpLink.tpl_prot_klap, line 78

		if (!seedData || seedData.size() < 48) { // library marker tpLink.tpl_prot_klap, line 80
			devData << [respStatus: "ERROR", // library marker tpLink.tpl_prot_klap, line 81
						failNote: "KLAP handshake response too short", // library marker tpLink.tpl_prot_klap, line 82
						hs1Status: "failed handshake response"] // library marker tpLink.tpl_prot_klap, line 83
		} else { // library marker tpLink.tpl_prot_klap, line 84
			byte[] remoteSeed = seedData[0 .. 15] // library marker tpLink.tpl_prot_klap, line 85
			byte[] serverHash = seedData[16 .. 47] // library marker tpLink.tpl_prot_klap, line 86
			byte[] localHash = devData.localHash.decodeBase64() // library marker tpLink.tpl_prot_klap, line 87
			byte[] authHash = [localSeed, remoteSeed, localHash].flatten() // library marker tpLink.tpl_prot_klap, line 88
			byte[] localAuthHash = mdEncode("SHA-256", authHash) // library marker tpLink.tpl_prot_klap, line 89
			if (localAuthHash == serverHash) { // library marker tpLink.tpl_prot_klap, line 90
				//	cookie // library marker tpLink.tpl_prot_klap, line 91
				def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker tpLink.tpl_prot_klap, line 92
				def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker tpLink.tpl_prot_klap, line 93
				//	seqNo and encIv // library marker tpLink.tpl_prot_klap, line 94
				byte[] payload = ["iv".getBytes(), localSeed, remoteSeed, localHash].flatten()	 // library marker tpLink.tpl_prot_klap, line 95
				byte[] fullIv = mdEncode("SHA-256", payload) // library marker tpLink.tpl_prot_klap, line 96
				byte[] byteSeqNo = fullIv[-4..-1] // library marker tpLink.tpl_prot_klap, line 97
				int seqNo = byteArrayToInteger(byteSeqNo) // library marker tpLink.tpl_prot_klap, line 98
				if (device) { // library marker tpLink.tpl_prot_klap, line 99
					state.seqNo = seqNo // library marker tpLink.tpl_prot_klap, line 100
				} // library marker tpLink.tpl_prot_klap, line 101
				//	encKey // library marker tpLink.tpl_prot_klap, line 102
				payload = ["lsk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker tpLink.tpl_prot_klap, line 103
				byte[] encKey = mdEncode("SHA-256", payload)[0..15] // library marker tpLink.tpl_prot_klap, line 104
				//	encSig // library marker tpLink.tpl_prot_klap, line 105
				payload = ["ldk".getBytes(), localSeed, remoteSeed, localHash].flatten() // library marker tpLink.tpl_prot_klap, line 106
				byte[] encSig = mdEncode("SHA-256", payload)[0..27] // library marker tpLink.tpl_prot_klap, line 107
				if (device) { // library marker tpLink.tpl_prot_klap, line 108
					device.updateSetting("cookie",[type:"password", value: cookie])  // library marker tpLink.tpl_prot_klap, line 109
					device.updateSetting("encKey",[type:"password", value: JsonOutput.toJson(encKey)])  // library marker tpLink.tpl_prot_klap, line 110
					device.updateSetting("encIv",[type:"password", value: JsonOutput.toJson(fullIv[0..11])])  // library marker tpLink.tpl_prot_klap, line 111
					device.updateSetting("encSig",[type:"password", value: JsonOutput.toJson(encSig)])  // library marker tpLink.tpl_prot_klap, line 112
				} else { // library marker tpLink.tpl_prot_klap, line 113
					devData << [cookie: cookie, seqNo: seqNo, encIv: fullIv[0..11],  // library marker tpLink.tpl_prot_klap, line 114
								encSig: encSig, encKey: encKey] // library marker tpLink.tpl_prot_klap, line 115
					} // library marker tpLink.tpl_prot_klap, line 116
				byte[] loginHash = [remoteSeed, localSeed, localHash].flatten() // library marker tpLink.tpl_prot_klap, line 117
				def hs2CmdStatus = klapHandshake2(loginHash, cookie, devData) // library marker tpLink.tpl_prot_klap, line 118
				if (hs2CmdStatus == "OK") { // library marker tpLink.tpl_prot_klap, line 119
					tryStatus = "OK" // library marker tpLink.tpl_prot_klap, line 120
				} else { // library marker tpLink.tpl_prot_klap, line 121
					devData << [failNote: "failed in klapHandshake2"] // library marker tpLink.tpl_prot_klap, line 122
					tryStatus = "failure" // library marker tpLink.tpl_prot_klap, line 123
				} // library marker tpLink.tpl_prot_klap, line 124
			} // library marker tpLink.tpl_prot_klap, line 125
		} // library marker tpLink.tpl_prot_klap, line 126
	} else { // library marker tpLink.tpl_prot_klap, line 127
		devData << [respStatus: resp.status, message: resp.errorMessage, // library marker tpLink.tpl_prot_klap, line 128
					failNote: "failed in parseKlapHandshake"] // library marker tpLink.tpl_prot_klap, line 129
			tryStatus = "failure" // library marker tpLink.tpl_prot_klap, line 130
	} // library marker tpLink.tpl_prot_klap, line 131
	if (tryStatus == "failure") { // library marker tpLink.tpl_prot_klap, line 132
		if (hs1Status == "tryAltHash") { // library marker tpLink.tpl_prot_klap, line 133
			hs1Status = "failed altHash attempt" // library marker tpLink.tpl_prot_klap, line 134
		} else { // library marker tpLink.tpl_prot_klap, line 135
			hs1Status = "tryAltHash" // library marker tpLink.tpl_prot_klap, line 136
		} // library marker tpLink.tpl_prot_klap, line 137
	} else { // library marker tpLink.tpl_prot_klap, line 138
		hs1Status = "complete" // library marker tpLink.tpl_prot_klap, line 139
	} // library marker tpLink.tpl_prot_klap, line 140
	devData << [hs1Status: hs1Status] // library marker tpLink.tpl_prot_klap, line 141
	if (hs1Status == "tryAltHash") { // library marker tpLink.tpl_prot_klap, line 142
		String altHash = altLocalHash // library marker tpLink.tpl_prot_klap, line 143
		String baseUrl = devData.baseUrl // library marker tpLink.tpl_prot_klap, line 144
		if (device) { // library marker tpLink.tpl_prot_klap, line 145
			altHash = parent.altLocalHash // library marker tpLink.tpl_prot_klap, line 146
			baseUrl = getDataValue("baseUrl") // library marker tpLink.tpl_prot_klap, line 147
		} // library marker tpLink.tpl_prot_klap, line 148
		klapHandshake(baseUrl, altHash, devData) // library marker tpLink.tpl_prot_klap, line 149
	} else if (hs1Status.contains("fail")) { // library marker tpLink.tpl_prot_klap, line 150
		log.warn "parseKlapHandshake: [hs1Status: ${hs1Status}]" // library marker tpLink.tpl_prot_klap, line 151
//		if (app) { atomicState.deviceWait = false } // library marker tpLink.tpl_prot_klap, line 152
	} // library marker tpLink.tpl_prot_klap, line 153
} // library marker tpLink.tpl_prot_klap, line 154

def klapHandshake2(loginHash, cookie, devData) { // library marker tpLink.tpl_prot_klap, line 156
	def uri = "${devData.baseUrl}/handshake2" // library marker tpLink.tpl_prot_klap, line 157
	String status = "OK" // library marker tpLink.tpl_prot_klap, line 158
	if (device) { uri = "${getDataValue("baseUrl")}/handshake2" } // library marker tpLink.tpl_prot_klap, line 159
	byte[] body = mdEncode("SHA-256", loginHash) // library marker tpLink.tpl_prot_klap, line 160
	try { // library marker tpLink.tpl_prot_klap, line 161
		Map reqParams = [uri: uri, // library marker tpLink.tpl_prot_klap, line 162
						 body: body, // library marker tpLink.tpl_prot_klap, line 163
						 timeout: 10, // library marker tpLink.tpl_prot_klap, line 164
						 headers: ["Cookie": cookie, "Connection": "close"], // library marker tpLink.tpl_prot_klap, line 165
						 contentType: "application/octet-stream", // library marker tpLink.tpl_prot_klap, line 166
						 requestContentType: "application/octet-stream"] // library marker tpLink.tpl_prot_klap, line 167
		asynchttpPost("parseKlapHandshake2", reqParams, devData) // library marker tpLink.tpl_prot_klap, line 168
	} catch (err) { // library marker tpLink.tpl_prot_klap, line 169
		status = "klapHandshake2Error" // library marker tpLink.tpl_prot_klap, line 170
		Map logData = [method: "klapHandshake2", status: status, error: err] // library marker tpLink.tpl_prot_klap, line 171
		log.warn logData // library marker tpLink.tpl_prot_klap, line 172
	} // library marker tpLink.tpl_prot_klap, line 173
	return status // library marker tpLink.tpl_prot_klap, line 174
} // library marker tpLink.tpl_prot_klap, line 175

def parseKlapHandshake2(resp, devData) { // library marker tpLink.tpl_prot_klap, line 177
	Map logData = [method: "parseKlapHandshake2", devData: devData] // library marker tpLink.tpl_prot_klap, line 178
	if (resp.status == 200) { // library marker tpLink.tpl_prot_klap, line 179
		logData << [respStatus: "Login OK"] // library marker tpLink.tpl_prot_klap, line 180
		if (device) { // library marker tpLink.tpl_prot_klap, line 181
			setCommsError("OK") // library marker tpLink.tpl_prot_klap, line 182
			runIn(1, refresh) // library marker tpLink.tpl_prot_klap, line 183
		} else { // library marker tpLink.tpl_prot_klap, line 184
			sendKlapDataCmd(devData) // library marker tpLink.tpl_prot_klap, line 185
		} // library marker tpLink.tpl_prot_klap, line 186
		logDebug(logData) // library marker tpLink.tpl_prot_klap, line 187
	} else { // library marker tpLink.tpl_prot_klap, line 188
		logData << [respStatus: "LOGIN FAILED", reason: "ERROR in HTTP response", // library marker tpLink.tpl_prot_klap, line 189
					resp: resp.properties] // library marker tpLink.tpl_prot_klap, line 190
		log.warn logData  // library marker tpLink.tpl_prot_klap, line 191
	} // library marker tpLink.tpl_prot_klap, line 192
} // library marker tpLink.tpl_prot_klap, line 193

def getKlapParams(cmd, encKey, encIv, encSig, seqNo, uri, cookie) { // library marker tpLink.tpl_prot_klap, line 195
	seqNo = seqNo + 1 // library marker tpLink.tpl_prot_klap, line 196
	if (device) { state.seqNo = seqNo } // library marker tpLink.tpl_prot_klap, line 197
	Map encryptedData = klapEncrypt(cmd.getBytes(), encKey,  // library marker tpLink.tpl_prot_klap, line 198
									encIv, encSig, seqNo) // library marker tpLink.tpl_prot_klap, line 199
	Map reqParams = [ // library marker tpLink.tpl_prot_klap, line 200
		uri: uri, // library marker tpLink.tpl_prot_klap, line 201
		body: encryptedData.cipherData, // library marker tpLink.tpl_prot_klap, line 202
		headers: ["Cookie": cookie, "Connection": "close"], // library marker tpLink.tpl_prot_klap, line 203
		contentType: "application/octet-stream", // library marker tpLink.tpl_prot_klap, line 204
		requestContentType: "application/octet-stream", // library marker tpLink.tpl_prot_klap, line 205
		timeout: 5, // library marker tpLink.tpl_prot_klap, line 206
		ignoreSSLIssues: true] // library marker tpLink.tpl_prot_klap, line 207
	return reqParams // library marker tpLink.tpl_prot_klap, line 208
} // library marker tpLink.tpl_prot_klap, line 209

def parseKlapData(resp) { // library marker tpLink.tpl_prot_klap, line 211
	Map parseData = [parseMethod: "parseKlapData"] // library marker tpLink.tpl_prot_klap, line 212
	try { // library marker tpLink.tpl_prot_klap, line 213
		byte[] keyBytes = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_prot_klap, line 214
		byte[] ivBytes = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_prot_klap, line 215
		int seqNo = state.seqNo // library marker tpLink.tpl_prot_klap, line 216
		byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker tpLink.tpl_prot_klap, line 217
		Map cmdResp =  new JsonSlurper().parseText( // library marker tpLink.tpl_prot_klap, line 218
			klapDecrypt(cipherResponse, keyBytes, ivBytes, seqNo)) // library marker tpLink.tpl_prot_klap, line 219
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker tpLink.tpl_prot_klap, line 220
	} catch (err) { // library marker tpLink.tpl_prot_klap, line 221
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker tpLink.tpl_prot_klap, line 222
	} // library marker tpLink.tpl_prot_klap, line 223
	return parseData // library marker tpLink.tpl_prot_klap, line 224
} // library marker tpLink.tpl_prot_klap, line 225

//	===== Encryption ===== // library marker tpLink.tpl_prot_klap, line 227
def klapEncrypt(byte[] request, encKey, encIv, encSig, seqNo) { // library marker tpLink.tpl_prot_klap, line 228
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker tpLink.tpl_prot_klap, line 229
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker tpLink.tpl_prot_klap, line 230
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker tpLink.tpl_prot_klap, line 231
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker tpLink.tpl_prot_klap, line 232
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker tpLink.tpl_prot_klap, line 233
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker tpLink.tpl_prot_klap, line 234
	byte[] cipherRequest = cipher.doFinal(request) // library marker tpLink.tpl_prot_klap, line 235

	byte[] payload = [encSig, encSeqNo, cipherRequest].flatten() // library marker tpLink.tpl_prot_klap, line 237
	byte[] signature = mdEncode("SHA-256", payload) // library marker tpLink.tpl_prot_klap, line 238
	cipherRequest = [signature, cipherRequest].flatten() // library marker tpLink.tpl_prot_klap, line 239
	return [cipherData: cipherRequest, seqNumber: seqNo] // library marker tpLink.tpl_prot_klap, line 240
} // library marker tpLink.tpl_prot_klap, line 241

def klapDecrypt(cipherResponse, encKey, encIv, seqNo) { // library marker tpLink.tpl_prot_klap, line 243
	byte[] encSeqNo = integerToByteArray(seqNo) // library marker tpLink.tpl_prot_klap, line 244
	byte[] ivEnc = [encIv, encSeqNo].flatten() // library marker tpLink.tpl_prot_klap, line 245
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker tpLink.tpl_prot_klap, line 246
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker tpLink.tpl_prot_klap, line 247
	IvParameterSpec iv = new IvParameterSpec(ivEnc) // library marker tpLink.tpl_prot_klap, line 248
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker tpLink.tpl_prot_klap, line 249
	byte[] byteResponse = cipher.doFinal(cipherResponse) // library marker tpLink.tpl_prot_klap, line 250
	return new String(byteResponse, "UTF-8") // library marker tpLink.tpl_prot_klap, line 251
} // library marker tpLink.tpl_prot_klap, line 252

// ~~~~~ end include (1081) tpLink.tpl_prot_klap ~~~~~

// ~~~~~ start include (1080) tpLink.tpl_prot_aes ~~~~~
library ( // library marker tpLink.tpl_prot_aes, line 1
	name: "tpl_prot_aes", // library marker tpLink.tpl_prot_aes, line 2
	namespace: "tpLink", // library marker tpLink.tpl_prot_aes, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_prot_aes, line 4
	description: "AES protocol. Hub V 2.5.3", // library marker tpLink.tpl_prot_aes, line 5
	category: "utilities", // library marker tpLink.tpl_prot_aes, line 6
	documentationLink: "" // library marker tpLink.tpl_prot_aes, line 7
) // library marker tpLink.tpl_prot_aes, line 8
//	AES protocol was deprecated by TP-Link aroung 2021 and for (all?) devices.  // library marker tpLink.tpl_prot_aes, line 9
//	the firmware updated transitioned to KLAP. There may still be some devices // library marker tpLink.tpl_prot_aes, line 10
//	out there; so this code is necessary. Unfortunately, no testing since all // library marker tpLink.tpl_prot_aes, line 11
//	of the developers devices were transitioned long ago. // library marker tpLink.tpl_prot_aes, line 12
//	===== AES Smart Support ===== // library marker tpLink.tpl_prot_aes, line 13
def aesHandshake(baseUrl, devData = [:]) { // library marker tpLink.tpl_prot_aes, line 14
	Map rsaKey = getRsaKey() // library marker tpLink.tpl_prot_aes, line 15
	devData << [baseUrl: baseUrl, rsaKey : rsaKey] // library marker tpLink.tpl_prot_aes, line 16
	String pubPem = "-----BEGIN PUBLIC KEY-----\n" +  // library marker tpLink.tpl_prot_aes, line 17
		"${rsaKey.public}" + "-----END PUBLIC KEY-----\n" // library marker tpLink.tpl_prot_aes, line 18
	Map cmdBody = [method: "handshake", params: [key: pubPem]] // library marker tpLink.tpl_prot_aes, line 19
	Map reqParams = [uri: baseUrl, // library marker tpLink.tpl_prot_aes, line 20
					 headers: ["Connection": "close"], // library marker tpLink.tpl_prot_aes, line 21
					 body: new JsonBuilder(cmdBody).toString(), // library marker tpLink.tpl_prot_aes, line 22
					 requestContentType: "application/json", // library marker tpLink.tpl_prot_aes, line 23
					 timeout: 5 ] // library marker tpLink.tpl_prot_aes, line 24
   asynchttpPost("parseAesHandshake", reqParams, devData) // library marker tpLink.tpl_prot_aes, line 25
} // library marker tpLink.tpl_prot_aes, line 26


def parseAesHandshake(resp, devData){ // library marker tpLink.tpl_prot_aes, line 29
	Map logData = [method: "parseAesHandshake"] // library marker tpLink.tpl_prot_aes, line 30
	if (resp.status == 200 && resp.data != null) { // library marker tpLink.tpl_prot_aes, line 31
		try { // library marker tpLink.tpl_prot_aes, line 32
			Map cmdResp =  new JsonSlurper().parseText(resp.data) // library marker tpLink.tpl_prot_aes, line 33
			def cookieHeader = resp.headers["Set-Cookie"].toString() // library marker tpLink.tpl_prot_aes, line 34
			def cookie = cookieHeader.substring(cookieHeader.indexOf(":") +1, cookieHeader.indexOf(";")) // library marker tpLink.tpl_prot_aes, line 35
			byte[] privateKeyBytes = devData.rsaKey.private.decodeBase64() // library marker tpLink.tpl_prot_aes, line 36
			byte[] deviceKeyBytes = cmdResp.result.key.getBytes("UTF-8").decodeBase64() // library marker tpLink.tpl_prot_aes, line 37
    		Cipher instance = Cipher.getInstance("RSA/ECB/PKCS1Padding") // library marker tpLink.tpl_prot_aes, line 38
			instance.init(2, KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes))) // library marker tpLink.tpl_prot_aes, line 39
			byte[] cryptoArray = instance.doFinal(deviceKeyBytes) // library marker tpLink.tpl_prot_aes, line 40
			byte[] encKey = cryptoArray[0..15] // library marker tpLink.tpl_prot_aes, line 41
			byte[] encIv = cryptoArray[16..31] // library marker tpLink.tpl_prot_aes, line 42
			logData << [respStatus: "Cookies/Keys Updated", cookie: cookie, // library marker tpLink.tpl_prot_aes, line 43
						encKey: encKey, encIv: encIv] // library marker tpLink.tpl_prot_aes, line 44
			String password = encPassword // library marker tpLink.tpl_prot_aes, line 45
			String username = encUsername // library marker tpLink.tpl_prot_aes, line 46
			if (device) { // library marker tpLink.tpl_prot_aes, line 47
				password = parent.encPassword // library marker tpLink.tpl_prot_aes, line 48
				username = parent.encUsername // library marker tpLink.tpl_prot_aes, line 49
				device.updateSetting("cookie",[type:"password", value: cookie]) // library marker tpLink.tpl_prot_aes, line 50
				device.updateSetting("encKey", [type:"password", value: JsonOutput.toJson(encKey)]) // library marker tpLink.tpl_prot_aes, line 51
				device.updateSetting("encIv", [type:"password",value: JsonOutput.toJson(encIv)]) // library marker tpLink.tpl_prot_aes, line 52
			} else { // library marker tpLink.tpl_prot_aes, line 53
				devData << [cookie: cookie, encIv: encIv, encKey: encKey] // library marker tpLink.tpl_prot_aes, line 54
			} // library marker tpLink.tpl_prot_aes, line 55
			//	Login to the device // library marker tpLink.tpl_prot_aes, line 56
			Map cmdBody = [method: "login_device", // library marker tpLink.tpl_prot_aes, line 57
						   headers: ["Connection": "close"], // library marker tpLink.tpl_prot_aes, line 58
						   params: [password: password, // library marker tpLink.tpl_prot_aes, line 59
									username: username], // library marker tpLink.tpl_prot_aes, line 60
						   requestTimeMils: 0] // library marker tpLink.tpl_prot_aes, line 61
			def cmd = JsonOutput.toJson(cmdBody).toString() // library marker tpLink.tpl_prot_aes, line 62
			Map reqParams = getAesParams(cmd, encKey, encIv, devData.baseUrl, cookie) // library marker tpLink.tpl_prot_aes, line 63
			asynchttpPost("parseAesLogin", reqParams, devData) // library marker tpLink.tpl_prot_aes, line 64
			logDebug(logData) // library marker tpLink.tpl_prot_aes, line 65
		} catch (err) { // library marker tpLink.tpl_prot_aes, line 66
			atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 67
			logData << [respStatus: "ERROR parsing HTTP resp.data", // library marker tpLink.tpl_prot_aes, line 68
						respData: resp.data, error: err] // library marker tpLink.tpl_prot_aes, line 69
			log.warn logData  // library marker tpLink.tpl_prot_aes, line 70
		} // library marker tpLink.tpl_prot_aes, line 71
	} else { // library marker tpLink.tpl_prot_aes, line 72
		atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 73
		logData << [respStatus: "ERROR in HTTP response", resp: resp.properties] // library marker tpLink.tpl_prot_aes, line 74
		log.warn logData  // library marker tpLink.tpl_prot_aes, line 75
	} // library marker tpLink.tpl_prot_aes, line 76
} // library marker tpLink.tpl_prot_aes, line 77

def parseAesLogin(resp, devData) { // library marker tpLink.tpl_prot_aes, line 79
	Map logData = [method: "parseAesLogin"] // library marker tpLink.tpl_prot_aes, line 80
	if (resp.status == 200 && resp?.json) { // library marker tpLink.tpl_prot_aes, line 81
		if (resp.json.error_code == 0) { // library marker tpLink.tpl_prot_aes, line 82
			try { // library marker tpLink.tpl_prot_aes, line 83
				byte[] keyBytes // library marker tpLink.tpl_prot_aes, line 84
				byte[] ivBytes // library marker tpLink.tpl_prot_aes, line 85
				if (device) { // library marker tpLink.tpl_prot_aes, line 86
					keyBytes = new JsonSlurper().parseText(encKey) as byte[] // library marker tpLink.tpl_prot_aes, line 87
					ivBytes = new JsonSlurper().parseText(encIv) as byte[] // library marker tpLink.tpl_prot_aes, line 88
				} else { // library marker tpLink.tpl_prot_aes, line 89
					keyBytes = devData.encKey as byte[] // library marker tpLink.tpl_prot_aes, line 90
					ivBytes = devData.encIv as byte[] // library marker tpLink.tpl_prot_aes, line 91
				} // library marker tpLink.tpl_prot_aes, line 92
				String clearResp = aesDecrypt(resp.json.result.response, // library marker tpLink.tpl_prot_aes, line 93
											  keyBytes, ivBytes)				 // library marker tpLink.tpl_prot_aes, line 94
				Map cmdResp = new JsonSlurper().parseText(clearResp) // library marker tpLink.tpl_prot_aes, line 95
				if (cmdResp.error_code == 0) { // library marker tpLink.tpl_prot_aes, line 96
					def token = cmdResp.result.token // library marker tpLink.tpl_prot_aes, line 97
					logData << [respStatus: "OK", token: token] // library marker tpLink.tpl_prot_aes, line 98
					if (device) { // library marker tpLink.tpl_prot_aes, line 99
						device.updateSetting("token",[type:"password", value: token]) // library marker tpLink.tpl_prot_aes, line 100
						setCommsError("OK") // library marker tpLink.tpl_prot_aes, line 101
						runIn(1, refresh) // library marker tpLink.tpl_prot_aes, line 102
					} else { // library marker tpLink.tpl_prot_aes, line 103
						devData << [token: token] // library marker tpLink.tpl_prot_aes, line 104
						sendAesDataCmd(devData) // library marker tpLink.tpl_prot_aes, line 105
					} // library marker tpLink.tpl_prot_aes, line 106
					logDebug(logData) // library marker tpLink.tpl_prot_aes, line 107
				} else { // library marker tpLink.tpl_prot_aes, line 108
					atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 109
					logData << [respStatus: "ERROR code in cmdResp",  // library marker tpLink.tpl_prot_aes, line 110
								error_code: cmdResp.error_code, // library marker tpLink.tpl_prot_aes, line 111
								check: "cryptoArray, credentials", data: cmdResp] // library marker tpLink.tpl_prot_aes, line 112
					log.debug logData // library marker tpLink.tpl_prot_aes, line 113
				} // library marker tpLink.tpl_prot_aes, line 114
			} catch (err) { // library marker tpLink.tpl_prot_aes, line 115
				atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 116
				logData << [respStatus: "ERROR parsing respJson", respJson: resp.json, // library marker tpLink.tpl_prot_aes, line 117
							error: err] // library marker tpLink.tpl_prot_aes, line 118
				log.debug logData // library marker tpLink.tpl_prot_aes, line 119
			} // library marker tpLink.tpl_prot_aes, line 120
		} else { // library marker tpLink.tpl_prot_aes, line 121
			atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 122
			logData << [respStatus: "ERROR code in resp.json", errorCode: resp.json.error_code, // library marker tpLink.tpl_prot_aes, line 123
						respJson: resp.json] // library marker tpLink.tpl_prot_aes, line 124
			log.debug logData // library marker tpLink.tpl_prot_aes, line 125
		} // library marker tpLink.tpl_prot_aes, line 126
	} else { // library marker tpLink.tpl_prot_aes, line 127
		atomicState.deviceWait = false // library marker tpLink.tpl_prot_aes, line 128
		logData << [respStatus: "ERROR in HTTP response", respStatus: resp.status, data: resp.properties] // library marker tpLink.tpl_prot_aes, line 129
		log.warn logData // library marker tpLink.tpl_prot_aes, line 130
	} // library marker tpLink.tpl_prot_aes, line 131
} // library marker tpLink.tpl_prot_aes, line 132

def getAesParams(cmd, encKey, encIv, uri, cookie) { // library marker tpLink.tpl_prot_aes, line 134
	Map reqBody = [method: "securePassthrough", // library marker tpLink.tpl_prot_aes, line 135
				   params: [request: aesEncrypt(cmd, encKey, encIv)]] // library marker tpLink.tpl_prot_aes, line 136
	Map reqParams = [uri: uri, // library marker tpLink.tpl_prot_aes, line 137
					 body: new groovy.json.JsonBuilder(reqBody).toString(), // library marker tpLink.tpl_prot_aes, line 138
					 contentType: "application/json", // library marker tpLink.tpl_prot_aes, line 139
					 requestContentType: "application/json", // library marker tpLink.tpl_prot_aes, line 140
					 timeout: 5, // library marker tpLink.tpl_prot_aes, line 141
					 headers: ["Cookie": cookie]] // library marker tpLink.tpl_prot_aes, line 142
	return reqParams // library marker tpLink.tpl_prot_aes, line 143
} // library marker tpLink.tpl_prot_aes, line 144

def parseAesData(resp) { // library marker tpLink.tpl_prot_aes, line 146
	Map parseData = [parseMethod: "parseAesData"] // library marker tpLink.tpl_prot_aes, line 147
	try { // library marker tpLink.tpl_prot_aes, line 148
		byte[] keyBytes = new JsonSlurper().parseText(encKey) // library marker tpLink.tpl_prot_aes, line 149
		byte[] ivBytes = new JsonSlurper().parseText(encIv) // library marker tpLink.tpl_prot_aes, line 150
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt(resp.json.result.response, // library marker tpLink.tpl_prot_aes, line 151
														 keyBytes, ivBytes)) // library marker tpLink.tpl_prot_aes, line 152
		parseData << [cryptoStatus: "OK", cmdResp: cmdResp] // library marker tpLink.tpl_prot_aes, line 153
	} catch (err) { // library marker tpLink.tpl_prot_aes, line 154
		parseData << [cryptoStatus: "decryptDataError", error: err] // library marker tpLink.tpl_prot_aes, line 155
	} // library marker tpLink.tpl_prot_aes, line 156
	return parseData // library marker tpLink.tpl_prot_aes, line 157
} // library marker tpLink.tpl_prot_aes, line 158

def getRsaKey() { // library marker tpLink.tpl_prot_aes, line 160
	return [public: "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDGr/mHBK8aqx7UAS+g+TuAvE3J2DdwsqRn9MmAkjPGNon1ZlwM6nLQHfJHebdohyVqkNWaCECGXnftnlC8CM2c/RujvCrStRA0lVD+jixO9QJ9PcYTa07Z1FuEze7Q5OIa6pEoPxomrjxzVlUWLDXt901qCdn3/zRZpBdpXzVZtQIDAQAB", // library marker tpLink.tpl_prot_aes, line 161
			private: "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAMav+YcErxqrHtQBL6D5O4C8TcnYN3CypGf0yYCSM8Y2ifVmXAzqctAd8kd5t2iHJWqQ1ZoIQIZed+2eULwIzZz9G6O8KtK1EDSVUP6OLE71An09xhNrTtnUW4TN7tDk4hrqkSg/GiauPHNWVRYsNe33TWoJ2ff/NFmkF2lfNVm1AgMBAAECgYEAocxCHmKBGe2KAEkq+SKdAxvVGO77TsobOhDMWug0Q1C8jduaUGZHsxT/7JbA9d1AagSh/XqE2Sdq8FUBF+7vSFzozBHyGkrX1iKURpQFEQM2j9JgUCucEavnxvCqDYpscyNRAgqz9jdh+BjEMcKAG7o68bOw41ZC+JyYR41xSe0CQQD1os71NcZiMVqYcBud6fTYFHZz3HBNcbzOk+RpIHyi8aF3zIqPKIAh2pO4s7vJgrMZTc2wkIe0ZnUrm0oaC//jAkEAzxIPW1mWd3+KE3gpgyX0cFkZsDmlIbWojUIbyz8NgeUglr+BczARG4ITrTV4fxkGwNI4EZxBT8vXDSIXJ8NDhwJBAIiKndx0rfg7Uw7VkqRvPqk2hrnU2aBTDw8N6rP9WQsCoi0DyCnX65Hl/KN5VXOocYIpW6NAVA8VvSAmTES6Ut0CQQCX20jD13mPfUsHaDIZafZPhiheoofFpvFLVtYHQeBoCF7T7vHCRdfl8oj3l6UcoH/hXMmdsJf9KyI1EXElyf91AkAvLfmAS2UvUnhX4qyFioitjxwWawSnf+CewN8LDbH7m5JVXJEh3hqp+aLHg1EaW4wJtkoKLCF+DeVIgbSvOLJw"] // library marker tpLink.tpl_prot_aes, line 162
} // library marker tpLink.tpl_prot_aes, line 163

//	=====	Encryption ====== // library marker tpLink.tpl_prot_aes, line 165
def aesEncrypt(request, encKey, encIv) { // library marker tpLink.tpl_prot_aes, line 166
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker tpLink.tpl_prot_aes, line 167
	SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker tpLink.tpl_prot_aes, line 168
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker tpLink.tpl_prot_aes, line 169
	cipher.init(Cipher.ENCRYPT_MODE, key, iv) // library marker tpLink.tpl_prot_aes, line 170
	String result = cipher.doFinal(request.getBytes("UTF-8")).encodeBase64().toString() // library marker tpLink.tpl_prot_aes, line 171
	return result.replace("\r\n","") // library marker tpLink.tpl_prot_aes, line 172
} // library marker tpLink.tpl_prot_aes, line 173

def aesDecrypt(cipherResponse, encKey, encIv) { // library marker tpLink.tpl_prot_aes, line 175
    byte[] decodedBytes = cipherResponse.decodeBase64() // library marker tpLink.tpl_prot_aes, line 176
	def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // library marker tpLink.tpl_prot_aes, line 177
    SecretKeySpec key = new SecretKeySpec(encKey, "AES") // library marker tpLink.tpl_prot_aes, line 178
	IvParameterSpec iv = new IvParameterSpec(encIv) // library marker tpLink.tpl_prot_aes, line 179
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // library marker tpLink.tpl_prot_aes, line 180
	return new String(cipher.doFinal(decodedBytes), "UTF-8") // library marker tpLink.tpl_prot_aes, line 181
} // library marker tpLink.tpl_prot_aes, line 182

// ~~~~~ end include (1080) tpLink.tpl_prot_aes ~~~~~

// ~~~~~ start include (1082) tpLink.tpl_prot_xor_udp ~~~~~
library ( // library marker tpLink.tpl_prot_xor_udp, line 1
	name: "tpl_prot_xor_udp", // library marker tpLink.tpl_prot_xor_udp, line 2
	namespace: "tpLink", // library marker tpLink.tpl_prot_xor_udp, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_prot_xor_udp, line 4
	description: "Xor protocol plus UDP support. Hub V 2.5.3", // library marker tpLink.tpl_prot_xor_udp, line 5
	category: "utilities", // library marker tpLink.tpl_prot_xor_udp, line 6
	documentationLink: "" // library marker tpLink.tpl_prot_xor_udp, line 7
) // library marker tpLink.tpl_prot_xor_udp, line 8

//	===== UDP ===== // library marker tpLink.tpl_prot_xor_udp, line 10
def sendUdp(command, action, baseUrl, timeout = 5) { // library marker tpLink.tpl_prot_xor_udp, line 11
	def myHubAction = new hubitat.device.HubAction( // library marker tpLink.tpl_prot_xor_udp, line 12
		command, // library marker tpLink.tpl_prot_xor_udp, line 13
		hubitat.device.Protocol.LAN, [ // library marker tpLink.tpl_prot_xor_udp, line 14
			type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, // library marker tpLink.tpl_prot_xor_udp, line 15
			destinationAddress: baseUrl, // library marker tpLink.tpl_prot_xor_udp, line 16
			encoding: hubitat.device.HubAction.Encoding.HEX_STRING, // library marker tpLink.tpl_prot_xor_udp, line 17
			ignoreResponse: false, // library marker tpLink.tpl_prot_xor_udp, line 18
			parseWarning: true, // library marker tpLink.tpl_prot_xor_udp, line 19
			timeout: timeout, // library marker tpLink.tpl_prot_xor_udp, line 20
			callback: action]) // library marker tpLink.tpl_prot_xor_udp, line 21
	try { // library marker tpLink.tpl_prot_xor_udp, line 22
		sendHubCommand(myHubAction) // library marker tpLink.tpl_prot_xor_udp, line 23
	} catch (error) { // library marker tpLink.tpl_prot_xor_udp, line 24
		log.warn "sendLanCmd: command to ${baseUrl} failed. Error = ${error}" // library marker tpLink.tpl_prot_xor_udp, line 25
		unschedule("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 26
	} // library marker tpLink.tpl_prot_xor_udp, line 27
} // library marker tpLink.tpl_prot_xor_udp, line 28

/*	UDP Timeout is required since the parameter parseWarning does not // library marker tpLink.tpl_prot_xor_udp, line 30
	currently work for single polls. */ // library marker tpLink.tpl_prot_xor_udp, line 31
def udpTimeout(String action = "parseXorResp") { // library marker tpLink.tpl_prot_xor_udp, line 32
    switch (action) { // library marker tpLink.tpl_prot_xor_udp, line 33
        case "getXorLanData": // library marker tpLink.tpl_prot_xor_udp, line 34
            getXorLanData("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 35
            break // library marker tpLink.tpl_prot_xor_udp, line 36

        case "quickFindXorData": // library marker tpLink.tpl_prot_xor_udp, line 38
            quickFindXorData("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 39
            break // library marker tpLink.tpl_prot_xor_udp, line 40

        case "getSecureLanData": // library marker tpLink.tpl_prot_xor_udp, line 42
            getSecureLanData("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 43
            break // library marker tpLink.tpl_prot_xor_udp, line 44

        case "quickFindSecureData": // library marker tpLink.tpl_prot_xor_udp, line 46
            quickFindSecureData("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 47
            break // library marker tpLink.tpl_prot_xor_udp, line 48

        case "parseXorResp": // library marker tpLink.tpl_prot_xor_udp, line 50
            parseIotXorResp("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 51
            break // library marker tpLink.tpl_prot_xor_udp, line 52

        case "parseIotXorChildResp": // library marker tpLink.tpl_prot_xor_udp, line 54
            parseIotXorChildResp("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 55
            break // library marker tpLink.tpl_prot_xor_udp, line 56

        default: // library marker tpLink.tpl_prot_xor_udp, line 58
            logDebug("udpTimeout: [unsupportedAction: ${action}]") // library marker tpLink.tpl_prot_xor_udp, line 59
            atomicState.findingXor = false // library marker tpLink.tpl_prot_xor_udp, line 60
            atomicState.findingSecure = false // library marker tpLink.tpl_prot_xor_udp, line 61
            atomicState.deviceWait = false // library marker tpLink.tpl_prot_xor_udp, line 62
    } // library marker tpLink.tpl_prot_xor_udp, line 63
} // library marker tpLink.tpl_prot_xor_udp, line 64

def parseUdp(message, type) { // library marker tpLink.tpl_prot_xor_udp, line 66
	if (message.toString().contains("description")) { message = message.description } // library marker tpLink.tpl_prot_xor_udp, line 67
	def resp = parseLanMessage(message) // library marker tpLink.tpl_prot_xor_udp, line 68
	if (resp.type == "LAN_TYPE_UDPCLIENT") { // library marker tpLink.tpl_prot_xor_udp, line 69
		unschedule("udpTimeout") // library marker tpLink.tpl_prot_xor_udp, line 70
		if (device) { // library marker tpLink.tpl_prot_xor_udp, line 71
			setCommsError("OK") // library marker tpLink.tpl_prot_xor_udp, line 72
		} // library marker tpLink.tpl_prot_xor_udp, line 73
		String ip = convertHexToIP(resp.ip) // library marker tpLink.tpl_prot_xor_udp, line 74
		String port = convertHexToInt(resp.port) // library marker tpLink.tpl_prot_xor_udp, line 75
		String payload = "" // library marker tpLink.tpl_prot_xor_udp, line 76
		if (type == "xor") { // library marker tpLink.tpl_prot_xor_udp, line 77
			payload = inputXOR(resp.payload) // library marker tpLink.tpl_prot_xor_udp, line 78
			if (payload.length() >= 1023) {  // library marker tpLink.tpl_prot_xor_udp, line 79
				payload = udpStringCorrect(payload)  // library marker tpLink.tpl_prot_xor_udp, line 80
			} // library marker tpLink.tpl_prot_xor_udp, line 81
		} else { // library marker tpLink.tpl_prot_xor_udp, line 82
			byte[] payloadByte = hubitat.helper.HexUtils.hexStringToByteArray(resp.payload.drop(32)) // library marker tpLink.tpl_prot_xor_udp, line 83
			payload = new String(payloadByte) // library marker tpLink.tpl_prot_xor_udp, line 84
			if (payload.length() >= 1007) { // library marker tpLink.tpl_prot_xor_udp, line 85
				payload = udpStringCorrect(payload) // library marker tpLink.tpl_prot_xor_udp, line 86
			} // library marker tpLink.tpl_prot_xor_udp, line 87
		} // library marker tpLink.tpl_prot_xor_udp, line 88
		try { // library marker tpLink.tpl_prot_xor_udp, line 89
			Map cmdResp = new JsonSlurper().parseText(payload) // library marker tpLink.tpl_prot_xor_udp, line 90
			return [cmdResp: cmdResp, ip: ip, port:port, status: "ok"] // library marker tpLink.tpl_prot_xor_udp, line 91
		} catch (err) { // library marker tpLink.tpl_prot_xor_udp, line 92
			logWarn("parseUdp: ${err}") // library marker tpLink.tpl_prot_xor_udp, line 93
			return [status: "JsonSlurper parse error"] // library marker tpLink.tpl_prot_xor_udp, line 94
		} // library marker tpLink.tpl_prot_xor_udp, line 95
	} else { // library marker tpLink.tpl_prot_xor_udp, line 96
		logDebug("parseUdp: [error: not LAN_TYPE_UDPCLIENT, respType: ${resp.type}]") // library marker tpLink.tpl_prot_xor_udp, line 97
		return [status: "Not LAN_TYPE_UDPCLIENT"] // library marker tpLink.tpl_prot_xor_udp, line 98
	} // library marker tpLink.tpl_prot_xor_udp, line 99
} // library marker tpLink.tpl_prot_xor_udp, line 100

/*	Hubitat truncates the UDP return messages at the first response, ignoring // library marker tpLink.tpl_prot_xor_udp, line 102
	any additional responses. This attempts to recover the packet so that it // library marker tpLink.tpl_prot_xor_udp, line 103
	can be read/parsed by the system. Critical for some IOT devices. */ // library marker tpLink.tpl_prot_xor_udp, line 104
def udpStringCorrect(payloadString) { // library marker tpLink.tpl_prot_xor_udp, line 105
	if (payloadString.count("[") == payloadString.count("]")) { // library marker tpLink.tpl_prot_xor_udp, line 106
		payloadString = payloadString.substring(0, payloadString.lastIndexOf("]") + 1) // library marker tpLink.tpl_prot_xor_udp, line 107
	} else { // library marker tpLink.tpl_prot_xor_udp, line 108
		payloadString = payloadString.substring(0, payloadString.lastIndexOf("[")) // library marker tpLink.tpl_prot_xor_udp, line 109
		payloadString = payloadString.substring(0, payloadString.lastIndexOf(",")) // library marker tpLink.tpl_prot_xor_udp, line 110
	} // library marker tpLink.tpl_prot_xor_udp, line 111
	def addString = "}" * (1 + payloadString.count("{") - payloadString.count("}")) // library marker tpLink.tpl_prot_xor_udp, line 112
	payloadString += addString // library marker tpLink.tpl_prot_xor_udp, line 113
	return payloadString // library marker tpLink.tpl_prot_xor_udp, line 114
} // library marker tpLink.tpl_prot_xor_udp, line 115

private String outputXOR(String command) { // library marker tpLink.tpl_prot_xor_udp, line 117
    int key = 0xAB // library marker tpLink.tpl_prot_xor_udp, line 118
    StringBuilder encrypted = new StringBuilder() // library marker tpLink.tpl_prot_xor_udp, line 119
    command.bytes.each { byte b -> // library marker tpLink.tpl_prot_xor_udp, line 120
        int value = (b & 0xFF) ^ key // library marker tpLink.tpl_prot_xor_udp, line 121
        key = value // library marker tpLink.tpl_prot_xor_udp, line 122
        encrypted.append(String.format('%02x', value)) // library marker tpLink.tpl_prot_xor_udp, line 123
    } // library marker tpLink.tpl_prot_xor_udp, line 124
	return encrypted.toString() // library marker tpLink.tpl_prot_xor_udp, line 125
} // library marker tpLink.tpl_prot_xor_udp, line 126

private String inputXOR(String encryptedResponse) { // library marker tpLink.tpl_prot_xor_udp, line 128
    int key = 0xAB // library marker tpLink.tpl_prot_xor_udp, line 129
    StringBuilder decrypted = new StringBuilder() // library marker tpLink.tpl_prot_xor_udp, line 130

    encryptedResponse.findAll(/../).each { hexByte -> // library marker tpLink.tpl_prot_xor_udp, line 132
        int nextKey = Integer.parseInt(hexByte, 16) // library marker tpLink.tpl_prot_xor_udp, line 133
        int value = nextKey ^ key // library marker tpLink.tpl_prot_xor_udp, line 134
        key = nextKey // library marker tpLink.tpl_prot_xor_udp, line 135
        decrypted.append((char) value) // library marker tpLink.tpl_prot_xor_udp, line 136
    } // library marker tpLink.tpl_prot_xor_udp, line 137

    return decrypted.toString() // library marker tpLink.tpl_prot_xor_udp, line 139
} // library marker tpLink.tpl_prot_xor_udp, line 140

// ~~~~~ end include (1082) tpLink.tpl_prot_xor_udp ~~~~~

// ~~~~~ start include (1084) tpLink.tpl_id_logs ~~~~~
library ( // library marker tpLink.tpl_id_logs, line 1
	name: "tpl_id_logs", // library marker tpLink.tpl_id_logs, line 2
	namespace: "tpLink", // library marker tpLink.tpl_id_logs, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_id_logs, line 4
	description: "Common identifiers and logging", // library marker tpLink.tpl_id_logs, line 5
	category: "utilities", // library marker tpLink.tpl_id_logs, line 6
	documentationLink: "" // library marker tpLink.tpl_id_logs, line 7
) // library marker tpLink.tpl_id_logs, line 8
//String nameSpace() { return "davegut" } // library marker tpLink.tpl_id_logs, line 9

//String version() { return "2.5.3" } // library marker tpLink.tpl_id_logs, line 11

/*	For XOR, the discovery is simply a device status command. */ // library marker tpLink.tpl_id_logs, line 13
String xorDiscCmd() { // library marker tpLink.tpl_id_logs, line 14
    return outputXOR('{"system":{"get_sysinfo":{}}}') // library marker tpLink.tpl_id_logs, line 15
} // library marker tpLink.tpl_id_logs, line 16

/*	For Secure (KLAP/AES) devices, the below Hex string is always used. */ // library marker tpLink.tpl_id_logs, line 18
String secureDiscCmd() { // library marker tpLink.tpl_id_logs, line 19
    return '0200000101e51100095c11706d6f58577b22706172616d73223a7b227273615f6b6579223a222d2d2d2d2d424547494e205055424c4943204b45592d2d2d2d2d5c6e4d494942496a414e42676b71686b6947397730424151454641414f43415138414d49494243674b43415145416d684655445279687367797073467936576c4d385c6e54646154397a61586133586a3042712f4d6f484971696d586e2b736b4e48584d525a6550564134627532416257386d79744a5033445073665173795679536e355c6e6f425841674d303149674d4f46736350316258367679784d523871614b33746e466361665a4653684d79536e31752f564f2f47474f795436507459716f384e315c6e44714d77373563334b5a4952387a4c71516f744657747239543337536e50754a7051555a7055376679574b676377716e7338785a657a78734e6a6465534171765c6e3167574e75436a5356686d437931564d49514942576d616a37414c47544971596a5442376d645348562f2b614a32564467424c6d7770344c7131664c4f6a466f5c6e33737241683144744a6b537376376a624f584d51695666453873764b6877586177717661546b5658382f7a4f44592b2f64684f5374694a4e6c466556636c35585c6e4a514944415141425c6e2d2d2d2d2d454e44205055424c4943204b45592d2d2d2d2d5c6e227d7d' // library marker tpLink.tpl_id_logs, line 20
} // library marker tpLink.tpl_id_logs, line 21

//	===== Logging ===== // library marker tpLink.tpl_id_logs, line 23
void logInfo(msg) {  if (infoLog) { log.info msg } } // library marker tpLink.tpl_id_logs, line 24

void debugLogOff() { // library marker tpLink.tpl_id_logs, line 26
    if (device) { // library marker tpLink.tpl_id_logs, line 27
        device.updateSetting("logEnable", [type: "bool", value: false]) // library marker tpLink.tpl_id_logs, line 28
    } else if (app) { // library marker tpLink.tpl_id_logs, line 29
        app.updateSetting("logEnable", false) // library marker tpLink.tpl_id_logs, line 30
    } // library marker tpLink.tpl_id_logs, line 31
    logInfo("Debug logging disabled") // library marker tpLink.tpl_id_logs, line 32
} // library marker tpLink.tpl_id_logs, line 33

def logDebug(msg) { if (logEnable) { log.debug msg } } // library marker tpLink.tpl_id_logs, line 35

void logWarn(String msg) { log.warn msg } // library marker tpLink.tpl_id_logs, line 37

// ~~~~~ end include (1084) tpLink.tpl_id_logs ~~~~~

// ~~~~~ start include (1083) tpLink.tpl_encode ~~~~~
library ( // library marker tpLink.tpl_encode, line 1
	name: "tpl_encode", // library marker tpLink.tpl_encode, line 2
	namespace: "tpLink", // library marker tpLink.tpl_encode, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_encode, line 4
	description: "Common data encoding / decoding methods. Hub V 2.5.3", // library marker tpLink.tpl_encode, line 5
	category: "utilities", // library marker tpLink.tpl_encode, line 6
	documentationLink: "" // library marker tpLink.tpl_encode, line 7
) // library marker tpLink.tpl_encode, line 8

byte[] getSeed(Integer size) { // library marker tpLink.tpl_encode, line 10
    byte[] seed = new byte[size] // library marker tpLink.tpl_encode, line 11
    new Random().nextBytes(seed) // library marker tpLink.tpl_encode, line 12
    return seed // library marker tpLink.tpl_encode, line 13
} // library marker tpLink.tpl_encode, line 14

byte[] mdEncode(String hashMethod, byte[] data) { // library marker tpLink.tpl_encode, line 16
    MessageDigest digest = MessageDigest.getInstance(hashMethod) // library marker tpLink.tpl_encode, line 17
    digest.update(data) // library marker tpLink.tpl_encode, line 18
    return digest.digest() // library marker tpLink.tpl_encode, line 19
} // library marker tpLink.tpl_encode, line 20

String encodeUtf8(String message) { // library marker tpLink.tpl_encode, line 22
    return new String(message.getBytes("UTF-8"), "UTF-8") // library marker tpLink.tpl_encode, line 23
} // library marker tpLink.tpl_encode, line 24

Integer byteArrayToInteger(byte[] bytes) { // library marker tpLink.tpl_encode, line 26
    try { // library marker tpLink.tpl_encode, line 27
        return ((bytes[0] & 0xFF) << 24) | // library marker tpLink.tpl_encode, line 28
               ((bytes[1] & 0xFF) << 16) | // library marker tpLink.tpl_encode, line 29
               ((bytes[2] & 0xFF) << 8)  | // library marker tpLink.tpl_encode, line 30
               (bytes[3] & 0xFF) // library marker tpLink.tpl_encode, line 31
    } // library marker tpLink.tpl_encode, line 32
    catch (Exception e) { // library marker tpLink.tpl_encode, line 33
        log.warn "byteArrayToInteger: [byteArr: ${bytes}, ERROR: ${e}]" // library marker tpLink.tpl_encode, line 34
        return null // library marker tpLink.tpl_encode, line 35
    } // library marker tpLink.tpl_encode, line 36
} // library marker tpLink.tpl_encode, line 37

byte[] integerToByteArray(Integer value) { // library marker tpLink.tpl_encode, line 39
    String hexValue = hubitat.helper.HexUtils.integerToHexString(value, 4) // library marker tpLink.tpl_encode, line 40
    return hubitat.helper.HexUtils.hexStringToByteArray(hexValue) // library marker tpLink.tpl_encode, line 41
} // library marker tpLink.tpl_encode, line 42

private String convertHexToIP(String hex) { // library marker tpLink.tpl_encode, line 44
    return [ // library marker tpLink.tpl_encode, line 45
        convertHexToInt(hex[0..1]), // library marker tpLink.tpl_encode, line 46
        convertHexToInt(hex[2..3]), // library marker tpLink.tpl_encode, line 47
        convertHexToInt(hex[4..5]), // library marker tpLink.tpl_encode, line 48
        convertHexToInt(hex[6..7]) // library marker tpLink.tpl_encode, line 49
    ].join(".") // library marker tpLink.tpl_encode, line 50
} // library marker tpLink.tpl_encode, line 51

private Integer convertHexToInt(String hex) { // library marker tpLink.tpl_encode, line 53
    return Integer.parseInt(hex, 16) // library marker tpLink.tpl_encode, line 54
} // library marker tpLink.tpl_encode, line 55

// ~~~~~ end include (1083) tpLink.tpl_encode ~~~~~

// ~~~~~ start include (1077) tpLink.tpl_error ~~~~~
library ( // library marker tpLink.tpl_error, line 1
	name: "tpl_error", // library marker tpLink.tpl_error, line 2
	namespace: "tpLink", // library marker tpLink.tpl_error, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_error, line 4
	description: "Error processing. Hub V 2.5.3", // library marker tpLink.tpl_error, line 5
	category: "utilities", // library marker tpLink.tpl_error, line 6
	documentationLink: "" // library marker tpLink.tpl_error, line 7
) // library marker tpLink.tpl_error, line 8

def handleCommsError(status) { // library marker tpLink.tpl_error, line 10
	if (status == "OK" || status == 200) { // library marker tpLink.tpl_error, line 11
		setCommsError("OK") // library marker tpLink.tpl_error, line 12
	} else { // library marker tpLink.tpl_error, line 13
		errorCount = state.errorCount + 1 // library marker tpLink.tpl_error, line 14
		state.errorCount = errorCount // library marker tpLink.tpl_error, line 15
		Map logData = [method: "handleCommsError", status: status, errorCount: errorCount] // library marker tpLink.tpl_error, line 16
		switch (errorCount) { // library marker tpLink.tpl_error, line 17
			case 1: // library marker tpLink.tpl_error, line 18
			case 2: // library marker tpLink.tpl_error, line 19
				sendRetry() // library marker tpLink.tpl_error, line 20
				break // library marker tpLink.tpl_error, line 21
			case 3: // library marker tpLink.tpl_error, line 22
				configure() // library marker tpLink.tpl_error, line 23
				runIn(10, sendRetry) // library marker tpLink.tpl_error, line 24
				break // library marker tpLink.tpl_error, line 25
			case 4: // library marker tpLink.tpl_error, line 26
				sendRetry() // library marker tpLink.tpl_error, line 27
				break // library marker tpLink.tpl_error, line 28
			default: // library marker tpLink.tpl_error, line 29
				logData << [action: "SetCommsErrorTrue"] // library marker tpLink.tpl_error, line 30
				setCommsError(status, state.errorCount) // library marker tpLink.tpl_error, line 31
				break // library marker tpLink.tpl_error, line 32
		} // library marker tpLink.tpl_error, line 33
		logDebug(logData) // library marker tpLink.tpl_error, line 34
	} // library marker tpLink.tpl_error, line 35
} // library marker tpLink.tpl_error, line 36

void sendRetry(Map cmdData = state.cmdData) { // library marker tpLink.tpl_error, line 38
	Map cmdBody = cmdData.cmdBody // library marker tpLink.tpl_error, line 39
	String action = cmdData.action // library marker tpLink.tpl_error, line 40
	String retryMeth = cmdData.retryMeth // library marker tpLink.tpl_error, line 41
	"${retryMeth}"(cmdBody, true, action) // library marker tpLink.tpl_error, line 42
} // library marker tpLink.tpl_error, line 43

def setCommsError(status, count = 0) { // library marker tpLink.tpl_error, line 45
	if (status == "OK") { // library marker tpLink.tpl_error, line 46
		state.errorCount = 0 // library marker tpLink.tpl_error, line 47
		if (device.currentValue("commsError") == "true") { // library marker tpLink.tpl_error, line 48
			Map logData = [method: "setCommsError", status: status, count: count] // library marker tpLink.tpl_error, line 49
			sendEvent(name: "commsError", value: "false") // library marker tpLink.tpl_error, line 50
			logData << setPollInterval(systemPoll) // library marker tpLink.tpl_error, line 51
			state.cmdData = null // library marker tpLink.tpl_error, line 52
			unschedule("errorConfigure") // library marker tpLink.tpl_error, line 53
			logDebug(logData) // library marker tpLink.tpl_error, line 54
		} // library marker tpLink.tpl_error, line 55
	} else { // library marker tpLink.tpl_error, line 56
		Map logData = [method: "setCommsError", status: status, count: count] // library marker tpLink.tpl_error, line 57
		if (device.currentValue("commsError") == "false") { // library marker tpLink.tpl_error, line 58
			sendEvent(name: "commsError", value: "true") // library marker tpLink.tpl_error, line 59
			logData << setPollInterval("15 min") // library marker tpLink.tpl_error, line 60
			runEvery1Hour(errorConfigure) // library marker tpLink.tpl_error, line 61
			logData << [quickPolling: "off", errorConfigure: "ever hour"] // library marker tpLink.tpl_error, line 62
			if (status == 403) { // library marker tpLink.tpl_error, line 63
				logData << [loginError: loginErrorAction()] // library marker tpLink.tpl_error, line 64
			} else { // library marker tpLink.tpl_error, line 65
				logData << [lanError: lanErrorAction()] // library marker tpLink.tpl_error, line 66
			} // library marker tpLink.tpl_error, line 67
			log.warn logData // library marker tpLink.tpl_error, line 68
		} else { // library marker tpLink.tpl_error, line 69
			logData << [error: "Unspecified Error"] // library marker tpLink.tpl_error, line 70
			logDebug(logData) // library marker tpLink.tpl_error, line 71
		} // library marker tpLink.tpl_error, line 72
	} // library marker tpLink.tpl_error, line 73
} // library marker tpLink.tpl_error, line 74

def errorConfigure() { // library marker tpLink.tpl_error, line 76
	logDebug([method: "errorConfigure"]) // library marker tpLink.tpl_error, line 77
	if (device.currentValue("commsError") == "true") { // library marker tpLink.tpl_error, line 78
		configure() // library marker tpLink.tpl_error, line 79
	} else { // library marker tpLink.tpl_error, line 80
		unschedule("errorConfigure") // library marker tpLink.tpl_error, line 81
	} // library marker tpLink.tpl_error, line 82
} // library marker tpLink.tpl_error, line 83

def lanErrorAction() { // library marker tpLink.tpl_error, line 85
	def action = "Likely cause of this error is YOUR LAN device configuration: " // library marker tpLink.tpl_error, line 86
	action += "a. VERIFY your device is on the DHCP list in your router, " // library marker tpLink.tpl_error, line 87
	action += "b. VERIFY your device is in the active device list in your router, and " // library marker tpLink.tpl_error, line 88
	action += "c. TRY controlling your device from the TAPO phone app." // library marker tpLink.tpl_error, line 89
	return action // library marker tpLink.tpl_error, line 90
} // library marker tpLink.tpl_error, line 91

def loginErrorAction() { // library marker tpLink.tpl_error, line 93
	def action = "Likely cause is your login credentials are incorrect or the login has expired. " // library marker tpLink.tpl_error, line 94
	action += "a. RUN command Configure. b. If error persists, check your credentials in the App" // library marker tpLink.tpl_error, line 95
	return action // library marker tpLink.tpl_error, line 96
} // library marker tpLink.tpl_error, line 97

// ~~~~~ end include (1077) tpLink.tpl_error ~~~~~
