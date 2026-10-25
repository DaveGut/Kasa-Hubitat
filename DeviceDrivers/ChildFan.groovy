/*	Kasa/Tapo Integration Driver: TpLink Child Fan
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
=====  Driver Notes  =====
1.	Only fan in Kasa - Tapo inventory is the KS240 Child device (using Smart API);
	therefore, this driver only supports that device.
*/ 

String nameSpace() { return "davegut" }
String version() { return "2.5.3" }


import groovy.json.JsonBuilder
import groovy.transform.Field
@Field static final String DEV_TYPE = "Child Fan"
@Field static final Boolean IS_CHILD = true
@Field static final String SYSTEM = "system"
metadata {
	definition (name: "Kasa Child Fan", namespace: nameSpace(), author: "Dave Gutheinz", 
				importUrl: "")
	{
		capability "FanControl"
		attribute "level", "NUMBER"
	}
	preferences {
		emPrefs()
		commonPreferences()
	}
}

def installed() {
	Map logData = [method: "installed"]
	List supportedFanSpeeds = ["low","medium-low","medium", "high"]
	def fanSpeedsAttr = new groovy.json.JsonBuilder(supportedFanSpeeds)
	sendEvent(name: "supportedFanSpeeds", value: fanSpeedsAttr.toString())
	logData << [supportedFanSpeeds: supportedFanSpeeds]
	logData << commonInstalled()
	logInfo(logData)
}

def updated() {
	Map logData = [method: "updated", commonUpdated: commonUpdated()]
	if (getDataValue("isEm")) { logData << [emUpdated: emUpdated()] }
	logInfo(logData)
	runIn(2, refresh)
}

def setSpeed(fanspeed) {
	Map logData = [method: "setSpeed", fanspeed: fanspeed]
	int level
	switch(fanspeed) {
		case "low": level = 1; break
		case "medium-low": level = 2; break
		case "medium": level = 3; break
		case "high": level = 4; break
		case "on": level = 9; break
		case "off": level = 0; break
		case "auto": break
		default: 
			logData << [ALERT: "UNKNOWN SetSpeed ERROR"]
	}
	logData << [level: level]
	if (level == 0) { 
		setOnOff(false)
	} else if (level == 9) { 
		setOnOff(true) 
	} else {
		List requests = [[method: "set_device_info", params: [device_on: true, fan_speed_level: level]],
						 [method: "get_device_info"]]
		Map cmdBody = [method: "multipleRequest", params: [requests: requests]]
		if (IS_CHILD) {
			Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"), 
							   dni: device.deviceNetworkId, api: "smart"]
			parent.processChildCmd(commandData)
		} else {
			sendSmartCmd(cmdBody, true)
		}
	}
	logDebug(logData)
}

def cycleSpeed() {
	List cycles = ["low","medium-low","medium","high"]
	def nextIndex = cycles.indexOf(device.currentValue("speed")) + 1
	if (nextIndex > 3) { nextIndex = 0 }
	setSpeed(cycles[nextIndex])
}

def parse_get_device_info(devData) {
	def onOff = "off"
	if (devData.device_on == true) { onOff = "on" }
	def fanSpeed = devData.fan_speed_level
	def speed
	switch(fanSpeed) {
		case 0: speed = "off"; break
		case 1: speed = "low"; break
		case 2: speed = "medium-low"; break
		case 3: speed = "medium"; break
		case 4: speed = "high"; break
		default: speed = "parseError"
	}
	sendEvent(name: "speed", value: speed)
	sendEvent(name: "switch", value: onOff, type: state.eventType)
	logDebug("setDevStatus: [switch: ${onOff}, speed: ${speed}]")
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
