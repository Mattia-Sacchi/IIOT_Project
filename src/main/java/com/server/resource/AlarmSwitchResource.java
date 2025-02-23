package com.server.resource;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

import com.server.manager.CoapDataManagerProcess;
import com.server.objects.AlarmSwitch;
import com.utils.ResourceTypes;

public class AlarmSwitchResource extends StandardCoapResource {
    private static final String OBJECT_TITLE = "AlarmSwitch";
    private static AlarmSwitch alarmSwitch;

    public AlarmSwitch getInstance() {
        return alarmSwitch;
    }

    public AlarmSwitchResource(CoapDataManagerProcess dataManager, String deviceId, ResourceTypes type) {
        super(dataManager, deviceId, type);
        getAttributes().setTitle(OBJECT_TITLE);
        alarmSwitch = new AlarmSwitch();
    }

    @Override
    public void handlePUT(CoapExchange exchange) {
        try {
            
            String payload = exchange.getRequestText();

            switch (payload) {
                case "1":
                    alarmSwitch.setState(true);
                    break;
                case "0":
                    alarmSwitch.setState(false);
                    break;
                default:
                    exchange.respond(ResponseCode.BAD_REQUEST);
                    return;    
            }

            exchange.respond(ResponseCode.CHANGED, new String(), MediaTypeRegistry.APPLICATION_JSON);
            changed();
        } catch (Exception e) {
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}
