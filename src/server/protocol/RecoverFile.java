package server.protocol;

import server.Server;
import server.Controller;
import server.messaging.MessageBuilder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static server.Server.*;

public class RecoverFile {
    private final String filename;
    private final String fileId;
    private Controller controller;
    private int numChunks;
    private ConcurrentHashMap<Integer, byte[]> receivedChunks;

    public RecoverFile(String filename, String fileId) {
        this.filename = filename;
        this.fileId = fileId;
    }


    public void start(Controller controller) throws FileNotFoundException {
        this.controller = controller;

        /* If numChunks == 0, then the file is not backed up in the network */
        numChunks = controller.getNumChunks(fileId);

        if (numChunks == 0)
            throw new FileNotFoundException("File not found in the network.");

        receivedChunks = new ConcurrentHashMap<>(numChunks);

        for (int chunkNo = 0; chunkNo < numChunks; chunkNo++)  /* TODO: Afterwards, do not ask for all chunks at once.*/
            requestChunk(chunkNo);
    }

    private void requestChunk(int chunkNo) {
        new Thread(() -> {
            byte[] message = MessageBuilder.createMessage(Server.RESTORE_INIT, getProtocolVersion(), Integer.toString(getServerId()), fileId, Integer.toString(chunkNo));
            do {
                controller.sendToRecoveryChannel(message);
                try {
                    Thread.sleep(RESTORE_REPLY_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (receivedChunks.get(chunkNo) == null);

        }).start();
    }

    public void putChunk(int chunkNo, byte[] chunk) {
        receivedChunks.put(chunkNo, chunk);

        if (receivedChunks.size() == numChunks)
            recoverFile();
    }

    private void recoverFile() {
        new Thread(() -> {
            FileOutputStream fileOutputStream;
            try {
                fileOutputStream = new FileOutputStream(filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }

            for (int chunkNo = 0; chunkNo < receivedChunks.size(); chunkNo++)
                try {
                    System.out.println(chunkNo);
                    String temp = new String(receivedChunks.get(chunkNo).toString());
                    System.out.println(temp); //nao da erro
                    System.out.println(receivedChunks.get(chunkNo).length); //nao da erro
                    fileOutputStream.write(receivedChunks.get(chunkNo), chunkNo*CHUNK_SIZE, receivedChunks.get(chunkNo).length - chunkNo*CHUNK_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }).start();
    }

    public String getFileId() {
        return fileId;
    }
}
