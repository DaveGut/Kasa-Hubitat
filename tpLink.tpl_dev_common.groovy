library (
	name: "tpl_dev_common",
	namespace: "tpLink",
	author: "Dave Gutheinz",
	description: "kasa-tapo devices shared methods",
	category: "utilities",
	documentationLink: ""
)

capability "Refresh"
capability "Actuator"
capability "Configuration"
attribute "commsError", "string"

def commonPrefs() {
	input("systemPoll", "enum", title: "System poll interval", 
		  options: ["5 seconds", "10 seconds", "30 seconds", "1 minute", 
					"10 minutes", "15 minutes", "30 minutes"], 
		  defaultValue: "30 minutes")
	if (getDataValue("hasLed") == "true") {
		if (getDataValue("api") == "smart") {
			input ("ledRule", "enum", title: "LED Mode (if night mode, set type and times in phone app)",
			   	   options: ["auto", "never", "night_mode"], defaultValue: "always")
		} else {
			input ("ledRule", "enum", title: "LED On/OFF", options: ["on", "off"], 
				   defaultValue: "on")
		}
	}
	input ("nameSync", "enum", title: "Synchronize Names",
		   options: ["hubMaster", "deviceMaster", "no"], defaultValue: "no")
	input ("infoLog", "bool", title: "Enable descriptionText logging", defaultValue: true)
	input ("logEnable", "bool", title: "Enable debug logging", defaultValue: false)
	if (getDataValue("api") == "iot" && parent.localHash) {
		input ("changeProt", "bool", defaultValue: false, title: "CAUTION! If device " +
			   "does not work, try using the other Protocol.")
	}
}

def commonInstalled() {
	sendEvent(name: "commsError", value: "false")
	state.errorCount = 0
	return [commsError: "false", errorCount: 0]
}

def commonUpdated() {
	state.errorCount = 0
	sendEvent(name: "commsError", value: "false")
	if (logEnable) { runIn(1800, debugLogOff) }
	Map updStatus = [infoLog: infoLog, logEnable: logEnable, commsError: "false", errorCount: 0]
	if (changeProt) { updStatus << changeProtocol() }
	if (getDataValue("protocol") != "xor") { updStatus << scheduleLogin() }
	updStatus << setPollInterval(systemPoll)
	if (nameSync != "no") { updStatus << [syncName: syncName()] }
	if (ledRule != null) { updStatus << [ledUpdate: updateLed()] }
	runIn(5, deviceHandshake)
	return updStatus
}

/*	To be deleted after 2.5.3
	Error on transition due to scheduled events running prior to startUpHandler.
	Explicitly, this was pollInterval (although others may be involved).
	When poll is called, update the new systemPoll setting and unschedule poll.
	also did same on getEnergyToday. The nightly scheduled jobs are not covered.
*/
def poll() {
	unschedule()
	device.updateSetting("systemPoll", [type: "enum", value: pollInt])
}
def getEnergyToday() { unschedule() }

def changeProtocol() {
	Map respData = [:]
	device.updateSetting("changeProt", [type: "bool", value: false])
	String warnTxt = "<b>changeProtocol: You have chosen to change the protocol. This may be "
	warnTxt += "used as a part of troubleshooting. THIS ACTION IS REVERSIBLE BY SELECTING "
	warnTxt += "CHANGE PROTOCOL again. Allow 20 seconds before testing.</b>"
	log.warn warnTxt
	def baseUrl = getDataValue("baseUrl")
	if (getDataValue("protocol") == "xor") {
		def (ip, port) = baseUrl.tokenize(':')
		String newBaseUrl = "http://${ip}:80/app"
		updateDataValue("protocol", "klap")
		updateDataValue("baseUrl", newBaseUrl)
		respData << [protocol: "xor -> klap", baseUrl: "${baseUrl} -> ${newBaseUrl}"]
	} else {
		String ip = baseUrl.replace("http://", "").replace(":80/app","")
		String newBaseUrl = "${ip}:9999"
		updateDataValue("protocol", "xor")
		updateDataValue("baseUrl", newBaseUrl)
		respData << [protocol: "klap -> xor", baseUrl: "${baseUrl} -> ${newBaseUrl}"]
	}
	runIn(5, configure)
	return [changeProtocol: respData]
}

def updateLed() {
	if (getDataValue("api") == "smart") {
		List requests = [[method: "get_led_info"]]
		cmdBody = [method: "multipleRequest", params: [requests: requests]]
		sendSmartCmd(cmdBody, false)
	} else {
		int onOff = 1
		if (ledRule == "on") { onOff = 0 }
		Map cmdBody = [system: [set_led_off: [off: onOff]]]
		sendIotCmd(cmdBody, false)
	}
	return ledRule
}

def parse_get_led_info(result) {
	//	If not the same as ledRule, update.
	Map logData = [method: "parse_get_led_info"]
	log.trace result.night_mode
	if (ledRule != result.led_rule) {
		Map params = [led_rule: ledRule]
		if (ledRule == "night_mode") {
			params << [night_mode: result.night_mode]
		}
		List requests = [[method: "set_led_info",params: params]]
		logData << [status: "updatingLedRule"]
		cmdBody = [method: "multipleRequest", params: [requests: requests]]
		sendSmartCmd(cmdBody, false)
	}
	logData << [ledRule: ledRule]
	logDebug(logData)
}

def syncName() {
	String status = "no update requested"
	switch(nameSync) {
		case "hubMaster":
			status = "update device's name to hub device name"
			setDeviceAlias()
			break
		case "deviceMaster":
			status = "update hub device's name to device name"
			break
		default: 
			break
	}
	return [syncName: status]
}

def setDeviceAlias() {
	Map cmdBody = [:]
	if (getDataValue("api") == "iot") {
		if (getDataValue("plugNo") || DEV_TYPE == "Child Plug Switch") {
			cmdBody << [context: [child_ids:[getDataValue("deviceId")]]]
		}
		cmdBody << [system: [set_dev_alias: [alias: device.getLabel()]]]
		if (IS_CHILD) {
			Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"), 
						   	   dni: device.deviceNetworkId, api: "iot"]
			parent.processChildCmd(commandData)
		} else {
			sendIotCmd(cmdBody, true)
		}
	} else {
		String nickname = device.getLabel().bytes.encodeBase64().toString()
		List requests = [[method: "set_device_info", params: [nickname: nickname]]]
		cmdBody = [method: "multipleRequest", params: [requests: requests]]
		if (IS_CHILD) {
			Map commandData = [cmdBody: cmdBody, retry: true, devId: getDataValue("deviceId"),
							   dni: device.deviceNetworkId, api: "smart"]
			parent.processChildCmd(commandData)
		} else {
			sendSmartCmd(cmdBody, true)
		}
	}
}

def parseNameUpdate(response) {
	String name = device.getLabel()
	if (getDataValue("api") == "iot") {
		if (getDataValue("plugNo") != null) {
			Map newResp = response.children.find { it.id == getDataValue("plugNo") }
			if (newResp == null) {
				newResp = response.children.find { it.id == getDataValue("deviceId") }
			}
			response = newResp
		}
		name = response.alias
	} else {
		byte[] plainBytes = response.nickname.decodeBase64()
		name = new String(plainBytes)
	}
	device.setLabel(name)
	logData = [method: "updateNames", nameSync: nameSync, label: name]
	logDebug(logData)
	device.updateSetting("nameSync", [type: "enum", value: "no"])
}

def refresh() {
	if (getDataValue("api") == "smart") {
		if (DEV_TYPE == "Parent") {
			//	Smart parent works differently than iot parent using a specific
			//	refresh command to get child info.
			smartParentRefresh()
		} else {
			smartRefresh()
		}
	} else {
		iotRefresh()
	}
}

def scheduleLogin() {
	def startS = Math.round((59) * Math.random()).toInteger()
	def startM = Math.round((59) * Math.random()).toInteger()
	def startH = Math.round((11) * Math.random()).toInteger()
	schedule("${startS} ${startM} ${startH}/8 * * ?", "deviceHandshake")
	return [scheduleLogin: "8 hours"]
}

def setPollInterval(systemPoll) {
	int pollInterval = (systemPoll =~ /\d+/)[0].toInteger()
	String pollMethod
	if (getDataValue("api") == "smart") {
		if (DEV_TYPE == "Parent") {
			pollMethod = "smartParentPoll"
		} else {
			pollMethod = "smartPoll"
		}
	} else {
		pollMethod = "iotPoll"
	}
	if (systemPoll.contains("sec")) {
		state.retry = false
		def startS = Math.round((pollInterval-1) * Math.random()).toInteger()
		schedule("${startS}/${pollInterval} * * * * ?", pollMethod)
		logWarn("setPollInterval: Polling intervals of less than one minute " +
				"can take high resources and may impact hub performance.")
	} else {
		state.retry = true
		def startS = Math.round(59 * Math.random()).toInteger()
		def startM = Math.round((pollInterval-1) * Math.random()).toInteger()
		schedule("${startS} ${startM}/${pollInterval} * * * ?", pollMethod)
	}
	return [setPollInterval: [systemPoll: systemPoll, pollMethod: pollMethod]]
}

def deviceHandshake(protocol = getDataValue("protocol")) {
	if (protocol == "klap") {
		klapHandshake(getDataValue("baseUrl"), parent.localHash)
	} else if (protocol == "aes") {
		aesHandshake(getDataValue("baseUrl"))
	} else {
		iotRefresh()
	}
}

def configure() {
	//	Only check xor device if it is already xor.
	if (getDataValue("protocol") == "xor") {
		sendUdp(xorDiscCmd(), "parseCheckXor", getDataValue("baseUrl"), 5)
	} else {
		checkSecure()
	}
}

def parseCheckXor(message) {
	Map logData = [method: "parseCheckXor"]
	if (message == "udpTimeout") {
		logData << [status: "noXorDeviceFound", action: "checkSecure"]
		checkSecure()
	} else {
		unschedule("udpTimeout")
		Map respData = parseUdp(message, "xor")
		if (respData.status == "ok") {
			Map cmdResp = respData.cmdResp.system.get_sysinfo
			def foundDni
			if (cmdResp.mic_mac) {
				foundDni = cmdResp.mic_mac
			} else {
				foundDni = cmdResp.mac.replace(/:/, "")
			}
			if (getDataValue("plugNo")) {
				foundDni = foundDni + getDataValue("plugNo")
			}
			if (foundDni == device.deviceNetworkId) {
				logData << [status: "deviceFound", action: "updated"]
				runIn(1, deviceHandshake)
			} else {
				logData << [status: "foundDifferentDevice", action: "quickPoll"]
				parent.pollForDevices("poll")
			}
		} else {
			logData << [status: "invalidUdpResponse", action: "checkSecure"]
			checkSecure()
		}
	}
	logInfo(logData)
}

def checkSecure() {
	//	If XOR Fails or device is already Secure.....
	def ip = (getDataValue("baseUrl") =~ /\b\d{1,3}(?:\.\d{1,3}){3}\b/)[0]
	def ipPort = ip + ":20002"
	sendUdp(secureDiscCmd(), "parseCheckSecure", ipPort)
}

def parseCheckSecure(message) {
//	If an XOR device is found here (therefore, not found in the XOR Check), this
//	will transform the device from XOR to KLAP by updating the protocol and
//	baseURL values determined herein.
	Map logData = [method: "parseCheckSecure"]
	if (message == "udpTimeout") {
		logData << [status: "noSecureDeviceFound", next: "quickFind"]
		parent.pollForDevices("quickFind", 5)
	} else {
		unschedule("udpTimeout")
		Map respData = parseUdp(message, "secure")
		if (respData.status == "ok") {
			Map cmdResp = respData.cmdResp.result
			String foundProt = cmdResp.mgt_encrypt_schm.encrypt_type.toLowerCase()
			def foundDni = cmdResp.mac.replace(/-/, "")
			if (getDataValue("plugNo")) { foundDni = foundDni + getDataValue("plugNo") }
			if (foundDni == device.deviceNetworkId) {
				if (foundProt == "tpap") {
					logData << [foundProt: "TPAP", action: "NOT UPDATED. TPAP not supported.",
								note: "divice will likely work as specified"]
				} else if (foundProt != getDataValue("protocol") && foundProt != "tpap") {
					String httpPort = cmdResp.mgt_encrypt_schm.http_port
					String httpStr = "http://"
					String httpPath = "/app"
					String baseUrl = httpStr + respData.ip + ":" + httpPort + httpPath
					logData << [update: "protocolUpdated", newProtocol: foundProt, baseUrl: baseUrl]
					updateDataValue("protocol", foundProt)
					updateDataValue("baseUrl", baseUrl)
				}
				logData << [status: "deviceFound", next: "deviceHandshake"]
				runIn(1, deviceHandshake)
			} else {
				logData << [status: "foundDifferentDevice", next: "pollForDevices"]
			}
		} else {
			logData << [status: "invalidUdpResponse", next: "pollForDevices"]
		}
	}
	logInfo(logData)
}

/*
In the app during a find (discovery or quick) device data is generated
and sent to this method. XOR data should always (by design) comes first.
To preclude multiple processing, set state.updProcessed to true the first
time an update is processed and skip the remaining.
So if several finds are done rapidly, only the FIRST until the state times out.
Future updates: if there are changes in dataValues, an update section will
need to be added.
*/
void updateDeviceData(Map devData) {
	Map logData = [method: "updateDeviceData", devDataSize: devData.size()]
	/*	Second path to transition from Kasa to KasaTapo in case (for any
		reason) the hubStartupHandler is not successful.
		This should add methods if base data changes in future updates. But
		each should be kept in sequence to support users that infrequently
		update their hubs.*/
	verUpdate_2_5_3()
//	Instrumentation	
//log.trace "updateDeviceData: [devData: $devData]"
	if (devData != [:]) {
		String protocol = getDataValue("protocol")
		String baseUrl = getDataValue("baseUrl")
		String version = getDataValue("version")
		//	Device will update to KLAP once it is discovered via klap but 
		//	not via XOR.
		//	deviceHandshake is only run if something as changed.
		String notice = "Protocol or baseUrl are changed. CHECK DEVICE"
		boolean newData = false
		if (state.updProcessed == true) {
			logData << [notProcessed: "XOR data previously processed"]
		} else if (devData.protocol == protocol) {
			//	protocol not processed. Only xor or klap baseUrl processed.
			if (baseUrl != devData.baseUrl) {
				updateDataValue("baseUrl", devData.baseUrl)
				logData << [newBaseUrl: devData.baseUrl, NOTICE: notice]
				newData = true
			}
			state.updProcessed = true
		} else if (protocol == "xor" && devData.protocol != "xor") {
			//	protocol and baseUrl updated. This is the big case.
			//	only goes from xor -> klap. Never from klap -> xor.
			//	Note that preference "changeProt" provides the user
			//	a method to manually change protocol.
			updateDataValue("protocol", devData.protocol)
			updateDataValue("baseUrl", devData.baseUrl)
			logData << [newProtocol: devData.protocol, newBaseUrl: devData.baseUrl,
						NOTICE: notice]
			newData = true
			state.updProcessed = true
		} else {
			logData << [notProcessed: "ignored xor find of a klap protocol device."]
		}
			
		if (newData) { runIn(5, configure) }
		runIn(300, clearUpdProcessed)
		logInfo(logData)
	}
}

void clearUpdProcessed() { state.remove("xorReceived") }

//	Initiated by app.hubStartupHandler.
//	Does an immediated update of the device data followed by an
//	updated to reset settings and kick-start the device.
def verUpdate_2_5_3() {
	//	The signature for this update is NOT version. It is if the
	//	dataValue "protocol" exists. So exit if it exists.
	if (getDataValue("protocol") != null) { return }

	unschedule()
	logInfo("EXECUTING ONE-TIME CONVERSION: kasa Integration to kasaTapoIntegration")
	log.warn "This update will not correct IP issues if it is a local LAN issue."
	log.warn "CHECK YOUR DEVICE AND RUN CONFIGURATION IF THERE ARE ISSUES."
	Map respData = [method: "version_2_5_3_update"]
	Map currDataValues = device.getData()
	Map currSettings = settings
//	Instrumentation	for test
//log.debug "currDataValues: $currDataValues"
//log.debug "currSettings: $currSettings"
	//	Add new dataValues
	updateDataValue("api", "iot")
	updateDataValue("protocol", "xor")
	updateDataValue("manufacturer", "kasa")
	//	use deviceId for all devices (vice plugId for multiplugs.
	if (currDataValues.plugId != null) {
		updateDataValue("deviceId", currDataValues.plugId)
	}
	//	remove command setLed in favor of a preference that
	//	keys off of data value.
	if (DEV_TYPE == "Dimming Switch" || DEV_TYPE == "Parent"
	    || DEV_TYPE == "Plug Switch") {
		updateDataValue("hasLed", "true")
		String ledRule = device.currentValue("led")
		device.updateSetting("ledRule", [type: "enum", value: ledRule])
	}
	//	remove dataValue feature to allow direct call of
	//	ctHigh, ctLow, and isEm
	String feature = currDataValues.feature
	if (feature.contains("color") || feature.contains("light")) {
		updateDataValue("ctHigh", "6500")
		updateDataValue("ctLow", "2500")
	}
	if (currSettings.emFunction != null) {
		updateDataValue("isEm", "true")
	}
	//	Create new single call baseUrl.
	String baseUrl = "${currDataValues.deviceIP}:${currDataValues.devicePort}"
	updateDataValue("baseUrl", baseUrl)
	respData << [dataValues: "updated to 2.5.3"]
	
	//	Update / create new preferences map,
	//	convert poll interval from command and state to setting
	String pollInt = state.pollInterval	//	Value x minutes, x seconds
	device.updateSetting("systemPoll", [type: "enum", value: pollInt])
	if (currSettings.emFunction) {
		def emEnable = false
		if (currSettings.emFunction == true) { emEnable = true }
		device.updateSetting("emEnable", [type: "bool", value: emEnable])
	}
	//	avoid confusion with device parameter transition_time and driver data.
	if (currSettings.transition_Time) {
		device.updateSetting("transTime", [type: "number", value: currSettings.transition_Time])
	}
	//	simplify settings. now one transTime for all device types.
	if (currSettings.gentleOn) {
		def gentOn = currSettings.gentleOn.toInteger()
		int newTransTime = ((500 + gentOn) / 1000).toInteger()
		device.updateSetting("transTime", [type: "number", value: newTransTime])
	}
	updateDataValue("version", version())
	respData << [preferences: "updated to 2.5.3"]
	
//	Remove reduntant dataValues
	removeDataValue("deviceIP")
	removeDataValue("devicePort")
	removeDataValue("plugId")
	removeDataValue("feature")
	removeDataValue("emFunction")
	respData << [redundantDataValues: "removed"]
	
//	Remove redundnat settings
	device.removeSetting("transition_Time")
	device.removeSetting("gentleOn")
	device.removeSetting("gentleOff")
	device.removeSetting("syncBulbs")
	device.removeSetting("manualIp")
	device.removeSetting("manualPort")
	device.removeSetting("nameSync")
	device.removeSetting("rebootDev")
	device.removeSetting("fadeOn")
	device.removeSetting("fadeOff")
	device.removeSetting("longPress")
	device.removeSetting("doubleClick")	
	respData << [redundantSettings: "removed"]
	
//	Remove redundated states
	state.remove("pollInterval")
	respData << [redundantStates: "removed"]
	
	runIn(5, updated)
	logInfo(respData)
}
