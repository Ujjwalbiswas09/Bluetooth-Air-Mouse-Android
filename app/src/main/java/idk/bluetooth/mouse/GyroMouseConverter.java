package idk.bluetooth.mouse;


import android.hardware.SensorEvent;

public class GyroMouseConverter {

    private static final float DEADZONE = 0.05f;
    public float SENSITIVITY = 10.0f;
    private static final float ACCELERATION = 1.2f;

    private float accumulatedX = 0.0f;
    private float accumulatedY = 0.0f;

    private float smoothedGyroX = 0.0f;
    private float smoothedGyroY = 0.0f;

    public byte[] convertGyroToMouse(SensorEvent event) {
        float rawGyroX = event.values[2];
        float rawGyroY = event.values[0];

        // Calculate magnitude of raw gyro movement to dynamically adjust low-pass filter responsiveness
        float speed = (float) Math.sqrt(rawGyroX * rawGyroX + rawGyroY * rawGyroY);

        // If speed is very low (within deadzone range), use a larger alpha to decay quickly to a complete stop.
        // Otherwise, scale alpha from 0.25f to 1.0f based on speed to balance smoothing and latency.
        float alpha;
        if (speed < DEADZONE) {
            alpha = 0.5f;
        } else {
            alpha = 0.25f + Math.min(0.75f, (speed - DEADZONE) * 1.5f);
        }

        smoothedGyroX = alpha * rawGyroX + (1.0f - alpha) * smoothedGyroX;
        smoothedGyroY = alpha * rawGyroY + (1.0f - alpha) * smoothedGyroY;

        float filteredX = applyDeadzone(smoothedGyroX);
        float filteredY = applyDeadzone(smoothedGyroY);

        float mouseXSpeed = applySensitivityAndAcceleration(filteredX);
        float mouseYSpeed = applySensitivityAndAcceleration(filteredY);

        accumulatedX -= mouseXSpeed;
        accumulatedY -= mouseYSpeed;

        int moveX = (int) accumulatedX;
        int moveY = (int) accumulatedY;

        accumulatedX -= moveX;
        accumulatedY -= moveY;

        byte reportX = (byte) Math.max(-126, Math.min(126, moveX));
        byte reportY = (byte) Math.max(-126, Math.min(126, moveY));

        return new byte[]{reportX, reportY};
    }

    private float applyDeadzone(float value) {
        if (Math.abs(value) < DEADZONE) {
            return 0.0f;
        }
        // Soft deadzone: subtract deadzone to prevent jump discontinuities when starting movement
        return Math.signum(value) * (Math.abs(value) - DEADZONE);
    }

    private float applySensitivityAndAcceleration(float value) {
        if (value == 0.0f) return 0.0f;
        float scaled = value * SENSITIVITY;
        return Math.signum(scaled) * (float) Math.pow(Math.abs(scaled), ACCELERATION);
    }
}