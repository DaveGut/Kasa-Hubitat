library (
	name: "kasaCommon",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Kasa Device Common Methods",
	category: "utilities",
	documentationLink: ""
)

def getVer() { return "" }

capability "Switch"
capability "Refresh"
capability "Actuator"
capability "Configuration"
command "setPollInterval", [[
	name: "Poll Interval in seconds",
	constraints: ["default", "5 seconds", "10 seconds", "15 seconds",
				  "30 seconds", "1 minute", "5 minutes",  "10 minutes",
				  "30 minutes"],
	type: "ENUM"]]
attribute "commsError", "string"

def commonPrefs() {
	input ("infoLog", "bool", title: "Enable descriptionText logging", 
		   defaultValue: true)
	input ("logEnable", "bool", title: "Enable debug logging", defaultValue: false)
	input ("nameSync", "enum", title: "Synchronize Names", defaultValue: "none",
		   options: ["none": "Don't synchronize",
					 "device" : "Kasa device name master",
					 "Hubitat" : "Hubitat label master"])
	input ("manualIp", "string", title: "Manual IP Update <b>[Caution]</b>",
		   defaultValue: getDataValue("deviceIP"))
	input ("manualPort", "string", title: "Manual Port Update <b>[Caution]</b>",
		   defaultValue: getDataValue("devicePort"))
	input ("rebootDev", "bool", title: "Reboot device <b>[Caution]</b>",
		   defaultValue: false)
}

def installCommon() {
	sendEvent(name: "commsError", value: "false")
	state.errorCount = 0
	state.pollInterval = "30 minutes"
	runIn(5, updated)
	return [commsError: "false", pollInterval: "30 minutes", errorCount: 0]
}

def updateCommon() {
	//	Remove Kasa Cloud Access
	device.removeSetting("useCloud")
	device.removeSetting("bind")
	device.removeSetting("altLan")
	
	def updStatus = [:]
	if (rebootDev) {
		updStatus << [rebootDev: rebootDevice()]
		return updStatus
	}
	unschedule()
	if (nameSync != "none") {
		updStatus << [nameSync: syncName()]
	}
	if (logEnable) { runIn(1800, debugLogOff) }
	updStatus << [infoLog: infoLog, logEnable: logEnable]
	if (manualIp != getDataValue("deviceIP")) {
		updateDataValue("deviceIP", manualIp)
		updStatus << [ipUpdate: manualIp]
	}
	if (manualPort != getDataValue("devicePort")) {
		updateDataValue("devicePort", manualPort)
		updStatus << [portUpdate: manualPort]
	}
	state.errorCount = 0
	sendEvent(name: "commsError", value: "false")
	
	def pollInterval = state.pollInterval
	if (pollInterval == null) { pollInterval = "30 minutes" }
	state.pollInterval = pollInterval
	runIn(15, setPollInterval)
	updStatus << [pollInterval: pollInterval]
	if (emFunction) {
		scheduleEnergyAttrs()
		state.getEnergy = "This Month"
		updStatus << [emFunction: "scheduled"]
	}
	return updStatus
}

def configure() {
	Map logData = [method: "configure"]
	logInfo logData
	if (parent == null) {
		logData << [error: "No Parent App.  Aborted"]
		logWarn(logData)
	} else {
		logData << [appData: parent.updateConfigurations()]
		logInfo(logData)
	}
}

def refresh() { sendCmd("""{"system":{"get_sysinfo":{}}}""") }

def poll() { sendCmd("""{"system":{"get_sysinfo":{}}}""") }

def setPollInterval(interval = state.pollInterval) {
	if (interval == "default" || interval == "off" || interval == null) {
		interval = "30 minutes"
	} else if (useCloud || altLan || getDataValue("altComms") == "true") {
		if (interval.contains("sec")) {
			interval = "1 minute"
			logWarn("setPollInterval: Device using Cloud or rawSocket.  Poll interval reset to minimum value of 1 minute.")
		}
	}
	state.pollInterval = interval
	def pollInterval = interval.substring(0,2).toInteger()
	if (interval.contains("sec")) {
		def start = Math.round((pollInterval-1) * Math.random()).toInteger()
		schedule("${start}/${pollInterval} * * * * ?", "poll")
		logWarn("setPollInterval: Polling intervals of less than one minute " +
				"can take high resources and may impact hub performance.")
	} else {
		def start = Math.round(59 * Math.random()).toInteger()
		schedule("${start} */${pollInterval} * * * ?", "poll")
	}
	logDebug("setPollInterval: interval = ${interval}.")
	return interval
}

def rebootDevice() {
	device.updateSetting("rebootDev", [type:"bool", value: false])
	reboot()
	pauseExecution(10000)
	return "REBOOTING DEVICE"
}

def xxxxxxxxxbindUnbind() {
	def message
	if (getDataValue("deviceIP") == "CLOUD") {
		device.updateSetting("bind", [type:"bool", value: true])
		device.updateSetting("useCloud", [type:"bool", value: true])
		message = "No deviceIp.  Bind not modified."
	} else if (bind == null ||  getDataValue("feature") == "lightStrip") {
		message = "Getting current bind state"
		getBind()
	} else if (bind == true) {
		if (!parent.kasaToken || parent.userName == null || parent.userPassword == null) {
			message = "Username/pwd not set."
			getBind()
		} else {
			message = "Binding device to the Kasa Cloud."
			setBind(parent.userName, parent.userPassword)
		}
	} else if (bind == false) {
		message = "Unbinding device from the Kasa Cloud."
		setUnbind()
	}
	pauseExecution(5000)
	return message
}

def syncName() {
	def message
	if (nameSync == "Hubitat") {
		message = "Hubitat Label Sync"
		setDeviceAlias(device.getLabel())
	} else if (nameSync == "device") {
		message = "Device Alias Sync"
	} else {
		message = "Not Syncing"
	}
	device.updateSetting("nameSync",[type:"enum", value:"none"])
	return message
}

def updateName(response) {
	def name = device.getLabel()
	if (response.alias) {
		name = response.alias
		device.setLabel(name)
	} else if (response.err_code != 0) {
		def msg = "updateName: Name Sync from Hubitat to Device returned an error."
		msg+= "\n\rNote: <b>Some devices do not support syncing name from the hub.</b>\n\r"
		logWarn(msg)
		return
	}
	logInfo("updateName: Hubitat and Kasa device name synchronized to ${name}")
}

def getSysinfo() {
	sendCmd("""{"system":{"get_sysinfo":{}}}""")
}

def sysService() {
	def service = "system"
	def feature = getDataValue("feature")
	if (feature.contains("Bulb") || feature == "lightStrip") {
		service = "smartlife.iot.common.system"
	}
	return service
}

def reboot() {
	sendCmd("""{"${sysService()}":{"reboot":{"delay":1}}}""")
}

def setDeviceAlias(newAlias) {
	if (getDataValue("plugNo") != null) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
				""""system":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""")
	} else {
		sendCmd("""{"${sysService()}":{"set_dev_alias":{"alias":"${device.getLabel()}"}}}""")
	}
}

def updateAttr(attr, value) {
	if (device.currentValue(attr) != value) {
		sendEvent(name: attr, value: value)
	}
}

