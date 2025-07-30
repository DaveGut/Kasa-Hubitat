# Kasa-Hubitat

This is the distribution repository for the Hubitat Built-In Kasa Integration capability and is intended primarily for the Hubitat Team.

Released for Hubitat Version 2.4.2.

Verion 2.4.2 implements the below"
==	Version 2.4.2c
Issues resolved:

a.	Comms error.  Attribute "commsError" not resetting. 

	== Resolved: Added internal UDP timeout (since Hub function appears not to work).
 
b.	Cloud control. Cloud control not working (Kasa has migrated server to Tapo server).

	== Resolved:  Removed Cloud access from integration. Modified device data update
				  triggered on Hub reboot.
      
c.	User confusion on which intergration to use given new API in some devices.

	== Resolved:  Provide list of new user API devices on Add Devices page.
 
d.	LAN Issues. Kasa devices not discovered. Usually caused by either LAN issue,
	device issues, or device busy when polling.
 
	== Resolved:  Existing try again function on discovery page. Added note to
				  exercise device before trying again.
      
Continued Issue: LAN issues due to factors outside of Hubitat implementation.

1.	User LAN topology / security isolating device from Hub.
2.	Older routers temporarily "drop" LAN devices (usually when total devices
	exceed 20 or so).
3.	Interference from other connections to physical devices. (Kasa devices
	appear to ignore incoming UDP messages when a message is being processed.
	The more connections to the device, the higher probability.

## Instructions:

Installation Instructions:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Docs/Install.pdf

Device Capabilities:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Docs/DeviceCaps.pdf

Device Preferences:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Docs/DevicePrefs.pdf

Troubleshooting Guide:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Docs/Troubleshoot.pdf
