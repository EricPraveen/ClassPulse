package com.classpulse.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Standard application message envelope for the ClassPulse protocol.
 * <p>
 * Every message contains:
 * <ul>
 *     <li>{@code type}: The operation category ({@link MessageType}).</li>
 *     <li>{@code requestId}: Unique correlation ID matching requests to responses.</li>
 *     <li>{@code payload}: Key-value dictionary containing operation-specific data.</li>
 * </ul>
 */
public class Message {

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private MessageType type;
    private String requestId;
    private Map<String, Object> payload;

    public Message() {
        this.payload = new LinkedHashMap<>();
    }

    public Message(MessageType type, String requestId) {
        this.type = type;
        this.requestId = requestId;
        this.payload = new LinkedHashMap<>();
    }

    public Message(MessageType type, String requestId, Map<String, Object> payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = (payload != null) ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getPayload() {
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = (payload != null) ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    /**
     * Put a key-value pair into the payload.
     *
     * @param key   payload field key
     * @param value payload field value
     * @return this Message instance for method chaining
     */
    public Message putPayload(String key, Object value) {
        if (this.payload == null) {
            this.payload = new LinkedHashMap<>();
        }
        this.payload.put(key, value);
        return this;
    }

    /**
     * Retrieve a raw object from the payload.
     */
    public Object getPayloadField(String key) {
        return payload != null ? payload.get(key) : null;
    }

    /**
     * Retrieve a string field from the payload.
     */
    public String getPayloadString(String key) {
        Object val = getPayloadField(key);
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Retrieve an integer field from the payload. Handles numeric conversions safely.
     */
    public Integer getPayloadInt(String key) {
        Object val = getPayloadField(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Retrieve a double field from the payload.
     */
    public Double getPayloadDouble(String key) {
        Object val = getPayloadField(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Retrieve a boolean field from the payload.
     */
    public Boolean getPayloadBoolean(String key) {
        Object val = getPayloadField(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return Boolean.parseBoolean(val.toString());
    }

    /**
     * Retrieve a list field from the payload.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getPayloadList(String key) {
        Object val = getPayloadField(key);
        if (val instanceof List<?>) {
            return (List<T>) val;
        }
        return null;
    }

    /**
     * Validates that required envelope fields are present and valid.
     *
     * @throws IllegalArgumentException if type or requestId is missing
     */
    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Message 'type' must not be null");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("Message 'requestId' must not be null or blank");
        }
    }

    /**
     * Serializes this message to a UTF-8 JSON string.
     */
    public String toJson() {
        validate();
        return GSON.toJson(this);
    }

    /**
     * Deserializes a JSON string into a validated {@link Message}.
     *
     * @param json UTF-8 JSON string
     * @return parsed Message instance
     * @throws IllegalArgumentException if the JSON is malformed or required envelope fields are missing
     */
    public static Message fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string must not be null or empty");
        }
        try {
            Message msg = GSON.fromJson(json, Message.class);
            if (msg == null) {
                throw new IllegalArgumentException("Parsed message cannot be null");
            }
            msg.validate();
            if (msg.payload == null) {
                msg.payload = new LinkedHashMap<>();
            }
            return msg;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed JSON string: " + e.getMessage(), e);
        }
    }

    /**
     * Factory helper to construct a standardized ERROR response.
     */
    public static Message error(String requestId, String code, String message) {
        Message msg = new Message(MessageType.ERROR, requestId);
        msg.putPayload("code", code);
        msg.putPayload("message", message);
        return msg;
    }

    /**
     * Factory helper to construct a standardized SUCCESS response.
     */
    public static Message success(String requestId, String message) {
        Message msg = new Message(MessageType.SUCCESS, requestId);
        msg.putPayload("message", message);
        return msg;
    }

    /**
     * Factory helper to construct a request message.
     */
    public static Message request(MessageType type, String requestId) {
        return new Message(type, requestId);
    }

    /**
     * Factory helper to construct a response message.
     */
    public static Message response(MessageType type, String requestId) {
        return new Message(type, requestId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        if (type != message.type) return false;
        if (!Objects.equals(requestId, message.requestId)) return false;
        if (payload == null && message.payload == null) return true;
        if (payload == null || message.payload == null) return false;
        if (payload.size() != message.payload.size()) return false;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!message.payload.containsKey(entry.getKey())) return false;
            Object v1 = entry.getValue();
            Object v2 = message.payload.get(entry.getKey());
            if (!areValuesEqual(v1, v2)) return false;
        }
        return true;
    }

    private static boolean areValuesEqual(Object o1, Object o2) {
        if (Objects.equals(o1, o2)) return true;
        if (o1 instanceof Number && o2 instanceof Number) {
            return Double.compare(((Number) o1).doubleValue(), ((Number) o2).doubleValue()) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, requestId, payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", requestId='" + requestId + '\'' +
                ", payload=" + payload +
                '}';
    }
}
