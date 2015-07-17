package service.handler.twostep;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.logging.Level;
import java.util.logging.Logger;

import message.Operation;
import message.twostep.csn.*;
import service.Config;
import service.handler.ConnectionHandler;
import utility.Utils;

/**
 *
 * @author Scott
 */
public class CSNHandler implements ConnectionHandler {
    public static final File ATTESTATION;
    
    private static int CSN;
    private final Socket socket;
    private final KeyPair keyPair;
    
    static {
        ATTESTATION = new File(Config.ATTESTATION_DIR_PATH + "/service-provider/csn");
        CSN = 0;
    }
    
    public CSNHandler(Socket socket, KeyPair keyPair) {
        this.socket = socket;
        this.keyPair = keyPair;
    }
    
    @Override
    public void run() {
        PublicKey clientPubKey = service.KeyPair.CLIENT.getKeypair().getPublic();
        
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            Request req = Request.parse(Utils.receive(in));
            
            if (!req.validate(clientPubKey)) {
                throw new SignatureException("REQ validation failure");
            }
            
            String result;
            
            Operation op = req.getOperation();
            
            File file = new File(Config.DATA_DIR_PATH + '/' + op.getPath());
            boolean sendFileAfterAck = false;
            
            if (req.getConsecutiveSequenceNumber() == CSN + 1) {
                CSN += 1;
                
                String fname = Config.DATA_DIR_PATH + "/" + file.getName();
                
                switch (op.getType()) {
                    case UPLOAD:
                        file = new File(Config.DOWNLOADS_DIR_PATH + '/' + op.getPath());
                        
                        Utils.receive(in, file);
                        
                        String digest = Utils.digest(file);
                        
                        if (op.getMessage().compareTo(digest) == 0) {
                            result = "ok";
                        } else {
                            result = "upload fail";
                        }
                        
                        Utils.writeDigest(file.getPath(), digest);
                        
                        break;
                    case AUDIT:
                        file = new File(op.getPath());
                        
                        result = Utils.readDigest(file.getPath());
                        
                        sendFileAfterAck = true;
                        
                        break;
                    case DOWNLOAD:
                        result = Utils.readDigest(file.getPath());
                        
                        sendFileAfterAck = true;
                        
                        break;
                    default:
                        result = "operation type mismatch";
                }
            } else {
                result = "CSN mismatch";
            }
            
            Acknowledgement ack = new Acknowledgement(result, req);
            
            ack.sign(keyPair);
            
            Utils.send(out, ack.toString());
            
            if (sendFileAfterAck) {
                Utils.send(out, file);
            }
            
            Utils.appendAndDigest(ATTESTATION, ack.toString() + '\n');
            
            socket.close();
        } catch (IOException | SignatureException ex) {
            Logger.getLogger(CSNHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
