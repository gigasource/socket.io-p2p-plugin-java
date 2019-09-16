package io.gigasource.p2p_client.exception;

public class InvalidConnectionStateException extends RuntimeException {
    public InvalidConnectionStateException(String message) {
        super(message);
    }
}
