package dev.betterendfield.next;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validates the runtime projection; manifests and UI drafts never assert playback state. */
final class ModelDriverControl {
    static final int MIN_SPEED = 250, MAX_SPEED = 4000, MAX_DRIVERS = 256;
    static final int MAX_JSON_CHARS = 256 * 1024;
    interface Transport {
        String read();
        boolean set(String commandId, int state, int speed);
    }
    enum Error { NONE, UNAVAILABLE, INVALID_STATUS, REJECTED, INVALID_COMMAND }
    record Driver(String id, String commandId, String name, int state, boolean once, int speed) {}
    record Snapshot(List<Driver> drivers, Error error) {
        boolean available() { return error == Error.NONE; }
    }
    record Result(Snapshot snapshot, Error error) {}

    static Snapshot read(Transport transport) {
        try { return parse(transport.read()); }
        catch (RuntimeException | LinkageError failure) { return new Snapshot(List.of(), Error.UNAVAILABLE); }
    }

    static Snapshot parse(String raw) {
        if (raw == null || raw.length() > MAX_JSON_CHARS) return new Snapshot(List.of(), Error.INVALID_STATUS);
        try {
            JSONArray values = new JSONArray(raw);
            if (values.length() > MAX_DRIVERS) return new Snapshot(List.of(), Error.INVALID_STATUS);
            var ids = new HashSet<String>();
            var commands = new HashSet<String>();
            var drivers = new ArrayList<Driver>();
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                String id = item.getString("id"), command = item.getString("command_id");
                String name = item.getString("name");
                Object stateValue = item.get("state"), speedValue = item.get("speed_millis");
                if (!(stateValue instanceof Integer) || !(speedValue instanceof Integer)
                        || !(item.get("once") instanceof Boolean))
                    return new Snapshot(List.of(), Error.INVALID_STATUS);
                int state = (Integer) stateValue, speed = (Integer) speedValue;
                if (id.isEmpty() || id.length() > 1024 || name.length() > 4096
                        || command.length() > 48 || !command.matches("[0-9]+:[0-9]+")
                        || !ids.add(id) || !commands.add(command) || state < 0 || state > 3
                        || speed < MIN_SPEED || speed > MAX_SPEED)
                    return new Snapshot(List.of(), Error.INVALID_STATUS);
                drivers.add(new Driver(id, command, name.isEmpty() ? id : name,
                        state, item.getBoolean("once"), speed));
            }
            return new Snapshot(List.copyOf(drivers), Error.NONE);
        } catch (org.json.JSONException invalid) {
            return new Snapshot(List.of(), Error.INVALID_STATUS);
        }
    }

    static Result apply(Transport transport, Driver driver, int state, int speed) {
        if (state < 0 || state > 2 || speed < MIN_SPEED || speed > MAX_SPEED)
            return new Result(read(transport), Error.INVALID_COMMAND);
        try {
            boolean accepted = transport.set(driver.commandId(), state, speed);
            Snapshot observed = read(transport);
            return new Result(observed, accepted ? observed.error() : Error.REJECTED);
        } catch (RuntimeException | LinkageError failure) {
            return new Result(new Snapshot(List.of(), Error.UNAVAILABLE), Error.UNAVAILABLE);
        }
    }
}
