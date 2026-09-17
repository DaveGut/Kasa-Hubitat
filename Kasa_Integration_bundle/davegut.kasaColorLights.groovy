library (
	name: "kasaColorLights",
	namespace: "davegut",
	author: "Dave Gutheinz",
	description: "Kasa Color/CT Bulb and Light Common Methods",
	category: "utilities",
	documentationLink: ""
)

def setCircadian() {
	sendCmd("""{"${service()}":{"${method()}":{"mode":"circadian"}}}""")
}

def setHue(hue) { setColor([hue: hue]) }

def setSaturation(saturation) { setColor([saturation: saturation]) }

def setColor(Map color, transTime = transition_Time) {
	if (color == null) {
		LogWarn("setColor: Color map is null. Command not executed.")
	} else {
		def level = device.currentValue("level")
		if (color.level) { level = color.level }
		def hue = device.currentValue("hue")
		if (color.hue || color.hue == 0) { hue = color.hue.toInteger() }
		def saturation = device.currentValue("saturation")
		if (color.saturation || color.saturation == 0) { saturation = color.saturation }
		hue = Math.round(0.49 + hue * 3.6).toInteger()
		if (hue < 0 || hue > 360 || saturation < 0 || saturation > 100 || level < 0 || level > 100) {
			logWarn("setColor: Entered hue, saturation, or level out of range! (H:${hue}, S:${saturation}, L:${level}")
 		} else {
			setLightColor(level, 0, hue, saturation, transTime)
		}
	}
}

def setRGB(rgb) {
	logDebug("setRGB: ${rgb}") 
	def rgbArray = rgb.split('\\,')
	def hsvData = hubitat.helper.ColorUtils.rgbToHSV([rgbArray[0].toInteger(), rgbArray[1].toInteger(), rgbArray[2].toInteger()])
	def hue = (0.5 + hsvData[0]).toInteger()
	def saturation = (0.5 + hsvData[1]).toInteger()
	def level = (0.5 + hsvData[2]).toInteger()
	def Map hslData = [
		hue: hue,
		saturation: saturation,
		level: level
		]
	setColor(hslData)
}

def setLightColor(level, colorTemp, hue, saturation, transTime = 0) {
	level = checkLevel(level)
	if (level == 0) {
		setLightOnOff(0, transTime)
	} else {
		transTime = checkTransTime(transTime)
		sendCmd("""{"${service()}":{"${method()}":{"ignore_default":1,"on_off":1,""" +
				""""brightness":${level},"color_temp":${colorTemp},""" +
				""""hue":${hue},"saturation":${saturation},"transition_period":${transTime}}}}""")
	}
}

def bulbPresetCreate(psName) {
	if (!state.bulbPresets) { state.bulbPresets = [:] }
	psName = psName.trim().toLowerCase()
	logDebug("bulbPresetCreate: ${psName}")
	def psData = [:]
	psData["hue"] = device.currentValue("hue")
	psData["saturation"] = device.currentValue("saturation")
	psData["level"] = device.currentValue("level")
	def colorTemp = device.currentValue("colorTemperature")
	if (colorTemp == null) { colorTemp = 0 }
	psData["colTemp"] = colorTemp
	state.bulbPresets << ["${psName}": psData]
}

def bulbPresetDelete(psName) {
	psName = psName.trim()
	logDebug("bulbPresetDelete: ${psName}")
	def presets = state.bulbPresets
	if (presets.toString().contains(psName)) {
		presets.remove(psName)
	} else {
		logWarn("bulbPresetDelete: ${psName} is not a valid name.")
	}
}

def syncBulbPresets() {
	device.updateSetting("syncBulbs", [type:"bool", value: false])
	parent.syncBulbPresets(state.bulbPresets)
	return "Syncing"
}

def updatePresets(bulbPresets) {
	logInfo("updatePresets: ${bulbPresets}")
	state.bulbPresets = bulbPresets
}

def bulbPresetSet(psName, transTime = transition_Time) {
	psName = psName.trim()
	if (state.bulbPresets."${psName}") {
		def psData = state.bulbPresets."${psName}"
		def hue = Math.round(0.49 + psData.hue.toInteger() * 3.6).toInteger()
		setLightColor(psData.level, psData.colTemp, hue, psData.saturation, transTime)
	} else {
		logWarn("bulbPresetSet: ${psName} is not a valid name.")
	}
}
