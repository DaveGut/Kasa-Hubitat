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

#include davegut.kasaCommon
#include davegut.kasaCommunications
#include davegut.Logging
#include davegut.kasaLights
#include davegut.kasaEnergyMonitor
