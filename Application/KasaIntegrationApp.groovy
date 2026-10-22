/*	Install Kasa / Tapo Plugs, Switches, and Bulbs.
	Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
*/

String nameSpace() { return "davegut" }
String version() { return "2.5.3" }


definition(
	name: "Kasa Integration",
	namespace: nameSpace(),
	author: "Dave Gutheinz",
	description: "Application to install TP-Link Kasa and Tapo  bulbs, plugs, and switches.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	installOnOpen: true,
	singleInstance: true,
	documentationLink: "https://github.com/DaveGut/Kasa-Hubitat/blob/main/KasaIntegrationHelp.pdf",
	importUrl: ""
)

preferences {
	page(name: "startPage")
	page(name: "setupApp")
	page(name: "addDevicesPage")
	page(name: "removeDevicesPage")
}

void installed() {
	//	Initialize settings on installation
	def hub = location.hubs[0]
	def hubIpArray = hub.localIP.split('\\.')
	def segment = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
	app?.updateSetting("version", [type:"string", value: version()])
	app?.updateSetting("lanSegment", [type:"string", value: segment])
	app?.updateSetting("ports", [type:"string", value: "9999"])
	app?.updateSetting("asInst", [type:"bool", value: true])	//Display setup page quick help
	app?.updateSetting("spInst", [type:"bool", value: true])	//Display start page quick help
	atomicState.iotDevices = [:]	//database for IOT devices
	atomicState.smartDevices = [:]	//database for SMART devices
	atomicState.findingSecure = false	//Wait state to preclude smartDevices concurrence issues.
	atomicState.findingXor = false	//Wait state to preclude xorDevices concurrence issues.
	atomicState.deviceWait = false
	initialize()
	logInfo("installed: [status: Initialized settings and states.]")
}

void initialize() {
	subscribe(location, "systemStart", "hubStartupHandler")
}

void hubStartupHandler() {
	//	Runs on startup. Calls child.updateVersion.
	//	Is specific for each INTEGRATION Version Change.
	//	Note: The verUpdate_2_5_3 should be kept since it is the
	//	transition from Kasa to KasaTapo app (supports users delaying
	//	the hub update for significant periods.
	//	Method child.verUpdate_2_5_3 is in library tpl_dev_common.
	//	Those child method will determine what to do for each version.
	//	Note: method version() is in library tpl_id_logs
	Map logData = [method: "hubStartupHandler", children: "updating"]
	def children = getChildDevices()
	children.each { child ->
		child.verUpdate_2_5_3()
	}
	if (settings.version != version()) {
		logData << [currVersion: settings.version, newVersion: version()]
		if (version() == "2.5.3") {
			//	remove old settings
			app.removeSetting("routerIp")
			app.removeSetting("configureEnabled")
			app.removeSetting("appSetup")
			app.removeSetting("hostLimits")
			app?.updateSetting("asInst", [type:"bool", value: true])
			app?.updateSetting("instMatter", [type:"bool", value: true])
			app?.updateSetting("infoLog", [type:"bool", value: true])
			//	update states
			state.remove("devices")
			state.remove("tapoDevices")
			state.remove("hostArray")
			state.remove("devices")
			//	Update app for next run.
			app.updateSetting("version", [type:"string", value: version()])
			runIn(2, updated)
		} else {
			logData << [status: "no app updates required"]
		}
	}
	logInfo(logData)
}

def updated() {
	unschedule()
	unsubscribe()
	initialize()
	app?.updateSetting("logEnable", [type:"bool", value: false])
	atomicState.iotDevices = [:]
	atomicState.smartDevices = [:]
	atomicState.findingSecure = false
	atomicState.findingXor = false
	atomicState.deviceWait = false
	log.info "updated: [status: setting updated for next session]"
}

def uninstalled() {
	List uninstalled = []
    getAllChildDevices().each {
		uninstalled << [it.alias]
        deleteChildDevice(it.deviceNetworkId)
    }
	log.info "uninstalled: [status: Device ${uninstalled} and App uninstalled]"
}

//	===== Start Page =====
def startPage() {
	log.info "startPage"
	if (selectedRemoveDevices) {removeDevices()}
	else if (selectedAddDevices) { addDevices() }
	//	Update port and segment array
	state.segArray = lanSegment.split('\\,')
	state.portArray = ports.split('\\,')

	return dynamicPage(name:"startPage",
					   uninstall: true,
					   install: true) {
		section() {
			String setupParams = "user: ${userName}, installMatter: <b>${instMatter}</b>, "
			setupParams += "LanSegments: ${state.segArray}, KasaPorts: ${state.portArray}."
			if (spInst) { paragraph startPgInst() }
			if (!userName || !userPassword) {
				setupParams += "\n<b>App will only insall Kasa IOT Devices.</b>"
			} else {
				setupParams += "\nApp will install Kasa IOT and Kasa/Tapo SMART Devices."
			}
			href "setupApp",
				title: "<b>Setup Application and Credentials</b>",
				description: setupParams
			href "addDevicesPage",
				title: "<b>Scan for devices and add</b>",
				description: "Be patient. It can take up to 1.6 minutes."
			href "removeDevicesPage",
				title: "<b>Remove Devices</b>",
				description: "Select devices to remove from Hubitat."
			input "logEnable", "bool",
				   title: "<b>Debug logging</b>",
				   submitOnChange: true, defaultValue: true
			input "infoLog", "bool",
				   title: "<b>Info logging</b>",
				   submitOnChange: true, defaultValue: true
			input "spInst", "bool", title: "<b>Display Start Page Help</b>",
				submitOnChange: true, defaultValue: true
		}
	}
}

String startPgInst() {
	String quickSP = "Install Kasa/Tapo Device and prepare for Hubitat control."
	quickSP += "\n\ta.\tKasa/Tapo Phone App: Install device."
	quickSP += "\n\tb.\tKasa/Tapo Phone App: Turn on Third Party Compatibility."
	quickSP += "\n\tc.\tKasa/Tapo Phone App: Exercise device to be installed."
	quickSP += "\n\td.\tKasa/Tapo Phone App: Turn OFF Multi-Factor Authentication."
	quickSP += "\n\te.\tYour WiFiRouter: Set DHCP Reservation for Device."
	quickSP += "\nIn this Integration."
	quickSP += "\n\ta.\tSelect Setup Application and Credentials."
	quickSP += "\n\tb.\tOn return select desired action."
	quickSP += "\n<b>Detailed help: use the ? icon above</b>."
	return quickSP
}
 
//	===== Setup App Page =====
def setupApp() { 
	return dynamicPage (name: "setupApp", 
    					title: "Setup Application and Update Credentials",
						nextPage: "startPage",
                        install: false) {
		section() {
			if (asInst) { paragraph setUpInst() }
			input ("userName", "string",
            		title: "Email Address", 
                    required: false,
                    submitOnChange: true)
			input ("userPassword", "string",
            		title: "Account Password",
                    required: false,
                    submitOnChange: true)
			if (userName && userPassword) {
				def credCheck = checkCreds(userName, userPassword)
				if (credCheck != "OK") {
					paragraph credCheck
				}
				createSecureCreds()
			}
			//	Install Matter
			input "instMatter", "bool", title: "<b>Install matter capable devices</b>",
				submitOnChange: true, defaultValue: false
			if (instMatter) {
				paragraph "\tCaution.  If the devices is already installed in Hubitat, this can created a second version."
			}
			//	Lan segments and ports
			input "lanSegment", "string",
				title: "<b>Lan Segments</b> (only works for legacy Kasa devices).", 
				submitOnChange: true
			input "ports", "string",
				title: "<b>Kasa (IOT) ONLY ports</b> (only works for legacy Kasa devices)", 
				submitOnChange: true
			input "asInst", "bool", title: "<b>Display Setup Help</b>",
				submitOnChange: true, defaultValue: true
		}
	}
}
 
String setUpInst() {
	String setUpInst = "Username and Password"
			setUpInst += "\n\ta.\tPassword and Username are both case sensitive."
			setUpInst += "\n\tb.\tRecommend Tapo/Kasa password be alpha/numeric only."
			setUpInst += "Install Matter. Caution, can cause duplicate instantiation of devices."
			setUpInst += "\n\tLan Segments and ports. Advanced users only."
	return setUpInst
}

String checkCreds(userName, userPassword) {
	def resp = "OK"
	String altResp = "Credentials have special characters:"
	Map credSpecChars = [:]
	def pattern = /[^a-zA-Z0-9@.]/
	def matcher = userName =~ pattern
	List specChars = []
	matcher.each { match -> specChars << match[0] }
	if (specChars != []) { credSpecChars << [userName: specChars] }
	specChars = userPassword.findAll(/\W+/)
	if (specChars != []) { credSpecChars << [userPassword: specChars] }
	if (credSpecChars != [:]) {
		resp = "\tYour credentials may have issues with the following special characters:"
		resp += "\n\t\t${credSpecChars}"
	}
	return resp
}

void createSecureCreds() {
	String encUsername = mdEncode("SHA-1", userName.bytes).encodeHex().encodeAsBase64().toString()
	app?.updateSetting("encUsername", [type: "string", value: encUsername])
	String encPassword = userPassword.trim().bytes.encodeBase64().toString()
	app?.updateSetting("encPassword", [type: "string", value: encPassword])
	//	Get normal localHush
	String authHash = genKlapLocalHash(userName, userPassword)
	app?.updateSetting("localHash", [type: "string", value: authHash])
	//	Get alternate local hash using default passwords
	authHash = genKlapLocalHash("test@tp-link.net", "test")
	app?.updateSetting("altLocalHash", [type: "string", value: authHash])
	logDebug("createSecureCreds: Created credential settings and checked credentials for special characters")
} 

String genKlapLocalHash(name, password) {
	byte[] userHashByte = mdEncode("SHA-1", encodeUtf8(name).getBytes())
	byte[] passwordHashByte = mdEncode("SHA-1", encodeUtf8(password.trim()).getBytes())
	byte[] authHashByte = [userHashByte, passwordHashByte].flatten()
	String authHash = mdEncode("SHA-256", authHashByte).encodeBase64().toString()
	return authHash
}

//	===== Add Devices Page =====
def addDevicesPage() {
	Map logData = [method: "addDevicesPage"]
	logData << [pollForDevices: pollForDevices("find", 10)]
//	Map devicesData = atomicState.iotDevices + atomicState.smartDevices
	Map devicesData = atomicState.iotDevices
	if (atomicState.smartDevices) {
		devicesData += atomicState.smartDevices
	}
	logData << [devicesDataSize: devicesData.size()]
	app?.removeSetting("selectedAddDevices")
	Map uninstalledDevices = [:]
	devicesData.each {
		def isChild = getChildDevice(it.key)
		if (!isChild) {
			def driver = "kasaTapo ${it.value.type}"
			uninstalledDevices["${it.key}"] = "${it.value.alias}, ${it.value.type}, ${it.value.api}, ${it.value.protocol}, ${it.key}"
		}
	}
	uninstalledDevices = uninstalledDevices.sort{ it.value }
	logData << [totalUninstalledDevs: uninstalledDevices.size()]
	logInfo(logData)
	return dynamicPage(name:"addDevicesPage",
					   title: "Add Devices to Hubitat",
					   nextPage: "startPage",
					   install: false) {
		section() {
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "<b>Devices to add</b> (${uninstalledDevices.size() ?: 0} available).\n\t" +
				   "Total Devices: ${devicesData.size()}",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: uninstalledDevices)
			href "addDevicesPage",
				title: "<b>Rescan for Additional Devices</b>",
				description: "<b>Exercise missing devices through the Kasa or Tapo Phone App.</b>"
		}
	}
} 

def addDevices() {
	logInfo([method: "addDevices", selectedAddDevices: selectedAddDevices])
	Map devicesData = atomicState.iotDevices + atomicState.smartDevices
	selectedAddDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = devicesData.find { it.key == dni }.value
			addDevice(device, dni)
		}
		pauseExecution(2000)
	}
	app?.removeSetting("selectedAddDevices")
}

def addDevice(device, dni) {
	Map logData = [method: "addDevice", dni: dni, alias: device.alias]
	try {
		String label = device.alias.replaceAll("[\u201C\u201D]", "\"").replaceAll("[\u2018\u2019]", "'").replaceAll("[^\\p{ASCII}]", "")
		String name = device.model
		String driver = "Kasa ${device.type}"
		["alias", "type", "driver"].each { device.remove(it) }
		addChildDevice(nameSpace(), driver, dni,
			["label": label,
			 "name" : name,
			 "data" : device]
		)
		logData << [status: "added"]
	} catch (err) {
		logData << [status: "failedToAdd", device: device, errorMsg: err]
	}
	logInfo(logData)
	return
}

//	===== Remove Devices =====
def removeDevicesPage() {
	Map installedDevices = [:]
	getChildDevices().each {
		installedDevices << ["${it.device.deviceNetworkId}": it.device.label]
	}
	logDebug("removeDevicesPage: ${installedDevices}")
	return dynamicPage(name:"removeDevicesPage",
					   title:"<b>Remove Devices from Hubitat</b>",
					   nextPage: "startPage",
					   install: false) {
		section() {
			input ("selectedRemoveDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to remove (${installedDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: installedDevices)
		}
	} 
}

void removeDevices() {
	logInfo([method: "removeDevices", selectedRemoveDevices: selectedRemoveDevices])
	List removed = []
	selectedRemoveDevices.each { dni ->
		removed << [getChildDevice(dni)]
		deleteChildDevice(dni)
	}
	app?.removeSetting("selectedRemoveDevices")
}

//	===== Device Discovery and devices database creation =====
Map pollForDevices(type, timeout = 5) {
	//	type = find (add devices), poll (quick poll for limited data).
	Map logData = [method: "pollForDevices", type: type, timeout: "${timeout}", xorPortArray: state.portArray]
	def xorAction = "getXorLanData"
	def secureAction = "getSecureLanData"
	if (type == "poll") {
		xorAction = "quickFindXorData"
		secureAction = "quickFindSecureData"
	}
	//	XOR/UDP devices 
	state.portArray.each { port ->
		atomicState.findingSecure = false
		atomicState.findingXor = true
		findDevices(xorAction, xorDiscCmd(), port, timeout)
		if (type == "poll") {
			def await = waitForXorFind(20)
		} else {
			def await = waitForXorFind(45)
		}			
	}
	//	KLAP/AES devices
	if (userName && userPassword) {
		atomicState.findingSecure = true
		atomicState.findingXor = false
		findDevices(secureAction, secureDiscCmd(), 20002, timeout)
		if (type == "poll") {
			def await = waitForSecureFind(20)
		} else {
			def await = waitForSecureFind(60)
		}			
		pauseExecution(2000)
	}
	logDebug(logData)
	return logData
}

def waitForXorFind(secs) {
	//	Optimizes timing between the IOT/Smart find as well as reduces state concurrence issues.
	Map logData = [method: "waitForIotFind"]
	int i = 0
	for(i = 0; i < secs; i += 1) {
		if (atomicState.findingXor == false) {
			logData << [seconds: i]
			if(i == secs - 1) {
				logData << [ERROR: "Find took excessive time."]
				log.warn logData
			}
			i = secs
		} else {
			pauseExecution(1000)
		}
	}
	logDebug(logData)
	return
}

def waitForSecureFind(secs) {
	//	Optimizes timing between the IOT/Smart find as well as reduces state concurrence issues.
	Map logData = [method: "waitForSecureFind"]
	int i = 0
	for(i = 0; i < secs; i += 1) {
		if (atomicState.findingSecure == false) {
			logData << [seconds: i]
			if(i == secs - 1) {
				logData << [ERROR: "Find took excessive time."]
				log.warn logData
			}
			i = secs
		} else {
			pauseExecution(1000)
		}
	}
	logDebug(logData)
	return
}

void findDevices(action, cmdData, port, timeout) {
	Map logData = [method: "findDevices", action: action, port: port, timeout: timeout]
	state.segArray.each {
		def pollSegment = it.trim()
		List deviceIPs = []
		for(int i = 2; i < 255; i++) {
			deviceIPs.add("${pollSegment}.${i.toString()}")
		}
		logInfo("findDevices: executing findDevice poll to port ${port} (takes up to 40 seconds).")
		//	Pre-poll to exercise interface, waking deep-sleeping devices.
		sendUdp(cmdData, "nullParse", "${deviceIPs.join(',')}:${port}", 3)
		pauseExecution(4000)
		//	Required since Hubitat does not currently return parseWarning message.
		runIn(timeout + 6, udpTimeout, [data: action])
		sendUdp(cmdData, action, "${deviceIPs.join(',')}:${port}", timeout)
	}
	logDebug(logData)
}
 
void addDeviceData(dni, devData) {
	Map logData = [method: "addDeviceData", alias: devData.alias]
	
//	Instrumentation option
//	logData << [devData: devData]
	
	devData << [version: version()]
	def childDev = getChildDevice(dni)
	if (childDev) {
		logData << [isChild: true, action: "send data to child device"]
		childDev.updateDeviceData(devData)
	} else {
		Map devicesData = [:]
		//	klap protocol are put in smartDevices to assure klap supremacy
		if (devData.protocol == "xor") {
			devicesData.putAll(atomicState.iotDevices ?: [:])
			devicesData << ["${dni}": devData]
			atomicState.iotDevices = devicesData
			logData << [action: "added to iotDevices."]
		} else {
			devicesData.putAll(atomicState.smartDevices ?: [:])
			devicesData << ["${dni}": devData]
			atomicState.smartDevices = devicesData
			logData << [action: "added to smartDevices."]
		}
	}
	atomicState.deviceWait = false
	logInfo(logData)
}

def nullParse(resp) { }

def waitForDevData(secs) {
	//	Assures one device processed at a time during async methods.
	int i = 0
	def intervals = 2 * secs
	for(i = 0; i < intervals; i += 1) {
		if (atomicState.deviceWait == false) {
				i = intervals
		} else {
			pauseExecution(500)
		}
	}
	return
}









// ~~~~~ start include (1039) tpLink.tpl_app_xor ~~~~~
library (  // library marker tpLink.tpl_app_xor, line 1
	name: "tpl_app_xor", // library marker tpLink.tpl_app_xor, line 2
	namespace: "tpLink", // library marker tpLink.tpl_app_xor, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_app_xor, line 4
	description: "Kasa (IOT) Protocol metnods for Kasa/Tapo application. Hub V 2.5.3", // library marker tpLink.tpl_app_xor, line 5
	category: "utilities", // library marker tpLink.tpl_app_xor, line 6
	documentationLink: "" // library marker tpLink.tpl_app_xor, line 7
) // library marker tpLink.tpl_app_xor, line 8

void getXorLanData(response) { // library marker tpLink.tpl_app_xor, line 10
	unschedule("udpTimeout") // library marker tpLink.tpl_app_xor, line 11
	Map logData = [method: "getXorLanData"] // library marker tpLink.tpl_app_xor, line 12
	if (response == "udpTimeout") { // library marker tpLink.tpl_app_xor, line 13
		logData << [response: "udpTimeout", warning: "No devices found"] // library marker tpLink.tpl_app_xor, line 14
	} else { // library marker tpLink.tpl_app_xor, line 15
		logData << [responses: response.size()] // library marker tpLink.tpl_app_xor, line 16
		if (response instanceof Map) { // library marker tpLink.tpl_app_xor, line 17
			atomicState.deviceWait = true // library marker tpLink.tpl_app_xor, line 18
			getXorDiscData(response) // library marker tpLink.tpl_app_xor, line 19
			waitForDevData(5) // library marker tpLink.tpl_app_xor, line 20
		} else { // library marker tpLink.tpl_app_xor, line 21
			response.each { // library marker tpLink.tpl_app_xor, line 22
				atomicState.deviceWait = true // library marker tpLink.tpl_app_xor, line 23
				getXorDiscData(it) // library marker tpLink.tpl_app_xor, line 24
				waitForDevData(5) // library marker tpLink.tpl_app_xor, line 25
			} // library marker tpLink.tpl_app_xor, line 26
		} // library marker tpLink.tpl_app_xor, line 27
	} // library marker tpLink.tpl_app_xor, line 28
	atomicState.findingXor = false // library marker tpLink.tpl_app_xor, line 29
	logInfo(logData) // library marker tpLink.tpl_app_xor, line 30
} // library marker tpLink.tpl_app_xor, line 31

void getXorDiscData(response) { // library marker tpLink.tpl_app_xor, line 33
	Map discData = parseUdp(response, "xor") // library marker tpLink.tpl_app_xor, line 34
	if (discData.status == "ok") { // library marker tpLink.tpl_app_xor, line 35
		String baseUrl = "${discData.ip}:${discData.port}" // library marker tpLink.tpl_app_xor, line 36
		getIotDeviceData(discData.cmdResp, baseUrl, "xor") // library marker tpLink.tpl_app_xor, line 37
	} else { // library marker tpLink.tpl_app_xor, line 38
		atomicState.deviceWait = false // library marker tpLink.tpl_app_xor, line 39
		Map logData = [ method  : "getXorDiscData", status  : "errorInParseUDP", // library marker tpLink.tpl_app_xor, line 40
					   discData: discData] // library marker tpLink.tpl_app_xor, line 41
        logDebug(logData) // library marker tpLink.tpl_app_xor, line 42
    } // library marker tpLink.tpl_app_xor, line 43
} // library marker tpLink.tpl_app_xor, line 44

void getIotDeviceData(cmdResp, baseUrl, protocol) { // library marker tpLink.tpl_app_xor, line 46
	Map sysInfo = cmdResp?.system?.get_sysinfo // library marker tpLink.tpl_app_xor, line 47
	if (!sysInfo) { // library marker tpLink.tpl_app_xor, line 48
		atomicState.deviceWait = false // library marker tpLink.tpl_app_xor, line 49
		logDebug("getIotDeviceData: [status: missingSysInfo]") // library marker tpLink.tpl_app_xor, line 50
		return // library marker tpLink.tpl_app_xor, line 51
	} // library marker tpLink.tpl_app_xor, line 52
	cmdResp = sysInfo	 // library marker tpLink.tpl_app_xor, line 53
	def dni // library marker tpLink.tpl_app_xor, line 54
	Map logData = [method: "getIotDeviceData", protocol: protocol] // library marker tpLink.tpl_app_xor, line 55
	if (cmdResp.mic_mac) { // library marker tpLink.tpl_app_xor, line 56
		dni = cmdResp.mic_mac // library marker tpLink.tpl_app_xor, line 57
	} else if (cmdResp.mac) { // library marker tpLink.tpl_app_xor, line 58
		dni = cmdResp.mac.replace(/:/, "") // library marker tpLink.tpl_app_xor, line 59
	} else { // library marker tpLink.tpl_app_xor, line 60
		logData << [alias: cmdResp.alias, cmdResp: cmdResp, // library marker tpLink.tpl_app_xor, line 61
					status: "Failed to add. DNI Not found in response"] // library marker tpLink.tpl_app_xor, line 62
		logDebug(logData) // library marker tpLink.tpl_app_xor, line 63
		atomicState.deviceWait = false // library marker tpLink.tpl_app_xor, line 64
		return // library marker tpLink.tpl_app_xor, line 65
	} // library marker tpLink.tpl_app_xor, line 66
	//	Design to preclude unnecessary processing and also device protocol // library marker tpLink.tpl_app_xor, line 67
	//	ping-pong. For IOT devices, if it has already been found on this  // library marker tpLink.tpl_app_xor, line 68
	//	application session, do not add. // library marker tpLink.tpl_app_xor, line 69
	if (atomicState.iotDevices?.containsKey(dni) && getChildDevice(dni)) { // library marker tpLink.tpl_app_xor, line 70
		logData << [alias: cmdResp.alias, status: "aready in state.iotDevices. Ignored."] // library marker tpLink.tpl_app_xor, line 71
		logInfo(logData) // library marker tpLink.tpl_app_xor, line 72
		atomicState.deviceWait = false // library marker tpLink.tpl_app_xor, line 73
		return // library marker tpLink.tpl_app_xor, line 74
	} // library marker tpLink.tpl_app_xor, line 75
	def kasaType = cmdResp.type // library marker tpLink.tpl_app_xor, line 76
	if (cmdResp.mic_type) { kasaType = cmdResp.mic_type } // library marker tpLink.tpl_app_xor, line 77
	Map device = [api: "iot", baseUrl: baseUrl, model: cmdResp.model, protocol: protocol, // library marker tpLink.tpl_app_xor, line 78
				 manufacturer: "kasa"] // library marker tpLink.tpl_app_xor, line 79
	switch(kasaType) { // library marker tpLink.tpl_app_xor, line 80
		case "IOT.SMARTPLUGSWITCH": // library marker tpLink.tpl_app_xor, line 81
			def type = "Plug Switch" // library marker tpLink.tpl_app_xor, line 82
			if (cmdResp.children) { // library marker tpLink.tpl_app_xor, line 83
				//	if any of the children are installed as legacy, children are added // library marker tpLink.tpl_app_xor, line 84
				//	to devices as devices (vice not installed except the parent) // library marker tpLink.tpl_app_xor, line 85
				def isInstalled = false // library marker tpLink.tpl_app_xor, line 86
				cmdResp.children.each { // library marker tpLink.tpl_app_xor, line 87
					def id = it.id[-2..-1] // library marker tpLink.tpl_app_xor, line 88
					def child = getChildDevice(dni + id) // library marker tpLink.tpl_app_xor, line 89
					if (getChildDevice(dni + id)) { isInstalled = true } // library marker tpLink.tpl_app_xor, line 90
				} // library marker tpLink.tpl_app_xor, line 91
				if (!isInstalled) { // library marker tpLink.tpl_app_xor, line 92
					device << [type: "Parent", alias: cmdResp.alias, deviceId: cmdResp.deviceId] // library marker tpLink.tpl_app_xor, line 93
					device << [hasLed: true] // library marker tpLink.tpl_app_xor, line 94
					addDeviceData(dni, device) // library marker tpLink.tpl_app_xor, line 95
				} else { // library marker tpLink.tpl_app_xor, line 96
					//	In case not all children for the installed device are installed. // library marker tpLink.tpl_app_xor, line 97
					//	plus updated child data for those installed. // library marker tpLink.tpl_app_xor, line 98
					if(cmdResp.led_off) { device << [hasLed: true] } // library marker tpLink.tpl_app_xor, line 99
					if (cmdResp.feature == "TIM:ENE") { device << [isEm : true] } // library marker tpLink.tpl_app_xor, line 100
					cmdResp.children.each { // library marker tpLink.tpl_app_xor, line 101
						Map childDevice = [:] // library marker tpLink.tpl_app_xor, line 102
						childDevice.putAll(device ?: [:]) // library marker tpLink.tpl_app_xor, line 103
						def id = it.id[-2..-1] // library marker tpLink.tpl_app_xor, line 104
						def deviceId = cmdResp.deviceId + id // library marker tpLink.tpl_app_xor, line 105
						def alias = it.alias // library marker tpLink.tpl_app_xor, line 106
						def childDni = dni + id // library marker tpLink.tpl_app_xor, line 107
						childDevice << [plugNo: id, deviceId: deviceId, alias: alias, type: type] // library marker tpLink.tpl_app_xor, line 108
						addDeviceData(childDni, childDevice) // library marker tpLink.tpl_app_xor, line 109
					} // library marker tpLink.tpl_app_xor, line 110
				} // library marker tpLink.tpl_app_xor, line 111
			} else { // library marker tpLink.tpl_app_xor, line 112
				if(cmdResp.led_off) { device << [hasLed: true] } // library marker tpLink.tpl_app_xor, line 113
				if (cmdResp.feature == "TIM:ENE") { device << [isEm : true] } // library marker tpLink.tpl_app_xor, line 114
				if (cmdResp.brightness) { // library marker tpLink.tpl_app_xor, line 115
					type = "Dimming Switch" // library marker tpLink.tpl_app_xor, line 116
				} // library marker tpLink.tpl_app_xor, line 117
				device << [type: type, alias: cmdResp.alias, deviceId: cmdResp.deviceId] // library marker tpLink.tpl_app_xor, line 118
				addDeviceData(dni, device) // library marker tpLink.tpl_app_xor, line 119
			} // library marker tpLink.tpl_app_xor, line 120
			break // library marker tpLink.tpl_app_xor, line 121
		case "IOT.SMARTBULB": // library marker tpLink.tpl_app_xor, line 122
			def descr = cmdResp.description // library marker tpLink.tpl_app_xor, line 123
			def isCt = cmdResp.is_variable_color_temp // library marker tpLink.tpl_app_xor, line 124
			def isColor = cmdResp.is_color // library marker tpLink.tpl_app_xor, line 125
			if (descr.contains("Light Strip")) { // library marker tpLink.tpl_app_xor, line 126
				type = "Light Strip" // library marker tpLink.tpl_app_xor, line 127
			} else { // library marker tpLink.tpl_app_xor, line 128
				if (isCt == 0 && isColor == 0) { // library marker tpLink.tpl_app_xor, line 129
					type = "Mono Bulb" // library marker tpLink.tpl_app_xor, line 130
				} else if (isCt == 1 && isColor == 0) { // library marker tpLink.tpl_app_xor, line 131
						device << [ctLow: 2700, ctHigh: 6500] // library marker tpLink.tpl_app_xor, line 132
					type = "CT Bulb" // library marker tpLink.tpl_app_xor, line 133
				} else if (isCt ==1 && isColor == 1) { // library marker tpLink.tpl_app_xor, line 134
					device << [ctLow: 2700, ctHigh: 6500] // library marker tpLink.tpl_app_xor, line 135
					type = "Color Bulb" // library marker tpLink.tpl_app_xor, line 136
				} // library marker tpLink.tpl_app_xor, line 137
			} // library marker tpLink.tpl_app_xor, line 138
			device << [isEm: true, type: type, alias: cmdResp.alias,  // library marker tpLink.tpl_app_xor, line 139
					   deviceId: cmdResp.deviceId] // library marker tpLink.tpl_app_xor, line 140
				addDeviceData(dni, device) // library marker tpLink.tpl_app_xor, line 141
			break // library marker tpLink.tpl_app_xor, line 142
		default:  // library marker tpLink.tpl_app_xor, line 143
			atomicState.deviceWait = false  // library marker tpLink.tpl_app_xor, line 144
			logData << [ "${cmdResp.alias}": "kasaType ${kasaType} is not supported" ]  // library marker tpLink.tpl_app_xor, line 145
			logDebug(logData) // library marker tpLink.tpl_app_xor, line 146
			break // library marker tpLink.tpl_app_xor, line 147
	} // library marker tpLink.tpl_app_xor, line 148
	logDebug(logData) // library marker tpLink.tpl_app_xor, line 149
} // library marker tpLink.tpl_app_xor, line 150

void quickFindXorData(response) { // library marker tpLink.tpl_app_xor, line 152
	unschedule("udpTimeout") // library marker tpLink.tpl_app_xor, line 153
	atomicState.findingXor = false // library marker tpLink.tpl_app_xor, line 154
	Map logData = [method: "quickFindXorData"] // library marker tpLink.tpl_app_xor, line 155
	if (response == "udpTimeout") { // library marker tpLink.tpl_app_xor, line 156
		logData << [response: "udpTimeout", status: "No devices found"] // library marker tpLink.tpl_app_xor, line 157
	} else { // library marker tpLink.tpl_app_xor, line 158
		def foundDevs = 0 // library marker tpLink.tpl_app_xor, line 159
		if (response instanceof Map) { // library marker tpLink.tpl_app_xor, line 160
			foundDevs += 1 // library marker tpLink.tpl_app_xor, line 161
			def respData = parseUdp(response, "xor") // library marker tpLink.tpl_app_xor, line 162
			updateXorChild(respData) // library marker tpLink.tpl_app_xor, line 163
		} else { // library marker tpLink.tpl_app_xor, line 164
   			 response.each { // library marker tpLink.tpl_app_xor, line 165
        		foundDevs += 1 // library marker tpLink.tpl_app_xor, line 166
        		Map respData = parseUdp(it, "xor") // library marker tpLink.tpl_app_xor, line 167
        		updateXorChild(respData) // library marker tpLink.tpl_app_xor, line 168
    		} // library marker tpLink.tpl_app_xor, line 169
		} // library marker tpLink.tpl_app_xor, line 170
		logData << [devicesFound: foundDevs, status: "allSentToParse"] // library marker tpLink.tpl_app_xor, line 171
	} // library marker tpLink.tpl_app_xor, line 172
	logInfo(logData)	 // library marker tpLink.tpl_app_xor, line 173
} // library marker tpLink.tpl_app_xor, line 174

void updateXorChild(respData) { // library marker tpLink.tpl_app_xor, line 176
	if (respData.status == "ok") { // library marker tpLink.tpl_app_xor, line 177
		Map sysInfo = respData?.cmdResp?.system?.get_sysinfo // library marker tpLink.tpl_app_xor, line 178
		if (!sysInfo) { // library marker tpLink.tpl_app_xor, line 179
			logDebug("updateXorChild: [status: missingSysInfo]") // library marker tpLink.tpl_app_xor, line 180
			return // library marker tpLink.tpl_app_xor, line 181
		} // library marker tpLink.tpl_app_xor, line 182
		String dni = sysInfo.mic_mac // library marker tpLink.tpl_app_xor, line 183
		if (sysInfo.mac) { dni = sysInfo.mac.replace(/:/, "") } // library marker tpLink.tpl_app_xor, line 184
		String baseUrl = "${respData.ip}:${respData.port}" // library marker tpLink.tpl_app_xor, line 185
		Map devData = [protocol: "xor", baseUrl: baseUrl, version: version()] // library marker tpLink.tpl_app_xor, line 186
		def child = getChildDevice(dni) // library marker tpLink.tpl_app_xor, line 187
		if (child) { child.updateDeviceData(devData) } // library marker tpLink.tpl_app_xor, line 188
	} else { // library marker tpLink.tpl_app_xor, line 189
		logDebug("updateXorChild: [error: not LAN_TYPE_UDPCLIENT]") // library marker tpLink.tpl_app_xor, line 190
	} // library marker tpLink.tpl_app_xor, line 191
} // library marker tpLink.tpl_app_xor, line 192

// ~~~~~ end include (1039) tpLink.tpl_app_xor ~~~~~

// ~~~~~ start include (1040) tpLink.tpl_app_secure ~~~~~
library (  // library marker tpLink.tpl_app_secure, line 1
	name: "tpl_app_secure", // library marker tpLink.tpl_app_secure, line 2
	namespace: "tpLink", // library marker tpLink.tpl_app_secure, line 3
	author: "Dave Gutheinz", // library marker tpLink.tpl_app_secure, line 4
	description: "Smart Protocol (KLAP/AES) metnods for Kasa/Tapo application. Hub V 2.5.3", // library marker tpLink.tpl_app_secure, line 5
	category: "utilities", // library marker tpLink.tpl_app_secure, line 6
	documentationLink: "" // library marker tpLink.tpl_app_secure, line 7
) // library marker tpLink.tpl_app_secure, line 8

void getSecureLanData(response) { // library marker tpLink.tpl_app_secure, line 10
	unschedule("udpTimeout") // library marker tpLink.tpl_app_secure, line 11
	Map logData = [method: "getSecureLanData"] // library marker tpLink.tpl_app_secure, line 12
	if (response == "udpTimeout") { // library marker tpLink.tpl_app_secure, line 13
		logData << [response: "udpTimeout", warning: "No devices found"] // library marker tpLink.tpl_app_secure, line 14
	} else { // library marker tpLink.tpl_app_secure, line 15
		logData << [responses: response.size()] // library marker tpLink.tpl_app_secure, line 16
		if (response instanceof Map) { // library marker tpLink.tpl_app_secure, line 17
    		atomicState.deviceWait = true // library marker tpLink.tpl_app_secure, line 18
    		getSecureDiscData(response) // library marker tpLink.tpl_app_secure, line 19
    		def wait = waitForDevData(5) // library marker tpLink.tpl_app_secure, line 20
		} else { // library marker tpLink.tpl_app_secure, line 21
			response.each { // library marker tpLink.tpl_app_secure, line 22
				atomicState.deviceWait = true // library marker tpLink.tpl_app_secure, line 23
				getSecureDiscData(it) // library marker tpLink.tpl_app_secure, line 24
				def wait = waitForDevData(5) // library marker tpLink.tpl_app_secure, line 25
			} // library marker tpLink.tpl_app_secure, line 26
		} // library marker tpLink.tpl_app_secure, line 27
	} // library marker tpLink.tpl_app_secure, line 28
	atomicState.findingSecure = false // library marker tpLink.tpl_app_secure, line 29
	logInfo(logData) // library marker tpLink.tpl_app_secure, line 30
} // library marker tpLink.tpl_app_secure, line 31

void getSecureDiscData(response) { // library marker tpLink.tpl_app_secure, line 33
	Map discData = parseUdp(response, "secure") // library marker tpLink.tpl_app_secure, line 34
	if (discData.status == "ok") { // library marker tpLink.tpl_app_secure, line 35
		Map payload = discData?.cmdResp?.result // library marker tpLink.tpl_app_secure, line 36
		Map encryptScheme = payload?.mgt_encrypt_schm // library marker tpLink.tpl_app_secure, line 37
		if (!payload?.mac || !payload?.device_type  // library marker tpLink.tpl_app_secure, line 38
			|| !encryptScheme?.encrypt_type || !encryptScheme?.http_port) { // library marker tpLink.tpl_app_secure, line 39
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 40
			logDebug("getSecureDiscData: " +  // library marker tpLink.tpl_app_secure, line 41
				"[status: invalidDiscoveryPayload, " +  // library marker tpLink.tpl_app_secure, line 42
				"ip: ${discData?.ip}, payload: ${payload}]") // library marker tpLink.tpl_app_secure, line 43
			return // library marker tpLink.tpl_app_secure, line 44
		} // library marker tpLink.tpl_app_secure, line 45
		String model = payload.device_model // library marker tpLink.tpl_app_secure, line 46
		String protocol = payload.mgt_encrypt_schm.encrypt_type.toLowerCase() // library marker tpLink.tpl_app_secure, line 47
		//	Handle prefer_tpap = true by hard coding tpap to klap. // library marker tpLink.tpl_app_secure, line 48
		if (protocol == "tpap") { protocol = "klap" } // library marker tpLink.tpl_app_secure, line 49
		String devType = payload.device_type // library marker tpLink.tpl_app_secure, line 50
		String dni = payload.mac.replaceAll("-", "") // library marker tpLink.tpl_app_secure, line 51
		Map logData =[method: "getSecureDiscData", devType: devType, model: model,  // library marker tpLink.tpl_app_secure, line 52
					  dni: dni, protocol: protocol] // library marker tpLink.tpl_app_secure, line 53
		List supTypes = ["SMART.TAPOBULB", "SMART.TAPOPLUG", "SMART.TAPOSWITCH", // library marker tpLink.tpl_app_secure, line 54
					 	"SMART.KASAPLUG", "SMART.KASASWITCH", "SMART.KASABULB", // library marker tpLink.tpl_app_secure, line 55
						 "IOT.SMARTPLUGSWITCH", "IOT.SMARTBULB"] // library marker tpLink.tpl_app_secure, line 56
		if (!supTypes.contains(devType)) { // library marker tpLink.tpl_app_secure, line 57
			logData << [status: "Ignored. Unsupported device."] // library marker tpLink.tpl_app_secure, line 58
			logInfo(logData) // library marker tpLink.tpl_app_secure, line 59
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 60
		} else if (atomicState.iotDevices?.containsKey(dni)  // library marker tpLink.tpl_app_secure, line 61
				   && getChildDevice(dni)) { // library marker tpLink.tpl_app_secure, line 62
			//	Design to preclude unnecessary processing and also device protocol // library marker tpLink.tpl_app_secure, line 63
			//	ping-pong. For Smart devices, if it has already been found on this  // library marker tpLink.tpl_app_secure, line 64
			//	application session, do not add. // library marker tpLink.tpl_app_secure, line 65
			logData << [status: "Ignored! Found in iotDevices and already installed."] // library marker tpLink.tpl_app_secure, line 66
			logInfo(logData) // library marker tpLink.tpl_app_secure, line 67
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 68
		} else if (atomicState.smartDevices?.containsKey(dni)) { // library marker tpLink.tpl_app_secure, line 69
			logData << [status: "Ignored. Aready in state.smartDevices."] // library marker tpLink.tpl_app_secure, line 70
			logInfo(logData) // library marker tpLink.tpl_app_secure, line 71
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 72
		} else { // library marker tpLink.tpl_app_secure, line 73
			Map devData = [:] // library marker tpLink.tpl_app_secure, line 74
			def api = "smart" // library marker tpLink.tpl_app_secure, line 75
			if (devType.contains("IOT")) { api = "iot" } // library marker tpLink.tpl_app_secure, line 76
			String httpPort = payload.mgt_encrypt_schm.http_port // library marker tpLink.tpl_app_secure, line 77
			String httpStr = "http://" // library marker tpLink.tpl_app_secure, line 78
			String httpPath = "/app" // library marker tpLink.tpl_app_secure, line 79
			if (payload.mgt_encrypt_schm.is_support_https) { httpStr = "https://" } // library marker tpLink.tpl_app_secure, line 80
			String baseUrl = httpStr + discData.ip + ":" + httpPort + httpPath // library marker tpLink.tpl_app_secure, line 81
			devData << [type: devType, model: model, baseUrl: baseUrl, dni: dni, // library marker tpLink.tpl_app_secure, line 82
						api: api, protocol: protocol] // library marker tpLink.tpl_app_secure, line 83
			if (devData.protocol == "klap") { // library marker tpLink.tpl_app_secure, line 84
				klapHandshake(baseUrl, localHash, devData) // library marker tpLink.tpl_app_secure, line 85
			} else if (devData.protocol == "aes") { // library marker tpLink.tpl_app_secure, line 86
				aesHandshake(baseUrl, devData) // library marker tpLink.tpl_app_secure, line 87
			} else { // library marker tpLink.tpl_app_secure, line 88
				atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 89
				logData << [status: "unsupportedProtocol: ${devData.protocol}"] // library marker tpLink.tpl_app_secure, line 90
				logInfo(logData) // library marker tpLink.tpl_app_secure, line 91
			} // library marker tpLink.tpl_app_secure, line 92
		} // library marker tpLink.tpl_app_secure, line 93
	} else { // library marker tpLink.tpl_app_secure, line 94
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 95
		Map logData = [method: "getSecureDiscData", status: "errorInParseUDP", discData: discData] // library marker tpLink.tpl_app_secure, line 96
		logDebug(logData) // library marker tpLink.tpl_app_secure, line 97
	} // library marker tpLink.tpl_app_secure, line 98
} // library marker tpLink.tpl_app_secure, line 99

void getSmartDeviceData(devData, cmdData) { // library marker tpLink.tpl_app_secure, line 101
	Map logData = [method: "getSmartDeviceData"] // library marker tpLink.tpl_app_secure, line 102
	Map device = [api: "smart", protocol: devData.protocol, baseUrl: """${devData.baseUrl}""", // library marker tpLink.tpl_app_secure, line 103
				  model: devData.model] // library marker tpLink.tpl_app_secure, line 104
	Map componentResponse = cmdData?.find { it.method == "component_nego" } // library marker tpLink.tpl_app_secure, line 105
	Map deviceInfoResponse = cmdData?.find { it.method == "get_device_info" } // library marker tpLink.tpl_app_secure, line 106
	List comps = componentResponse?.result?.component_list ?: [] // library marker tpLink.tpl_app_secure, line 107
	Map cmdResp = deviceInfoResponse?.result // library marker tpLink.tpl_app_secure, line 108
	if (!cmdResp) { // library marker tpLink.tpl_app_secure, line 109
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 110
		logDebug("getSmartDeviceData: [status: missingDeviceInfo]") // library marker tpLink.tpl_app_secure, line 111
		return // library marker tpLink.tpl_app_secure, line 112
	} // library marker tpLink.tpl_app_secure, line 113

	def dni = devData.dni // library marker tpLink.tpl_app_secure, line 115
	byte[] aliasBytes = cmdResp.nickname.decodeBase64() // library marker tpLink.tpl_app_secure, line 116
	String alias = new String(aliasBytes) // library marker tpLink.tpl_app_secure, line 117
	if (!(instMatter ?: false) && comps.find {it.id == "matter"}) { // library marker tpLink.tpl_app_secure, line 118
		logData << ["${alias}": device, status: "Not added. Matter Install Not Selected"] // library marker tpLink.tpl_app_secure, line 119
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 120
	} else { // library marker tpLink.tpl_app_secure, line 121
		def tpType = devData.type // library marker tpLink.tpl_app_secure, line 122
		if (alias == "") { alias = devData.model } // library marker tpLink.tpl_app_secure, line 123
		String manufacturer = "tapo" // library marker tpLink.tpl_app_secure, line 124
		if (tpType.contains("KASA")) { manufacturer = "kasa" } // library marker tpLink.tpl_app_secure, line 125
		device << [alias: alias, manufacturer: manufacturer, deviceId: cmdResp.device_id] // library marker tpLink.tpl_app_secure, line 126
		def type = "unknown" // library marker tpLink.tpl_app_secure, line 127
		if (tpType.contains("PLUG") || tpType.contains("SWITCH")) { // library marker tpLink.tpl_app_secure, line 128
			type = "Plug Switch" // library marker tpLink.tpl_app_secure, line 129
			if (comps.find { it.id == "control_child" }) { // library marker tpLink.tpl_app_secure, line 130
				type = "Parent" // library marker tpLink.tpl_app_secure, line 131
			} else if (comps.find{it.id=="dimmer"} || comps.find{it.id=="brightness"}) { // library marker tpLink.tpl_app_secure, line 132
				type = "Dimming Switch" // library marker tpLink.tpl_app_secure, line 133
			} // library marker tpLink.tpl_app_secure, line 134
		} else if (tpType.contains("BULB")) { // library marker tpLink.tpl_app_secure, line 135
			type = "Mono Bulb" // library marker tpLink.tpl_app_secure, line 136
			if (comps.find { it.id == "light_strip" }) { // library marker tpLink.tpl_app_secure, line 137
				type = "Light Strip" // library marker tpLink.tpl_app_secure, line 138
			} else if (comps.find { it.id == "color" }) { // library marker tpLink.tpl_app_secure, line 139
				type = "Color Bulb" // library marker tpLink.tpl_app_secure, line 140
			} else if (comps.find { it.id == "color_temperature" } ) { // library marker tpLink.tpl_app_secure, line 141
				type = "CT Bulb" // library marker tpLink.tpl_app_secure, line 142
			} // library marker tpLink.tpl_app_secure, line 143

			if (comps.find { it.id == "color_temperature" } ) { // library marker tpLink.tpl_app_secure, line 145
				def ctHigh = 6500 // library marker tpLink.tpl_app_secure, line 146
				def ctLow = 2700 // library marker tpLink.tpl_app_secure, line 147
				if (cmdResp.color_temp_range) { // library marker tpLink.tpl_app_secure, line 148
					ctHigh = cmdResp.color_temp_range[1] // library marker tpLink.tpl_app_secure, line 149
					ctLow = cmdResp.color_temp_range[0] // library marker tpLink.tpl_app_secure, line 150
				} // library marker tpLink.tpl_app_secure, line 151
				device << [ctHigh: ctHigh, ctLow: ctLow] // library marker tpLink.tpl_app_secure, line 152
			} // library marker tpLink.tpl_app_secure, line 153
		} // library marker tpLink.tpl_app_secure, line 154
		device << [type: type] // library marker tpLink.tpl_app_secure, line 155
		if (comps.find {it.id == "led"}) { device << [hasLed: "true"] } // library marker tpLink.tpl_app_secure, line 156
		if (type != "Parent") { // library marker tpLink.tpl_app_secure, line 157
			if (comps.find {it.id == "energy_monitoring"}) { device << [isEm: "true"] } // library marker tpLink.tpl_app_secure, line 158
			if (comps.find {it.id == "on_off_gradually"}) { device << [gradOnOff: "true"] } // library marker tpLink.tpl_app_secure, line 159
		} // library marker tpLink.tpl_app_secure, line 160
		addDeviceData(dni, device) // library marker tpLink.tpl_app_secure, line 161
	} // library marker tpLink.tpl_app_secure, line 162
	logDebug(logData) // library marker tpLink.tpl_app_secure, line 163
} // library marker tpLink.tpl_app_secure, line 164

void quickFindSecureData(response) { // library marker tpLink.tpl_app_secure, line 166
	unschedule("udpTimeout") // library marker tpLink.tpl_app_secure, line 167
	atomicState.findingSecure = false // library marker tpLink.tpl_app_secure, line 168
	Map logData = [method: "quickFindSecureData"] // library marker tpLink.tpl_app_secure, line 169
	if (response == "udpTimeout") { // library marker tpLink.tpl_app_secure, line 170
		logData << [response: "udpTimeout", status: "No devices found"] // library marker tpLink.tpl_app_secure, line 171
	} else { // library marker tpLink.tpl_app_secure, line 172
		def foundDevs = 0 // library marker tpLink.tpl_app_secure, line 173
		if (response instanceof Map) { // library marker tpLink.tpl_app_secure, line 174
			foundDevs += 1 // library marker tpLink.tpl_app_secure, line 175
			def respData = parseUdp(response, "smart") // library marker tpLink.tpl_app_secure, line 176
			updateSecureChild(respData) // library marker tpLink.tpl_app_secure, line 177
		} else { // library marker tpLink.tpl_app_secure, line 178
			response.each { // library marker tpLink.tpl_app_secure, line 179
				foundDevs +=1 // library marker tpLink.tpl_app_secure, line 180
				def respData = parseUdp(it, "smart") // library marker tpLink.tpl_app_secure, line 181
				updateSecureChild(respData) // library marker tpLink.tpl_app_secure, line 182
			} // library marker tpLink.tpl_app_secure, line 183
		} // library marker tpLink.tpl_app_secure, line 184
		logData << [devicesFound: foundDevs, status: "allSentToParse"] // library marker tpLink.tpl_app_secure, line 185
	} // library marker tpLink.tpl_app_secure, line 186
	logInfo(logData) // library marker tpLink.tpl_app_secure, line 187
} // library marker tpLink.tpl_app_secure, line 188

void updateSecureChild(respData) { // library marker tpLink.tpl_app_secure, line 190
	if (respData.status == "ok") { // library marker tpLink.tpl_app_secure, line 191
		if (respData.status != "ok") { // library marker tpLink.tpl_app_secure, line 192
			logDebug("updateSecureChild: [error: not LAN_TYPE_UDPCLIENT]") // library marker tpLink.tpl_app_secure, line 193
			return  // library marker tpLink.tpl_app_secure, line 194
		}  // library marker tpLink.tpl_app_secure, line 195
		Map payload = respData?.cmdResp?.result  // library marker tpLink.tpl_app_secure, line 196
		Map encryptScheme = payload?.mgt_encrypt_schm  // library marker tpLink.tpl_app_secure, line 197
		if (!payload?.mac || !encryptScheme?.encrypt_type  // library marker tpLink.tpl_app_secure, line 198
			|| !encryptScheme?.http_port) {  // library marker tpLink.tpl_app_secure, line 199
			logDebug("updateSecureChild: [status: invalidDiscoveryPayload]") // library marker tpLink.tpl_app_secure, line 200
			return // library marker tpLink.tpl_app_secure, line 201
		}  // library marker tpLink.tpl_app_secure, line 202
		String protocol = encryptScheme.encrypt_type.toLowerCase()  // library marker tpLink.tpl_app_secure, line 203
		if (protocol == "tpap") { protocol = "klap" } // library marker tpLink.tpl_app_secure, line 204
		String httpPort = payload.mgt_encrypt_schm.http_port // library marker tpLink.tpl_app_secure, line 205
		String httpStr = "http://" // library marker tpLink.tpl_app_secure, line 206
		String httpPath = "/app" // library marker tpLink.tpl_app_secure, line 207
		if (payload.mgt_encrypt_schm.is_support_https) { httpStr = "https://" } // library marker tpLink.tpl_app_secure, line 208
		String baseUrl = httpStr + respData.ip + ":" + httpPort + httpPath // library marker tpLink.tpl_app_secure, line 209
		Map deviceData = [protocol: protocol, baseUrl: baseUrl] // library marker tpLink.tpl_app_secure, line 210
		String dni = payload.mac.replaceAll("-", "") // library marker tpLink.tpl_app_secure, line 211
		def child = getChildDevice(dni) // library marker tpLink.tpl_app_secure, line 212
		if (child) { child.updateDeviceData(deviceData) } // library marker tpLink.tpl_app_secure, line 213
	} else { // library marker tpLink.tpl_app_secure, line 214
		logDebug("updateSecureChild: [error: not LAN_TYPE_UDPCLIENT]") // library marker tpLink.tpl_app_secure, line 215
	} // library marker tpLink.tpl_app_secure, line 216
} // library marker tpLink.tpl_app_secure, line 217

Map getDataCmd(api) { // library marker tpLink.tpl_app_secure, line 219
	Map cmdBody = [:] // library marker tpLink.tpl_app_secure, line 220
	if (api == "iot") { // library marker tpLink.tpl_app_secure, line 221
		cmdBody = [system: [get_sysinfo:[]]] // library marker tpLink.tpl_app_secure, line 222
	} else { // library marker tpLink.tpl_app_secure, line 223
		List requests = [[method: "get_device_info"], [method: "component_nego"]] // library marker tpLink.tpl_app_secure, line 224
		cmdBody = [method: "multipleRequest", params: [requests: requests]] // library marker tpLink.tpl_app_secure, line 225
	} // library marker tpLink.tpl_app_secure, line 226
	return cmdBody // library marker tpLink.tpl_app_secure, line 227
} // library marker tpLink.tpl_app_secure, line 228

//	===== get KLAP Protocol Data ===== // library marker tpLink.tpl_app_secure, line 230
void sendKlapDataCmd(devData) { // library marker tpLink.tpl_app_secure, line 231
	Map reqParams = [timeout: 10, headers: ["Cookie": devData.cookie]] // library marker tpLink.tpl_app_secure, line 232
	def seqNo = devData.seqNo + 1 // library marker tpLink.tpl_app_secure, line 233
	String cmdBodyJson = new groovy.json.JsonBuilder(getDataCmd(devData.api)).toString() // library marker tpLink.tpl_app_secure, line 234
	Map encryptedData = klapEncrypt(cmdBodyJson.getBytes(), devData.encKey, // library marker tpLink.tpl_app_secure, line 235
									devData.encIv, devData.encSig, seqNo) // library marker tpLink.tpl_app_secure, line 236
	reqParams << [ // library marker tpLink.tpl_app_secure, line 237
		uri: "${devData.baseUrl}/request?seq=${encryptedData.seqNumber}", // library marker tpLink.tpl_app_secure, line 238
		body: encryptedData.cipherData, // library marker tpLink.tpl_app_secure, line 239
		timeout:10, // library marker tpLink.tpl_app_secure, line 240
		contentType: "application/octet-stream", // library marker tpLink.tpl_app_secure, line 241
		requestContentType: "application/octet-stream"] // library marker tpLink.tpl_app_secure, line 242
	asynchttpPost("parseKlapResp", reqParams, devData) // library marker tpLink.tpl_app_secure, line 243
} // library marker tpLink.tpl_app_secure, line 244

void parseKlapResp(resp, devData) { // library marker tpLink.tpl_app_secure, line 246
	Map logData = [method: "parseKlapResp", ip: devData.baseUrl, model: devData.model] // library marker tpLink.tpl_app_secure, line 247
	if (resp.status == 200) { // library marker tpLink.tpl_app_secure, line 248
		Map cmdResp = [:] // library marker tpLink.tpl_app_secure, line 249
		try { // library marker tpLink.tpl_app_secure, line 250
			byte[] cipherResponse = resp.data.decodeBase64()[32..-1] // library marker tpLink.tpl_app_secure, line 251
			def clearResp = klapDecrypt(cipherResponse,devData.encKey, // library marker tpLink.tpl_app_secure, line 252
									devData.encIv, devData.seqNo + 1) // library marker tpLink.tpl_app_secure, line 253
			cmdResp =  new JsonSlurper().parseText(clearResp) // library marker tpLink.tpl_app_secure, line 254
			logData << [status: "OK"] // library marker tpLink.tpl_app_secure, line 255
		} catch (err) { // library marker tpLink.tpl_app_secure, line 256
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 257
			logData << [error: err, errTxt: "error decyphering response"] // library marker tpLink.tpl_app_secure, line 258
			logDebug(logData) // library marker tpLink.tpl_app_secure, line 259
			return // library marker tpLink.tpl_app_secure, line 260
		} // library marker tpLink.tpl_app_secure, line 261
		if (cmdResp?.error_code == 0 && cmdResp?.result?.responses) { // library marker tpLink.tpl_app_secure, line 262
			getSmartDeviceData(devData, cmdResp.result.responses) // library marker tpLink.tpl_app_secure, line 263
		} else if (cmdResp?.system?.get_sysinfo?.err_code == 0) { // library marker tpLink.tpl_app_secure, line 264
			getIotDeviceData(cmdResp, devData.baseUrl, devData.protocol) // library marker tpLink.tpl_app_secure, line 265
		} else { // library marker tpLink.tpl_app_secure, line 266
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 267
			logData << [status: "errorInCmdResp", cmdResp: cmdResp] // library marker tpLink.tpl_app_secure, line 268
			logDebug(logData) // library marker tpLink.tpl_app_secure, line 269
		}		 // library marker tpLink.tpl_app_secure, line 270
	} else { // library marker tpLink.tpl_app_secure, line 271
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 272
		logData << [status: "httpFailure", data: resp.properties] // library marker tpLink.tpl_app_secure, line 273
		logDebug(logData) // library marker tpLink.tpl_app_secure, line 274
	} // library marker tpLink.tpl_app_secure, line 275
} // library marker tpLink.tpl_app_secure, line 276

//	===== get Smart AES Protocol Data ===== // library marker tpLink.tpl_app_secure, line 278
void sendAesDataCmd(Map devData) { // library marker tpLink.tpl_app_secure, line 279
	String cmd = new JsonBuilder(getDataCmd(devData.api)).toString() // library marker tpLink.tpl_app_secure, line 280
	String uri = "${devData.baseUrl}?token=${devData.token}" // library marker tpLink.tpl_app_secure, line 281
	Map reqParams = getAesParams(cmd, devData.encKey, devData.encIv, // library marker tpLink.tpl_app_secure, line 282
								 uri, devData.cookie) // library marker tpLink.tpl_app_secure, line 283
	devData.retry = false // library marker tpLink.tpl_app_secure, line 284
	asynchttpPost("parseAesResp", reqParams, devData) // library marker tpLink.tpl_app_secure, line 285
} // library marker tpLink.tpl_app_secure, line 286

void parseAesResp(resp, devData) { // library marker tpLink.tpl_app_secure, line 288
	Map logData = [method: "parseAesResp", api: devData.api, model: devData.model] // library marker tpLink.tpl_app_secure, line 289
	if (resp.status != 200 || !resp?.json?.result?.response) { // library marker tpLink.tpl_app_secure, line 290
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 291
		logData << [status: "httpFailure", data: resp.properties] // library marker tpLink.tpl_app_secure, line 292
		logDebug(logData) // library marker tpLink.tpl_app_secure, line 293
		return // library marker tpLink.tpl_app_secure, line 294
	} // library marker tpLink.tpl_app_secure, line 295
	try { // library marker tpLink.tpl_app_secure, line 296
		Map cmdResp = new JsonSlurper().parseText(aesDecrypt( // library marker tpLink.tpl_app_secure, line 297
			resp.json.result.response, devData.encKey, devData.encIv)) // library marker tpLink.tpl_app_secure, line 298
		logData << [status: "OK"] // library marker tpLink.tpl_app_secure, line 299
		if (devData.api == "smart" && cmdResp?.error_code == 0 && // library marker tpLink.tpl_app_secure, line 300
			cmdResp?.result?.responses) { // library marker tpLink.tpl_app_secure, line 301
			getSmartDeviceData(devData, cmdResp.result.responses) // library marker tpLink.tpl_app_secure, line 302
		} else if (devData.api == "iot" && // library marker tpLink.tpl_app_secure, line 303
				   cmdResp?.system?.get_sysinfo?.err_code == 0) { // library marker tpLink.tpl_app_secure, line 304
			getIotDeviceData(cmdResp, devData.baseUrl, devData.protocol) // library marker tpLink.tpl_app_secure, line 305
		} else { // library marker tpLink.tpl_app_secure, line 306
			atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 307
			logData << [ status: "errorInCmdResp", cmdResp: cmdResp] // library marker tpLink.tpl_app_secure, line 308
            logDebug(logData) // library marker tpLink.tpl_app_secure, line 309
		} // library marker tpLink.tpl_app_secure, line 310
	} catch (err) { // library marker tpLink.tpl_app_secure, line 311
		atomicState.deviceWait = false // library marker tpLink.tpl_app_secure, line 312
		logData << [status: "deviceDataParseError", error: err] // library marker tpLink.tpl_app_secure, line 313
		logDebug(logData) // library marker tpLink.tpl_app_secure, line 314
	} // library marker tpLink.tpl_app_secure, line 315
} // library marker tpLink.tpl_app_secure, line 316

// ~~~~~ end include (1040) tpLink.tpl_app_secure ~~~~~

// ~~~~~ start include (1056) tpLink.tpl_prot_klap ~~~~~
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

// ~~~~~ end include (1056) tpLink.tpl_prot_klap ~~~~~

// ~~~~~ start include (1055) tpLink.tpl_prot_aes ~~~~~
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

// ~~~~~ end include (1055) tpLink.tpl_prot_aes ~~~~~

// ~~~~~ start include (1057) tpLink.tpl_prot_xor_udp ~~~~~
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

// ~~~~~ end include (1057) tpLink.tpl_prot_xor_udp ~~~~~

// ~~~~~ start include (1059) tpLink.tpl_id_logs ~~~~~
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

// ~~~~~ end include (1059) tpLink.tpl_id_logs ~~~~~

// ~~~~~ start include (1060) tpLink.tpl_encode ~~~~~
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

// ~~~~~ end include (1060) tpLink.tpl_encode ~~~~~
