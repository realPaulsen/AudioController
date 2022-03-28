package ooo.paulsen.audiocontrol;

import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import ooo.paulsen.Main;
import ooo.paulsen.io.PCustomProtocol;
import ooo.paulsen.io.serial.PSerialConnection;
import ooo.paulsen.io.serial.PSerialListener;
import ooo.paulsen.utils.PSystem;

import java.util.ArrayList;

public class AudioManager {


    // Serial
    private PSerialConnection serial;
    private PSerialListener listener;
    private PCustomProtocol audioControlProtocol_NEW, audioControlProtocol_OLD;

    // AudioControl
    private AudioController controller;
    private ArrayList<String> processes = new ArrayList<>();

    /**
     * @throws Exception when the Controller is not supporting the current Operating-System
     */
    public AudioManager() throws Exception {
        initListener();
        initController();

        audioControlProtocol_OLD = new PCustomProtocol("am", '[', ']', "^{^", "^}^");
        audioControlProtocol_NEW = new PCustomProtocol("ac", '[', ']', "^{^", "^}^");
    }

    private void initController() throws Exception {

        switch (PSystem.getOSType()) {
            case LINUX: {
                System.out.println("[AudioManager] :: Detected OS: Linux");
                controller = new AudioControllerLinux();
                break;
            }
            case WINDOWS: {
                System.out.println("[AudioManager] :: Detected OS: Windows");
                controller = new AudioControllerWin();
                break;
            }
            default: {
                throw new Exception("This Operating-System is not supported!\nDetected: " + PSystem.getOSType());
            }
        }

    }

    private void initListener() {
        listener = new PSerialListener() {
            @Override
            public synchronized void readLine(String s) {
//                System.out.println(System.currentTimeMillis() + " Incomming from " + serial.getPortName() + ": " + s);

                String message1 = audioControlProtocol_NEW.getMessage(s); // new Arduino-Software
                String message2 = audioControlProtocol_OLD.getMessage(s); // old Arduino-Software (deprecated)

                if (message1 != null || message2 != null) {
                    if (message1 != null)
                        s = message1.replace("=>", "");
                    else
                        s = message2.replace("=>", "");

                    String controlName = "", volumeInt = "";
                    boolean hasName = false;
                    for (int i = 0; i < s.length(); i++) {
                        if (hasName)
                            volumeInt += s.charAt(i);
                        else if (s.charAt(i) == '|')
                            hasName = true;
                        else {
                            controlName += s.charAt(i);
                        }
                    }

                    if (Main.getControl(controlName) == null) {
                        Main.createControl(controlName);
                    }

                    try {
                        float controlVolume = (float) Math.min(Math.max(Integer.parseInt(volumeInt), 0), 1000) / 1000;

                        Main.setControlVolume(controlName, controlVolume);
                    } catch (NumberFormatException e) {
                    }
                }

                // if s is part of no protocol the line gets ignored!
            }
        };
    }

    public boolean connectToSerial(String port) {
        try {
            if (serial != null)
                serial.disconnect();

            serial = new PSerialConnection(port);
            serial.setDisconnectEvent(new Runnable() {
                @Override
                public void run() {
                    Main.ui.updateCurrentSerialConnection();
                }
            });
            serial.addListener(listener);
            return serial.connect();
        } catch (SerialPortInvalidPortException e) {
            return false;
        }
    }

    public boolean disconnectSerial() {
        if (serial != null)
            return serial.disconnect();
        return true;
    }

    public void setVolume(String process, float volume) {
        controller.setVolume(process, volume);
    }

    public ArrayList<String> getProcesses() {
        return processes;
    }

    public void refreshProcesses() {
        if (controller != null) {
            controller.refreshProcesses();
            processes = controller.getProcesses();
        }
    }

    /**
     * exits Serial and AudioController
     */
    public void stop() {
        disconnectSerial();
        controller.stop();
    }

    public String getPortName() {
        if (serial != null)
            return serial.getPortName();
        return null;
    }

    public boolean isSerialConnected() {
        if (serial != null)
            return serial.isConnected();
        return false;
    }

    public boolean isAudioConnected() {
        return controller.isAudioConnected();
    }

}
