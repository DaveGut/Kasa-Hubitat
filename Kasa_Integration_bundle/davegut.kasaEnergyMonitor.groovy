library (
	name: "kasaEnergyMonitor",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Kasa Device Energy Monitor Methods",
	category: "energyMonitor",
	documentationLink: ""
)

capability "Power Meter"
capability "Energy Meter"
attribute "currMonthTotal", "number"
attribute "currMonthAvg", "number"
attribute "lastMonthTotal", "number"
attribute "lastMonthAvg", "number"

def emPrefs() {
	if (getDataValue("feature") == "TIM:ENE") {
		input ("emFunction", "bool", title: "Enable Energy Monitor",
			   defaultValue: false)
		if (emFunction) {
			input ("energyPollInt", "enum", title: "Energy Poll Interval (minutes)",
				   options: ["1 minute", "5 minutes", "30 minutes"], 
				   defaultValue: "30 minutes")
			}
		}
}

def setupEmFunction() {
	if (emFunction && device.currentValue("currMonthTotal") > 0) {
		state.getEnergy = "This Month"
		return "Continuing EM Function"
	} else if (emFunction) {
		zeroizeEnergyAttrs()
		state.response = ""
		state.getEnergy = "This Month"
		//	Run order / delay is critical for successful operation.
		getEnergyThisMonth()
		runIn(10, getEnergyLastMonth)
		return "Initialized"
	} else if (emFunction && device.currentValue("power") != null) {
		//	for power != null, EM had to be enabled at one time.  Set values to 0.
		zeroizeEnergyAttrs()
		state.remove("getEnergy")
		return "Disabled"
	} else {
		return "Not initialized"
	}
}

def scheduleEnergyAttrs() {
	schedule("10 0 0 * * ?", getEnergyThisMonth)
	schedule("15 2 0 1 * ?", getEnergyLastMonth)
	switch(energyPollInt) {
		case "1 minute":
			runEvery1Minute(getEnergyToday)
			break
		case "5 minutes":
			runEvery5Minutes(getEnergyToday)
			break
		default:
			runEvery30Minutes(getEnergyToday)
	}
}

def zeroizeEnergyAttrs() {
	sendEvent(name: "power", value: 0, unit: "W")
	sendEvent(name: "energy", value: 0, unit: "KWH")
	sendEvent(name: "currMonthTotal", value: 0, unit: "KWH")
	sendEvent(name: "currMonthAvg", value: 0, unit: "KWH")
	sendEvent(name: "lastMonthTotal", value: 0, unit: "KWH")
	sendEvent(name: "lastMonthAvg", value: 0, unit: "KWH")
}
	
def getDate() {
	def currDate = new Date()
	int year = currDate.format("yyyy").toInteger()
	int month = currDate.format("M").toInteger()
	int day = currDate.format("d").toInteger()
	return [year: year, month: month, day: day]
}

def distEmeter(emeterResp) {
	def date = getDate()
	logDebug("distEmeter: ${emeterResp}, ${date}, ${state.getEnergy}")
	def lastYear = date.year - 1
	if (emeterResp.get_realtime) {
		setPower(emeterResp.get_realtime)
	} else if (emeterResp.get_monthstat) {
		def monthList = emeterResp.get_monthstat.month_list
		if (state.getEnergy == "Today") {
			setEnergyToday(monthList, date)
		} else if (state.getEnergy == "This Month") {
			setThisMonth(monthList, date)
		} else if (state.getEnergy == "Last Month") {
			setLastMonth(monthList, date)
		} else if (monthList == []) {
			logDebug("distEmeter: monthList Empty. No data for year.")
		}
	} else {
		logWarn("distEmeter: Unhandled response = ${emeterResp}")
	}
}

def getPower() {
	if (emFunction) {
		if (device.currentValue("switch") == "on") {
			getRealtime()
		} else if (device.currentValue("power") != 0) {
			sendEvent(name: "power", value: 0, descriptionText: "Watts", unit: "W", type: "digital")
		}
	}
}

def setPower(response) {
	logDebug("setPower: ${response}")
	def power = response.power
	if (power == null) { power = response.power_mw / 1000 }
	power = (power + 0.5).toInteger()
	def curPwr = device.currentValue("power")
	def pwrChange = false
	if (curPwr != power) {
		if (curPwr == null || (curPwr == 0 && power > 0)) {
			pwrChange = true
		} else {
			def changeRatio = Math.abs((power - curPwr) / curPwr)
			if (changeRatio > 0.03) {
				pwrChange = true
			}
		}
	}
	if (pwrChange == true) {
		sendEvent(name: "power", value: power, descriptionText: "Watts", unit: "W", type: "digital")
	}
}

def getEnergyToday() {
	if (device.currentValue("switch") == "on") {
		state.getEnergy = "Today"
		def year = getDate().year
		logDebug("getEnergyToday: ${year}")
		runIn(5, getMonthstat, [data: year])
	}
}

def setEnergyToday(monthList, date) {
	logDebug("setEnergyToday: ${date}, ${monthList}")
	def data = monthList.find { it.month == date.month && it.year == date.year}
	def status = [:]
	def energy = 0
	if (data == null) {
		status << [msgError: "Return Data Null"]
	} else {
		energy = data.energy
		if (energy == null) { energy = data.energy_wh/1000 }
		energy = Math.round(100*energy)/100 - device.currentValue("currMonthTotal")
	}
	if (device.currentValue("energy") != energy) {
		sendEvent(name: "energy", value: energy, descriptionText: "KiloWatt Hours", unit: "KWH")
		status << [energy: energy]
	}
	if (status != [:]) { logInfo("setEnergyToday: ${status}") }
	if (!state.getEnergy) {
		schedule("10 0 0 * * ?", getEnergyThisMonth)
		schedule("15 2 0 1 * ?", getEnergyLastMonth)
		state.getEnergy = "This Month"
		getEnergyThisMonth()
		runIn(10, getEnergyLastMonth)
	}
}

def getEnergyThisMonth() {
	state.getEnergy = "This Month"
	def year = getDate().year
	logDebug("getEnergyThisMonth: ${year}")
	runIn(5, getMonthstat, [data: year])
}

def setThisMonth(monthList, date) {
	logDebug("setThisMonth: ${date} // ${monthList}")
	def data = monthList.find { it.month == date.month && it.year == date.year}
	def status = [:]
	def totEnergy = 0
	def avgEnergy = 0
	if (data == null) {
		status << [msgError: "Return Data Null"]
	} else {
		status << [msgError: "OK"]
		totEnergy = data.energy
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 }
		if (date.day == 1) {
			avgEnergy = 0
		} else {
			avgEnergy = totEnergy /(date.day - 1)
		}
	}
	totEnergy = Math.round(100*totEnergy)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "currMonthTotal", value: totEnergy, 
			  descriptionText: "KiloWatt Hours", unit: "KWH")
	status << [currMonthTotal: totEnergy]
	sendEvent(name: "currMonthAvg", value: avgEnergy, 
		 	 descriptionText: "KiloWatt Hours per Day", unit: "KWH")
	status << [currMonthAvg: avgEnergy]
	getEnergyToday()
	logInfo("setThisMonth: ${status}")
}

def getEnergyLastMonth() {
	state.getEnergy = "Last Month"
	def date = getDate()
	def year = date.year
	if (date.month == 1) {
		year = year - 1
	}
	logDebug("getEnergyLastMonth: ${year}")
	runIn(5, getMonthstat, [data: year])
}

def setLastMonth(monthList, date) {
	logDebug("setLastMonth: ${date} // ${monthList}")
	def lastMonthYear = date.year
	def lastMonth = date.month - 1
	if (date.month == 1) {
		lastMonthYear -+ 1
		lastMonth = 12
	}
	def data = monthList.find { it.month == lastMonth }
	def status = [:]
	def totEnergy = 0
	def avgEnergy = 0
	if (data == null) {
		status << [msgError: "Return Data Null"]
	} else {
		status << [msgError: "OK"]
		def monthLength
		switch(lastMonth) {
			case 4:
			case 6:
			case 9:
			case 11:
				monthLength = 30
				break
			case 2:
				monthLength = 28
				if (lastMonthYear == 2020 || lastMonthYear == 2024 || lastMonthYear == 2028) { 
					monthLength = 29
				}
				break
			default:
				monthLength = 31
		}
		totEnergy = data.energy
		if (totEnergy == null) { totEnergy = data.energy_wh/1000 }
		avgEnergy = totEnergy / monthLength
	}
	totEnergy = Math.round(100*totEnergy)/100
	avgEnergy = Math.round(100*avgEnergy)/100
	sendEvent(name: "lastMonthTotal", value: totEnergy, 
			  descriptionText: "KiloWatt Hours", unit: "KWH")
	status << [lastMonthTotal: totEnergy]
	sendEvent(name: "lastMonthAvg", value: avgEnergy, 
			  descriptionText: "KiloWatt Hoursper Day", unit: "KWH")
	status << [lastMonthAvg: avgEnergy]
	logInfo("setLastMonth: ${status}")
}

def getRealtime() {
	def feature = getDataValue("feature")
	if (getDataValue("plugNo") != null) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
				""""emeter":{"get_realtime":{}}}""")
	} else if (feature.contains("Bulb") || feature == "lightStrip") {
		sendCmd("""{"smartlife.iot.common.emeter":{"get_realtime":{}}}""")
	} else {
		sendCmd("""{"emeter":{"get_realtime":{}}}""")
	}
}

def getMonthstat(year) {
	def feature = getDataValue("feature")
	if (getDataValue("plugNo") != null) {
		sendCmd("""{"context":{"child_ids":["${getDataValue("plugId")}"]},""" +
				""""emeter":{"get_monthstat":{"year": ${year}}}}""")
	} else if (feature.contains("Bulb") || feature == "lightStrip") {
		sendCmd("""{"smartlife.iot.common.emeter":{"get_monthstat":{"year": ${year}}}}""")
	} else {
		sendCmd("""{"emeter":{"get_monthstat":{"year": ${year}}}}""")
	}
}
