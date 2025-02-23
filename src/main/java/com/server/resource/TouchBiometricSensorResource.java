package com.server.resource;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

import com.server.manager.CoapDataManagerProcess;
import com.server.objects.AlarmController;
import com.server.objects.AlarmSwitch;
import com.server.objects.TouchBiometricSensor;
import com.utils.Log;
import com.utils.ResourceTypes;
import com.utils.SenMLPack;
import com.utils.SenMLRecord;

public class TouchBiometricSensorResource extends StandardCoapResource {

    private static final String OBJECT_TITLE = "TouchBiometricSensor";
    TouchBiometricSensor m_sensor;
    private boolean m_stateChange = false;

    public TouchBiometricSensorResource(CoapDataManagerProcess dataManager, String deviceId,
            ResourceTypes type) {
        super(dataManager, deviceId, type);
        getAttributes().setTitle(OBJECT_TITLE);
        m_sensor = new TouchBiometricSensor();
    }

    private Optional<String> getJsonSenMlResponse(String currentFingerprint) {
        try {

            SenMLPack pack = new SenMLPack();
            SenMLRecord record = new SenMLRecord();
            record.setBn(getDeviceId());
            record.setN(getName());
            record.setVs(currentFingerprint);
            record.setT(m_sensor.getTimestamp());
            record.setU("bit");
            pack.add(record);
            return Optional.of(this.gson.toJson(pack));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    void scheduledTask()
    {
        AlarmSwitchResource alarmSwitchRes = ((AlarmSwitchResource) getInstance(ResourceTypes.RT_ALARM_SWITCH));
        AlarmSwitch alarmSwitch = alarmSwitchRes.getInstance();
        // Reset state change in order to recover state and work again
        m_stateChange = false;
        alarmSwitch.setState(true);
    }

    private boolean checkFingerprint(String fingerprint) {
        try {
            if (!m_sensor.checkBiometricData(fingerprint)) {
                return false;
            }

            // I take a alarm system resource Instance
            AlarmSwitchResource alarmSwitchRes = ((AlarmSwitchResource) getInstance(ResourceTypes.RT_ALARM_SWITCH));
            // I take a alarm siren resource Instance
            AlarmControllerResource alarmControllerRes = ((AlarmControllerResource) getInstance(
                    ResourceTypes.RT_ALARM_CONTROLLER));

            // I take a alarm system Instance
            AlarmSwitch alarmSwitch = alarmSwitchRes.getInstance();
            // I take a alarm siren Instance
            AlarmController alarmController = alarmControllerRes.getInstance();

            // I take the current state of the system
            boolean alarmSystemState = alarmSwitch.getState();
            boolean alarmSirenState = alarmController.getState();

            // Check for errors (implausibility)
            if (alarmSirenState && !alarmSystemState) {
                Log.error("Implausibility", "The system isn't armed while the siren is running?",
                        "Disarming the system");
                // Try to return to a acceptable situation
                alarmController.setState(false);
                return true;
            }

            if (alarmSirenState && alarmSystemState) {
                alarmController.setState(false);
                alarmSwitch.setState(false);
                return true;
            }

            // Ok now the cases with delay needed
            if (!alarmSystemState) {
                if(m_stateChange)
                {
                    Log.error("Already doing that","The system is already turning on");
                    return false;
                }

                Log.debug("Arming the system",
                        String.format("You have %d seconds to leave the house",
                                AlarmSwitch.EXIT_DELAY));

                m_stateChange = true;

                ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
                Runnable task = () -> scheduledTask();
                ses.schedule(task, AlarmSwitch.EXIT_DELAY, TimeUnit.SECONDS);
                ses.shutdown();
            }
            else
            {
                alarmSwitch.setState(false);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
    /*
    @Override
    public void handlePOST(CoapExchange exchange) {

        try {
            SenMLPack senMLPack = gson.fromJson(exchange.getRequestText(), SenMLPack.class);

            // First condition it needs to have at least one record
            boolean result = !senMLPack.isEmpty();
            // Check if it has already the fingerprints if so it fails
            for (SenMLRecord r : senMLPack) {
                if (!sensor.addFingerPrint(r.getVs()))
                    result = false;
            }

            if (!result)
                exchange.respond(ResponseCode.BAD_REQUEST);

            exchange.respond(ResponseCode.CREATED, new String(), MediaTypeRegistry.APPLICATION_JSON);
            changed();

        } catch (Exception e) {
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }*/

    // Just for debug
    @Override
    public void handlePUT(CoapExchange exchange) {
        try {
            SenMLPack senMLPack = gson.fromJson(exchange.getRequestText(), SenMLPack.class);

            // First condition the size must be one (One check at a time)
            if (senMLPack.size() != 1) {
                exchange.respond(ResponseCode.BAD_REQUEST);
                return;
            }

            String fingerprint = senMLPack.get(0).getVs();

            if (checkFingerprint(fingerprint)) {
                exchange.respond(ResponseCode.CHANGED, new String(), MediaTypeRegistry.APPLICATION_JSON);
                changed();
            } else
                exchange.respond(ResponseCode.UNAUTHORIZED, new String(), MediaTypeRegistry.APPLICATION_JSON);

        } catch (Exception e) {
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        try {

            String fingerprint = m_sensor.measure();

            if (!(exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_SENML_JSON
                    || exchange.getRequestOptions().getAccept() == MediaTypeRegistry.APPLICATION_JSON)) {
                exchange.respond(CoAP.ResponseCode.CONTENT, fingerprint,
                        MediaTypeRegistry.TEXT_PLAIN);
                return;
            }

            checkFingerprint(fingerprint);

            Optional<String> senMlPayload = getJsonSenMlResponse(fingerprint);
            if (senMlPayload.isPresent()) {
                exchange.respond(CoAP.ResponseCode.CONTENT, senMlPayload.get(),
                        MediaTypeRegistry.APPLICATION_SENML_JSON);
            } else {
                exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}
