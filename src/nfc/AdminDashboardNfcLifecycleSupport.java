package nfc;

final class AdminDashboardNfcLifecycleSupport {
    interface ErrorLogger {
        void log(String context, Exception error);
    }

    static final class ReaderSession {
        final NFCReader reader;
        final Thread thread;

        ReaderSession(NFCReader reader, Thread thread) {
            this.reader = reader;
            this.thread = thread;
        }
    }

    private AdminDashboardNfcLifecycleSupport() {
    }

    static ReaderSession restartReader(NFCReader currentReader, Thread currentThread, ErrorLogger logger) {
        shutdownReader(currentReader, currentThread, logger);

        String portName = NFCReader.resolveConfiguredPortName();
        if (portName == null) {
            System.out.println("ℹ️ NFC reader disabled by configuration.");
            return new ReaderSession(null, null);
        }

        if (!NFCReader.isPortAvailable(portName)) {
            System.out.println("ℹ️ NFC reader not started. Port " + portName + " is unavailable. Available ports: " + NFCReader.availablePortsSummary());
            return new ReaderSession(null, null);
        }

        NFCReader reader = new NFCReader(portName);
        Thread thread = new Thread(reader);
        thread.setName("taska-nfc-reader");
        thread.setDaemon(true);
        thread.start();
        return new ReaderSession(reader, thread);
    }

    static void shutdownReader(NFCReader currentReader, Thread currentThread, ErrorLogger logger) {
        if (currentReader != null) {
            currentReader.stopReading();
        }
        if (currentThread != null && currentThread.isAlive()) {
            currentThread.interrupt();
            try {
                currentThread.join();
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.log("interrupted while stopping NFC reader", ex);
            }
        }
    }
}