package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.logging.Level;
import java.util.logging.Logger;

import message.Operation;
import message.OperationType;
import message.fourstep.chainhash_lsn.*;
import service.Config;
import service.handler.fourstep.ChainHashAndLSNHandler;
import service.handler.fourstep.HashingChainTable;
import service.handler.fourstep.LSNTable;
import utility.Utils;

/**
 *
 * @author Scott
 */
public class ChainHashAndLSNClient extends Client {
    private static final File ATTESTATION;
    
    static {
        ATTESTATION = new File(Config.ATTESTATION_DIR_PATH + "/client/chainhash-lsn");
    }
    
    private final String id;
    private int lsn;
    
    public ChainHashAndLSNClient(String id, KeyPair keyPair, KeyPair spKeyPair) {
        this(Config.SERVICE_HOSTNAME, Config.CHAINHASH_LSN_SERVICE_PORT, id, keyPair, spKeyPair);
    }
    
    public ChainHashAndLSNClient(String hostname,
                                 int port,
                                 String id,
                                 KeyPair keyPair,
                                 KeyPair spKeyPair) {
        super(hostname, port, keyPair, spKeyPair);
        
        this.id = id;
        this.lsn = 1;
    }
    
    @Override
    protected void hook(Operation op, Socket socket, DataOutputStream out, DataInputStream in)
            throws SignatureException, IllegalAccessException {
        Request req = new Request(op, id, lsn);

        req.sign(keyPair);

        Utils.send(out, req.toString());

        Response res = Response.parse(Utils.receive(in));

        if (!res.validate(spKeyPair.getPublic())) {
            throw new SignatureException("RES validation failure");
        }

        ReplyResponse rr = new ReplyResponse(res);

        rr.sign(keyPair);

        Utils.send(out, rr.toString());

        Acknowledgement ack = Acknowledgement.parse(Utils.receive(in));

        if (!ack.validate(spKeyPair.getPublic())) {
            throw new SignatureException("ACK validation failure");
        }

        String result = ack.getResult();

        lsn += 1;

        switch (op.getType()) {
            case AUDIT:
            case DOWNLOAD:
                String fname = Config.DOWNLOADS_DIR_PATH + '/' + op.getPath();

                File file = new File(fname);

                Utils.receive(in, file);

                String digest = Utils.digest(file);

                if (result.compareTo(digest) == 0) {
                    result = "download success";
                } else {
                    result = "download file digest mismatch";
                }

                break;
        }

        long start = System.currentTimeMillis();
        Utils.write(ATTESTATION, ack.toString());
        this.attestationCollectTime += System.currentTimeMillis() - start;
    }
    
    public boolean audit(File attestation, PublicKey cliKey, PublicKey spKey) {
        boolean success = true;
        
        LSNTable lsnTab = new LSNTable();
        HashingChainTable hashingChainTab = new HashingChainTable();
        
        try (FileReader fr = new FileReader(attestation);
             BufferedReader br = new BufferedReader(fr)) {
            do {
                String s = br.readLine();
                
                if (s == null) {
                    break;
                }
                
                Acknowledgement ack = Acknowledgement.parse(s);
                ReplyResponse rr = ack.getReplyResponse();
                Response res = rr.getResponse();
                Request req = res.getRequest();
                
                String clientID = req.getClientID();
                
                if (lsnTab.isMatched(clientID, req.getLocalSequenceNumber())) {
                    lsnTab.increment(clientID);
                } else {
                    success = false;
                }
                
                if (hashingChainTab.getLastChainHash(clientID).compareTo(res.getChainHash()) == 0) {
                    hashingChainTab.chain(clientID, Utils.digest(ack.toString()));
                } else {
                    success = false;
                }
                
                success &= ack.validate(spKey) & rr.validate(cliKey);
                success &= res.validate(spKey) & req.validate(cliKey);
            } while (success);
        } catch (IOException ex) {
            success = false;
            
            Logger.getLogger(ChainHashAndLSNClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return success;
    }
    
    public static void main(String[] args) {
        KeyPair keypair = Config.KeyPair.CLIENT.getKeypair();
        KeyPair spKeypair = Config.KeyPair.SERVICE_PROVIDER.getKeypair();
        ChainHashAndLSNClient client = new ChainHashAndLSNClient("client", keypair, spKeypair);
        Operation op = new Operation(OperationType.DOWNLOAD, Config.FILE.getName(), "");
        
        System.out.println("Running:");
        
        long time = System.currentTimeMillis();
        for (int i = 1; i <= Config.NUM_RUNS; i++) {
            client.run(op);
        }
        time = System.currentTimeMillis() - time;
        
        System.out.println(Config.NUM_RUNS + " times cost " + (time - client.attestationCollectTime) + "ms (without collect attestations)");
        System.out.println("Collect attestations cost " + client.attestationCollectTime + "ms");
        
        System.out.println("Auditing:");
        
        op = new Operation(OperationType.AUDIT, ChainHashAndLSNHandler.ATTESTATION.getPath(), "");
        
        client.run(op);
        
        File auditFile = new File(Config.DOWNLOADS_DIR_PATH + '/' + ChainHashAndLSNHandler.ATTESTATION.getPath());
        
        // to prevent ClassLoader's init overhead
        client.audit(auditFile, keypair.getPublic(), spKeypair.getPublic());
        
        time = System.currentTimeMillis();
        boolean audit = client.audit(auditFile,
                                     keypair.getPublic(), spKeypair.getPublic());
        time = System.currentTimeMillis() - time;
        
        System.out.println("Audit: " + audit + ", cost " + time + "ms");
    }
}
