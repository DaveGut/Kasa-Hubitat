/*	Kasa/Tapo Integration Driver: TpLink Child Dimmer
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
Notes: 
1.	There are zero IOT api child dimmers. So this driver does not support IOT api child dimmers.
*/ 

String nameSpace() { return "davegut" }
String version() { return "2.5.3" }

import groovy.transform.Field
@Field static final String DEV_TYPE = "Child Dimming Switch"
@Field static final Boolean IS_CHILD = true
@Field static final String LIGHT_TRANS_TYPE = "transition_period"
@Field static final String SYSTEM = "system"
metadata {
	definition (name: "Kasa Child Dimming Switch", namespace: nameSpace(), author: "Dave Gutheinz", 
				importUrl: "")
	{
		capability "Light"
	}
	preferences {
		emPrefs()
		commonPreferences()
		levelPrefs()
	}
}

def installed() {
	Map logData = [method: "installed"]
	logData << commonInstalled()
	logInfo(logData)
	runIn(1, updated)
}

def updated() {
	Map logData = [method: "updated"]
	unschedule()
	logData << commonUpdated()
	logData << levelUpdated()
	if (getDataValue("isEm")) { logData << [emUpdated: emUpdated()] }
	logInfo(logData)
	runIn(2, refresh)
}

def setDevStatus(onOff, level) {
	sendEvent(name: "switch", value: onOff, type: state.eventType)
	sendEvent(name: "level", value: level, type: state.eventType)
	logDebug("setDevStatus: [switch: ${onOff}, level: ${level}]")
}

//	===== Smart Methods =====
def parse_get_device_info(devData) {
	def onOff = "off"
	if (devData.device_on == true) { onOff = "on" }
	int level = devData.brightness
	setDevStatus(onOff, level)
	if (emEnable) {
		def delay = getDataValue("deviceId")[-1].toInteger()
		runIn(2*delay, smartPowerRefresh)
	}
}







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

// ~~~~~ start include (1070) tpLink.tpl_child_common ~~~~~
library ( // library marker tpLink.tpl_child_common, line 1
	name: "tpl_child_common", // library marker tpLink.tpl_child_common, line 2
	namespace: "tpLink", // library marker tpLink.tpl_child_common, line 3
	author: "Compiled by Dave Gutheinz", // library marker tpLink.tpl_child_common, line 4
	description: "Common child methods library for Kasa/Tapo and Tapo SMART protocol drivers. Hub V 2.5.3", // library marker tpLink.tpl_child_common, line 5
	category: "utilities", // library marker tpLink.tpl_child_common, line 6
	documentationLink: "" // library marker tpLink.tpl_child_common, line 7
) // library marker tpLink.tpl_child_common, line 8

capability "Refresh" // library marker tpLink.tpl_child_common, line 10

def commonPreferences() { // library marker tpLink.tpl_child_common, line 12
	input ("logEnable", "bool",  title: "Enable debug logging for 30 minutes", defaultValue: false) // library marker tpLink.tpl_child_common, line 13
	input ("infoLog", "bool", title: "Enable information logging",defaultValue: true) // library marker tpLink.tpl_child_common, line 14
} // library marker tpLink.tpl_child_common, line 15

def commonInstalled() { // library marker tpLink.tpl_child_common, line 17
	return [eventType: "digital"] // library marker tpLink.tpl_child_common, line 18
} // library marker tpLink.tpl_child_common, line 19

def commonUpdated() { // library marker tpLink.tpl_child_common, line 21
	Map logData = [infoLog: infoLog, debugLog: logEnable] // library marker tpLink.tpl_child_common, line 22
	if (logEnable == true) { runIn(1800, debugLogOff) } // library marker tpLink.tpl_child_common, line 23
	return logData // library marker tpLink.tpl_child_common, line 24
} // library marker tpLink.tpl_child_common, line 25

def refresh() { // library marker tpLink.tpl_child_common, line 27
	state.eventType = "digital" // library marker tpLink.tpl_child_common, line 28
	Map commandData = [retry: true, devId: getDataValue("deviceId"), dni: device.deviceNetworkId] // library marker tpLink.tpl_child_common, line 29
	if (getDataValue("api") == "smart") { // library marker tpLink.tpl_child_common, line 30
		List requests = [[method: "get_device_info"]] // library marker tpLink.tpl_child_common, line 31
		Map cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_child_common, line 32
		commandData << [cmdBody: cmdBody, api: "smart"] // library marker tpLink.tpl_child_common, line 33
	} else { // library marker tpLink.tpl_child_common, line 34
		Map cmdBody = [system: [get_sysinfo: []]] // library marker tpLink.tpl_child_common, line 35
		commandData << [cmdBody: cmdBody, api: "iot"] // library marker tpLink.tpl_child_common, line 36
	} // library marker tpLink.tpl_child_common, line 37
	parent.processChildCmd(commandData) // library marker tpLink.tpl_child_common, line 38
} // library marker tpLink.tpl_child_common, line 39

// ~~~~~ end include (1070) tpLink.tpl_child_common ~~~~~

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
