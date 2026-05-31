package com.roomix.api.exception;

public class InsufficientTokensException extends RuntimeException {

    private final int required;
    private final int available;

    public InsufficientTokensException(int required, int available) {
        super(String.format(
                "Tokens insuffisants : %d requis, vous avez %d. Rechargez votre solde pour continuer.",
                required, available));
        this.required  = required;
        this.available = available;
    }

    public int getRequired()  { return required;  }
    public int getAvailable() { return available; }
}
