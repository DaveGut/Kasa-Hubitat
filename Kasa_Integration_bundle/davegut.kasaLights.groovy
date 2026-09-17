library (
	name: "kasaLights",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Kasa Bulb and Light Common Methods",
	category: "utilities",
	documentationLink: ""
)

def on() { setLightOnOff(1, transition_Time) }

def off() { setLightOnOff(0, transition_Time) }

def setLevel(level, transTime = transition_Time) {
	setLightLevel(level, transTime)
}

def startLevelChange(direction) {
	unschedule(levelUp)
	unschedule(levelDown)
	if (direction == "up") { levelUp() }
	else { levelDown() }
}

def stopLevelChange() {
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 100) { return }
	def newLevel = curLevel + 4
	if (newLevel > 100) { newLevel = 100 }
	setLevel(newLevel, 0)
	runIn(1, levelUp)
}

def levelDown() {
	def curLevel = device.currentValue("level").toInteger()
	if (curLevel == 0 || device.currentValue("switch") == "off") { return }
	def newLevel = curLevel - 4
	if (newLevel < 0) { off() }
	else {
		setLevel(newLevel, 0)
		runIn(1, levelDown)
	}
}

def service() {
	def service = "smartlife.iot.smartbulb.lightingservice"
	if (getDataValue("feature") == "lightStrip") { service = "smartlife.iot.lightStrip" }
	return service
}

def method() {
	def method = "transition_light_state"
	if (getDataValue("feature") == "lightStrip") { method = "set_light_state" }
	return method
}

def checkTransTime(transTime) {
	if (transTime == null || transTime < 0) { transTime = 0 }
	transTime = 1000 * transTime.toInteger()
	if (transTime > 8000) { transTime = 8000 }
	return transTime
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

def setLightOnOff(onOff, transTime = 0) {
	state.eventType = "digital"
	transTime = checkTransTime(transTime)
	sendCmd("""{"${service()}":{"${method()}":{"on_off":${onOff},""" +
			""""transition_period":${transTime}}}}""")
}

def setLightLevel(level, transTime = 0) {
	state.eventType = "digital"
	level = checkLevel(level)
	if (level == 0) {
		setLightOnOff(0, transTime)
	} else {
		transTime = checkTransTime(transTime)
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" +
				""""brightness":${level},"transition_period":${transTime}}}}""")
	}
}
