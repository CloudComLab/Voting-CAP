package wei_chih.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.SignatureException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import client.Client;
import message.Operation;
import message.OperationType;
import wei_chih.service.Config;
import wei_chih.utility.Utils;
import wei_chih.utility.MerkleTree;
import wei_chih.message.wei_chih.Request;
import wei_chih.message.wei_chih.Acknowledgement;

/**
 *
 * @author Chienweichih
 */
public class WeiChihClient extends Client {

    private static final Logger LOGGER;

    static {
        LOGGER = Logger.getLogger(WeiChihClient.class.getName());
    }

    private Acknowledgement acknowledgement;

    private Map<Integer, Integer> sequenceNumbers;
    private final Map<Integer, Acknowledgement> syncAcks;
    private final Map<Integer, Acknowledgement> acks;

    public WeiChihClient(KeyPair keyPair, KeyPair spKeyPair) {
        super(Config.SERVICE_HOSTNAME,
                Experiment.SYNC_PORT,
                keyPair,
                spKeyPair,
                true);

        syncAcks = new HashMap<>();
        acks = new HashMap<>();

        for (int p : Experiment.SERVER_PORTS) {
            syncAcks.put(p, null);
            acks.put(p, null);
        }
    }

    @Override
    public void run(final List<Operation> operations, int runTimes) {
        System.out.println("Running (" + runTimes + " times):");

        double[] results = new double[runTimes];

        // for best result
        for (int i = 1; i <= runTimes; i++) {
            final int x = i;
            pool.execute(() -> {

                try (Socket syncSocket = new Socket(Config.SYNC_HOSTNAME, Experiment.SYNC_PORT);
                        DataOutputStream syncOut = new DataOutputStream(syncSocket.getOutputStream());
                        DataInputStream SyncIn = new DataInputStream(syncSocket.getInputStream())) {
                    Operation op = operations.get(x % operations.size());

                    boolean syncSuccess = syncAtts(new Operation(OperationType.DOWNLOAD,
                            Config.EMPTY_STRING,
                            (op.getType() == OperationType.UPLOAD) ? Config.EMPTY_STRING : "Download Please"),
                            syncOut,
                            SyncIn);
                    if (!syncSuccess) {
                        System.err.println("Sync Error");
                    }

                    ///////////////////////////////////////////////////////////////////////////////////////////////////
                    int diffPort = execute(op, Experiment.SERVER_PORTS[0]);
                    if (diffPort != -1) {
                        execute(new Operation(OperationType.AUDIT,
                                "/ATT_FOR_AUDIT",
                                Config.EMPTY_STRING),
                                diffPort);
                        boolean audit = audit(diffPort);
                        System.out.println("Audit: " + audit);
                    }

                    ///////////////////////////////////////////////////////////////////////////////////////////////////
                    syncSuccess = syncAtts(new Operation(OperationType.UPLOAD,
                            Config.EMPTY_STRING,
                            (op.getType() == OperationType.UPLOAD) ? "Upload Please" : Config.EMPTY_STRING),
                            syncOut,
                            SyncIn);
                    if (!syncSuccess) {
                        System.err.println("Sync Error");
                    }

                    syncSocket.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            });
        }

        for (int i = 1; i <= runTimes; i++) {
            final int x = i;
            pool.execute(() -> {
                long time = System.nanoTime();
                try (Socket syncSocket = new Socket(Config.SYNC_HOSTNAME, Experiment.SYNC_PORT);
                        DataOutputStream syncOut = new DataOutputStream(syncSocket.getOutputStream());
                        DataInputStream SyncIn = new DataInputStream(syncSocket.getInputStream())) {
                    Operation op = operations.get(x % operations.size());

                    boolean syncSuccess = syncAtts(new Operation(OperationType.DOWNLOAD,
                            Config.EMPTY_STRING,
                            (op.getType() == OperationType.UPLOAD) ? Config.EMPTY_STRING : "Download Please"),
                            syncOut,
                            SyncIn);
                    if (!syncSuccess) {
                        System.err.println("Sync Error");
                    }

                    ///////////////////////////////////////////////////////////////////////////////////////////////////
                    int diffPort = execute(op, Experiment.SERVER_PORTS[0]);
                    if (diffPort != -1) {
                        execute(new Operation(OperationType.AUDIT,
                                "/ATT_FOR_AUDIT",
                                Config.EMPTY_STRING),
                                diffPort);
                        boolean audit = audit(diffPort);
                        System.out.println("Audit: " + audit);
                    }

                    ///////////////////////////////////////////////////////////////////////////////////////////////////
                    syncSuccess = syncAtts(new Operation(OperationType.UPLOAD,
                            Config.EMPTY_STRING,
                            (op.getType() == OperationType.UPLOAD) ? "Upload Please" : Config.EMPTY_STRING),
                            syncOut,
                            SyncIn);
                    if (!syncSuccess) {
                        System.err.println("Sync Error");
                    }

                    syncSocket.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
                results[x - 1] = (System.nanoTime() - time) / 1e9;
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        Utils.printExperimentResult(results);

        runAudit();
    }

    private void runAudit() {
        System.out.println("Auditing:");

        long time = System.nanoTime();
        execute(new Operation(OperationType.AUDIT,
                "/ATT_FOR_AUDIT",
                Config.EMPTY_STRING),
                Experiment.SERVER_PORTS[0]);
        System.out.println("Download attestation, cost " + (System.nanoTime() - time) / 1e9 + " s");

        try (Socket syncSocket = new Socket(Config.SYNC_HOSTNAME, Experiment.SYNC_PORT);
                DataOutputStream syncOut = new DataOutputStream(syncSocket.getOutputStream());
                DataInputStream SyncIn = new DataInputStream(syncSocket.getInputStream())) {

            boolean syncSuccess = syncAtts(new Operation(OperationType.DOWNLOAD,
                    Config.EMPTY_STRING,
                    "Download Please"),
                    syncOut,
                    SyncIn);
            if (!syncSuccess) {
                System.err.println("Sync Error");
            }

            ///////////////////////////////////////////////////////////////////////////////////////////////////
            time = System.nanoTime();
            int testPort = Experiment.SERVER_PORTS[0];
            boolean audit = audit(testPort);
            System.out.println("Audit: " + audit + ", cost " + (System.nanoTime() - time) / 1e9 + " s");

            ///////////////////////////////////////////////////////////////////////////////////////////////////
            syncSuccess = syncAtts(new Operation(OperationType.UPLOAD,
                    Config.EMPTY_STRING,
                    Config.EMPTY_STRING),
                    syncOut,
                    SyncIn);
            if (!syncSuccess) {
                System.err.println("Sync Error");
            }

            syncSocket.close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public final int execute(Operation op, int auditPort) {
        if (op.getType() == OperationType.AUDIT) {
            try (Socket socket = new Socket(hostname, auditPort);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {
                handle(op, socket, out, in);
                socket.close();
            } catch (IOException | SignatureException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            return -1;
        }

        Map<Integer, String> results = new HashMap<>();

        for (int p : Experiment.SERVER_PORTS) {
            try (Socket socket = new Socket(hostname, p);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {
                int sn = sequenceNumbers.get(p);
                op = new Operation(op.getType(), op.getPath(), op.getMessage(), Integer.toString(sn));
                handle(op, socket, out, in);

                sequenceNumbers.replace(p, sn + 1);
                acks.replace(p, acknowledgement);
                switch (op.getType()) {
                    case DOWNLOAD:
                        results.put(p, acknowledgement.getFileHash());
                        break;
                    case UPLOAD:
                        results.put(p, acknowledgement.getRoothash());
                }

                socket.close();
            } catch (IOException | SignatureException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return voting(results);
    }

    @Override
    protected void handle(Operation op, Socket socket, DataOutputStream out, DataInputStream in)
            throws SignatureException {
        Request req = new Request(op);
        req.sign(keyPair);
        Utils.send(out, req.toString());

        acknowledgement = Acknowledgement.parse(Utils.receive(in));

        if (!acknowledgement.validate(spKeyPair.getPublic())) {
            throw new SignatureException("ACK validation failure");
        }

        File file = new File(Config.DOWNLOADS_DIR_PATH + op.getPath());
        switch (op.getType()) {
            case DOWNLOAD:
                if (socket.getPort() == Config.SERVICE_PORT[0]
                        || socket.getLocalPort() == Config.SERVICE_PORT[0]) {
                    Utils.receive(in, file);
                }
                break;
            case UPLOAD:
                if (socket.getPort() == Config.SERVICE_PORT[0]
                        || socket.getLocalPort() == Config.SERVICE_PORT[0]) {
                    Utils.send(out, new File(Experiment.dataDirPath + op.getPath()));
                }
                break;
            case AUDIT:
                Utils.receive(in, file);
                break;
            default:
        }
    }

    @Override
    public boolean audit(File spFile) {
        return audit(-1);
    }

    public boolean audit(int port) {
        if (port == -1) {
            return false;
        }

        Acknowledgement lastAck = syncAcks.get(port);
        String lastRootHash = lastAck.getRoothash();

        Acknowledgement thisack = acks.get(port);
        String thisRootHash = thisack.getRoothash();

        String serverGaveMeRoothash = null;
        String meCalculateRoothash = null;

        String attFileName = Config.DOWNLOADS_DIR_PATH + "/ATT_FOR_AUDIT";
        Operation thisOp = thisack.getRequest().getOperation();

        switch (thisOp.getType()) {
            case DOWNLOAD:
                serverGaveMeRoothash = Utils.read(attFileName);
                meCalculateRoothash = serverGaveMeRoothash;
                break;
            case UPLOAD:
                MerkleTree merkleTree = Utils.Deserialize(attFileName);
                serverGaveMeRoothash = merkleTree.getRootHash();

                merkleTree.update(thisOp.getPath(), thisOp.getMessage());
                meCalculateRoothash = merkleTree.getRootHash();

                break;
            default:
        }

        return 0 == lastRootHash.compareTo(serverGaveMeRoothash) + thisRootHash.compareTo(meCalculateRoothash);
    }

    private int voting(Map<Integer, String> inputs) {
        Map<String, Integer> occurrenceCount = new HashMap<>();
        String currentMaxElement = (String) inputs.get(Experiment.SERVER_PORTS[0]);

        for (String element : inputs.values()) {
            Integer elementCount = occurrenceCount.get(element);
            if (elementCount != null) {
                occurrenceCount.put(element, elementCount + 1);
                if (elementCount >= occurrenceCount.get(currentMaxElement)) {
                    currentMaxElement = element;
                }
            } else {
                occurrenceCount.put(element, 1);
            }
        }

        for (Integer port_i : inputs.keySet()) {
            if (0 != currentMaxElement.compareTo(inputs.get(port_i))) {
                return port_i;
            }
        }

        return -1;
    }

    private boolean syncAtts(Operation op, DataOutputStream out, DataInputStream in) {
        File syncSN = new File(Config.DOWNLOADS_DIR_PATH + "/syncSN");
        File syncAck = new File(Config.DOWNLOADS_DIR_PATH + "/syncAck");
        Map<Integer, String> syncAckStrs = new HashMap<>();

        Request req = new Request(op);
        req.sign(keyPair);
        Utils.send(out, req.toString());

        switch (op.getType()) {
            case DOWNLOAD:
                Utils.receive(in, syncSN);
                sequenceNumbers = Utils.Deserialize(syncSN.getAbsolutePath());
                break;
            case UPLOAD:
                Utils.Serialize(syncSN, sequenceNumbers);
                Utils.send(out, syncSN);
                break;
            default:
                return false;
        }

        if (0 == op.getMessage().compareTo(Config.EMPTY_STRING)) {
            return true;
        }

        switch (op.getType()) {
            case DOWNLOAD:

                Utils.receive(in, syncAck);
                syncAckStrs = Utils.Deserialize(syncAck.getAbsolutePath());

                for (int p : Experiment.SERVER_PORTS) {
                    if (syncAckStrs.get(p) != null) {
                        syncAcks.replace(p, Acknowledgement.parse(syncAckStrs.get(p)));
                    }
                }
                break;
            case UPLOAD:
                for (int p : Experiment.SERVER_PORTS) {
                    String lastStr = (acks.get(p) == null) ? null : acks.get(p).toString();
                    syncAckStrs.put(p, lastStr);
                }

                Utils.Serialize(syncAck, syncAckStrs);
                Utils.send(out, syncAck);
                break;
            default:
                return false;
        }

        return true;
    }

    @Override
    public String getHandlerAttestationPath() {
        throw new java.lang.UnsupportedOperationException();
    }
}
