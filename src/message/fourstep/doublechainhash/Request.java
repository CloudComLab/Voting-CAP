package message.fourstep.doublechainhash;

import message.Operation;
import message.OperationType;
import message.SOAPMessage;
import org.w3c.dom.NodeList;

/**
 *
 * @author Scott
 */
public class Request extends SOAPMessage {
    private static final long serialVersionUID = 20141013L;
    private final Operation operation;
    private final String clientID;
    
    public Request(Operation op, String id) {
        super("request");
        
        this.operation = op;
        this.clientID = id;
        
        add2Body(operation.toMap());
        add2Body("client-id", clientID);
    }
    
    private Request(javax.xml.soap.SOAPMessage message) {
        super(message);
        
        NodeList body = getBody();
        NodeList operation = body.item(0).getChildNodes();
        
        OperationType opType = OperationType.valueOf(operation.item(0).getTextContent());
        String path = operation.item(1).getTextContent();
        String msg = operation.item(2).getTextContent();
        String opClientID = operation.item(3).getTextContent();
        
        this.operation = new Operation(opType, path, msg, opClientID);
        this.clientID = body.item(1).getTextContent();
    }
    
    public Operation getOperation() {
        return operation;
    }
    
    public String getClientID() {
        return clientID;
    }
    
    public static Request parse(String receive) {
        return new Request(SOAPMessage.parseSOAP(receive));
    }
}
