# espHomeSendDynamicServiceRequest
Library helper function for creating dynamic ESPHome service requests

Example driver usage:

metadata {
    definition(name: 'LightCore Sample Hub', namespace: 'LightCoreSystems', author: 'David Thomas Stewart', singleThreaded: true) {
        capability 'Refresh' 
        capability 'Initialize'
         
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
    }
      
    preferences {
        input name: 'ipAddress', type: 'text', title: 'Device IP Address', required: true
        input name: 'password', type: 'text', title: 'Device Password <i>(if required)</i>', required: false
        input name: 'logEnable', type: 'bool', title: 'Enable Debug Logging', defaultValue: false
        input name: 'logTextEnable', type: 'bool', title: 'Enable descriptionText logging', defaultValue: true
    }
 
	command "sendCommand", [
	    [name: "loadId", type: "NUMBER", description: "Load ID"],
	    [name: "level",  type: "NUMBER", description: "Level (0–100)"]
	]    
}

public void initialize() { 
    openSocket()
    if (logEnable) runIn(1800, 'logsOff')
}

public void installed() { 
    log.info "${device} driver installed"
}

public void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false)
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void updated() {
    log.info "${device} driver configuration updated"
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled')
    log.info "${device} driver uninstalled"
}

def sendCommand(loadId, level) {
    log.info "entered sendCommand with load=${loadId}, level=${level}"
    if (loadId == null || level == null) {
        log.error "sendCommand called with null arguments: loadId=${loadId}, level=${level}"
        return
    }
    
    int id = ((loadId instanceof Number) ? loadId.intValue() : loadId.toString().toInteger())
    int lvl = ((level instanceof Number) ? level.intValue() : level.toString().toInteger())

    Long serviceKey = state?.myServiceKey
    if (serviceKey == null) {
        log.error "Service key is null; cannot send ExecuteServiceRequest"
        return
    }
    
    log.debug "USING SERVICE KEY=${serviceKey} (hex=${Long.toHexString(serviceKey)}) with load=${id}, level=${lvl}"

    try	
    {
        espHomeSendDynamicServiceRequest(serviceKey,
            [
                [ id:'id', type: WIRETYPE_VARINT, value: id ],
                [ id:'lvl', type: WIRETYPE_VARINT, value: lvl  ]
            ]
        )  
	} 
    catch (Exception e) 
    {
		log.error "Raw socket send failed: ${e}"
	}
}

// parse messages from ESPHome API
public void parse(Map message) 
{
    if (logEnable)
    	log.debug "Hub ${message}: type[${message.type}] platform[${message.platform}]"

    switch (message.type as String) 
    {
        case 'complete':
          if (state.services) 
        	{
              state.services.each { svc ->
				          ong rawKey = svc.key as Long
				          long unsignedKey = rawKey & 0xFFFFFFFFL

                  if (svc.objectId == "test_service") 
                  {
                      state.myServiceKey = unsignedKey
                      log.info "DISCOVERED service key for set_load_level: ${state.myServiceKey}"
                  }
              }
          }
        	else
          {
              if (logEnable) 
                  log.debug "No services discovered"
          }
          break
    }
}

// include ESPHome helper
#include esphome.espHomeApiHelper
#include esphome.espHomeSendDynamicServiceRequest



And the ESPHome yaml file:

api:
    - service: test_service
      variables:
        load_id: int    
        level: int      
      then:
        - lambda: |-
            ESP_LOGI("api", "set_load_level called with load_id=%d, level=%d", load_id, level);
