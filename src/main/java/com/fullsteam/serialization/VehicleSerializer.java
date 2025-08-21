package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.io.IOException;

public class VehicleSerializer extends AbstractSerializer<Vehicle> {

    @Override
    public void serializeFields(Vehicle vehicle, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("vehicleName", vehicle.getVehicleName());
        gen.writeStringField("vehicleType", vehicle.getVehicleType().name());
        gen.writeNumberField("x", vehicle.position().x());
        gen.writeNumberField("y", vehicle.position().y());
        gen.writeNumberField("angle", vehicle.getAngle());
        gen.writeNumberField("hp", vehicle.getHp());
        gen.writeNumberField("maxHp", vehicle.getMaxHp());
        gen.writeNumberField("radius", vehicle.getBoundingRadius());
        gen.writeArrayFieldStart("vertices");
        for (Vector2D vertex : vehicle.vertices()) {
            serializers.defaultSerializeValue(vertex, gen);
        }
        gen.writeEndArray();

        gen.writeBooleanField("destroyed", vehicle.isDestroyed());

        // Driver and passenger info
        if (vehicle.getDriverId() != null) {
            gen.writeNumberField("driverId", vehicle.getDriverId());
        }

        gen.writeArrayFieldStart("passengerIds");
        for (Long passengerId : vehicle.getPassengerIds()) {
            gen.writeNumber(passengerId);
        }
        gen.writeEndArray();

        // Mounted weapons information
//        gen.writeArrayFieldStart("mountedWeapons");
//        for (Vehicle.MountedWeapon mountedWeapon : vehicle.getMountedWeapons()) {
//            gen.writeStartObject();
//
//            gen.writeFieldName("weapon");
//            serializers.defaultSerializeValue(mountedWeapon.getWeapon(), gen);
//
//            gen.writeNumberField("mountAngleOffset", mountedWeapon.getMountAngleOffset());
//            gen.writeNumberField("currentAmmo", mountedWeapon.getCurrentAmmo());
//            gen.writeBooleanField("reloading", mountedWeapon.isReloading());
//
//            if (mountedWeapon.getControllerId() != null) {
//                gen.writeNumberField("controllerId", mountedWeapon.getControllerId());
//            }
//
//            gen.writeEndObject();
//        }
//        gen.writeEndArray();

        gen.writeNumberField("maxPassengers", vehicle.getMaxPassengers());
    }
}
