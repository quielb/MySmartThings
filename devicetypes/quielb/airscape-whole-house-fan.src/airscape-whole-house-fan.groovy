/**
 *  AirScape Whole House Fan
 *
 *  Copyright 2017 Barry Quiel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
metadata {
	definition (name: "AirScape Whole House Fan", namespace: "quielb", author: "Barry Quiel") {
        capability "Polling"
        capability "Refresh"
		capability "Switch"
        
        attribute "insideTemp", "string"
        attribute "outsideTemp", "string"
        attribute "atticTemp", "string"
        attribute "fanSpeed", "string"
        attribute "fanCFM", "string"
        attribute "fanTimer", "string"
        attribute "doorMoving", "string"
        attribute "lastPoll", "string"
        
        command "speedUp"
        command "speedDown"
        command "timerAdd"
        command "autoControl"
        command "fanReesponse"
	}
    
    tiles(scale:2) {
    	multiAttributeTile(name:"switchControl", type: "generic", width: 6, height: 4, canChangeIcon: false) {
        	tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
        		attributeState( "off", label: '${name}', action: "switch.on", icon: "st.Appliances.appliances11", backgroundColor: "#ffffff", nextState: "doorMove" )
            	attributeState( "on", label: '${name}', action: "switch.off", icon: "st.Appliances.appliances11", backgroundColor: "#79b821",  )
                attributeState( "doorMove", label: 'Doors in Motion', action: "switch.off", icon: "st.Appliances.appliances11", backgroundColor: "#00a0dc", nextState: "doorMove" )
            }
            tileAttribute("device.fanSpeed", key: "VALUE_CONTROL", label: 'Speed Adjust') {
     			attributeState( "VALUE_UP", action: "speedUp")
        		attributeState( "VALUE_DOWN", action: "speedDown")
    		}
            tileAttribute("device.fanCFM", key: "SECONDARY_CONTROL") {
            	attributeState("default", label: '${currentValue} CFM')
            }
        }
        
        standardTile("timerControl", "device.fanTimer", width: 3, height: 3, canChangeIcon: false, decoration: "flat") {
        	state "default", label: '${currentValue}', action: "timerAdd", icon: "st.Health & Wellness.health7"
        }
        standardTile("refreshControl", "device.refresh", width: 6, height: 1, canChangeIcon: false, decoration: "flat") {
        	state "default", label: 'refresh', action: "refresh.refresh", icon: 'st.secondary.refresh-icon'
        }
        valueTile("outsideTemp", "device.outsideTemp", width: 3, height: 1, decoration: "flat") {
        	state "outsideTemp", label:'${currentValue}° outside'
        }
        valueTile("insideTemp", "device.insideTemp", width: 3, height: 1, decoration: "flat") {
        	state "insideTemp", label:'${currentValue}° inside'
        }
        valueTile("atticTemp", "device.atticTemp", width: 3, height: 1, decoration: "flat") {
        	state "atticTemp", label:'${currentValue}° attic'
        }
        
    
    main("switchControl")
    details(["switchControl", "timerControl", "outsideTemp", "insideTemp", "atticTemp", "refreshControl"])
    }
    
    preferences {
    	input name: "ip", type: "text", title: "IP Address", description: "IP Address of WHF", required: true, displayDuringSetup: true 
        input name: "debugOutput", type: "bool", defaultValue: false, title: "Enable Debug Logging", displayDuringSetup: true
    }
}


// parse events into attributes
def parse(String description) {
	if( device.preferences.debugOutput ) log.debug "Parsing '${description}'"
    
    def msg = parseLanMessage(description)
    
    def valuesMap = parseBodyAsMap(msg.body)
	if( device.preferences.debugOutput ) log.debug "Values Map ${valuesMap}"
    
    if(valuesMap["doorMoving"].toInteger() != 0) {
    	runIn(10, refresh())
        return
    }
    
    if(valuesMap["fanSpeed"].toInteger() == 0) {
        sendEvent(name: "switch", value: "off")
    }
    else if( valuesMap["fanSpeed"].toInteger() > 0 ) {
       	sendEvent(name: "switch", value: "on")
    }
    
    valuesMap.each { k, v ->
    	if( device.preferences.debugOutput ) log.debug "sending event for ${k} setting to ${v}"
        sendEvent(name: "${k}", value: "${v}", displayed: false)
    }
    
    
    def sensorMap = ["inside": null, "outside": null, "attic": null]
    def tempSensors = getChildDevices()
    tempSensors.findAll().each { tempSensor ->
      	sensorMap.each { key,value ->
           	if( tempSensor.label =~ /${key}/ ) {
            	sensorMap["${key}"] = tempSensor 
            }
        }
    }
    
    sensorMap.each { key, value ->
      	if( value == null ) {
        	try {
        		sensorMap["${key}"] = 
            		addChildDevice("smartthings", "Temperature Sensor", "${device.deviceNetworkId}-${key}", location.hubs[0].getId(),
                    	[label: "WHF ${key} Temperature", 
                        	isComponent: true,
                            componentLabel : "WHF ${key} Temperature",
                            componentName: "${device.deviceNetworkId}-${key}",
                            completedSetup: true,
                        ]
                    )
            }
            catch(e) {
        		log.debug "Failed to add ${key} temp sensor ${e}"
        	}
        }
        
        sensorMap["${key}"].sendEvent(name: "temperature", value: valuesMap["${key}Temp"], displayed: false)
    }
}

// handle commands
def poll() {
	if( device.preferences.debugOutput ) log.debug "Executing 'poll'"
    def now=new Date()
	def nowString = now.format("MMM/dd HH:mm", location.timeZone)
	if( device.preferences.debugOutput ) sendEvent("name":"lastPoll", "value":nowString, descriptionText: "WHF has executed a poll")
    sendHubCommand(talkToWHF(""))	
}

def refresh() {
	if( device.preferences.debugOutput ) log.debug "Executing 'refresh'"
    sendHubCommand(talkToWHF(""))
}

def on() {
	if( device.preferences.debugOutput ) log.debug "Executing 'on'"
    sendHubCommand(talkToWHF("on"))
}

def off() {
	if( device.preferences.debugOutput ) log.debug "Executing 'off'"
     sendHubCommand(talkToWHF("off"))
}

def speedUp() {
	if( device.preferences.debugOutput ) log.debug "Executing 'speedUp'"
	sendHubCommand(talkToWHF("speedUp"))
}

def speedDown() {
	if( device.preferences.debugOutput ) log.debug "Executing 'speedDown'"
	sendHubCommand(talkToWHF("speedDown"))
}

def timerAdd() {
	if( device.preferences.debugOutput ) log.debug "Executing 'timerAdd'"
    sendHubCommand(talkToWHF("timerAdd"))
}

private talkToWHF(fanAction) {
    def params
    if( device.preferences.debugOutput ) log.debug "Figuring out what to say to WHF"
    switch (fanAction) {
    	case ~/on|speedUp/:
        	params = [dir: "1"]
            break
        case 'off':
            params = [dir: "4"]
            break
        case 'speedDown':
        	params = [dir: "3"]
            break
        case 'timerAdd':
        	params = [dir: "2"]
            break
    }
    
	try {
    	def fanCall = new physicalgraph.device.HubAction(
        	[HOST: "$device.preferences.ip:80",
    		method: "GET",
        	path: "/fanspd.cgi",
        	query: params],
            device.deviceNetworkId,
            //[callback: fanResponse]
        )
        if( device.preferences.debugOutput ) log.debug "Giving back HubAction $fanCall"
        return fanCall
	}
    catch (e) {
    	log.notice "Shit broke $e on $fanCall"
    }
}

def fanResponse(response) {
	log.debug "Executing Call back"
}

private parseBodyAsMap(msgBody) {
    def acceptedKey = [
    	"fanspd": "fanSpeed",
        "timeremaining": "fanTimer",
        "cfm": "fanCFM",
        "house_temp": "insideTemp",
        "oa_temp": "outsideTemp",
        "attic_temp": "atticTemp",
        "doorinprocess": "doorMoving"
    ]
    
    def fanData = [:]
	msgBody.eachLine { param ->
        def dataSet = param.split('<')[1].split('>')
        if(acceptedKey.containsKey(dataSet[0])) {
            fanData[(acceptedKey[dataSet[0]])] = dataSet[1]
        }
    }
    return fanData
}